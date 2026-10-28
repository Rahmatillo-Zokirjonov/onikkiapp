package com.onikki.app.data.repository

import android.content.Context
import com.onikki.app.data.db.AppDatabase
import com.onikki.app.data.db.entity.TimetableEntry
import com.onikki.app.data.db.entity.TimetableRepeat
import com.onikki.app.domain.notifications.ReminderChannel
import com.onikki.app.domain.notifications.ReminderNotifier
import com.onikki.app.domain.timetable.SheetLesson
import com.onikki.app.domain.timetable.SheetTab
import com.onikki.app.domain.timetable.SheetTimetable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** How a sync went. */
sealed interface SheetSyncResult {
    data object NotConnected : SheetSyncResult
    data object Skipped : SheetSyncResult
    data class Updated(val lessons: Int, val changes: List<String>) : SheetSyncResult
    data class Failed(val message: String) : SheetSyncResult
}

/**
 * One connected tab of a sheet and the group read from it (the main timetable, the English timetable…).
 * [source] tags its rows in timetable_entries, so each connection replaces only its own lessons.
 */
data class SheetConnection(val sheetId: String, val gid: String?, val tab: String?, val group: String, val source: String) {
    val label: String get() = listOfNotNull(group, tab).joinToString(" · ")
}

/**
 * Keeps the timetable in step with Google Sheet tabs (shared "anyone with the link"): each connection's
 * lessons for its group replace the rows that connection wrote before; lessons typed in by hand are
 * never touched. A change is announced with a notification.
 */
class SheetSyncRepository(private val context: Context, private val db: AppDatabase) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()

    val lastSync: Long get() = prefs.getLong(KEY_LAST, 0L)
    val lastError: String? get() = prefs.getString(KEY_ERROR, null)

    /** Connected tabs; 2.6.0's single link/group becomes the first one (its rows keep the old tag). */
    val connections: List<SheetConnection>
        get() {
            prefs.getString(KEY_CONNECTIONS, null)?.let { json ->
                return runCatching {
                    val a = JSONArray(json)
                    (0 until a.length()).map { i ->
                        val o = a.getJSONObject(i)
                        SheetConnection(o.getString("sheet"), o.optString("gid").ifBlank { null }, o.optString("tab").ifBlank { null }, o.getString("group"), o.getString("source"))
                    }
                }.getOrDefault(emptyList())
            }
            val link = prefs.getString(KEY_LINK, null) ?: return emptyList()
            val group = prefs.getString(KEY_GROUP, null) ?: return emptyList()
            val (sheet, gid) = SheetTimetable.ids(link) ?: return emptyList()
            return listOf(SheetConnection(sheet, gid, null, group, LEGACY_SOURCE))
        }

    private fun save(list: List<SheetConnection>) {
        val a = JSONArray()
        list.forEach { c -> a.put(JSONObject().put("sheet", c.sheetId).put("gid", c.gid ?: "").put("tab", c.tab ?: "").put("group", c.group).put("source", c.source)) }
        prefs.edit().putString(KEY_CONNECTIONS, a.toString()).remove(KEY_LINK).remove(KEY_GROUP).apply()
    }

    private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) throw IllegalStateException(if (r.code == 401 || r.code == 403) "Jadval yopiq — \"Havolaga ega har kim ko'ra oladi\" qilib ulashilishi kerak" else "Jadvalni olib bo'lmadi (${r.code})")
            r.body?.string().orEmpty()
        }
    }

    private suspend fun rows(sheetId: String, gid: String?): List<List<String>> {
        val body = get(SheetTimetable.csvUrl(sheetId, gid))
        if (body.trimStart().startsWith("<")) throw IllegalStateException("Jadval yopiq yoki havola noto'g'ri")
        return SheetTimetable.parseCsv(body)
    }

    /** The tabs of the sheet a link points to (the linked one first); one unnamed tab if they can't be read. */
    suspend fun tabs(link: String): Result<List<SheetTab>> = runCatching {
        val (sheet, gid) = SheetTimetable.ids(link) ?: throw IllegalArgumentException("Bu Google Sheets havolasi emas")
        val found = runCatching { SheetTimetable.tabs(get("https://docs.google.com/spreadsheets/d/$sheet/htmlview")) }.getOrDefault(emptyList())
        found.sortedBy { if (it.gid == gid) 0 else 1 }.ifEmpty { listOf(SheetTab(gid ?: "", "Jadval")) }
    }

    /** The groups in one tab, and which of them include [academicGroup] (to star them). */
    suspend fun groups(link: String, gid: String, academicGroup: String? = null): Result<Pair<List<String>, Set<String>>> = runCatching {
        val (sheet, _) = SheetTimetable.ids(link) ?: throw IllegalArgumentException("Bu Google Sheets havolasi emas")
        val rows = rows(sheet, gid.ifBlank { null })
        val groups = SheetTimetable.groups(rows)
        groups to (academicGroup?.let { SheetTimetable.groupsWith(rows, it).toSet() } ?: emptySet())
    }

    /** Adds (or replaces, for the same tab) a connection and syncs it. */
    suspend fun connect(link: String, gid: String, tab: String?, group: String): SheetSyncResult {
        val (sheet, _) = SheetTimetable.ids(link) ?: return SheetSyncResult.Failed("Bu Google Sheets havolasi emas")
        val g = gid.ifBlank { null }
        val existing = connections
        val same = existing.firstOrNull { it.sheetId == sheet && it.gid == g }
        val conn = SheetConnection(sheet, g, tab, group, same?.source ?: "$SOURCE_PREFIX${g ?: sheet.take(8)}")
        save(existing.filterNot { it.sheetId == sheet && it.gid == g } + conn)
        prefs.edit().remove(KEY_ERROR).apply()
        return syncOne(conn, notify = false)
    }

    /** Removes one connection and its lessons. */
    suspend fun disconnect(conn: SheetConnection) {
        db.timetableDao().deleteBySource(conn.source)
        save(connections.filterNot { it.source == conn.source })
    }

    /** Syncs every connection (at most every [MIN_GAP_MS] unless [force]). */
    suspend fun sync(force: Boolean = false, notify: Boolean = true): SheetSyncResult {
        val list = connections
        if (list.isEmpty()) return SheetSyncResult.NotConnected
        if (!force && System.currentTimeMillis() - lastSync < MIN_GAP_MS) return SheetSyncResult.Skipped
        var lessons = 0
        val changes = mutableListOf<String>()
        var failure: SheetSyncResult.Failed? = null
        list.forEach { c ->
            when (val r = syncOne(c, notify)) {
                is SheetSyncResult.Updated -> { lessons += r.lessons; changes += r.changes }
                is SheetSyncResult.Failed -> failure = r
                else -> Unit
            }
        }
        return failure ?: SheetSyncResult.Updated(lessons, changes)
    }

    private suspend fun syncOne(c: SheetConnection, notify: Boolean): SheetSyncResult = try {
        val fresh = SheetTimetable.lessons(rows(c.sheetId, c.gid), c.group)
        if (fresh.isEmpty()) throw IllegalStateException("Jadvalda \"${c.group}\" guruhi topilmadi")
        val dao = db.timetableDao()
        val current = dao.bySource(c.source)
        val changes = SheetTimetable.changes(current.map { it.toLesson() }, fresh)
        if (changes.isNotEmpty() || current.isEmpty()) {
            // Keep the user's reminder choice for lessons that stayed in their slot.
            val remind = current.associate { it.externalKey to it.remindMinutes }
            dao.replaceSource(c.source, fresh.map { it.toEntry(c.source, remind[it.key] ?: DEFAULT_REMIND) })
        }
        prefs.edit().putLong(KEY_LAST, System.currentTimeMillis()).remove(KEY_ERROR).apply()
        if (notify && current.isNotEmpty() && changes.isNotEmpty()) {
            ReminderNotifier.show(
                context, "sheet-sync:${c.source}", ReminderChannel.TIMETABLE,
                title = "📅 Jadval o'zgardi · ${c.group}",
                text = changes.take(4).joinToString("\n") + if (changes.size > 4) "\n… yana ${changes.size - 4} ta" else ""
            )
        }
        SheetSyncResult.Updated(fresh.size, changes)
    } catch (e: Exception) {
        val msg = when (e) {
            is java.net.UnknownHostException, is java.net.SocketTimeoutException, is java.net.ConnectException -> "Internet yo'q — oxirgi jadval saqlanib turibdi"
            else -> e.message ?: "Jadvalni yangilab bo'lmadi"
        }
        prefs.edit().putString(KEY_ERROR, msg).apply()
        SheetSyncResult.Failed(msg)
    }

    private fun SheetLesson.toEntry(source: String, remindMinutes: Int?) = TimetableEntry(
        title = subject,
        repeat = TimetableRepeat.WEEKLY,
        dayOfWeek = dayOfWeek,
        start = start,
        end = end,
        place = room,
        note = teacher,
        remindMinutes = remindMinutes,
        source = source,
        externalKey = key
    )

    private fun TimetableEntry.toLesson() = SheetLesson(
        dayOfWeek = dayOfWeek,
        slot = externalKey?.substringAfter('-')?.toIntOrNull() ?: 0,
        start = start,
        end = end,
        subject = title,
        teacher = note,
        room = place
    )

    companion object {
        /** Rows synced by 2.6.0 (one connection) keep this tag. */
        const val LEGACY_SOURCE = "SHEET"
        const val SOURCE_PREFIX = "SHEET:"
        private const val PREFS = "timetable_sheet"
        private const val KEY_LINK = "link"
        private const val KEY_GROUP = "group"
        private const val KEY_CONNECTIONS = "connections"
        private const val KEY_LAST = "last_sync"
        private const val KEY_ERROR = "error"
        private const val DEFAULT_REMIND = 10
        /** Background syncs at most every 3 hours (the screen and a pull refresh force one). */
        const val MIN_GAP_MS = 3 * 60 * 60 * 1000L
    }
}

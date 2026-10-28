package com.onikki.app.data.repository

import android.content.Context
import com.onikki.app.data.db.AppDatabase
import com.onikki.app.data.db.entity.TimetableEntry
import com.onikki.app.data.db.entity.TimetableRepeat
import com.onikki.app.domain.notifications.ReminderChannel
import com.onikki.app.domain.notifications.ReminderNotifier
import com.onikki.app.domain.timetable.SheetLesson
import com.onikki.app.domain.timetable.SheetTimetable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** How a sync went. */
sealed interface SheetSyncResult {
    data object NotConnected : SheetSyncResult
    data object Skipped : SheetSyncResult
    data class Updated(val lessons: Int, val changes: List<String>) : SheetSyncResult
    data class Failed(val message: String) : SheetSyncResult
}

/**
 * Keeps the timetable in step with a Google Sheet (published / shared "anyone with the link"): the
 * sheet's lessons for the chosen group replace the rows that came from the sheet before (source = SHEET);
 * lessons typed in by hand are never touched. A change is announced with a notification.
 */
class SheetSyncRepository(private val context: Context, private val db: AppDatabase) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()

    val link: String? get() = prefs.getString(KEY_LINK, null)
    val group: String? get() = prefs.getString(KEY_GROUP, null)
    val lastSync: Long get() = prefs.getLong(KEY_LAST, 0L)
    val lastError: String? get() = prefs.getString(KEY_ERROR, null)

    private suspend fun rows(link: String): List<List<String>> = withContext(Dispatchers.IO) {
        val url = SheetTimetable.csvUrl(link) ?: throw IllegalArgumentException("Bu Google Sheets havolasi emas")
        http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) throw IllegalStateException(if (r.code == 401 || r.code == 403) "Jadval yopiq — \"Havolaga ega har kim ko'ra oladi\" qilib ulashilishi kerak" else "Jadvalni olib bo'lmadi (${r.code})")
            val body = r.body?.string().orEmpty()
            if (body.trimStart().startsWith("<")) throw IllegalStateException("Jadval yopiq yoki havola noto'g'ri")
            SheetTimetable.parseCsv(body)
        }
    }

    /** The groups in a sheet, for the picker. */
    suspend fun groups(link: String): Result<List<String>> = runCatching { SheetTimetable.groups(rows(link)) }

    suspend fun connect(link: String, group: String): SheetSyncResult {
        prefs.edit().putString(KEY_LINK, link.trim()).putString(KEY_GROUP, group).remove(KEY_ERROR).apply()
        return sync(force = true, notify = false)
    }

    /** Removes the synced lessons and forgets the sheet. */
    suspend fun disconnect() {
        db.timetableDao().deleteBySource(SOURCE)
        prefs.edit().clear().apply()
    }

    /**
     * Pulls the sheet (at most every [MIN_GAP_MS] unless [force]) and replaces the synced lessons when
     * something changed. Offline or a broken sheet keeps the last known lessons.
     */
    suspend fun sync(force: Boolean = false, notify: Boolean = true): SheetSyncResult {
        val link = link ?: return SheetSyncResult.NotConnected
        val group = group ?: return SheetSyncResult.NotConnected
        if (!force && System.currentTimeMillis() - lastSync < MIN_GAP_MS) return SheetSyncResult.Skipped
        return try {
            val fresh = SheetTimetable.lessons(rows(link), group)
            if (fresh.isEmpty()) throw IllegalStateException("Jadvalda \"$group\" guruhi topilmadi")
            val dao = db.timetableDao()
            val current = dao.bySource(SOURCE)
            val old = current.map { it.toLesson() }
            val changes = SheetTimetable.changes(old, fresh)
            if (changes.isNotEmpty() || current.isEmpty()) {
                // Keep the user's reminder choice for lessons that stayed in their slot.
                val remind = current.associate { it.externalKey to it.remindMinutes }
                dao.replaceSource(SOURCE, fresh.map { it.toEntry(remind[it.key] ?: DEFAULT_REMIND) })
            }
            prefs.edit().putLong(KEY_LAST, System.currentTimeMillis()).remove(KEY_ERROR).apply()
            if (notify && current.isNotEmpty() && changes.isNotEmpty()) {
                ReminderNotifier.show(
                    context, "sheet-sync", ReminderChannel.TIMETABLE,
                    title = "📅 Jadval o'zgardi · $group",
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
    }

    private fun SheetLesson.toEntry(remindMinutes: Int?) = TimetableEntry(
        title = subject,
        repeat = TimetableRepeat.WEEKLY,
        dayOfWeek = dayOfWeek,
        start = start,
        end = end,
        place = room,
        note = teacher,
        remindMinutes = remindMinutes,
        source = SOURCE,
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
        const val SOURCE = "SHEET"
        private const val PREFS = "timetable_sheet"
        private const val KEY_LINK = "link"
        private const val KEY_GROUP = "group"
        private const val KEY_LAST = "last_sync"
        private const val KEY_ERROR = "error"
        private const val DEFAULT_REMIND = 10
        /** Background syncs at most every 3 hours (the screen and a pull refresh force one). */
        const val MIN_GAP_MS = 3 * 60 * 60 * 1000L
    }
}

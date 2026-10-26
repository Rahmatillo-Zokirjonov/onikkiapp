package com.onikki.app.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.onikki.app.MainActivity
import com.onikki.app.OnIkkiApplication
import com.onikki.app.R
import com.onikki.app.domain.vocab.Exercises
import com.onikki.app.domain.vocab.HardSpelling
import com.onikki.app.domain.vocab.WordOfHour
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * "Soat so'zi" home-screen widget: a new word every hour (the platform's hourly update), due and hard
 * words first. Tapping it opens O'rganish with a short practice of that word; ↻ shows the next one.
 */
class WordWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = render(context, manager, ids)

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_NEXT) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            prefs.edit().putInt(KEY_SKIP, prefs.getInt(KEY_SKIP, 0) + 1).apply()
            refresh(context)
        }
    }

    private fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
        if (ids.isEmpty()) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val db = (context.applicationContext as OnIkkiApplication).database
                val today = LocalDate.now()
                val words = db.vocabWordDao().getAll()
                val hard = HardSpelling.pick(db.vocabReviewDao().rowsSince(today.minusDays(HardSpelling.DAYS)), today)
                val skip = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_SKIP, 0)
                val word = WordOfHour.pick(words, today, hard, System.currentTimeMillis() / 3_600_000L, skip)
                ids.forEach { id -> manager.updateAppWidget(id, views(context, word)) }
            } finally {
                pending.finish()
            }
        }
    }

    private fun views(context: Context, word: com.onikki.app.data.db.entity.VocabWord?): RemoteViews {
        val v = RemoteViews(context.packageName, R.layout.widget_word)
        if (word == null) {
            v.setTextViewText(R.id.widget_english, "So'z yo'q")
            v.setTextViewText(R.id.widget_uzbek, "O'rganish bo'limida so'z qo'shing")
            v.setTextViewText(R.id.widget_example, "")
        } else {
            v.setTextViewText(R.id.widget_english, word.english)
            v.setTextViewText(R.id.widget_uzbek, Exercises.primary(word.uzbek))
            v.setTextViewText(R.id.widget_example, word.example.orEmpty())
        }
        val open = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(MainActivity.EXTRA_PRACTICE_WORD_ID, word?.id ?: 0L)
        v.setOnClickPendingIntent(
            R.id.widget_root,
            PendingIntent.getActivity(context, (word?.id ?: 0L).toInt(), open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        )
        val next = Intent(context, WordWidget::class.java).setAction(ACTION_NEXT)
        v.setOnClickPendingIntent(R.id.widget_next, PendingIntent.getBroadcast(context, 1, next, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        return v
    }

    companion object {
        private const val ACTION_NEXT = "com.onikki.app.widget.NEXT_WORD"
        private const val PREFS = "word_widget"
        private const val KEY_SKIP = "skip"

        /** Redraws every placed widget (after the app starts, or ↻). Safe when none is placed. */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, WordWidget::class.java))
            if (ids.isEmpty()) return
            context.sendBroadcast(
                Intent(context, WordWidget::class.java).setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE).putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            )
        }
    }
}

/** "Practise this word" from the widget: the nav host opens O'rganish, which starts the practice. */
object LearnDeepLink {
    private val pending = kotlinx.coroutines.flow.MutableStateFlow<Long?>(null)
    val requested: kotlinx.coroutines.flow.StateFlow<Long?> = pending

    fun request(wordId: Long) { pending.value = wordId }

    fun consume() { pending.value = null }
}

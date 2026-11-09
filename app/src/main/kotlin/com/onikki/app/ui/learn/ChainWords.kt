package com.onikki.app.ui.learn

import android.content.Context
import com.onikki.app.data.local.LearnPrefs
import com.onikki.app.domain.vocab.ImportedWord
import com.onikki.app.domain.vocab.WordChain
import com.onikki.app.ui.vocabulary.DictionaryStore
import java.time.LocalDate

/** The chain's words as the app knows them: curated + dictionary, with the user's replacements. */
object ChainWords {
    /** Curated words, then the dictionary by frequency (curated only if the dictionary can't load). */
    suspend fun base(context: Context): List<ImportedWord> {
        val dict = runCatching { DictionaryStore.get(context) }.getOrNull()
        return WordChain.pool(dict?.popularMissing(emptySet(), 10_000)?.map { ImportedWord(it.english, it.uzbek, null, null) }.orEmpty())
    }

    /** The words reached by today (days 1..today), in chain order; empty before the chain is started. */
    suspend fun learned(context: Context): List<ImportedWord> {
        val prefs = LearnPrefs(context)
        val start = prefs.chainStart() ?: return emptyList()
        val n = WordChain.dayNumber(start, LocalDate.now())
        if (n <= 0) return emptyList()
        val reached = WordChain.reached(n, WordChain.totalDays(start), prefs.chainGoal())
        return WordChain.arrange(base(context), prefs.chainOverrides(), prefs.chainRemoved()).take(reached)
    }
}

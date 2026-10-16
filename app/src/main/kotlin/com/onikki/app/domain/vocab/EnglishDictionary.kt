package com.onikki.app.domain.vocab

/** One built-in entry: an English word or phrase and its Uzbek meaning(s), comma-separated. */
data class DictEntry(val english: String, val uzbek: String, val rank: Int)

/**
 * The built-in English → Uzbek word list (assets/dict_en_uz.tsv, most common words first).
 * Pure: built from the file's lines, so the search is unit-tested without Android.
 */
class EnglishDictionary(lines: Sequence<String>) {
    private val entries: List<DictEntry> = lines
        .mapNotNull { line ->
            val tab = line.indexOf('\t')
            if (tab <= 0) null else line.substring(0, tab).trim().lowercase() to line.substring(tab + 1).trim()
        }
        .filter { (en, uz) -> en.isNotEmpty() && uz.isNotEmpty() }
        .distinctBy { it.first }
        .mapIndexed { i, (en, uz) -> DictEntry(en, uz, i) }
        .toList()

    private val byWord = entries.associateBy { it.english }

    val size: Int get() = entries.size

    fun lookup(english: String): DictEntry? = byWord[clean(english)]

    /**
     * Like a search box: words starting with what was typed first (common ones first), then phrases
     * with a word starting with it ("look" → "look", "look for", "good look…").
     */
    fun suggest(typed: String, limit: Int = 6): List<DictEntry> {
        val q = clean(typed)
        if (q.isEmpty()) return emptyList()
        val starts = entries.filter { it.english.startsWith(q) }
        val wordStarts = entries.filter { e -> !e.english.startsWith(q) && e.english.split(' ').any { it.startsWith(q) } }
        return (starts.sortedWith(compareBy({ it.english.length > q.length + 12 }, { it.rank })) + wordStarts).take(limit)
    }

    /** Common words the user doesn't have yet, in frequency order (for "+ N ta ommabop so'z"). */
    fun popularMissing(known: Set<String>, count: Int): List<DictEntry> =
        entries.asSequence()
            .filter { clean(it.english) !in known && !it.uzbek.startsWith("(") && !it.uzbek.startsWith("-") }
            .take(count)
            .toList()

    companion object {
        fun clean(text: String): String = text.trim().lowercase().replace(Regex("""\s+"""), " ")
    }
}

package com.onikki.app.domain.vocab

/** One parsed import line. */
data class ImportedWord(val english: String, val uzbek: String, val example: String?, val pastForm: String?, val participle: String? = null)

/**
 * Bulk text import: one word per line, "english | uzbek | misol gap" (the example is optional).
 * "decide (decided)" puts the irregular past form aside; "go (went, gone)" also the participle (V3). Lines without "|" fall back to
 * "apple - olma", "apple: olma" or a tab. Blank lines and "#" comments are skipped.
 */
object VocabImport {
    private val PAST = Regex("""^(.+?)\s*\(([^)]+)\)\s*$""")
    private val FALLBACK = Regex("""\s+[-–—=]\s+|\t|\s*:\s*""")

    fun parseLine(line: String): ImportedWord? {
        val text = line.trim()
        if (text.isEmpty() || text.startsWith("#")) return null
        val parts = if ('|' in text) text.split('|').map { it.trim() } else text.split(FALLBACK, limit = 2).map { it.trim() }
        if (parts.size < 2 || parts[0].isEmpty() || parts[1].isEmpty()) return null
        val m = PAST.find(parts[0])
        val english = m?.groupValues?.get(1)?.trim() ?: parts[0]
        val forms = m?.groupValues?.get(2)?.split(',', ';')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
        return ImportedWord(
            english = english,
            uzbek = parts[1],
            example = parts.getOrNull(2)?.trim()?.ifEmpty { null },
            pastForm = forms.getOrNull(0),
            participle = forms.getOrNull(1)
        )
    }

    fun parse(text: String): List<ImportedWord> =
        text.lines().mapNotNull(::parseLine).distinctBy { AnswerCheck.normalize(it.english) }

    /** The starter deck, as given. */
    const val LESSON_1 = """decide (decided) | qaror qilmoq | I decided to study English.
forget (forgot) | unutmoq | I forgot my phone at home.
remember | eslamoq | I can't remember his name.
arrive | yetib kelmoq | The bus arrived at 8 am.
wait | kutmoq | Please wait for me.
choose (chose) | tanlamoq | I chose the red phone.
explain | tushuntirmoq | Can you explain this word?
improve | yaxshilamoq | I want to improve my English.
spend (spent) | sarflamoq, o'tkazmoq | I spent two hours on homework.
believe | ishonmoq | I believe you.
important | muhim | English is very important.
different | boshqacha | Tashkent is different from Fergana.
expensive | qimmat | This laptop is expensive.
cheap | arzon | Bread is cheap.
busy | band | I am busy today.
ready | tayyor | Are you ready?
reason | sabab | What is the reason?
advice | maslahat | Can you give me some advice?
experience | tajriba | She has a lot of experience.
future | kelajak | In the future, I want to travel."""
}

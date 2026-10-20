package com.onikki.app.domain.speaking

import com.onikki.app.domain.vocab.SentenceCheck

/** A speaking question; [hints] are useful words or phrases for the answer. */
data class OralPrompt(val id: String, val level: String, val question: String, val questionUz: String, val hints: List<String>) {
    /** About how many words a good answer has at this level. */
    val targetWords: Int get() = OralCheck.targetWords(level)
}

/** A mistake the AI found: what was said, the right way, and why (Uzbek). */
data class OralMistake(val wrong: String, val right: String, val why: String)

/** The evaluation of a spoken answer. Sub-scores are 0–100; [byAi] is false for the offline check. */
data class OralResult(
    val overall: Int,
    val grammar: Int?,
    val vocabulary: Int,
    val relevance: Int?,
    val length: Int,
    val feedback: String,
    val corrected: String?,
    val mistakes: List<OralMistake> = emptyList(),
    val byAi: Boolean = false
)

object OralPrompts {
    private fun p(id: String, level: String, q: String, uz: String, vararg hints: String) = OralPrompt(id, level, q, uz, hints.toList())

    val ALL: List<OralPrompt> = listOf(
        p("oa1-1", "A1", "Tell me about your family.", "Oilangiz haqida gapirib bering.", "There are … people in my family", "my father is", "brother / sister", "we live in", "I love"),
        p("oa1-2", "A1", "What do you do every morning?", "Har kuni ertalab nima qilasiz?", "I get up at", "then", "I have breakfast", "I go to", "usually"),
        p("oa1-3", "A1", "Describe your room.", "Xonangizni tasvirlab bering.", "There is a", "next to", "my bed", "the window", "I like my room because"),
        p("oa1-4", "A1", "What is your favourite food and why?", "Sevimli taomingiz qaysi va nega?", "My favourite food is", "because", "delicious", "my mother cooks", "I eat it"),
        p("oa1-5", "A1", "What do you do at the weekend?", "Dam olish kunlari nima qilasiz?", "On Saturday", "on Sunday", "I play", "with my friends", "sometimes"),
        p("oa1-6", "A1", "Tell me about your best friend.", "Eng yaqin do'stingiz haqida gapiring.", "My best friend is", "he / she is", "we often", "he / she likes", "kind"),
        p("oa2-1", "A2", "Tell me about your last holiday.", "Oxirgi ta'tilingiz haqida gapirib bering.", "Last summer I went to", "we stayed", "we visited", "it was", "the best part was"),
        p("oa2-2", "A2", "What did you do yesterday?", "Kecha nima qildingiz?", "Yesterday I", "in the morning", "after that", "in the evening", "I was tired"),
        p("oa2-3", "A2", "Describe your hometown.", "Tug'ilgan shahringizni tasvirlab bering.", "I come from", "it is famous for", "there are", "people are", "the best place is"),
        p("oa2-4", "A2", "What are your plans for next summer?", "Kelasi yozgi rejalaringiz qanday?", "I'm going to", "I want to", "I hope", "maybe", "with my family"),
        p("oa2-5", "A2", "Tell me about a film you watched recently.", "Yaqinda ko'rgan filmingiz haqida gapiring.", "Recently I watched", "it is about", "the main character", "I liked it because", "I recommend"),
        p("oa2-6", "A2", "What is your favourite season and why?", "Sevimli faslingiz qaysi va nega?", "My favourite season is", "because", "the weather is", "I can", "I don't like"),
        p("ob1-1", "B1", "Describe a person who has influenced you.", "Sizga ta'sir qilgan inson haqida gapiring.", "has influenced me", "I admire", "taught me", "for example", "thanks to him / her"),
        p("ob1-2", "B1", "What are the advantages and disadvantages of social media?", "Ijtimoiy tarmoqlarning foydasi va zarari qanday?", "on the one hand", "on the other hand", "stay in touch", "waste time", "in my opinion"),
        p("ob1-3", "B1", "Tell me about a difficult situation you solved.", "Hal qilgan qiyin vaziyatingiz haqida gapiring.", "once", "the problem was", "I decided to", "finally", "I learned that"),
        p("ob1-4", "B1", "How do you usually learn new things?", "Odatda yangi narsalarni qanday o'rganasiz?", "I prefer", "by watching", "practise", "it helps me", "the most effective way"),
        p("ob1-5", "B1", "If you could live in another country, where would you live and why?", "Boshqa davlatda yashay olsangiz, qayerda va nega?", "If I could, I would", "because", "the culture", "the people", "I would miss"),
        p("ob1-6", "B1", "Describe a goal you want to achieve in the next five years.", "Keyingi besh yilda erishmoqchi bo'lgan maqsadingiz haqida gapiring.", "my goal is to", "in order to", "I have already", "I'm planning to", "it will help me"),
        p("ob2-1", "B2", "Should students be allowed to use AI tools for homework?", "O'quvchilarga uy vazifasida AI dan foydalanishga ruxsat berish kerakmi?", "I'd argue that", "as long as", "critical thinking", "it depends on", "however"),
        p("ob2-2", "B2", "How has technology changed the way people work?", "Texnologiya odamlarning ishlash usulini qanday o'zgartirdi?", "remote work", "productivity", "compared to", "as a result", "nowadays"),
        p("ob2-3", "B2", "Describe a decision you regret and what you learned from it.", "Afsuslangan qaroringiz va undan nimani o'rganganingizni gapiring.", "I wish I had", "if I had", "looking back", "I realised", "since then"),
        p("ob2-4", "B2", "Is it better to work for a big company or start your own business?", "Katta kompaniyada ishlash yaxshimi yoki o'z biznesingizni ochishmi?", "stability", "freedom", "risk", "whereas", "personally, I'd rather"),
        p("ob2-5", "B2", "What can cities do to reduce traffic and pollution?", "Shaharlar tirbandlik va ifloslanishni qanday kamaytirishi mumkin?", "public transport", "encourage", "should invest in", "in the long run", "for instance"),
        p("ob2-6", "B2", "How important is it to learn a foreign language today?", "Bugun chet tili o'rganish qanchalik muhim?", "opportunities", "communicate", "it's essential", "not only … but also", "to sum up")
    )

    fun forLevel(level: String) = ALL.filter { it.level == level }
}

/**
 * Checking a spoken answer without AI: only what can be measured — length for the level, variety of
 * words, and use of the suggested phrases. Grammar and meaning need the AI.
 */
object OralCheck {
    fun targetWords(level: String): Int = when (level) {
        "A1" -> 25
        "A2" -> 40
        "B1" -> 60
        else -> 80
    }

    fun offline(prompt: OralPrompt, transcript: String, seconds: Int): OralResult {
        val words = SentenceCheck.words(transcript)
        val length = (words.size * 100 / prompt.targetWords).coerceAtMost(100)
        val variety = if (words.isEmpty()) 0 else (words.toSet().size * 100 / words.size).coerceIn(0, 100)
        val text = " " + words.joinToString(" ") + " "
        val used = prompt.hints.count { h ->
            h.split('/', '…').map { SentenceCheck.words(it).joinToString(" ") }.filter { it.isNotBlank() }.any { text.contains(" $it ") }
        }
        val hintScore = if (prompt.hints.isEmpty()) 100 else (used * 100 / minOf(3, prompt.hints.size)).coerceAtMost(100)
        val vocabulary = (variety * 0.6 + hintScore * 0.4).toInt()
        val overall = (length * 0.5 + vocabulary * 0.5).toInt()
        val notes = buildList {
            if (words.size < prompt.targetWords) add("Javob qisqa: ${words.size} so'z. ${prompt.level} uchun ~${prompt.targetWords} so'z yaxshi.")
            else add("Uzunligi yaxshi: ${words.size} so'z, $seconds soniya.")
            if (used == 0) add("Taklif qilingan iboralardan foydalanib ko'ring: ${prompt.hints.take(2).joinToString(", ")}.")
            else add("$used ta foydali ibora ishlatildi.")
            if (variety < 55 && words.size >= 15) add("Bir xil so'zlar ko'p takrorlandi — sinonimlar qo'shing.")
            add("Grammatika va ma'noni AI tekshirishi uchun API kalit qo'shing.")
        }
        return OralResult(overall, null, vocabulary, null, length, notes.joinToString("\n"), null)
    }

    /** XP: by the score, plus a little for speaking longer. */
    fun xp(result: OralResult, seconds: Int): Int = result.overall / 10 + (seconds / 15).coerceAtMost(4)
}

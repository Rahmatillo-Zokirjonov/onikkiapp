package com.onikki.app.domain.vocab

import kotlin.random.Random

// ============================================================ confusing pairs

/** Two words that get mixed up, with their meanings and a short Uzbek rule. */
data class ConfusingPair(val key: String, val first: String, val second: String, val firstUz: String, val secondUz: String, val rule: String)

/** One question: a sentence with "___" and which of the two words fills it (0 = first, 1 = second). */
data class PairQuestion(val pair: ConfusingPair, val sentence: String, val answer: Int, val why: String) {
    val options: List<String> get() = listOf(pair.first, pair.second)
    val right: String get() = options[answer]
    val key: String get() = pair.key + ":" + sentence.lowercase()
}

object ConfusingPairs {
    private fun p(key: String, a: String, b: String, aUz: String, bUz: String, rule: String) = ConfusingPair(key, a, b, aUz, bUz, rule)

    val PAIRS = listOf(
        p("lend-borrow", "lend", "borrow", "qarzga bermoq", "qarzga olmoq", "lend — o'zingnikini boshqaga berasan; borrow — boshqanikini o'zing olasan."),
        p("interesting-interested", "interesting", "interested", "qiziqarli (narsa)", "qiziqqan (odam)", "-ing — narsa qanday (qiziqarli film); -ed — odam o'zini qanday his qiladi (men qiziqaman)."),
        p("boring-bored", "boring", "bored", "zerikarli", "zerikkan", "-ing — narsa zerikarli; -ed — odam zerikdi."),
        p("advice-advise", "advice", "advise", "maslahat (ot)", "maslahat bermoq (fe'l)", "advice — ot (some advice, good advice); advise — fe'l (I advise you to…)."),
        p("expensive-expansive", "expensive", "expansive", "qimmat", "keng, katta hajmli", "expensive — narxi baland; expansive — keng (expansive view)."),
        p("say-tell", "say", "tell", "aytmoq", "kimgadir aytmoq", "tell + odam (tell me); say + gap (say hello, say that…)."),
        p("make-do", "make", "do", "yasamoq, tayyorlamoq", "qilmoq, bajarmoq", "make — yangi narsa paydo bo'ladi (make a cake); do — ish, vazifa (do homework)."),
        p("learn-teach", "learn", "teach", "o'rganmoq", "o'rgatmoq", "learn — o'zing o'rganasan; teach — boshqaga o'rgatasan."),
        p("look-watch", "look", "watch", "qaramoq", "tomosha qilmoq", "look (at) — bir qarash; watch — harakatni uzoq kuzatish (TV, film, match)."),
        p("then-than", "then", "than", "keyin", "…dan (taqqoslash)", "than — solishtirishda (bigger than); then — vaqt (first… then…)."),
        p("lose-loose", "lose", "loose", "yo'qotmoq", "bo'sh, keng", "lose — fe'l (lose your keys); loose — sifat (loose shoes)."),
        p("quiet-quite", "quiet", "quite", "jim, tinch", "ancha, juda", "quiet — shovqinsiz; quite — darajani bildiradi (quite good)."),
        p("fun-funny", "fun", "funny", "maroqli, qiziq", "kulgili", "fun — vaqt yoqimli o'tdi (the party was fun); funny — kuldiradi (a funny joke)."),
        p("remember-remind", "remember", "remind", "eslamoq", "eslatmoq", "remember — o'zing eslaysan; remind — boshqaga eslatasan (remind me)."),
        p("bring-take", "bring", "take", "olib kelmoq", "olib ketmoq", "bring — bu yerga, gapiruvchi tomon; take — u yerga, boshqa joyga."),
        p("alone-lonely", "alone", "lonely", "yolg'iz (bir o'zi)", "yolg'izlikdan zerikkan", "alone — yonida hech kim yo'q; lonely — yolg'izlikdan xafa.")
    )

    private val byKey = PAIRS.associateBy { it.key }

    /** Built-in questions: key | sentence | answer index | why (empty why = the pair's rule). */
    private val LINES = """
        lend-borrow|Can you ___ me your pen?|0|me ga berasan → lend
        lend-borrow|Can I ___ your pen for a minute?|1|o'zim olaman → borrow
        lend-borrow|I ___ some money from my brother yesterday.|1|akamdan oldim → borrow
        interesting-interested|I am ___ in football.|1|odam qiziqadi → interested
        interesting-interested|This book is very ___.|0|kitob qiziqarli → interesting
        interesting-interested|Are you ___ in history?|1|odam → interested in
        boring-bored|The film was ___.|0|film zerikarli → boring
        boring-bored|I was so ___ that I fell asleep.|1|men zerikdim → bored
        advice-advise|Can you give me some ___?|0|some + ot → advice
        advice-advise|I ___ you to see a doctor.|1|fe'l kerak → advise
        advice-advise|My teacher gave me good ___.|0|good + ot → advice
        expensive-expansive|This phone is very ___.|0|narx → expensive
        expensive-expansive|From the hotel there is an ___ view of the sea.|1|keng manzara → expansive
        say-tell|Can you ___ me the time?|1|tell + me
        say-tell|She didn't ___ anything.|0|odamsiz → say
        say-tell|Please ___ hello to your parents.|0|say hello
        make-do|I have to ___ my homework.|1|vazifa → do
        make-do|Let's ___ a cake for her birthday.|0|yangi narsa → make
        make-do|Could you ___ me a favour?|1|do a favour
        learn-teach|I ___ programming to students.|1|boshqaga o'rgataman → teach
        learn-teach|I want to ___ Japanese.|0|o'zim o'rganaman → learn
        look-watch|I ___ TV every evening.|1|TV → watch
        look-watch|___ at this photo!|0|look at
        then-than|Tashkent is bigger ___ Fergana.|1|solishtirish → than
        then-than|First we had lunch, ___ we went to the park.|0|vaqt → then
        lose-loose|Don't ___ your keys again!|0|fe'l → lose
        lose-loose|These trousers are too ___.|1|sifat → loose
        quiet-quite|Please be ___, the baby is sleeping.|0|jim → quiet
        quiet-quite|The test was ___ difficult.|1|daraja → quite
        fun-funny|The party was a lot of ___.|0|a lot of fun
        fun-funny|He told a very ___ joke.|1|kuldiradi → funny
        remember-remind|Please ___ me to call my mother.|1|remind me
        remember-remind|I can't ___ his name.|0|o'zim eslayman → remember
        bring-take|Please ___ your book to class tomorrow.|0|bu yerga → bring
        bring-take|Don't forget to ___ an umbrella when you go out.|1|u yerga → take
        alone-lonely|She lives ___ in a small flat.|0|bir o'zi → alone
        alone-lonely|He felt ___ after his friends moved away.|1|xafa → lonely
    """.trimIndent()

    val BUILT_IN: List<PairQuestion> by lazy {
        LINES.lines().mapNotNull { l ->
            val p = l.split('|')
            if (p.size < 4) return@mapNotNull null
            val pair = byKey[p[0].trim()] ?: return@mapNotNull null
            PairQuestion(pair, p[1].trim(), p[2].trim().toIntOrNull() ?: return@mapNotNull null, p[3].trim())
        }
    }

    /** A user-added pair as a question (its sentence and answer). */
    fun custom(id: Long, first: String, second: String, firstUz: String, secondUz: String, sentence: String, answer: Int, why: String): PairQuestion {
        val pair = ConfusingPair("u$id", first.trim(), second.trim(), firstUz.trim(), secondUz.trim(), why.trim())
        return PairQuestion(pair, sentence.trim(), answer.coerceIn(0, 1), why.trim())
    }

    /** A round: least-recently-seen first, mixed pairs, never the same pair twice in a row. */
    fun round(pool: List<PairQuestion>, recentKeys: List<String>, random: Random, size: Int = 10): List<PairQuestion> {
        val rank = recentKeys.withIndex().associate { (i, k) -> k to i }
        val queue = pool.shuffled(random).sortedBy { rank[it.key] ?: -1 }.take(size).toMutableList()
        // Spread pairs apart: next comes a question of a different pair than the last one, from the pair
        // with the most questions left (so two of one pair don't end up last, side by side).
        val out = mutableListOf<PairQuestion>()
        while (queue.isNotEmpty()) {
            val left = queue.groupingBy { it.pair.key }.eachCount()
            val last = out.lastOrNull()?.pair?.key
            val i = queue.indices.filter { queue[it].pair.key != last }
                .maxWithOrNull(compareBy<Int> { left.getValue(queue[it].pair.key) }.thenByDescending { it })
                ?: 0
            out += queue.removeAt(i)
        }
        return out
    }

    fun isValid(first: String, second: String, sentence: String, answer: Int): Boolean =
        first.isNotBlank() && second.isNotBlank() && !first.equals(second, ignoreCase = true) && "___" in sentence && answer in 0..1
}

// ============================================================ word families

/** A family member: the word, its meaning, and a sentence with "___" where it goes. */
data class FamilyMember(val word: String, val uzbek: String, val sentence: String)

data class WordFamily(val root: String, val rootUz: String, val members: List<FamilyMember>) {
    val all: List<String> get() = listOf(root) + members.map { it.word }
}

/** One question: the root and a sentence → the right form. */
data class FamilyQuestion(val family: WordFamily, val member: FamilyMember) {
    val key: String get() = family.root + ":" + member.word
}

object WordFamilies {
    private fun f(root: String, rootUz: String, vararg m: Triple<String, String, String>) = WordFamily(root, rootUz, m.map { FamilyMember(it.first, it.second, it.third) })
    private fun m(word: String, uz: String, sentence: String) = Triple(word, uz, sentence)

    val ALL = listOf(
        f("danger", "xavf", m("dangerous", "xavfli", "This road is ___ at night.")),
        f("health", "sog'liq", m("healthy", "sog'lom", "Vegetables are very ___.")),
        f("help", "yordam", m("helpful", "foydali, yordam beradigan", "Thank you, you were very ___.")),
        f("care", "ehtiyot, g'amxo'rlik", m("careful", "ehtiyotkor", "Be ___! The floor is wet.")),
        f("success", "muvaffaqiyat", m("successful", "muvaffaqiyatli", "She is a ___ businesswoman.")),
        f("beauty", "go'zallik", m("beautiful", "go'zal", "Samarkand is a ___ city.")),
        f("friend", "do'st", m("friendly", "do'stona, xushmuomala", "The people here are very ___.")),
        f(
            "interest", "qiziqish",
            m("interesting", "qiziqarli", "This is an ___ book."),
            m("interested", "qiziqqan", "I am ___ in science.")
        ),
        f("teach", "o'rgatmoq", m("teacher", "o'qituvchi", "My English ___ is from London.")),
        f("write", "yozmoq", m("writer", "yozuvchi", "Abdulla Qodiriy was a famous ___.")),
        f("play", "o'ynamoq", m("player", "o'yinchi", "He is the best ___ in our team.")),
        f("sun", "quyosh", m("sunny", "quyoshli", "It is warm and ___ today.")),
        f("rain", "yomg'ir", m("rainy", "yomg'irli", "Take an umbrella, it's a ___ day.")),
        f("comfort", "qulaylik", m("comfortable", "qulay", "This chair is very ___.")),
        f("use", "foydalanmoq", m("useful", "foydali", "This app is really ___."), m("useless", "foydasiz", "My old phone is ___ now.")),
        f("hope", "umid", m("hopeful", "umidli", "We are ___ that it will stop raining.")),
        f("nation", "millat", m("national", "milliy", "Plov is our ___ dish.")),
        f("music", "musiqa", m("musician", "musiqachi", "My uncle is a ___."))
    )

    /** The built-in family a word belongs to (as root or member), or one keyed by the user's [familyRoot]. */
    fun of(word: String, familyRoot: String? = null): WordFamily? {
        val w = word.trim().lowercase()
        val r = familyRoot?.trim()?.lowercase()?.ifBlank { null }
        return ALL.firstOrNull { fam -> fam.root == r || fam.all.any { it.lowercase() == w } }
    }

    /**
     * Root part and ending of a member, for colouring: the longest shared beginning, keeping the
     * y → i change visible ("beaut" + "iful", "danger" + "ous", "teach" + "er").
     */
    fun split(root: String, word: String): Pair<String, String> {
        val r = root.lowercase()
        val w = word.lowercase()
        var n = 0
        while (n < r.length && n < w.length && r[n] == w[n]) n++
        return word.take(n) to word.drop(n)
    }

    val QUESTIONS: List<FamilyQuestion> by lazy { ALL.flatMap { fam -> fam.members.map { FamilyQuestion(fam, it) } } }

    fun round(recentKeys: List<String>, random: Random, size: Int = 10): List<FamilyQuestion> {
        val rank = recentKeys.withIndex().associate { (i, k) -> k to i }
        return QUESTIONS.shuffled(random).sortedBy { rank[it.key] ?: -1 }.take(size)
    }

    fun check(q: FamilyQuestion, input: String): Verdict = Spelling.verdict(input, listOf(q.member.word))
}

package com.onikki.app.domain.vocab

import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class ChainDayState { DONE, TODAY, MISSED, FUTURE }

/**
 * One day of the chain: [expected] new words join it (the goal spread evenly over the days), [total] the
 * chain's length by the end of the day; [words] may be short if the list runs out.
 */
data class ChainDay(
    val number: Int,
    val date: LocalDate,
    val words: List<ImportedWord>,
    val state: ChainDayState,
    val total: Int,
    val expected: Int
) {
    val complete: Boolean get() = words.size == expected
}

/**
 * Study Skills "so'z zanjiri": the chain grows every day by an even share of the goal (150 words by
 * 15 January — 2 a day over 75 days), so it reaches the goal on the last day. Each day's new words are
 * learned and written from memory, and the whole chain is said in order (ChainSequence). Words come
 * from a fixed list (curated words, then the dictionary by frequency), so a day's words never change.
 * Pure; the start date, goal and finished days are stored by the caller.
 */
object WordChain {
    val END: LocalDate = LocalDate.of(2027, 1, 15)
    const val DECK = "Study Skills"
    const val DEFAULT_GOAL = 150

    /** Day number of [today] counted from [start] (1-based); 0 before the start. */
    fun dayNumber(start: LocalDate, today: LocalDate): Int =
        if (today.isBefore(start)) 0 else ChronoUnit.DAYS.between(start, minOf(today, END)).toInt() + 1

    fun totalDays(start: LocalDate): Int = (ChronoUnit.DAYS.between(start, END).toInt() + 1).coerceAtLeast(1)

    /** The chain's length by the end of [day] of [days]: the [goal] spread evenly, all of it on the last day. */
    fun reached(day: Int, days: Int, goal: Int): Int =
        if (day <= 0 || days <= 0) 0 else minOf(goal, ((goal.toLong() * minOf(day, days) + days - 1) / days).toInt())

    /** How many new words day [day] brings. */
    fun newCount(day: Int, days: Int, goal: Int): Int = reached(day, days, goal) - reached(day - 1, days, goal)

    /** Day [day]'s new words: the next ones of [pool] after the earlier days'. */
    fun wordsFor(day: Int, pool: List<ImportedWord>, days: Int, goal: Int): List<ImportedWord> =
        if (day <= 0) emptyList() else pool.drop(reached(day - 1, days, goal)).take(newCount(day, days, goal))

    /** The chain's word list: the curated words, then [more] (the dictionary by frequency), no repeats. */
    fun pool(more: List<ImportedWord>): List<ImportedWord> =
        (WORDS + more).distinctBy { AnswerCheck.normalize(it.english) }

    fun key(w: ImportedWord): String = AnswerCheck.normalize(w.english)

    /**
     * The chain with the user's changes: [overrides] put a word at a position (0-based), [removed] words
     * never appear. Replacing a word = override at its place + the old word removed, so no other
     * position moves (only the far end, when a word is borrowed from there).
     */
    fun arrange(base: List<ImportedWord>, overrides: Map<Int, ImportedWord>, removed: Set<String>): List<ImportedWord> {
        val skip = removed + overrides.values.map(::key)
        val rest = base.filter { key(it) !in skip }.iterator()
        val last = overrides.keys.maxOrNull() ?: -1
        val out = ArrayList<ImportedWord>()
        var p = 0
        while (true) {
            val w = overrides[p] ?: if (rest.hasNext()) rest.next() else null
            if (w == null) { if (p > last) break } else out += w
            p++
        }
        return out
    }

    /** "Boshqa so'z ber": a not yet used word from the far end of the list (null if none is left). */
    fun autoPick(base: List<ImportedWord>, overrides: Map<Int, ImportedWord>, removed: Set<String>, old: ImportedWord): ImportedWord? {
        val taken = removed + overrides.values.map(::key) + key(old)
        return base.lastOrNull { key(it) !in taken }
    }

    /** Whether [english] can go to [position] without the same word being in the chain twice. */
    fun canUse(english: String, chain: List<ImportedWord>, position: Int): Boolean {
        val k = AnswerCheck.normalize(english)
        return k.isNotEmpty() && chain.withIndex().none { it.index != position && key(it.value) == k }
    }

    /** The first day the list can't fill (null if it covers the whole chain). */
    fun firstShortDay(start: LocalDate, pool: List<ImportedWord>, goal: Int): Int? {
        val days = totalDays(start)
        return (1..days).firstOrNull { reached(it, days, goal) > pool.size }
    }

    fun days(start: LocalDate, today: LocalDate, done: Set<Int>, pool: List<ImportedWord>, goal: Int): List<ChainDay> {
        val current = dayNumber(start, today)
        val days = totalDays(start)
        return (1..days).map { n ->
            val state = when {
                n in done -> ChainDayState.DONE
                n == current -> ChainDayState.TODAY
                n < current -> ChainDayState.MISSED
                else -> ChainDayState.FUTURE
            }
            ChainDay(n, start.plusDays((n - 1).toLong()), wordsFor(n, pool, days, goal), state, reached(n, days, goal), newCount(n, days, goal))
        }
    }

    /** Each word checked in order; the day counts only when every one is exactly right. */
    fun check(inputs: List<String>, words: List<ImportedWord>): List<Verdict> =
        words.mapIndexed { i, w -> AnswerCheck.judge(inputs.getOrElse(i) { "" }, listOf(w.english)) }

    fun passed(verdicts: List<Verdict>): Boolean = verdicts.isNotEmpty() && verdicts.all { it == Verdict.CORRECT }

    /** Longest run of finished days ending at [current] (or the day before it, if today isn't done yet). */
    fun streak(done: Set<Int>, current: Int): Int {
        var d = if (current in done) current else current - 1
        var n = 0
        while (d >= 1 && d in done) { n++; d-- }
        return n
    }

    /** 90 words, in chain order (A2 → B1/IELTS); includes the user's known trouble words. */
    val WORDS: List<ImportedWord> = VocabImport.parse(
        """
        achieve | erishmoq | She worked hard to achieve her goal.
        weather | ob-havo | The weather is cold today.
        dangerous | xavfli | This road is dangerous at night.
        price | narx | The price of bread went up.
        future | kelajak | I want a good job in the future.
        experience | tajriba | She has a lot of work experience.
        borrow | qarzga olmoq | Can I borrow your pen?
        lend | qarzga bermoq | I can lend you some money.
        advice | maslahat | My father gave me good advice.
        advise | maslahat bermoq | The doctor advised me to rest.
        interested | qiziqqan | I am interested in football.
        opportunity | imkoniyat | This job is a great opportunity.
        environment | atrof-muhit | We must protect the environment.
        knowledge | bilim | Reading increases your knowledge.
        necessary | zarur | Is it necessary to book a table?
        improve | yaxshilamoq | I want to improve my English.
        decision | qaror | It was a difficult decision.
        healthy | sog'lom | Vegetables are healthy.
        believe | ishonmoq | I believe you can do it.
        receive | olmoq, qabul qilmoq | I received a letter yesterday.
        society | jamiyat | Education is important for society.
        government | hukumat | The government built a new road.
        increase | oshmoq, ko'paymoq | Prices increase every year.
        reduce | kamaytirmoq | We should reduce plastic waste.
        influence | ta'sir | Parents have a big influence on children.
        responsible | mas'uliyatli | She is responsible for the project.
        although | garchi | Although it was raining, we went out.
        however | biroq | It was cheap. However, it was bad.
        especially | ayniqsa | I love fruit, especially apples.
        recently | yaqinda | I have recently moved to Tashkent.
        compare | solishtirmoq | Don't compare yourself with others.
        describe | tasvirlamoq | Can you describe your house?
        explain | tushuntirmoq | Please explain this rule again.
        suggest | taklif qilmoq | I suggest going by train.
        prefer | afzal ko'rmoq | I prefer tea to coffee.
        avoid | qochmoq, oldini olmoq | Try to avoid fast food.
        develop | rivojlanmoq | The city is developing fast.
        provide | ta'minlamoq | The hotel provides free breakfast.
        require | talab qilmoq | This job requires experience.
        succeed | muvaffaqiyatga erishmoq | If you work hard, you will succeed.
        attitude | munosabat | She has a positive attitude.
        behaviour | xulq | His behaviour was strange.
        benefit | foyda | Exercise has many benefits.
        challenge | qiyinchilik, sinov | Learning a language is a challenge.
        community | jamoa, mahalla | Our community is very friendly.
        convenient | qulay | The metro is very convenient.
        crowded | gavjum | The bus was very crowded.
        essential | juda zarur | Water is essential for life.
        familiar | tanish | This song sounds familiar.
        generation | avlod | The young generation uses phones a lot.
        habit | odat | Reading is a good habit.
        independent | mustaqil | She is very independent.
        issue | masala, muammo | Pollution is a serious issue.
        journey | sayohat, yo'l | The journey took five hours.
        limit | chegara | There is a speed limit here.
        measure | o'lchamoq | Measure the room before you buy a carpet.
        noise | shovqin | I can't sleep because of the noise.
        obvious | aniq, ravshan | The answer is obvious.
        patient | sabrli | Be patient, the bus will come.
        pollution | ifloslanish | Air pollution is bad for health.
        quality | sifat | This bag is good quality.
        rather | ko'ra, afzal | I'd rather stay at home.
        schedule | jadval | What is your schedule for today?
        skill | ko'nikma | Cooking is a useful skill.
        suitable | mos, munosib | This film is suitable for children.
        temperature | harorat | The temperature is 30 degrees.
        tradition | an'ana | Navruz is an old tradition.
        unemployment | ishsizlik | Unemployment is a big problem.
        valuable | qimmatli | Time is valuable.
        wealth | boylik | Health is more important than wealth.
        accommodation | turar joy | We found cheap accommodation.
        appropriate | o'rinli, munosib | Wear appropriate clothes for the interview.
        consequence | oqibat | Every action has a consequence.
        contribute | hissa qo'shmoq | Everyone can contribute to the project.
        efficient | samarali | This car is very efficient.
        encourage | rag'batlantirmoq | My teacher encouraged me to read more.
        evidence | dalil | There is no evidence.
        facility | sharoit, jihoz | The school has good sports facilities.
        significant | sezilarli, muhim | There was a significant change.
        solution | yechim | We need a solution to this problem.
        technology | texnologiya | Technology makes life easier.
        urban | shaharga oid | Urban life is busy.
        advantage | afzallik | Living in a city has many advantages.
        disadvantage | kamchilik | The main disadvantage is the price.
        attract | jalb qilmoq | Samarkand attracts many tourists.
        research | tadqiqot | Scientists did research on sleep.
        survey | so'rovnoma | The survey showed interesting results.
        recommend | tavsiya qilmoq | I recommend this book.
        effort | harakat, kuch | Learning English takes effort.
        purpose | maqsad | What is the purpose of your visit?
        """
    )
}

package com.onikki.app.domain.podcast

import com.onikki.app.domain.vocab.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PodcastTest {
    @Test
    fun threeLengthsPerLevel_sixQuestionsEach_evidenceLinesHoldTheAnswer() {
        Podcasts.LEVELS.forEach { l ->
            assertEquals(l, listOf(1, 2, 3), PodcastLibrary.ALL.filter { it.level == l }.map { it.minutes }.sorted())
        }
        assertEquals(12, PodcastLibrary.ALL.map { it.id }.toSet().size)
        PodcastLibrary.ALL.forEach { p ->
            assertEquals(p.id, 6, p.questions.size)
            // Roughly 90–150 words a minute at the reading speed.
            assertTrue("${p.id} ${p.wordCount}", p.wordCount in (p.minutes * 85)..(p.minutes * 160))
            p.questions.forEach { q ->
                assertTrue("${p.id}: ${q.text}", q.line in p.lines.indices)
                if (q.kind == QuestionKind.SHORT) {
                    val line = p.lines[q.line].text.lowercase()
                    // At least one accepted answer is said in that line (numbers may be spelled out).
                    assertTrue("${p.id}: ${q.text}", q.accepted.any { line.contains(it.lowercase()) })
                }
            }
        }
        assertTrue(PodcastLibrary.ALL.any { it.isDialogue })
    }

    @Test
    fun parsingDialogueAndQuestions() {
        val p = Podcasts.parse("x", "A1", 1, "T", "t", "A: Hi there.\nB: Hello!\nThe end.", "C|Q?|a;b;c;d|2|0\nTF|S.|T|1\nS|Who?|Ali~ali|2\nbad line")
        assertEquals(listOf("A", "B", null), p.lines.map { it.speaker })
        assertEquals("Hi there.", p.lines[0].text)
        assertEquals(3, p.questions.size)
        assertEquals(0, p.questions[1].answer)
        assertEquals("True", p.questions[1].rightAnswer)
    }

    @Test
    fun checkingAndScore() {
        val short = PodcastQuestion(QuestionKind.SHORT, "Where?", accepted = listOf("bazaar", "market"))
        assertEquals(Verdict.CORRECT, Podcasts.check(short, "Bazaar", -1))
        assertEquals(Verdict.CORRECT, Podcasts.check(short, "to the bazaar", -1))
        assertEquals(Verdict.ALMOST, Podcasts.check(short, "bazar", -1))
        assertEquals(Verdict.WRONG, Podcasts.check(short, "park", -1))
        val tf = PodcastQuestion(QuestionKind.TRUE_FALSE, "x", listOf("True", "False"), 1)
        assertEquals(Verdict.CORRECT, Podcasts.check(tf, "", 1))
        assertEquals(Verdict.WRONG, Podcasts.check(tf, "", -1))
        assertEquals(75, Podcasts.understood(listOf(Verdict.CORRECT, Verdict.ALMOST, Verdict.CORRECT, Verdict.ALMOST)))
        assertEquals(83, Podcasts.understood(List(5) { Verdict.CORRECT } + Verdict.WRONG))
        assertEquals(30 + 10, Podcasts.xp(List(6) { Verdict.CORRECT }, 2))
    }
}

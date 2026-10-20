package com.onikki.app.domain.podcast

import com.onikki.app.domain.ai.AiPlans
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PodcastAiTest {
    private fun reply(lineCount: Int) = buildString {
        append("""{"title":"A Day at the Market","topic_uz":"Bozor","lines":[""")
        append((0 until lineCount).joinToString(",") { i ->
            val speaker = if (i % 2 == 0) "A" else "B"
            val text = when (i) {
                1 -> "I bought twelve apples and some fresh bread at the market this morning."
                3 -> "The seller’s name was Rustam and he was very kind to everyone."
                else -> "This is sentence number $i of a long and friendly conversation about the market."
            }
            """{"speaker":"$speaker","text":"$text"}"""
        })
        append("""],"questions":[""")
        append(
            listOf(
                """{"kind":"choice","text":"How many apples?","options":["two","ten","twelve","twenty"],"answer_index":2,"accepted":[],"line":1}""",
                """{"kind":"true_false","text":"Rustam was rude.","options":[],"answer_index":1,"accepted":[],"line":3}""",
                """{"kind":"short","text":"What was the seller's name?","options":[],"answer_index":-1,"accepted":["Rustam"],"line":3}""",
                """{"kind":"short","text":"What did she buy?","options":[],"answer_index":-1,"accepted":["oranges"],"line":1}""",
                """{"kind":"choice","text":"Bad","options":["a","b"],"answer_index":0,"accepted":[],"line":1}""",
                """{"kind":"true_false","text":"They were at the market.","options":[],"answer_index":0,"accepted":[],"line":0}""",
                """{"kind":"choice","text":"When?","options":["morning","evening","night","noon"],"answer_index":0,"accepted":[],"line":1}"""
            ).joinToString(",")
        )
        append("]}")
    }

    @Test
    fun aiPodcastIsVetted_badQuestionsDropped_tooShortRejected() {
        val parsed = AiPlans.parsePodcast(reply(12), "A2", 1)
        assertNotNull(parsed)
        assertEquals("The seller's name was Rustam and he was very kind to everyone.", parsed!!.lines[3].text)
        val vetted = Podcasts.vet(parsed, 1)
        assertNotNull(vetted)
        // "oranges" isn't in its line and the two-option choice is malformed.
        assertEquals(5, vetted!!.questions.size)
        assertTrue(vetted.questions.none { it.accepted.contains("oranges") })
        assertNull(Podcasts.vet(AiPlans.parsePodcast(reply(4), "A2", 1)!!, 1)) // far too short for a minute
        assertNull(Podcasts.vet(parsed, 3)) // too short for three minutes
    }

    @Test
    fun storageRoundTrip() {
        val vetted = Podcasts.vet(AiPlans.parsePodcast(reply(12), "A2", 1)!!, 1)!!
        val back = Podcasts.parse("pai-1", "A2", 1, vetted.title, vetted.topic, Podcasts.transcriptText(vetted), Podcasts.questionsText(vetted))
        assertEquals(vetted.lines, back.lines)
        assertEquals(vetted.questions, back.questions)
        // The built-in ones survive the same round trip.
        PodcastLibrary.ALL.forEach { p ->
            val again = Podcasts.parse(p.id, p.level, p.minutes, p.title, p.topic, Podcasts.transcriptText(p), Podcasts.questionsText(p))
            assertEquals(p.id, p.questions, again.questions)
            assertNotNull(p.id, Podcasts.vet(p, p.minutes))
        }
    }
}

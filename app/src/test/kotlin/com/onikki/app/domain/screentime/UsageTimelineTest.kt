package com.onikki.app.domain.screentime

import com.onikki.app.domain.screentime.UsageEventLite.Kind.PAUSED
import com.onikki.app.domain.screentime.UsageEventLite.Kind.RESUMED
import com.onikki.app.domain.screentime.UsageEventLite.Kind.SCREEN_OFF
import org.junit.Assert.assertEquals
import org.junit.Test

class UsageTimelineTest {
    private val start = 0L
    private val hour = 3_600_000L
    private val min = 60_000L
    private val end = 24 * hour

    private fun ev(pkg: String, kind: UsageEventLite.Kind, t: Long, cls: String = "Main") = UsageEventLite(pkg, cls, kind, t)

    @Test
    fun simpleSessionSplitsAcrossHours() {
        // 09:50 → 10:20 in Telegram: 10 min in hour 9, 20 min in hour 10.
        val r = UsageTimeline.compute(listOf(ev("tg", RESUMED, 9 * hour + 50 * min), ev("tg", PAUSED, 10 * hour + 20 * min)), start, end, end)
        assertEquals(30 * min, r.totalMsByApp["tg"])
        assertEquals(10 * min, r.hourlyMs[9])
        assertEquals(20 * min, r.hourlyMs[10])
    }

    @Test
    fun appOpenBeforeMidnightCountsFromStartOnly() {
        // First event is a pause at 00:15 → it was open since before the day: 15 minutes today, not more.
        val r = UsageTimeline.compute(listOf(ev("yt", PAUSED, 15 * min)), start, end, end)
        assertEquals(15 * min, r.totalMsByApp["yt"])
    }

    @Test
    fun stillOpenCountsUntilNow() {
        val now = 14 * hour
        val r = UsageTimeline.compute(listOf(ev("ig", RESUMED, 13 * hour + 30 * min)), start, end, now)
        assertEquals(30 * min, r.totalMsByApp["ig"])
    }

    @Test
    fun switchingActivitiesInsideOneAppIsOneSession() {
        val events = listOf(
            ev("tg", RESUMED, 8 * hour, "Chats"),
            ev("tg", RESUMED, 8 * hour + 5 * min, "Chat"),   // new activity resumes before the old pauses
            ev("tg", PAUSED, 8 * hour + 5 * min, "Chats"),
            ev("tg", PAUSED, 8 * hour + 12 * min, "Chat")
        )
        val r = UsageTimeline.compute(events, start, end, end)
        assertEquals(12 * min, r.totalMsByApp["tg"])
    }

    @Test
    fun screenOffEndsSession() {
        val events = listOf(ev("yt", RESUMED, 20 * hour), UsageEventLite("", null, SCREEN_OFF, 20 * hour + 7 * min))
        val r = UsageTimeline.compute(events, start, end, end)
        assertEquals(7 * min, r.totalMsByApp["yt"])
    }

    @Test
    fun anotherAppResumingClosesThePreviousOne() {
        // A missing pause event must not let the first app run on forever.
        val events = listOf(ev("a", RESUMED, 10 * hour), ev("b", RESUMED, 10 * hour + 3 * min), ev("b", PAUSED, 10 * hour + 5 * min))
        val r = UsageTimeline.compute(events, start, end, end)
        assertEquals(3 * min, r.totalMsByApp["a"])
        assertEquals(2 * min, r.totalMsByApp["b"])
    }
}

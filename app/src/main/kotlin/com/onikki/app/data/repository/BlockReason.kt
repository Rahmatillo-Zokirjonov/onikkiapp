package com.onikki.app.data.repository

import java.time.LocalTime

/** Why an app is currently blocked (TZ 3.6 + custom windows and places). */
sealed class BlockReason {
    data object LimitReached : BlockReason()
    data class Schedule(val start: LocalTime, val end: LocalTime) : BlockReason()
    data class Zone(val zoneName: String) : BlockReason()
    data class PrayerTime(val prayerName: String) : BlockReason()
}

/** What should happen when an app comes to the foreground. */
sealed class AppGate {
    data object Allowed : AppGate()

    /** [canChallenge]: the word/text challenge may open it anyway (never when [strict]). */
    data class Blocked(val reason: BlockReason, val strict: Boolean, val canChallenge: Boolean) : AppGate()

    /** Not blocked, but this app asks for the challenge on every open. */
    data object ChallengeRequired : AppGate()
}

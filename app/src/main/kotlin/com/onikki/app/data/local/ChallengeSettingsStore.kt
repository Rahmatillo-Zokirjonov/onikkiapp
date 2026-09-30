package com.onikki.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ChallengeMode(val label: String) { WORDS("So'z yodlash"), PHRASE("Matn yozish") }

enum class ChallengeDirection(val label: String) { EN_UZ("English → O'zbek"), UZ_EN("O'zbek → English"), MIXED("Aralash") }

/** How the "earn your way into an app" challenge behaves. All user-editable in Sozlamalar / So'z yodlash. */
data class ChallengeSettings(
    val enabled: Boolean = true,
    val mode: ChallengeMode = ChallengeMode.WORDS,
    val wordCount: Int = 3,
    val direction: ChallengeDirection = ChallengeDirection.EN_UZ,
    /** After passing, the app stays open this long before it asks again; 0 = until the user leaves the app. */
    val graceMinutes: Int = 0,
    /** Lets a (non-strict) blocked app be opened by passing the challenge. */
    val unlockBlockedApps: Boolean = true,
    val phrase: String = DEFAULT_PHRASE
) {
    companion object {
        const val DEFAULT_PHRASE = "Men vaqtimni foydali ishlarga sarflayman"
        val WORD_COUNT_OPTIONS = listOf(1, 3, 5, 10)
        val GRACE_OPTIONS = listOf(0, 5, 15, 30, 60)
    }
}

private val Context.challengeDataStore by preferencesDataStore(name = "challenge_settings")

class ChallengeSettingsStore(private val context: Context) {
    private object Keys {
        val enabled = booleanPreferencesKey("enabled")
        val mode = stringPreferencesKey("mode")
        val wordCount = intPreferencesKey("word_count")
        val direction = stringPreferencesKey("direction")
        val grace = intPreferencesKey("grace_minutes")
        val unlockBlocked = booleanPreferencesKey("unlock_blocked")
        val phrase = stringPreferencesKey("phrase")
    }

    val settings: Flow<ChallengeSettings> = context.challengeDataStore.data.map { it.toSettings() }

    suspend fun update(transform: (ChallengeSettings) -> ChallengeSettings) {
        context.challengeDataStore.edit { prefs ->
            val next = transform(prefs.toSettings())
            prefs[Keys.enabled] = next.enabled
            prefs[Keys.mode] = next.mode.name
            prefs[Keys.wordCount] = next.wordCount
            prefs[Keys.direction] = next.direction.name
            prefs[Keys.grace] = next.graceMinutes
            prefs[Keys.unlockBlocked] = next.unlockBlockedApps
            prefs[Keys.phrase] = next.phrase
        }
    }

    private fun Preferences.toSettings(): ChallengeSettings {
        val defaults = ChallengeSettings()
        return ChallengeSettings(
            enabled = this[Keys.enabled] ?: defaults.enabled,
            mode = this[Keys.mode]?.let { runCatching { ChallengeMode.valueOf(it) }.getOrNull() } ?: defaults.mode,
            wordCount = this[Keys.wordCount] ?: defaults.wordCount,
            direction = this[Keys.direction]?.let { runCatching { ChallengeDirection.valueOf(it) }.getOrNull() } ?: defaults.direction,
            graceMinutes = this[Keys.grace] ?: defaults.graceMinutes,
            unlockBlockedApps = this[Keys.unlockBlocked] ?: defaults.unlockBlockedApps,
            phrase = this[Keys.phrase]?.takeIf { it.isNotBlank() } ?: defaults.phrase
        )
    }
}

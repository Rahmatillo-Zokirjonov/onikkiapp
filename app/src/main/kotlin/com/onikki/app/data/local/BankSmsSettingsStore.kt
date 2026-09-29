package com.onikki.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Bank SMS auto-import. Off until the user turns it on (it needs the SMS permission). */
data class BankSmsSettings(
    val enabled: Boolean = false,
    /** So'm per 1 USD — the app keeps everything in so'm, so foreign-card SMS are converted with this. */
    val usdRate: Long = DEFAULT_USD_RATE,
    val eurRate: Long = DEFAULT_EUR_RATE
) {
    companion object {
        const val DEFAULT_USD_RATE = 12_800L
        const val DEFAULT_EUR_RATE = 14_000L
    }
}

private val Context.bankSmsDataStore by preferencesDataStore(name = "bank_sms")

class BankSmsSettingsStore(private val context: Context) {
    private object Keys {
        val enabled = booleanPreferencesKey("enabled")
        val usd = longPreferencesKey("usd_rate")
        val eur = longPreferencesKey("eur_rate")
    }

    val settings: Flow<BankSmsSettings> = context.bankSmsDataStore.data.map { prefs ->
        BankSmsSettings(
            enabled = prefs[Keys.enabled] ?: false,
            usdRate = prefs[Keys.usd] ?: BankSmsSettings.DEFAULT_USD_RATE,
            eurRate = prefs[Keys.eur] ?: BankSmsSettings.DEFAULT_EUR_RATE
        )
    }

    suspend fun setEnabled(enabled: Boolean) {
        context.bankSmsDataStore.edit { it[Keys.enabled] = enabled }
    }

    suspend fun setRates(usd: Long, eur: Long) {
        context.bankSmsDataStore.edit {
            it[Keys.usd] = usd
            it[Keys.eur] = eur
        }
    }
}

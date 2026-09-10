package com.onikki.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "onikki_settings")
private val KEY_CLAUDE_API_KEY = stringPreferencesKey("claude_api_key")

/**
 * Stores the user's own Claude API key locally — there's no server or login
 * (TZ), so the user supplies their own key for the one module that calls
 * the network. Plain DataStore, not encrypted-at-rest: acceptable for a
 * personal, side-loaded, single-user device app. Worth upgrading to
 * EncryptedSharedPreferences/Keystore-backed storage later since this does
 * hold a billable API credential.
 */
class ApiKeyStore(private val context: Context) {
    val apiKey: Flow<String?> = context.settingsDataStore.data.map { prefs -> prefs[KEY_CLAUDE_API_KEY] }

    suspend fun setApiKey(key: String) {
        context.settingsDataStore.edit { prefs -> prefs[KEY_CLAUDE_API_KEY] = key }
    }

    suspend fun clearApiKey() {
        context.settingsDataStore.edit { prefs -> prefs.remove(KEY_CLAUDE_API_KEY) }
    }
}

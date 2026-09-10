package com.onikki.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.onboardingDataStore by preferencesDataStore(name = "onikki_onboarding")
private val KEY_COMPLETE = booleanPreferencesKey("onboarding_complete")

class OnboardingStore(private val context: Context) {
    val isComplete: Flow<Boolean> = context.onboardingDataStore.data.map { prefs -> prefs[KEY_COMPLETE] == true }

    suspend fun markComplete() {
        context.onboardingDataStore.edit { prefs -> prefs[KEY_COMPLETE] = true }
    }
}

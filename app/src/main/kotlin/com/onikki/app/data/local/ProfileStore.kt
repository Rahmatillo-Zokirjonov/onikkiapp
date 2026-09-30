package com.onikki.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.profileDataStore by preferencesDataStore(name = "onikki_profile")
private val KEY_NAME = stringPreferencesKey("name")
private val KEY_MAIN_GOAL = longPreferencesKey("main_goal_id")

/** The only personal detail the app keeps: what to call the user in greetings. Local only, no account. */
class ProfileStore(private val context: Context) {
    val name: Flow<String> = context.profileDataStore.data.map { it[KEY_NAME].orEmpty() }

    suspend fun setName(name: String) {
        context.profileDataStore.edit { it[KEY_NAME] = name.trim() }
    }

    /** The big goal pinned to Bosh sahifa; null = pick automatically. */
    val mainGoalId: Flow<Long?> = context.profileDataStore.data.map { it[KEY_MAIN_GOAL] }

    suspend fun setMainGoal(id: Long?) {
        context.profileDataStore.edit { if (id == null) it.remove(KEY_MAIN_GOAL) else it[KEY_MAIN_GOAL] = id }
    }
}

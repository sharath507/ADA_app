package com.example.ada.config

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "ada_settings")

class AppConfig(
    private val context: Context,
) {

    private val keyBackendUrl = stringPreferencesKey("backend_url")

    fun backendUrlFlow(defaultValue: String = "http://10.0.2.2:8000"): Flow<String> {
        return context.dataStore.data.map { prefs: Preferences ->
            prefs[keyBackendUrl] ?: defaultValue
        }
    }

    suspend fun setBackendUrl(url: String) {
        context.dataStore.edit { prefs ->
            prefs[keyBackendUrl] = url
        }
    }

    suspend fun getBackendUrlOnce(defaultValue: String = "http://10.0.2.2:8000"): String {
        val stored = context.dataStore.data
            .map { prefs -> prefs[keyBackendUrl] }
            .first()
        return if (stored.isNullOrBlank()) defaultValue else stored
    }
}

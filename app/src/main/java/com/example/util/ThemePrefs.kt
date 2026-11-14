package com.example.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode(val value: String, val label: String) {
    SYSTEM("system", "System Default"),
    LIGHT("light", "Light Mode"),
    DARK("dark", "Dark Mode");

    companion object {
        fun fromValue(value: String?): ThemeMode =
            entries.firstOrNull { it.value == value } ?: SYSTEM
    }
}

private val Context.septisPrefsStore: DataStore<Preferences> by preferencesDataStore(name = "septis_prefs")

class ThemePrefs(private val context: Context) {
    private val themeKey = stringPreferencesKey("theme_mode")

    val themeModeFlow: Flow<ThemeMode> = context.septisPrefsStore.data
        .map { prefs -> ThemeMode.fromValue(prefs[themeKey]) }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.septisPrefsStore.edit { prefs ->
            prefs[themeKey] = mode.value
        }
    }
}

package com.example.nexonote.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.nexonote.domain.model.AccentColor
import com.example.nexonote.domain.model.ThemeMode
import com.example.nexonote.domain.repository.SettingsRepository
import java.io.IOException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

class DataStoreSettingsRepository(private val store: DataStore<Preferences>) : SettingsRepository {
    private val themeKey = stringPreferencesKey("theme_mode")
    private val nameKey = stringPreferencesKey("display_name")
    private val accentKey = stringPreferencesKey("accent_color")
    // Let the dashboard surface read failures instead of presenting a missing name as real data.
    override val displayName = store.data.map { it[nameKey].orEmpty() }

    override suspend fun setDisplayName(name: String) {
        val normalized = name.trim()
        require(normalized.length <= 40)
        store.edit { it[nameKey] = normalized }
    }
    override val themeMode = store.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }.map { preferences ->
        ThemeMode.entries.firstOrNull { it.name == preferences[themeKey] } ?: ThemeMode.SYSTEM
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[themeKey] = mode.name }
    }

    override val accentColor = store.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }.map { preferences ->
        AccentColor.entries.firstOrNull { it.name == preferences[accentKey] } ?: AccentColor.PURPLE
    }

    override suspend fun setAccentColor(color: AccentColor) {
        store.edit { it[accentKey] = color.name }
    }
}

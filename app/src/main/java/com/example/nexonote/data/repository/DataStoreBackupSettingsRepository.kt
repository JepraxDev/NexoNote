package com.example.nexonote.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.nexonote.domain.model.BackupFrequency
import com.example.nexonote.domain.repository.BackupSettingsRepository
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

class DataStoreBackupSettingsRepository(
    private val store: DataStore<Preferences>,
) : BackupSettingsRepository {
    private val enabledKey = booleanPreferencesKey("backup_automatic_enabled")
    private val frequencyKey = stringPreferencesKey("backup_frequency")
    private val destinationKey = stringPreferencesKey("backup_destination_uri")
    private val lastKey = longPreferencesKey("backup_last_automatic_at")

    private val preferences = store.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }

    override val automaticEnabled: Flow<Boolean> = preferences.map { it[enabledKey] ?: false }

    override val frequency: Flow<BackupFrequency> = preferences.map { values ->
        BackupFrequency.entries.firstOrNull { it.name == values[frequencyKey] } ?: BackupFrequency.MANUAL
    }

    override val destinationUri: Flow<String?> = preferences.map { it[destinationKey]?.takeIf(String::isNotBlank) }

    override val lastAutomaticAt: Flow<Long?> = preferences.map { values ->
        values[lastKey]?.takeIf { it > 0 }
    }

    override suspend fun setAutomaticEnabled(enabled: Boolean) {
        store.edit { it[enabledKey] = enabled }
    }

    override suspend fun setFrequency(frequency: BackupFrequency) {
        store.edit { it[frequencyKey] = frequency.name }
    }

    override suspend fun setDestinationUri(uri: String?) {
        store.edit { preferences ->
            if (uri.isNullOrBlank()) preferences.remove(destinationKey)
            else preferences[destinationKey] = uri
        }
    }

    override suspend fun setLastAutomaticAt(value: Long) {
        store.edit { it[lastKey] = value }
    }
}

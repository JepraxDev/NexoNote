package com.example.nexonote.testing

import com.example.nexonote.domain.model.BackupHistoryEntry
import com.example.nexonote.domain.model.ThemeMode
import com.example.nexonote.domain.repository.BackupHistoryStore
import com.example.nexonote.domain.repository.SettingsRepository
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeSettingsRepository(
    initialName: String = "Soporte",
    initialTheme: ThemeMode = ThemeMode.SYSTEM,
) : SettingsRepository {
    private val name = MutableStateFlow(initialName)
    private val theme = MutableStateFlow(initialTheme)

    override val displayName: Flow<String> = name
    override suspend fun setDisplayName(name: String) {
        this.name.value = name
    }

    override val themeMode: Flow<ThemeMode> = theme
    override suspend fun setThemeMode(mode: ThemeMode) {
        theme.value = mode
    }
}

/** In-memory history used by backup tests; locations are returned via [locationFor]. */
class FakeBackupHistoryStore : BackupHistoryStore {
    private val items = mutableListOf<BackupHistoryEntry>()
    var managedDirectory: File? = null

    override suspend fun entries(): List<BackupHistoryEntry> = items.sortedByDescending { it.createdAt }

    override suspend fun add(entry: BackupHistoryEntry) {
        items.removeAll { it.id == entry.id }
        items += entry
    }

    override suspend fun remove(id: String): Boolean = items.removeAll { it.id == id }

    override suspend fun update(entry: BackupHistoryEntry) {
        val index = items.indexOfFirst { it.id == entry.id }
        if (index >= 0) items[index] = entry else items += entry
    }

    override suspend fun exists(entry: BackupHistoryEntry): Boolean {
        val file = locationFor(entry)?.let { File(it) } ?: return false
        return file.isFile
    }

    override fun locationFor(entry: BackupHistoryEntry): String? =
        managedDirectory?.let { directory -> File(directory, entry.fileName).absolutePath }
            ?: entry.uri
}

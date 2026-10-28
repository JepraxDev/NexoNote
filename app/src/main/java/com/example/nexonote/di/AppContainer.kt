package com.example.nexonote.di

import android.content.Context
import com.example.nexonote.backup.WorkManagerBackupScheduler
import com.example.nexonote.data.local.MIGRATION_6_7
import com.example.nexonote.data.local.MIGRATION_7_8
import com.example.nexonote.data.repository.DataStoreBackupSettingsRepository
import com.example.nexonote.data.repository.FileBackupHistoryStore
import com.example.nexonote.data.repository.LocalBackupRepository
import com.example.nexonote.data.repository.LocalEventRepository
import com.example.nexonote.data.repository.LocalExportDataRepository
import com.example.nexonote.data.repository.LocalOrganizationRepository
import com.example.nexonote.data.repository.LocalTableImportRepository
import com.example.nexonote.data.transfer.BackupService
import com.example.nexonote.data.transfer.ExportService
import com.example.nexonote.data.transfer.FileGateway
import com.example.nexonote.data.transfer.RestoreService
import com.example.nexonote.data.transfer.TableImportService
import com.example.nexonote.domain.repository.BackupHistoryStore
import com.example.nexonote.domain.repository.BackupRepository
import com.example.nexonote.domain.repository.BackupScheduler
import com.example.nexonote.domain.repository.BackupSettingsRepository
import com.example.nexonote.domain.repository.EventRepository
import com.example.nexonote.domain.repository.ExportDataRepository
import com.example.nexonote.domain.repository.OrganizationRepository
import com.example.nexonote.domain.repository.TableImportRepository
import com.example.nexonote.reminder.WorkManagerEventScheduler
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.example.nexonote.data.local.MIGRATION_1_2
import com.example.nexonote.data.local.MIGRATION_2_3
import com.example.nexonote.data.local.MIGRATION_3_4
import com.example.nexonote.data.local.MIGRATION_4_5
import com.example.nexonote.data.local.MIGRATION_5_6
import com.example.nexonote.data.local.NexoDatabase
import com.example.nexonote.data.repository.DataStoreSettingsRepository
import com.example.nexonote.data.repository.LocalActivityRepository
import com.example.nexonote.data.repository.LocalEntryRepository
import com.example.nexonote.data.repository.LocalNoteRepository
import com.example.nexonote.data.repository.LocalTableRepository
import com.example.nexonote.data.repository.LocalTaskRepository
import com.example.nexonote.domain.reminder.ReminderScheduler
import com.example.nexonote.domain.repository.ActivityRepository
import com.example.nexonote.domain.repository.EntryRepository
import com.example.nexonote.domain.repository.NoteRepository
import com.example.nexonote.domain.repository.SettingsRepository
import com.example.nexonote.domain.repository.TableRepository
import com.example.nexonote.domain.repository.TaskRepository
import com.example.nexonote.reminder.WorkManagerReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val Context.settingsDataStore by preferencesDataStore(name = "settings")
private val Context.backupSettingsDataStore by preferencesDataStore(name = "backup_settings")

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val database = Room.databaseBuilder(
        appContext, NexoDatabase::class.java, "nexonote.db"
    ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8).build()
    private val entryDao = database.entryDao()
    private val reminderScheduler: ReminderScheduler = WorkManagerReminderScheduler(appContext)
    val entryRepository: EntryRepository = LocalEntryRepository(entryDao)
    val activityRepository: ActivityRepository = LocalActivityRepository(entryDao)
    val noteRepository: NoteRepository = LocalNoteRepository(database)
    val taskRepository: TaskRepository = LocalTaskRepository(database, reminderScheduler)
    val tableRepository: TableRepository = LocalTableRepository(database)
    val eventRepository: EventRepository = LocalEventRepository(database, WorkManagerEventScheduler(appContext))
    val organizationRepository: OrganizationRepository = LocalOrganizationRepository(database)
    val settingsRepository: SettingsRepository = DataStoreSettingsRepository(appContext.settingsDataStore)

    val fileGateway = FileGateway(appContext)
    private val exportDataRepository: ExportDataRepository = LocalExportDataRepository(
        notes = noteRepository,
        tasks = taskRepository,
        tables = tableRepository,
        organization = organizationRepository,
    )
    val exportService = ExportService(exportDataRepository)

    val tableImportRepository: TableImportRepository = LocalTableImportRepository(database)
    val tableImportService = TableImportService(fileGateway, tableImportRepository)

    val backupSettingsRepository: BackupSettingsRepository =
        DataStoreBackupSettingsRepository(appContext.backupSettingsDataStore)
    val backupRepository: BackupRepository = LocalBackupRepository(database, settingsRepository)
    val backupHistoryStore: BackupHistoryStore = FileBackupHistoryStore(appContext, fileGateway)
    val backupService = BackupService(
        context = appContext,
        repository = backupRepository,
        history = backupHistoryStore,
        files = fileGateway,
    )
    val restoreService = RestoreService(appContext, backupRepository, fileGateway)
    val backupScheduler: BackupScheduler = WorkManagerBackupScheduler(appContext)

    /** Re-applies the persisted automatic-backup frequency when the app starts. */
    fun startBackupSchedule() {
        appScope.launch {
            backupScheduler.schedule(backupSettingsRepository.frequency.first())
        }
    }
}

package com.example.nexonote.di

import android.content.Context
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

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val database = Room.databaseBuilder(
        appContext, NexoDatabase::class.java, "nexonote.db"
    ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6).build()
    private val entryDao = database.entryDao()
    private val reminderScheduler: ReminderScheduler = WorkManagerReminderScheduler(appContext)
    val entryRepository: EntryRepository = LocalEntryRepository(entryDao)
    val activityRepository: ActivityRepository = LocalActivityRepository(entryDao)
    val noteRepository: NoteRepository = LocalNoteRepository(database)
    val taskRepository: TaskRepository = LocalTaskRepository(database, reminderScheduler)
    val tableRepository: TableRepository = LocalTableRepository(database)
    val settingsRepository: SettingsRepository = DataStoreSettingsRepository(appContext.settingsDataStore)
}

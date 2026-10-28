package com.example.nexonote.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.nexonote.data.local.MIGRATION_1_2
import com.example.nexonote.data.local.MIGRATION_2_3
import com.example.nexonote.data.local.MIGRATION_3_4
import com.example.nexonote.data.local.MIGRATION_4_5
import com.example.nexonote.data.local.MIGRATION_5_6
import com.example.nexonote.data.local.MIGRATION_6_7
import com.example.nexonote.data.local.MIGRATION_7_8
import com.example.nexonote.data.local.NexoDatabase
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {
    @get:Rule val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(), NexoDatabase::class.java,
    )

    @Test fun migrationFromStageOnePreservesRecordsAndStartsWithoutActivity() {
        val name = "migration-stage-one-test"
        helper.createDatabase(name, 1).apply {
            execSQL("INSERT INTO entries (id, title, type, completed) VALUES (1, 'Mi nota', 'NOTE', 0), (2, 'Mi tarea', 'TASK', 1), (3, 'Mi tabla', 'TABLE', 0)")
            close()
        }
        helper.runMigrationsAndValidate(
            name, 6, true, MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6,
        ).use { db ->
            db.query("SELECT * FROM entries ORDER BY id").use { cursor ->
                assertEquals(3, cursor.count)
                assertTrue(cursor.moveToFirst())
                assertEquals("Mi nota", cursor.getString(cursor.getColumnIndexOrThrow("title")))
                do {
                    assertTrue(cursor.isNull(cursor.getColumnIndexOrThrow("modifiedAt")))
                    assertTrue(cursor.isNull(cursor.getColumnIndexOrThrow("dueAt")))
                    assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow("priority")))
                } while (cursor.moveToNext())
            }
            db.query("SELECT id, title, content, type, color, createdAt, modifiedAt FROM notes").use { cursor ->
                assertEquals(1, cursor.count)
                assertTrue(cursor.moveToFirst())
                assertEquals(1L, cursor.getLong(cursor.getColumnIndexOrThrow("id")))
                assertEquals("Mi nota", cursor.getString(cursor.getColumnIndexOrThrow("title")))
                assertEquals("", cursor.getString(cursor.getColumnIndexOrThrow("content")))
                assertEquals("TEXT", cursor.getString(cursor.getColumnIndexOrThrow("type")))
                assertEquals("DEFAULT", cursor.getString(cursor.getColumnIndexOrThrow("color")))
                assertTrue(cursor.isNull(cursor.getColumnIndexOrThrow("createdAt")))
                assertTrue(cursor.isNull(cursor.getColumnIndexOrThrow("modifiedAt")))
            }
            db.query("SELECT COUNT(*) FROM activities").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
        }
    }

    @Test fun migrationFromDashboardPreviewBackfillsNoteMetadata() {
        val name = "migration-dashboard-preview-test"
        helper.createDatabase(name, 2).apply {
            execSQL("INSERT INTO entries (id, title, type, completed, modifiedAt, dueAt, priority) VALUES (1, 'Nota', 'NOTE', 0, 100, NULL, 0)")
            execSQL("INSERT INTO entries (id, title, type, completed, modifiedAt, dueAt, priority) VALUES (2, 'Tarea', 'TASK', 0, 200, NULL, 0)")
            close()
        }
        helper.runMigrationsAndValidate(
            name, 6, true, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6,
        ).use { db ->
            db.query("SELECT title, modifiedAt FROM notes WHERE id = 1").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Nota", cursor.getString(0))
                assertEquals(100L, cursor.getLong(1))
            }
            db.query("SELECT COUNT(*) FROM notes").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }
            db.query("SELECT COUNT(*) FROM activities").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
        }
    }

    @Test fun migrationToNotesCreatesEmptyRelationsAndKeepsHistory() {
        val name = "migration-notes-test"
        helper.createDatabase(name, 3).apply {
            execSQL("INSERT INTO entries (id, title, type, completed, modifiedAt, dueAt, priority) VALUES (1, 'Idea', 'NOTE', 0, 500, NULL, 0)")
            execSQL("INSERT INTO entries (id, title, type, completed, modifiedAt, dueAt, priority) VALUES (2, 'Pendiente', 'TASK', 1, 600, NULL, 0)")
            execSQL("INSERT INTO activities (entryId, kind, occurredAt) VALUES (1, 'NOTE_CREATED', 500)")
            close()
        }
        helper.runMigrationsAndValidate(
            name, 6, true, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6,
        ).use { db ->
            db.query("SELECT id, content, type, pinned, favorite, archived, trashed, modifiedAt FROM notes").use { cursor ->
                assertEquals(1, cursor.count)
                assertTrue(cursor.moveToFirst())
                assertEquals(1L, cursor.getLong(0))
                assertEquals("", cursor.getString(1))
                assertEquals("TEXT", cursor.getString(2))
                assertEquals(0, cursor.getInt(3))
                assertEquals(0, cursor.getInt(4))
                assertEquals(0, cursor.getInt(5))
                assertEquals(0, cursor.getInt(6))
                assertEquals(500L, cursor.getLong(7))
            }
            db.query("SELECT COUNT(*) FROM checklist_items").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
            db.query("SELECT COUNT(*) FROM note_labels").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
            db.query("SELECT COUNT(*) FROM activities").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }
        }
    }

    @Test fun migrationToTasksBackfillsExistingTasksWithoutInventingDetails() {
        val name = "migration-tasks-test"
        helper.createDatabase(name, 4).apply {
            execSQL("INSERT INTO entries (id, title, type, completed, modifiedAt, dueAt, priority) VALUES (1, 'Pendiente', 'TASK', 0, 500, 900, 2)")
            execSQL("INSERT INTO entries (id, title, type, completed, modifiedAt, dueAt, priority) VALUES (2, 'Hecha', 'TASK', 1, 600, NULL, 3)")
            execSQL("INSERT INTO entries (id, title, type, completed, modifiedAt, dueAt, priority) VALUES (3, 'Nota', 'NOTE', 0, 700, NULL, 0)")
            execSQL("INSERT INTO notes (id, title, content, type, color, pinned, favorite, archived, trashed, trashedAt, createdAt, modifiedAt) VALUES (3, 'Nota', '', 'TEXT', 'DEFAULT', 0, 0, 0, 0, NULL, NULL, 700)")
            close()
        }
        helper.runMigrationsAndValidate(name, 6, true, MIGRATION_4_5, MIGRATION_5_6).use { db ->
            db.query(
                "SELECT id, title, description, status, priority, dueAt, dueHasTime, reminderEnabled, recurrence, createdAt FROM tasks ORDER BY id"
            ).use { cursor ->
                assertEquals(2, cursor.count)
                assertTrue(cursor.moveToFirst())
                assertEquals(1L, cursor.getLong(0))
                assertEquals("Pendiente", cursor.getString(1))
                assertEquals("", cursor.getString(2))
                assertEquals("PENDING", cursor.getString(3))
                assertEquals(2, cursor.getInt(4))
                assertEquals(900L, cursor.getLong(5))
                assertEquals(0, cursor.getInt(6))
                assertEquals(0, cursor.getInt(7))
                assertEquals("NONE", cursor.getString(8))
                assertTrue(cursor.isNull(cursor.getColumnIndexOrThrow("createdAt")))
                assertTrue(cursor.moveToNext())
                assertEquals(2L, cursor.getLong(0))
                assertEquals("COMPLETED", cursor.getString(3))
                assertEquals(3, cursor.getInt(4))
            }
            db.query("SELECT COUNT(*) FROM subtasks").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
            db.query("SELECT COUNT(*) FROM task_labels").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
            db.query("SELECT COUNT(*) FROM notes").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }
        }
    }

    @Test fun migrationToTablesBackfillsLegacyTablesWithPrimaryNameColumn() {
        val name = "migration-tables-test"
        helper.createDatabase(name, 5).apply {
            execSQL("INSERT INTO entries (id, title, type, completed) VALUES (1, 'Inventario', 'TABLE', 0)")
            execSQL("INSERT INTO entries (id, title, type, completed) VALUES (2, 'Mi nota', 'NOTE', 0)")
            close()
        }
        helper.runMigrationsAndValidate(name, 6, true, MIGRATION_5_6).use { db ->
            db.query("SELECT id, name, icon, color FROM custom_tables").use { cursor ->
                assertEquals(1, cursor.count)
                assertTrue(cursor.moveToFirst())
                assertEquals(1L, cursor.getLong(0))
                assertEquals("Inventario", cursor.getString(1))
                assertEquals("GRID", cursor.getString(2))
                assertEquals("#6750A4", cursor.getString(3))
            }
            db.query(
                "SELECT id, tableId, name, type, isPrimary, required, position FROM custom_columns"
            ).use { cursor ->
                assertEquals(1, cursor.count)
                assertTrue(cursor.moveToFirst())
                assertEquals(1L, cursor.getLong(1))
                assertEquals("Nombre", cursor.getString(2))
                assertEquals("SHORT_TEXT", cursor.getString(3))
                assertEquals(1, cursor.getInt(4))
                assertEquals(1, cursor.getInt(5))
                assertEquals(0, cursor.getInt(6))
            }
            db.query("SELECT COUNT(*) FROM custom_rows").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
        }
    }

    @Test fun migrationToOrganizationKeepsContentAndAddsOptionalColumns() {
        val name = "migration-organization-test"
        helper.createDatabase(name, 7).apply {
            execSQL(
                "INSERT INTO notes (id, title, content, type, color, pinned, favorite, archived, trashed, createdAt) " +
                    "VALUES (1, 'Nota previa', 'Contenido', 'TEXT', 'DEFAULT', 0, 1, 0, 0, 100)"
            )
            execSQL("INSERT INTO labels (id, name) VALUES (1, 'trabajo')")
            execSQL("INSERT INTO note_labels (noteId, labelId) VALUES (1, 1)")
            close()
        }
        helper.runMigrationsAndValidate(name, 8, true, MIGRATION_7_8).use { db ->
            db.query("SELECT title, favorite, folderId, categoryId FROM notes WHERE id = 1").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Nota previa", cursor.getString(0))
                assertEquals(1, cursor.getInt(1))
                assertTrue(cursor.isNull(2))
                assertTrue(cursor.isNull(3))
            }
            db.query("SELECT color FROM labels WHERE id = 1").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("#6750A4", cursor.getString(0))
            }
            db.query("SELECT COUNT(*) FROM note_labels").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }
            listOf("folders", "categories", "saved_views", "table_labels", "event_labels").forEach { table ->
                db.query("SELECT COUNT(*) FROM $table").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals(0, cursor.getInt(0))
                }
            }
            db.query(
                "SELECT COUNT(*) FROM pragma_table_info('tasks') WHERE name IN ('favorite', 'folderId', 'categoryId')"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(3, cursor.getInt(0))
            }
            db.query(
                "SELECT COUNT(*) FROM pragma_table_info('events') WHERE name IN ('favorite', 'folderId', 'categoryId')"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(3, cursor.getInt(0))
            }
        }
    }
}


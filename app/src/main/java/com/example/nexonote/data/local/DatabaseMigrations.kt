package com.example.nexonote.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // A missing historical timestamp stays unknown; migration time is not modification time.
        db.execSQL("ALTER TABLE entries ADD COLUMN modifiedAt INTEGER")
        db.execSQL("ALTER TABLE entries ADD COLUMN dueAt INTEGER")
        db.execSQL("ALTER TABLE entries ADD COLUMN priority INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Activity starts being recorded from this version; past actions are not reconstructed.
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `activities` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `entryId` INTEGER NOT NULL,
                `kind` TEXT NOT NULL,
                `occurredAt` INTEGER NOT NULL,
                FOREIGN KEY(`entryId`) REFERENCES `entries`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_activities_entryId` ON `activities` (`entryId`)")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `notes` (
                `id` INTEGER NOT NULL,
                `title` TEXT NOT NULL,
                `content` TEXT NOT NULL,
                `type` TEXT NOT NULL,
                `color` TEXT NOT NULL,
                `pinned` INTEGER NOT NULL,
                `favorite` INTEGER NOT NULL,
                `archived` INTEGER NOT NULL,
                `trashed` INTEGER NOT NULL,
                `trashedAt` INTEGER,
                `createdAt` INTEGER,
                `modifiedAt` INTEGER,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`id`) REFERENCES `entries`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `checklist_items` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `noteId` INTEGER NOT NULL,
                `text` TEXT NOT NULL,
                `checked` INTEGER NOT NULL,
                `position` INTEGER NOT NULL,
                FOREIGN KEY(`noteId`) REFERENCES `notes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_checklist_items_noteId` ON `checklist_items` (`noteId`)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `labels` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_labels_name` ON `labels` (`name`)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `note_labels` (
                `noteId` INTEGER NOT NULL,
                `labelId` INTEGER NOT NULL,
                PRIMARY KEY(`noteId`, `labelId`),
                FOREIGN KEY(`noteId`) REFERENCES `notes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`labelId`) REFERENCES `labels`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_note_labels_labelId` ON `note_labels` (`labelId`)")
        db.execSQL(
            """
            INSERT INTO notes (
                id, title, content, type, color, pinned, favorite, archived, trashed,
                trashedAt, createdAt, modifiedAt
            )
            SELECT id, title, '', 'TEXT', 'DEFAULT', 0, 0, 0, 0, NULL, NULL, modifiedAt
            FROM entries
            WHERE type = 'NOTE'
            """.trimIndent()
        )
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `custom_tables` (
                `id` INTEGER NOT NULL,
                `name` TEXT NOT NULL,
                `description` TEXT NOT NULL,
                `icon` TEXT NOT NULL,
                `color` TEXT NOT NULL,
                `createdAt` INTEGER,
                `modifiedAt` INTEGER,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`id`) REFERENCES `entries`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_custom_tables_modifiedAt` ON `custom_tables` (`modifiedAt`)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `custom_columns` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `tableId` INTEGER NOT NULL,
                `name` TEXT NOT NULL,
                `type` TEXT NOT NULL,
                `required` INTEGER NOT NULL,
                `isPrimary` INTEGER NOT NULL,
                `defaultValue` TEXT,
                `position` INTEGER NOT NULL,
                FOREIGN KEY(`tableId`) REFERENCES `custom_tables`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_custom_columns_tableId` ON `custom_columns` (`tableId`)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `column_options` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `columnId` INTEGER NOT NULL,
                `name` TEXT NOT NULL,
                `color` TEXT NOT NULL,
                `position` INTEGER NOT NULL,
                FOREIGN KEY(`columnId`) REFERENCES `custom_columns`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_column_options_columnId` ON `column_options` (`columnId`)")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_column_options_columnId_name` " +
                "ON `column_options` (`columnId`, `name`)"
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `custom_rows` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `tableId` INTEGER NOT NULL,
                `title` TEXT NOT NULL,
                `createdAt` INTEGER,
                `modifiedAt` INTEGER,
                FOREIGN KEY(`tableId`) REFERENCES `custom_tables`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_custom_rows_tableId` ON `custom_rows` (`tableId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_custom_rows_modifiedAt` ON `custom_rows` (`modifiedAt`)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `custom_cells` (
                `rowId` INTEGER NOT NULL,
                `columnId` INTEGER NOT NULL,
                `value` TEXT,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`rowId`, `columnId`),
                FOREIGN KEY(`rowId`) REFERENCES `custom_rows`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`columnId`) REFERENCES `custom_columns`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_custom_cells_columnId` ON `custom_cells` (`columnId`)")

        db.execSQL(
            """
            INSERT INTO custom_tables (id, name, description, icon, color, createdAt, modifiedAt)
            SELECT id, title, '', 'GRID', '#6750A4', modifiedAt, modifiedAt
            FROM entries
            WHERE type = 'TABLE'
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO custom_columns (tableId, name, type, required, isPrimary, defaultValue, position)
            SELECT id, 'Nombre', 'SHORT_TEXT', 1, 1, NULL, 0
            FROM entries
            WHERE type = 'TABLE'
            """.trimIndent()
        )
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `tasks` (
                `id` INTEGER NOT NULL,
                `title` TEXT NOT NULL,
                `description` TEXT NOT NULL,
                `status` TEXT NOT NULL,
                `priority` INTEGER NOT NULL,
                `startAt` INTEGER,
                `dueAt` INTEGER,
                `dueHasTime` INTEGER NOT NULL,
                `reminderEnabled` INTEGER NOT NULL,
                `reminderAt` INTEGER,
                `recurrence` TEXT NOT NULL,
                `recurrenceEndAt` INTEGER,
                `seriesId` INTEGER,
                `occurrenceAt` INTEGER,
                `createdAt` INTEGER,
                `modifiedAt` INTEGER,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`id`) REFERENCES `entries`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_status` ON `tasks` (`status`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_dueAt` ON `tasks` (`dueAt`)")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_tasks_seriesId_occurrenceAt` " +
                "ON `tasks` (`seriesId`, `occurrenceAt`)"
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `subtasks` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `taskId` INTEGER NOT NULL,
                `text` TEXT NOT NULL,
                `completed` INTEGER NOT NULL,
                `position` INTEGER NOT NULL,
                FOREIGN KEY(`taskId`) REFERENCES `tasks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_subtasks_taskId` ON `subtasks` (`taskId`)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `task_labels` (
                `taskId` INTEGER NOT NULL,
                `labelId` INTEGER NOT NULL,
                PRIMARY KEY(`taskId`, `labelId`),
                FOREIGN KEY(`taskId`) REFERENCES `tasks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`labelId`) REFERENCES `labels`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_labels_labelId` ON `task_labels` (`labelId`)")
        db.execSQL(
            """
            INSERT INTO tasks (
                id, title, description, status, priority, startAt, dueAt, dueHasTime,
                reminderEnabled, reminderAt, recurrence, recurrenceEndAt, seriesId,
                occurrenceAt, createdAt, modifiedAt
            )
            SELECT
                id, title, '',
                CASE WHEN completed = 1 THEN 'COMPLETED' ELSE 'PENDING' END,
                priority, NULL, dueAt, 0, 0, NULL, 'NONE', NULL, NULL, NULL, NULL, modifiedAt
            FROM entries
            WHERE type = 'TASK'
            """.trimIndent()
        )
    }
}

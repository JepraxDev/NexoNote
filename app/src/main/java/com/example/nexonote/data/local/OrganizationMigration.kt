package com.example.nexonote.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Stage 7 is additive: folders, categories, colored tags, saved views and per-content links.
 * Existing rows keep their data; new optional columns stay NULL/false until the user organizes them.
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `folders` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `description` TEXT NOT NULL,
                `icon` TEXT NOT NULL,
                `color` TEXT NOT NULL,
                `parentId` INTEGER,
                `createdAt` INTEGER NOT NULL,
                `modifiedAt` INTEGER NOT NULL,
                FOREIGN KEY(`parentId`) REFERENCES `folders`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_folders_parentId` ON `folders` (`parentId`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `categories` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL COLLATE NOCASE,
                `color` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `modifiedAt` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_categories_name` ON `categories` (`name`)"
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `saved_views` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL COLLATE NOCASE,
                `query` TEXT NOT NULL,
                `categoryId` INTEGER,
                `folderId` INTEGER,
                `includeSubfolders` INTEGER NOT NULL,
                `tagIds` TEXT NOT NULL,
                `types` TEXT NOT NULL,
                `favoriteOnly` INTEGER NOT NULL,
                `dateFilter` TEXT NOT NULL,
                `stateFilter` TEXT NOT NULL,
                `sort` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `modifiedAt` INTEGER NOT NULL,
                FOREIGN KEY(`folderId`) REFERENCES `folders`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL,
                FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_saved_views_folderId` ON `saved_views` (`folderId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_saved_views_categoryId` ON `saved_views` (`categoryId`)")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_saved_views_name` ON `saved_views` (`name`)"
        )

        db.execSQL("ALTER TABLE `labels` ADD COLUMN `color` TEXT NOT NULL DEFAULT '#6750A4'")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `table_labels` (
                `tableId` INTEGER NOT NULL,
                `labelId` INTEGER NOT NULL,
                PRIMARY KEY(`tableId`, `labelId`),
                FOREIGN KEY(`tableId`) REFERENCES `custom_tables`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`labelId`) REFERENCES `labels`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_table_labels_labelId` ON `table_labels` (`labelId`)"
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `event_labels` (
                `eventId` INTEGER NOT NULL,
                `labelId` INTEGER NOT NULL,
                PRIMARY KEY(`eventId`, `labelId`),
                FOREIGN KEY(`eventId`) REFERENCES `events`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`labelId`) REFERENCES `labels`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_event_labels_labelId` ON `event_labels` (`labelId`)"
        )

        db.execSQL(
            "ALTER TABLE `notes` ADD COLUMN `folderId` INTEGER REFERENCES `folders`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL"
        )
        db.execSQL(
            "ALTER TABLE `notes` ADD COLUMN `categoryId` INTEGER REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_folderId` ON `notes` (`folderId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_categoryId` ON `notes` (`categoryId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_favorite` ON `notes` (`favorite`)")

        db.execSQL("ALTER TABLE `tasks` ADD COLUMN `favorite` INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            "ALTER TABLE `tasks` ADD COLUMN `folderId` INTEGER REFERENCES `folders`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL"
        )
        db.execSQL(
            "ALTER TABLE `tasks` ADD COLUMN `categoryId` INTEGER REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_folderId` ON `tasks` (`folderId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_categoryId` ON `tasks` (`categoryId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_favorite` ON `tasks` (`favorite`)")

        db.execSQL("ALTER TABLE `custom_tables` ADD COLUMN `favorite` INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            "ALTER TABLE `custom_tables` ADD COLUMN `folderId` INTEGER REFERENCES `folders`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL"
        )
        db.execSQL(
            "ALTER TABLE `custom_tables` ADD COLUMN `categoryId` INTEGER REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_custom_tables_folderId` ON `custom_tables` (`folderId`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_custom_tables_categoryId` ON `custom_tables` (`categoryId`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_custom_tables_favorite` ON `custom_tables` (`favorite`)"
        )

        db.execSQL("ALTER TABLE `events` ADD COLUMN `favorite` INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            "ALTER TABLE `events` ADD COLUMN `folderId` INTEGER REFERENCES `folders`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL"
        )
        db.execSQL(
            "ALTER TABLE `events` ADD COLUMN `categoryId` INTEGER REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_events_folderId` ON `events` (`folderId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_events_categoryId` ON `events` (`categoryId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_events_favorite` ON `events` (`favorite`)")
    }
}

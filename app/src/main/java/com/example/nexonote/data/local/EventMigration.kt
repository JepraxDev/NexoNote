package com.example.nexonote.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS events (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                title TEXT NOT NULL, startAt INTEGER NOT NULL, endAt INTEGER NOT NULL,
                description TEXT NOT NULL, allDay INTEGER NOT NULL, color TEXT NOT NULL,
                category TEXT NOT NULL, zoneId TEXT NOT NULL, recurrence TEXT NOT NULL,
                repeatUntil INTEGER, reminderMinutes INTEGER, parentId INTEGER, originalStart INTEGER,
                cancelled INTEGER NOT NULL, revision INTEGER NOT NULL, notifiedStart INTEGER, plannedStart INTEGER,
                FOREIGN KEY(parentId) REFERENCES events(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX index_events_parentId_originalStart ON events(parentId, originalStart)")
        db.execSQL("CREATE INDEX index_events_startAt ON events(startAt)")
    }
}

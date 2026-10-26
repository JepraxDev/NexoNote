package com.example.nexonote.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.ui.graphics.vector.ImageVector

enum class Destination(val route: String, val label: String, val icon: ImageVector) {
    HOME("home", "Inicio", Icons.Outlined.Home),
    NOTES("notes", "Notas", Icons.AutoMirrored.Outlined.StickyNote2),
    TASKS("tasks", "Tareas", Icons.Outlined.CheckCircle),
    TABLES("tables", "Tablas", Icons.Outlined.TableChart),
    SETTINGS("settings", "Ajustes", Icons.Outlined.Settings),
}

object NoteRoutes {
    const val EDITOR = "note/{noteId}"
    const val NOTE_ID = "noteId"
    const val NEW_NOTE_ID = 0L
    fun editor(noteId: Long): String = "note/$noteId"
}

object TaskRoutes {
    const val EDITOR = "task/{taskId}"
    const val TASK_ID = "taskId"
    const val NEW_TASK_ID = 0L
    fun editor(taskId: Long): String = "task/$taskId"
}

object TableRoutes {
    const val DETAIL = "table/{tableId}"
    const val TABLE_ID = "tableId"
    fun detail(tableId: Long): String = "table/$tableId"
}

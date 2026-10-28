package com.example.nexonote.domain.export

import com.example.nexonote.domain.model.Task
import com.example.nexonote.domain.model.TaskStatus

/** Builds a tabular representation of tasks, including their subtasks, dates and organisation. */
object TaskSheetBuilder {
    val headers = listOf(
        "Título",
        "Descripción",
        "Estado",
        "Prioridad",
        "Inicio",
        "Vencimiento",
        "Con hora",
        "Etiquetas",
        "Carpeta",
        "Categoría",
        "Subtareas",
        "Creada",
        "Modificada",
    )

    fun rows(
        tasks: List<Task>,
        categoryNames: Map<Long, String> = emptyMap(),
        folderNames: Map<Long, String> = emptyMap(),
    ): List<List<SheetCell?>> = listOf(headers.map { SheetCell.text(it) }) +
        tasks.map { task -> listOf(
            SheetCell.text(task.title),
            SheetCell.text(task.description),
            SheetCell.text(statusLabel(task.status)),
            SheetCell.text(task.priority.label),
            SheetCell.text(TransferFormats.formatDateTime(task.startAt)),
            SheetCell.text(TransferFormats.formatDateTime(task.dueAt)),
            SheetCell.text(if (task.dueHasTime) "Sí" else "No"),
            SheetCell.text(task.labels.joinToString(", ")),
            SheetCell.text(task.folderId?.let(folderNames::get)),
            SheetCell.text(task.categoryId?.let(categoryNames::get)),
            SheetCell.text(
                task.subtasks.filter { it.text.isNotBlank() }
                    .joinToString("\n") { "- [${if (it.completed) "x" else " "}] ${it.text}" }
            ),
            SheetCell.text(TransferFormats.formatDateTime(task.createdAt)),
            SheetCell.text(TransferFormats.formatDateTime(task.modifiedAt)),
        ) }

    fun statusLabel(status: TaskStatus): String = when (status) {
        TaskStatus.PENDING -> "Pendiente"
        TaskStatus.IN_PROGRESS -> "En curso"
        TaskStatus.COMPLETED -> "Completada"
    }
}

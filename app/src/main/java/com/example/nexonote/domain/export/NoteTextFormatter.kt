package com.example.nexonote.domain.export

import com.example.nexonote.domain.model.Note
import com.example.nexonote.domain.model.NoteType

/** Plain-text and Markdown renderers for notes. Both preserve Unicode exactly as stored. */
object NoteTextFormatter {
    fun plainText(note: Note, folderName: String? = null, categoryName: String? = null): String = buildString {
        appendLine(note.title.ifBlank { "Nota sin título" })
        appendLine("=".repeat((note.title.ifBlank { "Nota sin título" }).length.coerceIn(3, 80)))
        appendLine()
        appendMeta(note, folderName, categoryName, markdown = false)
        if (note.content.isNotBlank()) {
            appendLine()
            appendLine(note.content)
        }
        if (note.checklist.isNotEmpty()) {
            appendLine()
            appendLine("Lista de comprobación")
            note.checklist.forEach { item ->
                appendLine("[${if (item.checked) "x" else " "}] ${item.text}")
            }
        }
    }

    fun markdown(note: Note, folderName: String? = null, categoryName: String? = null): String = buildString {
        appendLine("# ${note.title.ifBlank { "Nota sin título" }}")
        appendLine()
        appendMeta(note, folderName, categoryName, markdown = true)
        if (note.content.isNotBlank()) {
            appendLine()
            appendLine(note.content)
        }
        if (note.checklist.isNotEmpty()) {
            appendLine()
            appendLine("## Lista de comprobación")
            appendLine()
            note.checklist.forEach { item ->
                appendLine("- [${if (item.checked) "x" else " "}] ${item.text}")
            }
        }
    }

    private fun StringBuilder.appendMeta(
        note: Note,
        folderName: String?,
        categoryName: String?,
        markdown: Boolean,
    ) {
        val created = TransferFormats.formatDateTime(note.createdAt)
        val modified = TransferFormats.formatDateTime(note.modifiedAt)
        if (created.isNotEmpty()) appendLine("- Creada: $created")
        if (modified.isNotEmpty()) appendLine("- Modificada: $modified")
        if (folderName != null) appendLine("- Carpeta: $folderName")
        if (categoryName != null) appendLine("- Categoría: $categoryName")
        if (note.labels.isNotEmpty()) {
            appendLine(
                if (markdown) {
                    "- Etiquetas: " + note.labels.joinToString(", ") { "#$it" }
                } else {
                    "- Etiquetas: " + note.labels.joinToString(", ")
                }
            )
        }
        if (note.type == NoteType.CHECKLIST) appendLine("- Tipo: lista de comprobación")
        if (note.archived) appendLine("- Estado: archivada")
        if (note.trashed) appendLine("- Estado: en papelera")
    }
}

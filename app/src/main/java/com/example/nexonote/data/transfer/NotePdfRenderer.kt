package com.example.nexonote.data.transfer

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.graphics.withClip
import androidx.core.graphics.withTranslation
import com.example.nexonote.domain.export.TransferFormats
import com.example.nexonote.domain.model.Note
import com.example.nexonote.domain.model.NoteType
import java.io.OutputStream

/**
 * Minimal, dependency-free PDF renderer for notes. It uses the platform [PdfDocument] and
 * [StaticLayout], so Unicode text and line wrapping follow the same rules as the UI.
 */
class NotePdfRenderer {
    private val pageWidth = 595
    private val pageHeight = 842
    private val margin = 48f

    fun render(
        notes: List<Note>,
        folderNames: Map<Long, String>,
        categoryNames: Map<Long, String>,
        out: OutputStream,
    ) {
        require(notes.isNotEmpty()) { "No hay notas para exportar." }
        val document = PdfDocument()
        var pageNumber = 1
        var page = document.startPage(
            PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        )
        var canvas = page.canvas
        var y = margin

        fun finishPage() {
            val footer = TextPaint().apply {
                color = Color.GRAY
                textSize = 8.5f
                isAntiAlias = true
            }
            canvas.drawText(
                "NexoNote · $pageNumber",
                margin,
                pageHeight - 24f,
                footer,
            )
        }

        fun newPage() {
            finishPage()
            document.finishPage(page)
            pageNumber++
            page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
            canvas = page.canvas
            y = margin
        }

        fun drawLayout(layout: StaticLayout, spacingAfter: Float) {
            if (y + layout.height <= pageHeight - margin - 20f) {
                canvas.withTranslation(margin, y) { layout.draw(this) }
                y += layout.height + spacingAfter
                return
            }
            for (line in 0 until layout.lineCount) {
                val top = layout.getLineTop(line)
                val bottom = layout.getLineBottom(line)
                val height = bottom - top
                if (y + height > pageHeight - margin - 20f) {
                    newPage()
                }
                canvas.withClip(margin, y, pageWidth - margin, y + height) {
                    withTranslation(margin, y - top) { layout.draw(this) }
                }
                y += height
            }
            y += spacingAfter
        }

        fun addText(text: String, paint: TextPaint, spacingAfter: Float = 10f) {
            if (text.isBlank()) return
            val layout = StaticLayout.Builder
                .obtain(text, 0, text.length, paint, (pageWidth - margin * 2).toInt())
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.12f)
                .setIncludePad(false)
                .build()
            drawLayout(layout, spacingAfter)
        }

        val titlePaint = TextPaint().apply {
            color = Color.BLACK
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val metaPaint = TextPaint().apply {
            color = Color.DKGRAY
            textSize = 9.5f
            isAntiAlias = true
        }
        val bodyPaint = TextPaint().apply {
            color = Color.BLACK
            textSize = 11f
            isAntiAlias = true
        }
        val headingPaint = TextPaint().apply {
            color = Color.BLACK
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val checklistPaint = TextPaint().apply {
            color = Color.BLACK
            textSize = 11f
            isAntiAlias = true
        }

        notes.forEachIndexed { index, note ->
            if (index > 0) newPage()
            addText(note.title.ifBlank { "Nota sin título" }, titlePaint, 8f)

            val meta = buildList {
                note.createdAt?.let { add("Creada: ${TransferFormats.formatDateTime(it)}") }
                note.modifiedAt?.let { add("Modificada: ${TransferFormats.formatDateTime(it)}") }
                note.folderId?.let(folderNames::get)?.let { add("Carpeta: $it") }
                note.categoryId?.let(categoryNames::get)?.let { add("Categoría: $it") }
                if (note.labels.isNotEmpty()) add("Etiquetas: ${note.labels.joinToString(", ")}")
                if (note.archived) add("Archivada")
                if (note.trashed) add("En la papelera")
            }.joinToString(" · ")
            if (meta.isNotBlank()) addText(meta, metaPaint, 12f)

            if (note.content.isNotBlank()) {
                addText(note.content, bodyPaint, 12f)
            }
            if (note.checklist.isNotEmpty()) {
                addText("Lista de comprobación", headingPaint, 6f)
                note.checklist.forEach { item ->
                    addText("[${if (item.checked) "x" else " "}] ${item.text}", checklistPaint, 4f)
                }
            }
            if (note.type == NoteType.CHECKLIST && note.checklist.isEmpty() && note.content.isBlank()) {
                addText("(lista de comprobación vacía)", metaPaint, 8f)
            }
        }

        finishPage()
        document.finishPage(page)
        try {
            document.writeTo(out)
        } finally {
            document.close()
        }
    }
}

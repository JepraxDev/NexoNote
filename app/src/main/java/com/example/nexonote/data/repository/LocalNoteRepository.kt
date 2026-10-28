package com.example.nexonote.data.repository

import androidx.room.withTransaction
import com.example.nexonote.data.local.ActivityEntity
import com.example.nexonote.data.local.ChecklistItemEntity
import com.example.nexonote.data.local.LabelEntity
import com.example.nexonote.data.local.NexoDatabase
import com.example.nexonote.data.local.NoteEntity
import com.example.nexonote.data.local.NoteLabelCrossRef
import com.example.nexonote.data.local.NoteWithDetails
import com.example.nexonote.domain.model.ChecklistItem
import com.example.nexonote.domain.model.Note
import com.example.nexonote.domain.model.NoteColor
import com.example.nexonote.domain.model.NoteInput
import com.example.nexonote.domain.model.NoteType
import com.example.nexonote.domain.repository.NoteRepository
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LocalNoteRepository(
    private val database: NexoDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) : NoteRepository {
    private val noteDao = database.noteDao()
    private val entryDao = database.entryDao()

    override val notes: Flow<List<Note>> = noteDao.observeAll().map { notes ->
        notes.map(NoteWithDetails::toDomain)
    }

    override val labels: Flow<List<String>> = noteDao.observeLabelNames()

    override fun observeNote(id: Long): Flow<Note?> = noteDao.observe(id).map { it?.toDomain() }

    override suspend fun save(id: Long?, input: NoteInput): Long = database.withTransaction {
        validate(input)
        val now = clock()
        val title = input.title.trim()
        val content = input.content
        val type = input.type
        val color = input.color
        val summary = summaryFor(input)
        if (input.folderId != null) {
            require(database.organizationDao().findFolder(input.folderId) != null) {
                "La carpeta seleccionada ya no existe."
            }
        }
        if (input.categoryId != null) {
            require(database.organizationDao().findCategory(input.categoryId) != null) {
                "La categoría seleccionada ya no existe."
            }
        }

        val noteId = if (id == null) {
            require(input.hasContent) { "La nota está vacía." }
            val entryId = entryDao.insert(
                com.example.nexonote.data.local.EntryEntity(
                    title = summary,
                    type = "NOTE",
                    modifiedAt = now,
                )
            )
            noteDao.insertNote(
                NoteEntity(
                    id = entryId,
                    title = title,
                    content = content,
                    type = type.name,
                    color = color.name,
                    pinned = input.pinned,
                    favorite = input.favorite,
                    archived = false,
                    trashed = false,
                    trashedAt = null,
                    createdAt = now,
                    modifiedAt = now,
                    folderId = input.folderId,
                    categoryId = input.categoryId,
                )
            )
            entryDao.insertActivity(
                ActivityEntity(entryId = entryId, kind = "NOTE_CREATED", occurredAt = now)
            )
            entryId
        } else {
            val existing = noteDao.find(id) ?: error("La nota ya no existe.")
            noteDao.updateNote(
                existing.note.copy(
                    title = title,
                    content = content,
                    type = type.name,
                    color = color.name,
                    pinned = input.pinned,
                    favorite = input.favorite,
                    modifiedAt = now,
                    folderId = input.folderId,
                    categoryId = input.categoryId,
                )
            )
            entryDao.updateNoteSummary(id, summary, now)
            id
        }

        noteDao.deleteChecklist(noteId)
        val checklist = input.checklist.mapIndexedNotNull { index, item ->
            val text = item.text.trim()
            if (text.isEmpty()) null
            else ChecklistItemEntity(
                noteId = noteId,
                text = text,
                checked = item.checked,
                position = index,
            )
        }
        if (checklist.isNotEmpty()) noteDao.insertChecklist(checklist)

        noteDao.deleteNoteLabels(noteId)
        input.labels.forEach { raw ->
            val name = normalizeLabel(raw)
            if (name.isEmpty()) return@forEach
            val label = noteDao.findLabel(name) ?: run {
                val labelId = noteDao.insertLabel(LabelEntity(name = name))
                if (labelId > 0) LabelEntity(id = labelId, name = name) else noteDao.findLabel(name)
            }
            label?.let { noteDao.insertNoteLabel(NoteLabelCrossRef(noteId, it.id)) }
        }
        noteId
    }

    override suspend fun setPinned(id: Long, pinned: Boolean) {
        noteDao.setPinned(id, pinned)
    }

    override suspend fun setFavorite(id: Long, favorite: Boolean) {
        noteDao.setFavorite(id, favorite)
    }

    override suspend fun setArchived(id: Long, archived: Boolean) {
        noteDao.setArchived(id, archived)
    }

    override suspend fun moveToTrash(id: Long) {
        noteDao.trash(id, clock())
    }

    override suspend fun restore(id: Long) {
        noteDao.restore(id)
    }

    override suspend fun deletePermanently(id: Long) {
        database.withTransaction { entryDao.deleteEntry(id) }
    }

    override suspend fun emptyTrash() {
        database.withTransaction { entryDao.deleteTrashedNotes() }
    }

    private fun validate(input: NoteInput) {
        require(input.title.trim().length <= 120) { "El título es demasiado largo." }
        require(input.labels.size <= 20) { "Hay demasiadas etiquetas." }
    }

    private fun summaryFor(input: NoteInput): String {
        val title = input.title.trim()
        if (title.isNotEmpty()) return title.take(120)
        val contentLine = input.content.lineSequence().map(String::trim).firstOrNull(String::isNotEmpty)
        if (contentLine != null) return contentLine.take(120)
        val checklistLine = input.checklist.map { it.text.trim() }.firstOrNull(String::isNotEmpty)
        return checklistLine?.take(120) ?: "Nota sin título"
    }

    private fun normalizeLabel(value: String): String =
        value.trim().lowercase(Locale.ROOT).take(30)
}

private fun NoteWithDetails.toDomain(): Note {
    val type = NoteType.entries.firstOrNull { it.name == note.type } ?: NoteType.TEXT
    val color = NoteColor.entries.firstOrNull { it.name == note.color } ?: NoteColor.DEFAULT
    return Note(
        id = note.id,
        title = note.title,
        content = note.content,
        type = type,
        color = color,
        pinned = note.pinned,
        favorite = note.favorite,
        archived = note.archived,
        trashed = note.trashed,
        trashedAt = note.trashedAt,
        createdAt = note.createdAt,
        modifiedAt = note.modifiedAt,
        labels = labels.map { it.name }.sorted(),
        checklist = checklist.sortedBy { it.position }.map {
            ChecklistItem(it.id, it.text, it.checked, it.position)
        },
        folderId = note.folderId,
        categoryId = note.categoryId,
    )
}

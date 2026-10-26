package com.example.nexonote.domain.model

enum class NoteType { TEXT, CHECKLIST }

enum class NoteColor { DEFAULT, RED, ORANGE, YELLOW, GREEN, BLUE, PURPLE }

enum class NoteFilter { ACTIVE, ARCHIVED, TRASH }

data class ChecklistItem(
    val id: Long = 0,
    val text: String,
    val checked: Boolean = false,
    val position: Int = 0,
)

data class ChecklistDraft(
    val text: String,
    val checked: Boolean = false,
)

data class Note(
    val id: Long,
    val title: String,
    val content: String,
    val type: NoteType,
    val color: NoteColor = NoteColor.DEFAULT,
    val pinned: Boolean = false,
    val favorite: Boolean = false,
    val archived: Boolean = false,
    val trashed: Boolean = false,
    val trashedAt: Long? = null,
    val createdAt: Long? = null,
    val modifiedAt: Long? = null,
    val labels: List<String> = emptyList(),
    val checklist: List<ChecklistItem> = emptyList(),
) {
    val displayTitle: String get() = title.ifBlank { "Nota sin título" }
    val isActive: Boolean get() = !archived && !trashed
    val hasContent: Boolean
        get() = title.isNotBlank() || content.isNotBlank() || checklist.any { it.text.isNotBlank() }
    val checkedItems: Int get() = checklist.count { it.checked && it.text.isNotBlank() }
    val totalItems: Int get() = checklist.count { it.text.isNotBlank() }
    val preview: String
        get() {
            if (content.isNotBlank()) return content.trim()
            return checklist.firstOrNull { it.text.isNotBlank() }?.text.orEmpty()
        }
}

data class NoteInput(
    val title: String,
    val content: String,
    val type: NoteType,
    val color: NoteColor,
    val pinned: Boolean,
    val favorite: Boolean,
    val checklist: List<ChecklistDraft> = emptyList(),
    val labels: List<String> = emptyList(),
) {
    val hasContent: Boolean
        get() = title.isNotBlank() || content.isNotBlank() || checklist.any { it.text.isNotBlank() }
}

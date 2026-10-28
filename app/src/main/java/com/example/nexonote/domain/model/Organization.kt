package com.example.nexonote.domain.model

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

const val DEFAULT_ORGANIZATION_COLOR = "#6750A4"

val ORGANIZATION_COLOR_PALETTE = listOf(
    "#6750A4", "#006A6A", "#B3261E", "#775A00", "#0061A4",
    "#4E6A2F", "#8A4F7D", "#5D4037", "#00695C", "#37474F",
)

enum class ContentType(val label: String, val singular: String) {
    NOTE("Notas", "Nota"),
    TASK("Tareas", "Tarea"),
    TABLE("Tablas", "Tabla"),
    EVENT("Eventos", "Evento"),
}

enum class FolderIcon(val label: String) {
    FOLDER("Carpeta"),
    WORK("Trabajo"),
    HOME("Casa"),
    STAR("Favorita"),
    LIGHTBULB("Idea"),
    BOOK("Libro"),
    CALENDAR("Calendario"),
    FLAG("Objetivo"),
    LIST("Lista"),
    ARCHIVE("Archivo"),
}

data class Folder(
    val id: Long,
    val name: String,
    val description: String = "",
    val icon: FolderIcon = FolderIcon.FOLDER,
    val color: String = DEFAULT_ORGANIZATION_COLOR,
    val parentId: Long? = null,
    val itemCount: Int = 0,
    val createdAt: Long? = null,
    val modifiedAt: Long? = null,
) {
    val displayName: String get() = name.ifBlank { "Carpeta sin nombre" }
}

data class Category(
    val id: Long,
    val name: String,
    val color: String = DEFAULT_ORGANIZATION_COLOR,
    val itemCount: Int = 0,
    val createdAt: Long? = null,
    val modifiedAt: Long? = null,
) {
    val displayName: String get() = name.ifBlank { "Categoría sin nombre" }
}

/**
 * Tags are the same persisted labels introduced for notes in stage 3. Tables, tasks and events
 * reuse the label table through cross references instead of duplicating a second tag system.
 */
data class Tag(
    val id: Long,
    val name: String,
    val color: String = DEFAULT_ORGANIZATION_COLOR,
    val itemCount: Int = 0,
) {
    val displayName: String get() = name.ifBlank { "Etiqueta" }
}

data class OrganizationItem(
    val type: ContentType,
    val id: Long,
    val title: String,
    val preview: String = "",
    val date: Long? = null,
    val favorite: Boolean = false,
    val folderId: Long? = null,
    val categoryId: Long? = null,
    val folderName: String? = null,
    val categoryName: String? = null,
    val folderColor: String? = null,
    val tags: List<Tag> = emptyList(),
    val taskStatus: TaskStatus? = null,
    val priority: TaskPriority = TaskPriority.UNSPECIFIED,
) {
    val displayTitle: String get() = title.ifBlank { "${type.singular} sin título" }
    val key: String get() = "${type.name}:$id"
}

enum class SearchDateFilter(val label: String) {
    ANY("Cualquier fecha"),
    TODAY("Hoy"),
    WEEK("Próximos 7 días"),
    MONTH("Próximos 30 días"),
    OVERDUE("Vencidos"),
}

enum class SearchStateFilter(val label: String) {
    ANY("Cualquier estado"),
    PENDING("Pendientes"),
    COMPLETED("Completadas"),
}

enum class SearchSort(val label: String) {
    DATE_DESC("Más recientes"),
    DATE_ASC("Más antiguos"),
    TITLE("Título"),
    TYPE("Tipo"),
    PRIORITY("Prioridad"),
}

data class SearchFilters(
    val query: String = "",
    val categoryId: Long? = null,
    val folderId: Long? = null,
    val includeSubfolders: Boolean = false,
    val tagIds: Set<Long> = emptySet(),
    val types: Set<ContentType> = ContentType.entries.toSet(),
    val favoriteOnly: Boolean = false,
    val date: SearchDateFilter = SearchDateFilter.ANY,
    val state: SearchStateFilter = SearchStateFilter.ANY,
    val sort: SearchSort = SearchSort.DATE_DESC,
) {
    val isDefault: Boolean
        get() = this == SearchFilters()

    fun matches(item: OrganizationItem, now: Long): Boolean {
        if (item.type !in types) return false
        if (favoriteOnly && !item.favorite) return false
        if (categoryId != null && item.categoryId != categoryId) return false
        if (tagIds.isNotEmpty() && !item.tags.map { it.id }.containsAll(tagIds)) return false
        val normalized = query.trim()
        if (normalized.isNotEmpty()) {
            val haystack = buildString {
                append(item.title).append(' ').append(item.preview).append(' ')
                item.folderName?.let { append(it).append(' ') }
                item.categoryName?.let { append(it).append(' ') }
                item.tags.forEach { append(it.name).append(' ') }
            }
            if (!haystack.contains(normalized, ignoreCase = true)) return false
        }
        if (state != SearchStateFilter.ANY) {
            if (item.type != ContentType.TASK) return false
            val completed = item.taskStatus == TaskStatus.COMPLETED
            if (state == SearchStateFilter.PENDING && completed) return false
            if (state == SearchStateFilter.COMPLETED && !completed) return false
        }
        val itemDate = item.date
        if (itemDate == null) return date == SearchDateFilter.ANY
        return when (date) {
            SearchDateFilter.ANY -> true
            SearchDateFilter.TODAY -> startOfDay(itemDate) == startOfDay(now)
            SearchDateFilter.WEEK ->
                endOfDay(itemDate) >= startOfDay(now) && startOfDay(itemDate) <= endOfDay(addDays(now, 7))
            SearchDateFilter.MONTH ->
                endOfDay(itemDate) >= startOfDay(now) && startOfDay(itemDate) <= endOfDay(addDays(now, 30))
            SearchDateFilter.OVERDUE ->
                item.type == ContentType.TASK && item.taskStatus != TaskStatus.COMPLETED && itemDate < now
        }
    }
}

data class SavedView(
    val id: Long,
    val name: String,
    val filters: SearchFilters,
    val createdAt: Long? = null,
    val modifiedAt: Long? = null,
) {
    val displayName: String get() = name.ifBlank { "Vista sin nombre" }
}

fun validateFolderName(name: String): String? {
    val trimmed = name.trim()
    return when {
        trimmed.isEmpty() -> "El nombre de la carpeta es obligatorio."
        trimmed.length > 80 -> "El nombre no puede superar 80 caracteres."
        else -> null
    }
}

fun validateFolderDescription(description: String): String? =
    if (description.length > 500) "La descripción no puede superar 500 caracteres." else null

fun validateCategoryName(name: String): String? {
    val trimmed = name.trim()
    return when {
        trimmed.isEmpty() -> "El nombre de la categoría es obligatorio."
        trimmed.length > 60 -> "El nombre no puede superar 60 caracteres."
        else -> null
    }
}

fun validateTagName(name: String): String? {
    val trimmed = name.trim()
    return when {
        trimmed.isEmpty() -> "El nombre de la etiqueta es obligatorio."
        trimmed.length > 30 -> "La etiqueta no puede superar 30 caracteres."
        trimmed.any(Char::isISOControl) -> "La etiqueta contiene caracteres no válidos."
        else -> null
    }
}

fun validateSavedViewName(name: String): String? {
    val trimmed = name.trim()
    return when {
        trimmed.isEmpty() -> "El nombre de la vista es obligatorio."
        trimmed.length > 60 -> "El nombre no puede superar 60 caracteres."
        else -> null
    }
}

fun validateColor(color: String): String? =
    if (!color.matches(Regex("^#[0-9a-fA-F]{6}$"))) "El color no es válido." else null

/** True when [folderId] is inside the subtree rooted at [candidateParentId]. */
fun folderCreatesCycle(folderId: Long?, candidateParentId: Long?, parentById: Map<Long, Long?>): Boolean {
    if (folderId == null || candidateParentId == null) return false
    if (folderId == candidateParentId) return true
    var cursor: Long? = candidateParentId
    val visited = mutableSetOf<Long>()
    while (cursor != null) {
        if (!visited.add(cursor)) return true
        if (cursor == folderId) return true
        cursor = parentById[cursor]
    }
    return false
}

fun descendantsOf(folderId: Long, folders: List<Folder>): Set<Long> {
    val children = folders.groupBy { it.parentId }
    val result = mutableSetOf<Long>()
    val visited = mutableSetOf(folderId)
    val queue = ArrayDeque<Long>()
    queue.add(folderId)
    while (queue.isNotEmpty()) {
        val current = queue.removeFirst()
        children[current].orEmpty().forEach { child ->
            if (visited.add(child.id)) {
                result.add(child.id)
                queue.add(child.id)
            }
        }
    }
    return result
}

fun folderPath(folder: Folder?, folders: List<Folder>): String {
    if (folder == null) return "Sin carpeta"
    val byId = folders.associateBy { it.id }
    val parts = mutableListOf<String>()
    var cursor: Folder? = folder
    val visited = mutableSetOf<Long>()
    while (cursor != null && visited.add(cursor.id)) {
        parts.add(0, cursor.displayName)
        cursor = cursor.parentId?.let(byId::get)
    }
    return parts.joinToString(" / ")
}

/** Suggested saved views available before the user creates their own. */
fun suggestedViews(): List<Pair<String, SearchFilters>> = listOf(
    "Pendientes" to SearchFilters(
        types = setOf(ContentType.TASK),
        state = SearchStateFilter.PENDING,
        sort = SearchSort.PRIORITY,
    ),
    "Importantes" to SearchFilters(favoriteOnly = true, sort = SearchSort.DATE_DESC),
    "Trabajo" to SearchFilters(query = "trabajo", sort = SearchSort.DATE_DESC),
    "Personal" to SearchFilters(query = "personal", sort = SearchSort.DATE_DESC),
)

fun SearchFilters.describe(): String {
    val parts = mutableListOf<String>()
    if (query.isNotBlank()) parts += "«${query.trim()}»"
    if (types.size != ContentType.entries.size) parts += types.joinToString(", ") { it.label }
    if (favoriteOnly) parts += "Favoritos"
    if (state != SearchStateFilter.ANY) parts += state.label
    if (date != SearchDateFilter.ANY) parts += date.label
    if (categoryId != null) parts += "Categoría"
    if (tagIds.isNotEmpty()) parts += "${tagIds.size} etiqueta(s)"
    return parts.joinToString(" · ").ifBlank { "Todos los elementos" }
}

data class ActivityBucket(
    val label: String,
    val startAt: Long,
    val value: Int,
)

data class OrganizationStats(
    val now: Long = 0,
    val totalNotes: Int = 0,
    val notesLast7Days: Int = 0,
    val notesLast30Days: Int = 0,
    val noteDaily: List<ActivityBucket> = emptyList(),
    val pendingTasks: Int = 0,
    val completedTasks: Int = 0,
    val overdueTasks: Int = 0,
    val tables: Int = 0,
    val records: Int = 0,
    val recordsLast30Days: Int = 0,
    val scheduledEvents: Int = 0,
    val upcomingEvents: Int = 0,
    val activityDaily: List<ActivityBucket> = emptyList(),
    val activityWeekly: List<ActivityBucket> = emptyList(),
    val activityMonthly: List<ActivityBucket> = emptyList(),
) {
    val totalTasks: Int get() = pendingTasks + completedTasks
    val completionRate: Float
        get() = if (totalTasks == 0) 0f else completedTasks.toFloat() / totalTasks
    val completionPercent: Int get() = (completionRate * 100).toInt()
    val isEmpty: Boolean
        get() = totalNotes == 0 && totalTasks == 0 && tables == 0 && scheduledEvents == 0
}

fun computeOrganizationStats(
    notes: List<Note>,
    tasks: List<Task>,
    tables: List<CustomTable>,
    events: List<CalendarEvent>,
    activityTimes: List<Long>,
    rowCreationTimes: List<Long>,
    now: Long,
): OrganizationStats {
    val activeNotes = notes.filter { it.isActive }
    val taskList = tasks.distinctBy { it.id }
    val pending = taskList.count { !it.isCompleted }
    val completed = taskList.count { it.isCompleted }
    val overdue = taskList.count { isTaskOverdue(it, now) }
    val series = events.filter { it.parentId == null && !it.cancelled }.distinctBy { it.id }
    val upcoming = eventOccurrences(events, now, addDays(now, 30)).size
    val locale = Locale.forLanguageTag("es")

    fun countSince(times: List<Long?>, since: Long): Int = times.count { it != null && it >= since }

    fun dailyBuckets(times: List<Long?>, days: Int): List<ActivityBucket> {
        val today = startOfDay(now)
        return (days - 1 downTo 0).map { offset ->
            val day = addDays(today, -offset)
            val next = addDays(day, 1)
            ActivityBucket(
                label = SimpleDateFormat("EEE d", locale).format(Date(day)),
                startAt = day,
                value = times.count { it != null && it >= day && it < next },
            )
        }
    }

    fun weeklyBuckets(times: List<Long?>, weeks: Int): List<ActivityBucket> {
        val thisWeek = startOfWeek(now)
        return (weeks - 1 downTo 0).map { offset ->
            val start = addDays(thisWeek, -offset * 7)
            val end = addDays(start, 7)
            ActivityBucket(
                label = SimpleDateFormat("d MMM", locale).format(Date(start)),
                startAt = start,
                value = times.count { it != null && it >= start && it < end },
            )
        }
    }

    fun monthlyBuckets(times: List<Long?>, months: Int): List<ActivityBucket> {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val starts = (months - 1 downTo 0).map { offset ->
            Calendar.getInstance().apply {
                timeInMillis = calendar.timeInMillis
                add(Calendar.MONTH, -offset)
            }.timeInMillis
        }
        return starts.mapIndexed { index, start ->
            val end = if (index + 1 < starts.size) starts[index + 1] else {
                Calendar.getInstance().apply {
                    timeInMillis = start
                    add(Calendar.MONTH, 1)
                }.timeInMillis
            }
            ActivityBucket(
                label = SimpleDateFormat("MMM", locale).format(Date(start)),
                startAt = start,
                value = times.count { it != null && it >= start && it < end },
            )
        }
    }

    val noteCreation = activeNotes.map { it.createdAt }
    val activityCreation = activityTimes.map { it as Long? }
    return OrganizationStats(
        now = now,
        totalNotes = activeNotes.size,
        notesLast7Days = countSince(noteCreation, startOfDay(addDays(now, -6))),
        notesLast30Days = countSince(noteCreation, startOfDay(addDays(now, -29))),
        noteDaily = dailyBuckets(noteCreation, 7),
        pendingTasks = pending,
        completedTasks = completed,
        overdueTasks = overdue,
        tables = tables.distinctBy { it.id }.size,
        records = tables.distinctBy { it.id }.sumOf { it.rowCount },
        recordsLast30Days = rowCreationTimes.count { it >= startOfDay(addDays(now, -29)) },
        scheduledEvents = series.size,
        upcomingEvents = upcoming,
        activityDaily = dailyBuckets(activityCreation, 7),
        activityWeekly = weeklyBuckets(activityCreation, 8),
        activityMonthly = monthlyBuckets(activityCreation, 6),
    )
}

private fun startOfWeek(timestamp: Long): Long {
    val calendar = Calendar.getInstance().apply { timeInMillis = startOfDay(timestamp) }
    calendar.add(Calendar.DAY_OF_YEAR, -((calendar.get(Calendar.DAY_OF_WEEK) - calendar.firstDayOfWeek + 7) % 7))
    return calendar.timeInMillis
}

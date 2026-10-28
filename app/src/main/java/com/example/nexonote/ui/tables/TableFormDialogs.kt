package com.example.nexonote.ui.tables

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.nexonote.domain.model.Category
import com.example.nexonote.domain.model.CustomTable
import com.example.nexonote.domain.model.DEFAULT_TABLE_COLOR
import com.example.nexonote.domain.model.Folder
import com.example.nexonote.domain.model.TableIcon
import com.example.nexonote.domain.model.TableInput
import com.example.nexonote.domain.model.Tag
import com.example.nexonote.domain.model.validateTableInput
import com.example.nexonote.ui.organization.OrganizationFields
import com.example.nexonote.ui.organization.TagSelectionSection

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TableFormDialog(
    initial: CustomTable?,
    busy: Boolean,
    folders: List<Folder> = emptyList(),
    categories: List<Category> = emptyList(),
    availableTags: List<Tag> = emptyList(),
    onDismiss: () -> Unit,
    onConfirm: (TableInput) -> Unit,
) {
    var name by remember(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    var description by remember(initial?.id) { mutableStateOf(initial?.description.orEmpty()) }
    var icon by remember(initial?.id) { mutableStateOf(initial?.icon ?: TableIcon.GRID) }
    var color by remember(initial?.id) { mutableStateOf(initial?.color ?: DEFAULT_TABLE_COLOR) }
    var folderId by remember(initial?.id) { mutableStateOf(initial?.folderId) }
    var categoryId by remember(initial?.id) { mutableStateOf(initial?.categoryId) }
    var favorite by remember(initial?.id) { mutableStateOf(initial?.favorite ?: false) }
    var tags by remember(initial?.id) { mutableStateOf(initial?.tags.orEmpty()) }
    var localError by remember { mutableStateOf<String?>(null) }

    val input = TableInput(
        name = name,
        description = description,
        icon = icon,
        color = color,
        folderId = folderId,
        categoryId = categoryId,
        favorite = favorite,
        tags = tags,
    )

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (initial == null) "Nueva tabla" else "Editar tabla") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        localError = null
                    },
                    label = { Text("Nombre") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("table_name"),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it.take(500) },
                    label = { Text("Descripción (opcional)") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth().testTag("table_description"),
                )
                Text("Icono", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TableIcon.entries.forEach { item ->
                        val selected = item == icon
                        IconButton(
                            onClick = { icon = item },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(
                                    if (selected) colorFromHex(color).copy(alpha = 0.1f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                )
                                .testTag("table_icon_${item.name}"),
                        ) {
                            Icon(
                                imageVector = tableIconVector(item),
                                contentDescription = item.label,
                                tint = if (selected) colorFromHex(color)
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Text("Color", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    tableColorPalette.forEach { hex ->
                        val selected = hex.equals(color, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(colorFromHex(hex))
                                .then(
                                    if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                    else Modifier
                                )
                                .clickable { color = hex }
                                .testTag("table_color_$hex"),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (selected) {
                                Icon(
                                    Icons.Outlined.Check,
                                    contentDescription = "Color seleccionado",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
                localError?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                FilterChip(
                    selected = favorite,
                    onClick = { favorite = !favorite },
                    label = { Text("Favorito") },
                    leadingIcon = {
                        Icon(
                            if (favorite) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                )
                HorizontalDivider()
                OrganizationFields(
                    folderId = folderId,
                    categoryId = categoryId,
                    folders = folders,
                    categories = categories,
                    onFolderChange = { folderId = it },
                    onCategoryChange = { categoryId = it },
                )
                TagSelectionSection(
                    selected = tags,
                    available = availableTags,
                    onAdd = { value ->
                        val normalized = value.trim().lowercase()
                        if (normalized.isNotEmpty() && normalized !in tags && tags.size < 20) {
                            tags = tags + normalized
                        }
                    },
                    onRemove = { value -> tags = tags - value },
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy,
                onClick = {
                    val error = validateTableInput(input)
                    if (error != null) {
                        localError = error
                    } else {
                        onConfirm(input.copy(name = name.trim(), description = description.trim()))
                    }
                },
                modifier = Modifier.testTag("table_confirm"),
            ) {
                Text(if (initial == null) "Crear" else "Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancelar") }
        },
    )
}

@Composable
fun DeleteTableDialog(
    table: CustomTable,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        icon = { Icon(tableIconVector(table.icon), contentDescription = null) },
        title = { Text("Eliminar tabla") },
        text = {
            Text("Se eliminarán «${table.displayName}», sus columnas y todos sus registros. Esta acción no se puede deshacer.")
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !busy,
                modifier = Modifier.testTag("table_delete_confirm"),
            ) {
                Text("Eliminar", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancelar") } },
    )
}

@Composable
fun TableIconBadge(
    icon: TableIcon,
    color: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colorFromHex(color).copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = tableIconVector(icon),
            contentDescription = null,
            tint = colorFromHex(color),
        )
    }
}


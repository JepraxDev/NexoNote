package com.example.nexonote.ui.tables

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.nexonote.domain.model.ColumnType
import com.example.nexonote.domain.model.CustomColumn
import com.example.nexonote.domain.model.TableRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RowEditorSheet(
    row: TableRow,
    columns: List<CustomColumn>,
    busy: Boolean,
    onDismiss: () -> Unit,
    onEditCell: (CustomColumn) -> Unit,
    onToggleCheckbox: (CustomColumn, Boolean) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Text(
                text = row.displayTitle,
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                "Toca un campo para editarlo. Los cambios se guardan al aplicar.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            columns.forEach { column ->
                HorizontalDivider()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = column.type != ColumnType.CHECKBOX) {
                            onEditCell(column)
                        }
                        .padding(vertical = 10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = columnIconVector(column.type),
                            contentDescription = null,
                            modifier = Modifier.height(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = column.displayName + if (column.required) " *" else "",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    if (column.type == ColumnType.CHECKBOX) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = row.value(column.id) == "1",
                                onCheckedChange = { onToggleCheckbox(column, it) },
                                enabled = !busy,
                            )
                            Text(if (row.value(column.id) == "1") "Marcado" else "Sin marcar")
                        }
                    } else if (row.value(column.id).isNullOrBlank()) {
                        Text(
                            "Añadir valor",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        )
                    } else {
                        CellValuePreview(column = column, row = row)
                    }
                }
            }
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Cerrar") }
            Spacer(Modifier.height(24.dp))
        }
    }
}

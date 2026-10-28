package com.example.nexonote.ui.tables

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.AttachMoney
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Numbers
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Work
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.graphics.toColorInt
import com.example.nexonote.domain.model.ColumnType
import com.example.nexonote.domain.model.TableIcon

val tableColorPalette = listOf(
    "#6750A4",
    "#006A6A",
    "#B3261E",
    "#7D5260",
    "#2F5D8A",
    "#4F6D3A",
    "#8C4A2F",
    "#5A5A66",
)

fun tableIconVector(icon: TableIcon): ImageVector = when (icon) {
    TableIcon.GRID -> Icons.Outlined.GridOn
    TableIcon.FOLDER -> Icons.AutoMirrored.Outlined.Label
    TableIcon.LIGHTBULB -> Icons.Outlined.Lightbulb
    TableIcon.STAR -> Icons.Outlined.Star
    TableIcon.WORK -> Icons.Outlined.Work
    TableIcon.HOME -> Icons.Outlined.Home
    TableIcon.BOOK -> Icons.AutoMirrored.Outlined.Notes
    TableIcon.CALENDAR -> Icons.Outlined.CalendarMonth
    TableIcon.FLAG -> Icons.Outlined.Flag
    TableIcon.LIST -> Icons.AutoMirrored.Outlined.List
}

fun columnIconVector(type: ColumnType): ImageVector = when (type) {
    ColumnType.SHORT_TEXT -> Icons.Outlined.TextFields
    ColumnType.LONG_TEXT -> Icons.AutoMirrored.Outlined.Notes
    ColumnType.INTEGER -> Icons.Outlined.Numbers
    ColumnType.DECIMAL -> Icons.Outlined.Numbers
    ColumnType.CURRENCY -> Icons.Outlined.AttachMoney
    ColumnType.DATE -> Icons.Outlined.CalendarMonth
    ColumnType.DATETIME -> Icons.Outlined.Schedule
    ColumnType.CHECKBOX -> Icons.Outlined.CheckBox
    ColumnType.SELECT -> Icons.Outlined.Sell
    ColumnType.MULTISELECT -> Icons.AutoMirrored.Outlined.FormatListBulleted
    ColumnType.STATUS -> Icons.Outlined.Flag
    ColumnType.URL -> Icons.Outlined.Link
    ColumnType.EMAIL -> Icons.Outlined.Email
    ColumnType.PHONE -> Icons.Outlined.Phone
}

fun colorFromHex(hex: String): Color {
    val normalized = hex.removePrefix("#")
    return runCatching {
        Color("#$normalized".toColorInt())
    }.getOrElse {
        Color(0xFF6750A4)
    }
}

fun primaryColumnIcon(): ImageVector = Icons.Outlined.Lock

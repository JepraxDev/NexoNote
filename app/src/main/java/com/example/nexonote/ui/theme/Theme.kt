package com.example.nexonote.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.nexonote.domain.model.AccentColor

/**
 * Primary tones for every accent. Light tones assume white text on top; dark tones assume a dark
 * onPrimary. Containers keep the Material 3 relationship so the rest of the UI stays legible.
 */
private data class AccentTones(
    val lightPrimary: Color,
    val lightContainer: Color,
    val lightOnContainer: Color,
    val darkPrimary: Color,
    val darkContainer: Color,
    val darkOnContainer: Color,
)

private fun tones(accent: AccentColor): AccentTones = when (accent) {
    AccentColor.PURPLE -> AccentTones(
        lightPrimary = Color(0xFF6650A4),
        lightContainer = Color(0xFFEADDFF),
        lightOnContainer = Color(0xFF21005D),
        darkPrimary = Color(0xFFD0BCFF),
        darkContainer = Color(0xFF4F378B),
        darkOnContainer = Color(0xFFEADDFF),
    )
    AccentColor.BLUE -> AccentTones(
        lightPrimary = Color(0xFF0061A4),
        lightContainer = Color(0xFFD1E4FF),
        lightOnContainer = Color(0xFF001D36),
        darkPrimary = Color(0xFF9ECAFF),
        darkContainer = Color(0xFF00497D),
        darkOnContainer = Color(0xFFD1E4FF),
    )
    AccentColor.TEAL -> AccentTones(
        lightPrimary = Color(0xFF006A6A),
        lightContainer = Color(0xFFCCE8E7),
        lightOnContainer = Color(0xFF002020),
        darkPrimary = Color(0xFF4DDADA),
        darkContainer = Color(0xFF005050),
        darkOnContainer = Color(0xFFCCE8E7),
    )
    AccentColor.GREEN -> AccentTones(
        lightPrimary = Color(0xFF386A20),
        lightContainer = Color(0xFFB8F397),
        lightOnContainer = Color(0xFF042100),
        darkPrimary = Color(0xFF9CD67D),
        darkContainer = Color(0xFF205107),
        darkOnContainer = Color(0xFFB8F397),
    )
    AccentColor.ORANGE -> AccentTones(
        lightPrimary = Color(0xFF8B5000),
        lightContainer = Color(0xFFFFDCC0),
        lightOnContainer = Color(0xFF2D1600),
        darkPrimary = Color(0xFFFFB870),
        darkContainer = Color(0xFF6A3900),
        darkOnContainer = Color(0xFFFFDCC0),
    )
    AccentColor.PINK -> AccentTones(
        lightPrimary = Color(0xFF7D5260),
        lightContainer = Color(0xFFFFD9E2),
        lightOnContainer = Color(0xFF31101D),
        darkPrimary = Color(0xFFEFB8C8),
        darkContainer = Color(0xFF633B48),
        darkOnContainer = Color(0xFFFFD9E2),
    )
    AccentColor.RED -> AccentTones(
        lightPrimary = Color(0xFFB3261E),
        lightContainer = Color(0xFFF9DEDC),
        lightOnContainer = Color(0xFF410E0B),
        darkPrimary = Color(0xFFF2B8B5),
        darkContainer = Color(0xFF8C1D18),
        darkOnContainer = Color(0xFFF9DEDC),
    )
}

/** Visible swatch for the settings picker; works on both light and dark surfaces. */
fun accentSwatch(accent: AccentColor): Color = tones(accent).lightPrimary

private fun lightScheme(accent: AccentColor): ColorScheme {
    val tones = tones(accent)
    return lightColorScheme().copy(
        primary = tones.lightPrimary,
        onPrimary = Color.White,
        primaryContainer = tones.lightContainer,
        onPrimaryContainer = tones.lightOnContainer,
        inversePrimary = tones.darkPrimary,
        secondary = tones.lightPrimary,
        onSecondary = Color.White,
        secondaryContainer = tones.lightContainer,
        onSecondaryContainer = tones.lightOnContainer,
        tertiary = tones.lightPrimary,
        onTertiary = Color.White,
        surfaceTint = tones.lightPrimary,
    )
}

private fun darkScheme(accent: AccentColor): ColorScheme {
    val tones = tones(accent)
    return darkColorScheme().copy(
        primary = tones.darkPrimary,
        onPrimary = Color(0xFF1C1B1F),
        primaryContainer = tones.darkContainer,
        onPrimaryContainer = tones.darkOnContainer,
        inversePrimary = tones.lightPrimary,
        secondary = tones.darkPrimary,
        onSecondary = Color(0xFF1C1B1F),
        secondaryContainer = tones.darkContainer,
        onSecondaryContainer = tones.darkOnContainer,
        tertiary = tones.darkPrimary,
        onTertiary = Color(0xFF1C1B1F),
        surfaceTint = tones.darkPrimary,
    )
}

@Composable
fun NexoNoteTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accent: AccentColor = AccentColor.PURPLE,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) darkScheme(accent) else lightScheme(accent)
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}

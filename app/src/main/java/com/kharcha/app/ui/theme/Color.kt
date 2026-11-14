package com.kharcha.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/*
 * Kharcha's own colours: "iris and marigold on ink".
 *
 *   - Surfaces are a near-black with a hint of indigo (dark) or a cool paper white (light).
 *   - Iris, a blue-violet, is the brand colour. The one big number on a screen sits on an
 *     iris "hero" card.
 *   - Marigold is the accent: things waiting for attention (pending items, a budget nearly used).
 *   - Money in is mint green, money out is coral. Red is kept for "stop": delete, over budget.
 */

val Iris = Color(0xFFB4B8FF)
val IrisDeep = Color(0xFF3D45CC)
val IrisInk = Color(0xFF0A0E52)
val Marigold = Color(0xFFFFC940)

val LightScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF4A52D6),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE0E0FF),
    onPrimaryContainer = Color(0xFF070B6B),
    inversePrimary = Color(0xFFBEC2FF),
    secondary = Color(0xFF5C5D72),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE1E0F9),
    onSecondaryContainer = Color(0xFF191A2C),
    tertiary = Color(0xFF7A5900),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDF9E),
    onTertiaryContainer = Color(0xFF261A00),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFBF8FF),
    onBackground = Color(0xFF1B1B21),
    surface = Color(0xFFFBF8FF),
    onSurface = Color(0xFF1B1B21),
    surfaceVariant = Color(0xFFE3E1EC),
    onSurfaceVariant = Color(0xFF46464F),
    surfaceTint = Color(0xFF4A52D6),
    inverseSurface = Color(0xFF303036),
    inverseOnSurface = Color(0xFFF3EFF7),
    outline = Color(0xFF777680),
    outlineVariant = Color(0xFFC7C5D0),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFBF8FF),
    surfaceDim = Color(0xFFDCD9E1),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F2FA),
    surfaceContainer = Color(0xFFEFEDF5),
    surfaceContainerHigh = Color(0xFFE9E7EF),
    surfaceContainerHighest = Color(0xFFE4E1E9),
)

val DarkScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFFBEC2FF),
    onPrimary = Color(0xFF161D9A),
    primaryContainer = Color(0xFF3038B5),
    onPrimaryContainer = Color(0xFFE0E0FF),
    inversePrimary = Color(0xFF4A52D6),
    secondary = Color(0xFFC5C4DD),
    onSecondary = Color(0xFF2E2F42),
    secondaryContainer = Color(0xFF3B3C52),
    onSecondaryContainer = Color(0xFFE1E0F9),
    tertiary = Color(0xFFF6C453),
    onTertiary = Color(0xFF3F2E00),
    tertiaryContainer = Color(0xFF5B4300),
    onTertiaryContainer = Color(0xFFFFDF9E),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0E0E13),
    onBackground = Color(0xFFE4E1E9),
    surface = Color(0xFF0E0E13),
    onSurface = Color(0xFFE4E1E9),
    surfaceVariant = Color(0xFF46464F),
    onSurfaceVariant = Color(0xFFC7C5D0),
    surfaceTint = Color(0xFFBEC2FF),
    inverseSurface = Color(0xFFE4E1E9),
    inverseOnSurface = Color(0xFF303036),
    outline = Color(0xFF91909A),
    outlineVariant = Color(0xFF46464F),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF39393F),
    surfaceDim = Color(0xFF0E0E13),
    surfaceContainerLowest = Color(0xFF09090D),
    surfaceContainerLow = Color(0xFF16161B),
    surfaceContainer = Color(0xFF1B1B21),
    surfaceContainerHigh = Color(0xFF25252B),
    surfaceContainerHighest = Color(0xFF303036),
)

/** The few colours Material has no role for. */
data class KharchaExtras(
    /** Background of the one big number on a screen. */
    val hero: Color,
    /** Text and icons on [hero]. */
    val onHero: Color,
    /** Money coming in. */
    val income: Color,
    /** Money going out. */
    val expense: Color,
)

fun kharchaExtras(dark: Boolean): KharchaExtras = if (dark) {
    KharchaExtras(hero = Iris, onHero = IrisInk, income = Color(0xFF7EDBA3), expense = Color(0xFFFFB3A8))
} else {
    KharchaExtras(hero = IrisDeep, onHero = Color(0xFFFFFFFF), income = Color(0xFF1B6B3F), expense = Color(0xFFB3261E))
}

// ---------------------------------------------------------------------------
// Other accents, and a true-black dark theme
// ---------------------------------------------------------------------------

/** The same colour moved to another place on the colour wheel, keeping how strong and how light it is. */
fun Color.withHue(hue: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(toArgb(), hsv)
    hsv[0] = hue
    return Color(android.graphics.Color.HSVToColor(hsv))
}

/**
 * A whole scheme in another accent: every iris-tinted colour is turned to the new hue.
 * Marigold (tertiary) and the error reds keep their meaning, so they are left alone.
 */
fun ColorScheme.withAccent(hue: Float): ColorScheme = copy(
    primary = primary.withHue(hue),
    onPrimary = onPrimary.withHue(hue),
    primaryContainer = primaryContainer.withHue(hue),
    onPrimaryContainer = onPrimaryContainer.withHue(hue),
    inversePrimary = inversePrimary.withHue(hue),
    secondary = secondary.withHue(hue),
    onSecondary = onSecondary.withHue(hue),
    secondaryContainer = secondaryContainer.withHue(hue),
    onSecondaryContainer = onSecondaryContainer.withHue(hue),
    background = background.withHue(hue),
    onBackground = onBackground.withHue(hue),
    surface = surface.withHue(hue),
    onSurface = onSurface.withHue(hue),
    surfaceVariant = surfaceVariant.withHue(hue),
    onSurfaceVariant = onSurfaceVariant.withHue(hue),
    surfaceTint = surfaceTint.withHue(hue),
    inverseSurface = inverseSurface.withHue(hue),
    inverseOnSurface = inverseOnSurface.withHue(hue),
    outline = outline.withHue(hue),
    outlineVariant = outlineVariant.withHue(hue),
    surfaceBright = surfaceBright.withHue(hue),
    surfaceDim = surfaceDim.withHue(hue),
    surfaceContainerLowest = surfaceContainerLowest.withHue(hue),
    surfaceContainerLow = surfaceContainerLow.withHue(hue),
    surfaceContainer = surfaceContainer.withHue(hue),
    surfaceContainerHigh = surfaceContainerHigh.withHue(hue),
    surfaceContainerHighest = surfaceContainerHighest.withHue(hue),
)

/** A dark scheme on true black, for OLED screens. Cards stay a shade lighter so they still read as cards. */
fun ColorScheme.onBlack(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0B0B0D),
    surfaceContainer = Color(0xFF141417),
    surfaceContainerHigh = Color(0xFF1C1C20),
    surfaceContainerHighest = Color(0xFF26262B),
)

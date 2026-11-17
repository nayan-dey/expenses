@file:OptIn(ExperimentalTextApi::class)

package com.kharcha.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.kharcha.app.R
import com.kharcha.app.core.model.FontChoice

/*
 * Type. Each bundled typeface is a "variable" font: a single file holds every weight, and
 * each line below asks that file for one of them. The four were chosen because they draw the
 * rupee sign (and most other currency signs) themselves, so amounts never fall back to a
 * different-looking system font. The person picks one in Settings, Appearance.
 */

private fun variable(resId: Int, weight: Int): Font = Font(
    resId = resId,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

private fun family(resId: Int): FontFamily =
    FontFamily(variable(resId, 400), variable(resId, 500), variable(resId, 600), variable(resId, 700), variable(resId, 800))

private val Jakarta = family(R.font.plus_jakarta_sans)
private val Manrope = family(R.font.manrope)
private val Figtree = family(R.font.figtree)
private val SpaceGrotesk = family(R.font.space_grotesk)
// Google Sans is cut down to Latin letters, digits and money signs (see AGENTS.md); its weights stop at 700.
private val GoogleSans = family(R.font.google_sans)
// Poppins has no variable file: one file per weight. It has no same-width digits, so columns of
// money line up a little less neatly than in the others.
private val Poppins = FontFamily(
    Font(R.font.poppins_regular, FontWeight(400)),
    Font(R.font.poppins_medium, FontWeight(500)),
    Font(R.font.poppins_semibold, FontWeight(600)),
    Font(R.font.poppins_bold, FontWeight(700)),
)

fun fontFamily(choice: FontChoice): FontFamily = when (choice) {
    FontChoice.JAKARTA -> Jakarta
    FontChoice.MANROPE -> Manrope
    FontChoice.FIGTREE -> Figtree
    FontChoice.SPACE -> SpaceGrotesk
    FontChoice.GOOGLE -> GoogleSans
    FontChoice.POPPINS -> Poppins
    FontChoice.SYSTEM -> FontFamily.Default
}

private val CenteredLines = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

/**
 * Builds a text style from plain numbers: `ts(family, 18f, 700)` reads as "size 18, weight 700".
 *
 * @param lh line height as a multiple of the size (1.4 = 140%)
 * @param ls letter spacing in em (negative pulls letters together)
 * @param tabular true = every digit has the same width, so columns of money line up
 */
fun ts(
    family: FontFamily,
    size: Float,
    weight: Int = 400,
    lh: Float = 1.35f,
    ls: Float = 0f,
    tabular: Boolean = false,
): TextStyle = TextStyle(
    fontFamily = family,
    fontSize = size.sp,
    fontWeight = FontWeight(weight),
    lineHeight = (size * lh).sp,
    letterSpacing = ls.em,
    lineHeightStyle = CenteredLines,
    fontFeatureSettings = if (tabular) "tnum" else null,
)

/** Kharcha's own named styles, for things Material has no slot for. Built for the chosen typeface. */
class KharchaTypeSet(family: FontFamily) {
    /** The one huge number on a screen. It shrinks by length so it always fits one line (see HeroAmount). */
    val hero = ts(family, 46f, 800, lh = 1.05f, ls = -0.03f, tabular = true)
    /** A big number on a card. */
    val figure = ts(family, 28f, 800, lh = 1.1f, ls = -0.02f, tabular = true)
    /** A number inside a list row. */
    val amount = ts(family, 16.5f, 700, lh = 1.2f, ls = -0.01f, tabular = true)
    /** Small capital label above something. */
    val eyebrow = ts(family, 11.5f, 700, lh = 1.3f, ls = 0.09f)
    /** The keys of the amount keypad. */
    val key = ts(family, 24f, 600, lh = 1.1f, tabular = true)
}

val LocalKharchaType = staticCompositionLocalOf { KharchaTypeSet(FontFamily.Default) }

/** Reach the named styles as `KharchaType.amount`, wherever a screen is being drawn. */
object KharchaType {
    val hero: TextStyle
        @Composable @ReadOnlyComposable get() = LocalKharchaType.current.hero
    val figure: TextStyle
        @Composable @ReadOnlyComposable get() = LocalKharchaType.current.figure
    val amount: TextStyle
        @Composable @ReadOnlyComposable get() = LocalKharchaType.current.amount
    val eyebrow: TextStyle
        @Composable @ReadOnlyComposable get() = LocalKharchaType.current.eyebrow
    val key: TextStyle
        @Composable @ReadOnlyComposable get() = LocalKharchaType.current.key
}

/** Material's own slots, filled with the chosen typeface so stock components match too. */
fun kharchaTypography(family: FontFamily) = Typography(
    displayLarge = ts(family, 54f, 800, lh = 1.04f, ls = -0.03f),
    displayMedium = ts(family, 44f, 800, lh = 1.06f, ls = -0.03f),
    displaySmall = ts(family, 36f, 800, lh = 1.08f, ls = -0.025f),
    headlineLarge = ts(family, 31f, 800, lh = 1.12f, ls = -0.02f),
    headlineMedium = ts(family, 27f, 800, lh = 1.14f, ls = -0.02f),
    headlineSmall = ts(family, 23f, 700, lh = 1.18f, ls = -0.015f),
    titleLarge = ts(family, 20.5f, 700, lh = 1.22f, ls = -0.01f),
    titleMedium = ts(family, 16.5f, 700, lh = 1.28f, ls = -0.005f),
    titleSmall = ts(family, 14.5f, 700, lh = 1.3f),
    bodyLarge = ts(family, 16f, 400, lh = 1.45f),
    bodyMedium = ts(family, 14.5f, 400, lh = 1.4f),
    bodySmall = ts(family, 12.5f, 400, lh = 1.35f),
    labelLarge = ts(family, 14.5f, 700, lh = 1.25f),
    labelMedium = ts(family, 12.5f, 600, lh = 1.25f),
    labelSmall = ts(family, 11f, 600, lh = 1.25f, ls = 0.02f),
)

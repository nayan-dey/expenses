@file:OptIn(ExperimentalTextApi::class)

package com.kharcha.app.ui.theme

import androidx.compose.material3.Typography
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

/*
 * One font does everything: Plus Jakarta Sans, a "variable" font. A single file holds every
 * weight from 200 to 800, and each line below asks that file for one of them. It was chosen
 * because it draws the rupee, euro, yen, won, naira, peso and dong signs itself, so amounts
 * do not fall back to a different-looking system font.
 */

private fun jakarta(weight: Int): Font = Font(
    resId = R.font.plus_jakarta_sans,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Jakarta = FontFamily(jakarta(400), jakarta(500), jakarta(600), jakarta(700), jakarta(800))

private val CenteredLines = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

/**
 * Builds a text style from plain numbers: `ts(18f, 700)` reads as "size 18, weight 700".
 *
 * @param lh line height as a multiple of the size (1.4 = 140%)
 * @param ls letter spacing in em (negative pulls letters together)
 * @param tabular true = every digit has the same width, so columns of money line up
 */
fun ts(
    size: Float,
    weight: Int = 400,
    lh: Float = 1.35f,
    ls: Float = 0f,
    tabular: Boolean = false,
): TextStyle = TextStyle(
    fontFamily = Jakarta,
    fontSize = size.sp,
    fontWeight = FontWeight(weight),
    lineHeight = (size * lh).sp,
    letterSpacing = ls.em,
    lineHeightStyle = CenteredLines,
    fontFeatureSettings = if (tabular) "tnum" else null,
)

/** Kharcha's own named styles, for things Material has no slot for. */
object KharchaType {
    /** The one huge number on a screen. It shrinks by length so it always fits one line (see HeroAmount). */
    val hero = ts(46f, 800, lh = 1.05f, ls = -0.03f, tabular = true)
    /** A big number on a card. */
    val figure = ts(28f, 800, lh = 1.1f, ls = -0.02f, tabular = true)
    /** A number inside a list row. */
    val amount = ts(16.5f, 700, lh = 1.2f, ls = -0.01f, tabular = true)
    /** Small capital label above something. */
    val eyebrow = ts(11.5f, 700, lh = 1.3f, ls = 0.09f)
    /** The keys of the amount keypad. */
    val key = ts(24f, 600, lh = 1.1f, tabular = true)
}

/** Material's own slots, filled with Plus Jakarta Sans so stock components match too. */
val KharchaTypography = Typography(
    displayLarge = ts(54f, 800, lh = 1.04f, ls = -0.03f),
    displayMedium = ts(44f, 800, lh = 1.06f, ls = -0.03f),
    displaySmall = ts(36f, 800, lh = 1.08f, ls = -0.025f),
    headlineLarge = ts(31f, 800, lh = 1.12f, ls = -0.02f),
    headlineMedium = ts(27f, 800, lh = 1.14f, ls = -0.02f),
    headlineSmall = ts(23f, 700, lh = 1.18f, ls = -0.015f),
    titleLarge = ts(20.5f, 700, lh = 1.22f, ls = -0.01f),
    titleMedium = ts(16.5f, 700, lh = 1.28f, ls = -0.005f),
    titleSmall = ts(14.5f, 700, lh = 1.3f),
    bodyLarge = ts(16f, 400, lh = 1.45f),
    bodyMedium = ts(14.5f, 400, lh = 1.4f),
    bodySmall = ts(12.5f, 400, lh = 1.35f),
    labelLarge = ts(14.5f, 700, lh = 1.25f),
    labelMedium = ts(12.5f, 600, lh = 1.25f),
    labelSmall = ts(11f, 600, lh = 1.25f, ls = 0.02f),
)

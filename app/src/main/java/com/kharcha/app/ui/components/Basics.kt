@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.kharcha.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import com.kharcha.app.core.model.Category
import com.kharcha.app.core.model.Kind
import com.kharcha.app.core.util.Money
import com.kharcha.app.ui.icons.Ic
import com.kharcha.app.ui.theme.KharchaType
import com.kharcha.app.ui.theme.LocalSensitive
import com.kharcha.app.ui.theme.Marigold

/*
 * Small building blocks used on every screen. Icons and text are Material's own; the shapes
 * come from Material 3 Expressive's shape library (MaterialShapes).
 */

/** Draws one icon from [Ic]. `fill = true` uses the solid version. */
@Composable
fun KIcon(
    icon: Ic,
    modifier: Modifier = Modifier,
    fill: Boolean = false,
    size: Dp = 24.dp,
    tint: Color = LocalContentColor.current,
    contentDescription: String? = null,
) {
    Icon(
        painter = painterResource(if (fill) icon.fill else icon.outline),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        tint = tint,
    )
}

@Composable
fun Gap(height: Dp) = Spacer(Modifier.height(height))

@Composable
fun HGap(width: Dp) = Spacer(Modifier.width(width))

// ---------------------------------------------------------------------------
// Money as text
// ---------------------------------------------------------------------------

/** What an amount looks like while "Sensitive mode" is on. */
const val HIDDEN_AMOUNT = "••••"

/** An amount as text, or dots while Sensitive mode is on. Every amount on screen goes through here. */
@Composable
@ReadOnlyComposable
fun money(minor: Long, code: String): String =
    if (LocalSensitive.current) HIDDEN_AMOUNT else Money.format(minor, code)

/** An item's amount: expenses carry a minus sign. */
@Composable
@ReadOnlyComposable
fun moneySigned(minor: Long, code: String, kind: Kind): String =
    if (LocalSensitive.current) HIDDEN_AMOUNT else Money.signed(minor, code, kind)

// ---------------------------------------------------------------------------
// Text pieces
// ---------------------------------------------------------------------------

/** A small capital label above something: "EXPENSE". */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text.uppercase(), style = KharchaType.eyebrow, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier)
}

/** The title of a section of a screen, with an optional link on the right ("See all"). */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, link: String? = null, onLink: (() -> Unit)? = null) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 22.dp, end = 8.dp, top = 20.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 10.dp)
                .semantics { heading() },
        )
        if (link != null && onLink != null) TextButton(onClick = onLink) { Text(link) }
    }
}

/** Small grey text under a group or at the bottom of a screen. */
@Composable
fun Foot(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 22.dp, vertical = 10.dp),
    )
}

/** A number that rolls to its new value when it changes. */
@Composable
fun RollingText(text: String, style: TextStyle, modifier: Modifier = Modifier, color: Color = LocalContentColor.current) {
    AnimatedContent(
        targetState = text,
        modifier = modifier,
        transitionSpec = {
            (slideInVertically { it / 2 } + fadeIn()) togetherWith (slideOutVertically { -it / 2 } + fadeOut()) using SizeTransform(clip = false)
        },
        label = "rolling text",
    ) { value ->
        Text(value, style = style, color = color, maxLines = 1, softWrap = false)
    }
}

/**
 * The one big amount on a screen. The longer the number, the smaller it is drawn, so
 * "₹80,588.00" and "₹1,20,80,588.00" both fit on one line.
 */
@Composable
fun HeroAmount(text: String, modifier: Modifier = Modifier, color: Color = LocalContentColor.current) {
    val scale = when {
        text.length <= 10 -> 1f
        text.length <= 13 -> 0.86f
        text.length <= 16 -> 0.72f
        else -> 0.6f
    }
    RollingText(text, KharchaType.hero.copy(fontSize = KharchaType.hero.fontSize * scale, lineHeight = KharchaType.hero.lineHeight * scale), modifier, color)
}

enum class TagKind { NEUTRAL, GOOD, WARN, BAD }

/** A small pill with a word in it: "Pending", "Repeats". */
@Composable
fun Tag(text: String, kind: TagKind, modifier: Modifier = Modifier, icon: Ic? = null) {
    val scheme = MaterialTheme.colorScheme
    val (bg, fg) = when (kind) {
        TagKind.NEUTRAL -> scheme.surfaceContainerHighest to scheme.onSurfaceVariant
        TagKind.GOOD -> scheme.primaryContainer to scheme.onPrimaryContainer
        TagKind.WARN -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        TagKind.BAD -> scheme.errorContainer to scheme.onErrorContainer
    }
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(bg)
            .padding(horizontal = 9.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) KIcon(icon, size = 13.dp, tint = fg, fill = true)
        Text(text, style = MaterialTheme.typography.labelSmall, color = fg, maxLines = 1)
    }
}

// ---------------------------------------------------------------------------
// Shapes, dots and the logo
// ---------------------------------------------------------------------------

/** An icon on a Material shape, for empty states and headers. */
@Composable
fun ShapeIcon(
    icon: Ic,
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    shape: RoundedPolygon = MaterialShapes.Cookie9Sided,
    background: Color = MaterialTheme.colorScheme.primaryContainer,
    tint: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(shape.toShape())
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        KIcon(icon, size = size * 0.46f, tint = tint, fill = true)
    }
}

/** White on dark colours, near-black on light ones, so an icon is readable on any category colour. */
fun onColor(background: Color): Color = if (background.luminance() > 0.55f) Color(0xFF16161D) else Color.White

/** The round coloured badge that stands for a category. */
@Composable
fun CategoryDot(icon: Ic, color: Color, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        KIcon(icon, size = size * 0.52f, tint = onColor(color), fill = true)
    }
}

/** The badge for a category, for "no category" (grey list) or for a transfer (arrows on the brand colour). */
@Composable
fun CategoryDot(category: Category?, modifier: Modifier = Modifier, size: Dp = 44.dp, transfer: Boolean = false) {
    when {
        transfer -> CategoryDot(Ic.swap_horiz, MaterialTheme.colorScheme.primary, modifier, size)
        category == null -> CategoryDot(Ic.list, Color(0xFF8E8E93), modifier, size)
        else -> CategoryDot(Ic.of(category.icon), Color(category.color), modifier, size)
    }
}

/** A rounded square with a tinted icon, used at the start of settings rows. */
@Composable
fun IconTile(icon: Ic, color: Color, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.3f))
            .background(color.copy(alpha = 0.2f))
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        KIcon(icon, size = size * 0.55f, tint = color, fill = true)
    }
}

/**
 * The Kharcha mark: a ring chart with one slice lifted out. The same drawing as the
 * launcher icon (res/drawable/ic_launcher_foreground.xml).
 */
@Composable
fun KharchaLogo(size: Dp, modifier: Modifier = Modifier, ring: Color = MaterialTheme.colorScheme.primary, slice: Color = Marigold) {
    Canvas(modifier.size(size).clearAndSetSemantics { }) {
        val side = this.size.minDimension
        val radius = side * 0.30f
        val stroke = Stroke(width = side * 0.2f, cap = StrokeCap.Round)
        val lift = side * 0.064f
        val box = Size(radius * 2, radius * 2)
        drawArc(ring, startAngle = 20f, sweepAngle = 230f, useCenter = false, topLeft = Offset(center.x - radius, center.y - radius), size = box, style = stroke)
        drawArc(slice, startAngle = 295f, sweepAngle = 40f, useCenter = false, topLeft = Offset(center.x - radius + lift, center.y - radius - lift), size = box, style = stroke)
    }
}

private val avatarShapes = listOf(
    MaterialShapes.Cookie9Sided,
    MaterialShapes.Clover4Leaf,
    MaterialShapes.Sunny,
    MaterialShapes.Pentagon,
    MaterialShapes.Cookie6Sided,
    MaterialShapes.Flower,
    MaterialShapes.Gem,
    MaterialShapes.Puffy,
    MaterialShapes.Slanted,
    MaterialShapes.Arch,
    MaterialShapes.ClamShell,
    MaterialShapes.SoftBurst,
    MaterialShapes.Cookie4Sided,
    MaterialShapes.Bun,
    MaterialShapes.Ghostish,
    MaterialShapes.Pill,
)

/**
 * The picture that stands for a sheet: its first letter on a Material shape. The shape and
 * colour are worked out from the sheet's id, so a sheet always looks the same.
 */
@Composable
fun ShapeAvatar(name: String, seed: String, modifier: Modifier = Modifier, size: Dp = 48.dp) {
    val scheme = MaterialTheme.colorScheme
    val number = seed.hashCode() and 0x7fffffff
    val shape = avatarShapes[number % avatarShapes.size].toShape()
    val (bg, fg) = when ((number / 7) % 4) {
        0 -> scheme.primaryContainer to scheme.onPrimaryContainer
        1 -> scheme.secondaryContainer to scheme.onSecondaryContainer
        2 -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        else -> scheme.primary to scheme.onPrimary
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(bg)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.trim().take(1).uppercase().ifEmpty { "?" },
            style = if (size >= 64.dp) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleMedium,
            color = fg,
        )
    }
}

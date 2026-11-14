package com.kharcha.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max

/*
 * The charts. Material has no chart component, so these three are drawn by hand
 * (together with the logo, they are the only hand-drawn things in the app).
 */

/** One part of a [RingChart]. `share` is its fraction of the whole, 0 to 1. */
data class RingPart(val color: Color, val share: Float)

/**
 * A ring split into coloured parts, with room in the middle for a total. The ring sweeps
 * round once when it appears.
 */
@Composable
fun RingChart(
    parts: List<RingPart>,
    modifier: Modifier = Modifier,
    thickness: Dp = 26.dp,
    description: String = "Chart",
    center: @Composable () -> Unit = {},
) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val sweep = remember { Animatable(0f) }
    LaunchedEffect(parts) {
        sweep.snapTo(0f)
        sweep.animateTo(1f, tween(durationMillis = 700))
    }
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clearAndSetSemantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val width = thickness.toPx()
            val inset = width / 2
            val box = Size(size.width - width, size.height - width)
            val corner = Offset(inset, inset)
            drawArc(track, 0f, 360f, useCenter = false, topLeft = corner, size = box, style = Stroke(width))
            // A small gap between parts, unless a part is too thin to spare it.
            val gap = if (parts.size > 1) 2.5f else 0f
            var start = -90f
            for (part in parts) {
                val full = part.share * 360f
                val angle = (full - gap).coerceAtLeast(0.6f) * sweep.value
                if (full > 0f) {
                    drawArc(part.color, start + gap / 2, angle, useCenter = false, topLeft = corner, size = box, style = Stroke(width, cap = StrokeCap.Butt))
                }
                start += full * sweep.value
            }
        }
        center()
    }
}

/** One bar of a [BarChart]. */
data class Bar(val label: String, val value: Long)

/**
 * A bar chart. Each bar grows from the bottom with a spring; the selected bar takes the
 * brand colour, the rest stay quiet. Tapping a bar selects it.
 */
@Composable
fun BarChart(
    bars: List<Bar>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 150.dp,
    description: String = "Chart",
    barColor: Color = MaterialTheme.colorScheme.primary,
) {
    val scheme = MaterialTheme.colorScheme
    val top = max(1L, bars.maxOfOrNull { it.value } ?: 1L)
    val dense = bars.size > 8
    val haptics = rememberHaptics()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(if (dense) 4.dp else 8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        bars.forEachIndexed { index, bar ->
            val target = if (bar.value <= 0L) 0.02f else (bar.value.toFloat() / top).coerceIn(0.04f, 1f)
            val grown by animateFloatAsState(target, spring(dampingRatio = 0.7f, stiffness = 220f), label = "bar")
            val isSelected = index == selected
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        haptics.tick()
                        onSelect(index)
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(grown)
                            .clip(RoundedCornerShape(if (dense) 6.dp else 10.dp))
                            .background(
                                when {
                                    isSelected -> barColor
                                    bar.value <= 0L -> scheme.surfaceContainerHighest
                                    else -> scheme.secondaryContainer
                                },
                            ),
                    )
                }
                // With many bars only every other one is labelled, or the labels would collide.
                val showLabel = !dense || index % 2 == (bars.lastIndex % 2) || isSelected
                Text(
                    if (showLabel) bar.label else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) barColor else scheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

/**
 * A line through a series of values with a soft fill under it: the balance over time.
 * The line is drawn from the lowest to the highest value in the series.
 */
@Composable
fun LineChart(values: List<Long>, modifier: Modifier = Modifier, height: Dp = 120.dp, description: String = "Chart") {
    val line = MaterialTheme.colorScheme.primary
    val zero = MaterialTheme.colorScheme.outlineVariant
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        if (values.size < 2) return@Canvas
        val low = values.min()
        val high = values.max()
        val span = (high - low).coerceAtLeast(1L).toFloat()
        val pad = 6.dp.toPx()
        val usable = size.height - pad * 2
        fun x(index: Int): Float = size.width * index / (values.size - 1)
        fun y(value: Long): Float = pad + usable * (1f - (value - low) / span)

        // Where zero sits, when the series crosses it.
        if (low < 0 && high > 0) {
            drawLine(zero, Offset(0f, y(0)), Offset(size.width, y(0)), strokeWidth = 1.dp.toPx())
        }
        val path = Path().apply {
            moveTo(x(0), y(values[0]))
            for (index in 1 until values.size) lineTo(x(index), y(values[index]))
        }
        val fill = Path().apply {
            addPath(path)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(line.copy(alpha = 0.28f), line.copy(alpha = 0f))))
        drawPath(path, line, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(line, radius = 5.dp.toPx(), center = Offset(x(values.lastIndex), y(values.last())))
    }
}

package com.kharcha.app.ui.habit

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.kharcha.app.ui.theme.KharchaTheme
import com.kharcha.app.ui.theme.Marigold

/** How grown the tree is for a streak of this many days: 0 (a seed) to 5 (a full tree with fruit). */
fun treeStage(streak: Int): Int = when {
    streak <= 0 -> 0
    streak < 3 -> 1
    streak < 7 -> 2
    streak < 14 -> 3
    streak < 30 -> 4
    else -> 5
}

/** How many more days until the tree grows again, or null when it is fully grown. */
fun daysToNextStage(streak: Int): Int? = listOf(1, 3, 7, 14, 30).firstOrNull { it > streak }?.let { it - streak }

fun treeName(stage: Int): String = listOf("a seed", "a sprout", "a sapling", "a young tree", "a tree", "a full tree with fruit")[stage.coerceIn(0, 5)]

/**
 * The streak, drawn as a tree. Every day you log something waters it, and it grows through
 * six stages. Miss a day and it starts again from a seed. It is an illustration, drawn by
 * hand because Material has nothing like it; it sways a little so it feels alive.
 */
@Composable
fun StreakTree(streak: Int, modifier: Modifier = Modifier, size: Dp) {
    val stage = treeStage(streak)
    val leaf = KharchaTheme.extras.income
    val leafDeep = if (KharchaTheme.dark) Color(0xFF4FB07C) else Color(0xFF2E8B57)
    val trunk = if (KharchaTheme.dark) Color(0xFFB08A63) else Color(0xFF7A5A3C)
    val soil = MaterialTheme.colorScheme.surfaceContainerHighest
    val sway by rememberInfiniteTransition(label = "tree").animateFloat(
        initialValue = -1.6f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 2600), RepeatMode.Reverse),
        label = "sway",
    )
    Canvas(
        modifier
            .size(size)
            .semantics { contentDescription = "Your streak as ${treeName(stage)}" },
    ) {
        // Everything is drawn on a 100 by 100 grid, then scaled to the real size.
        val u = this.size.minDimension / 100f
        fun at(x: Float, y: Float) = Offset(x * u, y * u)
        fun blob(x: Float, y: Float, r: Float, color: Color) = drawCircle(color, r * u, at(x, y))
        fun stem(toY: Float, width: Float) = drawLine(trunk, at(50f, 87f), at(50f, toY), strokeWidth = width * u, cap = StrokeCap.Round)

        drawOval(soil, topLeft = at(14f, 81f), size = Size(72f * u, 13f * u))
        rotate(degrees = if (stage == 0) 0f else sway, pivot = at(50f, 87f)) {
            drawStage(stage, ::blob, ::stem, leaf, leafDeep, trunk)
        }
    }
}

private fun DrawScope.drawStage(
    stage: Int,
    blob: (Float, Float, Float, Color) -> Unit,
    stem: (Float, Float) -> Unit,
    leaf: Color,
    leafDeep: Color,
    trunk: Color,
) {
    when (stage) {
        0 -> blob(50f, 84f, 4.5f, trunk)
        1 -> {
            stem(70f, 3f)
            blob(44f, 68f, 6.5f, leaf)
            blob(56.5f, 65f, 6.5f, leafDeep)
        }
        2 -> {
            stem(54f, 4.5f)
            blob(40f, 55f, 10f, leafDeep)
            blob(60f, 52f, 10f, leaf)
            blob(50f, 42f, 11f, leaf)
        }
        3 -> {
            stem(50f, 7f)
            blob(37f, 46f, 16f, leafDeep)
            blob(63f, 46f, 16f, leafDeep)
            blob(50f, 32f, 17f, leaf)
        }
        4 -> {
            stem(50f, 9f)
            blob(33f, 46f, 19f, leafDeep)
            blob(67f, 46f, 19f, leafDeep)
            blob(50f, 46f, 19f, leaf)
            blob(50f, 27f, 20f, leaf)
        }
        else -> {
            stem(50f, 11f)
            blob(29f, 45f, 21f, leafDeep)
            blob(71f, 45f, 21f, leafDeep)
            blob(38f, 27f, 17f, leaf)
            blob(62f, 27f, 17f, leaf)
            blob(50f, 42f, 22f, leaf)
            blob(50f, 20f, 16f, leaf)
            // Fruit: what a long streak earns.
            listOf(30f to 48f, 44f to 30f, 60f to 44f, 70f to 36f, 52f to 54f, 38f to 40f).forEach { (x, y) -> blob(x, y, 3.4f, Marigold) }
        }
    }
}

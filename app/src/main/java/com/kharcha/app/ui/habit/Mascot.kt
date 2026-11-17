package com.kharcha.app.ui.habit

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kharcha.app.domain.MASCOT_NAME
import com.kharcha.app.domain.Mood
import com.kharcha.app.platform.MascotPainter
import com.kharcha.app.ui.components.Eyebrow

/**
 * Gullu, alive: it breathes and blinks while it waits, and hops when the day is logged.
 * The drawing itself is platform/HabitArt.kt, shared with the widgets and notifications;
 * this only moves it. It is an illustration, drawn by hand because Material has no mascot.
 */
@Composable
fun Mascot(mood: Mood, modifier: Modifier = Modifier, size: Dp) {
    val painter = remember { MascotPainter() }
    val glad = mood == Mood.HAPPY || mood == Mood.PARTY
    val transition = rememberInfiniteTransition(label = "mascot")
    val bob by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = if (glad) 460 else 1700), RepeatMode.Reverse),
        label = "bob",
    )
    // One slow sweep from 0 to 1; the eyes shut for the last sliver of it.
    val blink by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 3600, easing = LinearEasing)),
        label = "blink",
    )
    val feeling = when (mood) {
        Mood.HAPPY -> "happy"
        Mood.PARTY -> "celebrating"
        Mood.WAITING -> "waiting"
        Mood.WORRIED -> "worried"
        Mood.SAD -> "sad"
    }
    Canvas(
        modifier
            .size(size)
            .semantics { contentDescription = "$MASCOT_NAME, $feeling" },
    ) {
        val side = this.size.minDimension
        val open = if (blink > 0.955f) 0.12f else 1f
        drawIntoCanvas { painter.draw(it.nativeCanvas, side, mood, bob, open) }
    }
}

/** Gullu with what it has to say beside it, in a speech bubble. */
@Composable
fun MascotSays(mood: Mood, line: String, modifier: Modifier = Modifier, size: Dp = 84.dp) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Mascot(mood, size = size)
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.weight(1f),
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Eyebrow(MASCOT_NAME)
                Text(line, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

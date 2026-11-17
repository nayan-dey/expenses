package com.kharcha.app.platform

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.kharcha.app.domain.Mood
import kotlin.math.abs

/*
 * Gullu, the mascot, and the streak tree as plain pictures.
 *
 * Both are drawn with Android's own Canvas rather than Compose, because they are needed in
 * places Compose cannot reach: home-screen widgets and notifications only accept bitmaps.
 * The app's screens use the very same drawing (ui/habit/Mascot.kt hands its Compose canvas
 * over to MascotPainter), so the mascot looks identical everywhere.
 *
 * Everything is laid out on a 100 by 100 grid and scaled to the size asked for.
 */

private const val BODY = 0xFFFFC940.toInt()
private const val BODY_SHADE = 0xFFEFA428.toInt()
private const val FACE = 0xFF3A2500.toInt()
private const val SLOT = 0xFF7A4E0C.toInt()
private const val CHEEK = 0x66FF6F61
private const val WHITE = 0xFFFFFFFF.toInt()
private const val GLOSS = 0x73FFFFFF
private const val COIN = 0xFF7C84F7.toInt()
private const val COIN_LIGHT = 0xFFB4B8FF.toInt()
private const val DROP = 0xFF64B5F6.toInt()
private const val SHADOW = 0x26000000

/** Draws Gullu: a round marigold money pot with a coin slot, two eyes and a mood. */
class MascotPainter {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val box = RectF()
    private val path = Path()

    /**
     * @param size the side of the square to draw in, in pixels
     * @param bob where it is in its little movement, from -1 to 1 (0 = standing still)
     * @param eyes how open the eyes are, from 0 (shut, mid-blink) to 1
     */
    fun draw(canvas: Canvas, size: Float, mood: Mood, bob: Float = 0f, eyes: Float = 1f) {
        val u = size / 100f
        val glad = mood == Mood.HAPPY || mood == Mood.PARTY
        // Glad: little hops off the ground. Otherwise: a slow breath up and down.
        val lift = if (glad) abs(bob) * 5f else bob * 1.1f + 1.1f
        val cy = 57f - lift

        fun oval(x: Float, y: Float, rx: Float, ry: Float, color: Int) {
            fill.color = color
            box.set((x - rx) * u, (y - ry) * u, (x + rx) * u, (y + ry) * u)
            canvas.drawOval(box, fill)
        }
        fun arc(x: Float, y: Float, rx: Float, ry: Float, start: Float, sweep: Float, width: Float, color: Int = FACE) {
            line.color = color
            line.strokeWidth = width * u
            box.set((x - rx) * u, (y - ry) * u, (x + rx) * u, (y + ry) * u)
            canvas.drawArc(box, start, sweep, false, line)
        }
        fun stroke(x1: Float, y1: Float, x2: Float, y2: Float, width: Float, color: Int = FACE) {
            line.color = color
            line.strokeWidth = width * u
            canvas.drawLine(x1 * u, y1 * u, x2 * u, y2 * u, line)
        }

        // The shadow on the ground shrinks as it hops.
        oval(50f, 92f, 23f - lift * 1.2f, 3.6f, SHADOW)
        // Feet.
        oval(39f, 87.5f - lift * 0.4f, 7.5f, 4.2f, BODY_SHADE)
        oval(61f, 87.5f - lift * 0.4f, 7.5f, 4.2f, BODY_SHADE)

        // A coin on its way into the slot, when there is something to be glad about.
        if (glad) {
            val coinY = cy - 33f - abs(bob) * 3f
            oval(50f, coinY, 7f, 7f, COIN)
            oval(50f, coinY, 4.4f, 4.4f, COIN_LIGHT)
        }

        // The pot: a darker rim showing below the body gives it weight.
        oval(50f, cy + 2.2f, 30f, 30f, BODY_SHADE)
        oval(50f, cy, 30f, 29.4f, BODY)
        oval(38f, cy - 17f, 8f, 4.2f, GLOSS)
        // The coin slot.
        fill.color = SLOT
        box.set(41f * u, (cy - 24.6f) * u, 59f * u, (cy - 21f) * u)
        canvas.drawRoundRect(box, 1.8f * u, 1.8f * u, fill)

        if (glad) {
            oval(30.5f, cy + 8f, 4.6f, 3.6f, CHEEK)
            oval(69.5f, cy + 8f, 4.6f, 3.6f, CHEEK)
        }

        // Eyes.
        val eyeY = cy - 1f
        if (glad) {
            // Shut in a smile: two little arches.
            arc(40f, eyeY + 1.5f, 5f, 5f, 200f, 140f, 2.8f)
            arc(60f, eyeY + 1.5f, 5f, 5f, 200f, 140f, 2.8f)
        } else {
            val open = eyes.coerceIn(0.12f, 1f)
            val look = if (mood == Mood.SAD) 1.4f else 0f
            oval(40f, eyeY + look, 4.2f, 5.2f * open, FACE)
            oval(60f, eyeY + look, 4.2f, 5.2f * open, FACE)
            if (open > 0.5f) {
                oval(41.5f, eyeY + look - 2f, 1.3f, 1.3f, WHITE)
                oval(61.5f, eyeY + look - 2f, 1.3f, 1.3f, WHITE)
            }
            if (mood == Mood.WORRIED || mood == Mood.SAD) {
                // Brows tilted up towards the middle.
                stroke(33.5f, eyeY - 8f, 43f, eyeY - 10.6f, 2.2f)
                stroke(57f, eyeY - 10.6f, 66.5f, eyeY - 8f, 2.2f)
            }
        }

        // Mouth.
        when (mood) {
            Mood.HAPPY -> arc(50f, cy + 8f, 8f, 6.5f, 20f, 140f, 2.8f)
            Mood.PARTY -> {
                path.reset()
                box.set(42.5f * u, (cy + 3f) * u, 57.5f * u, (cy + 18f) * u)
                path.addArc(box, 0f, 180f)
                path.close()
                fill.color = FACE
                canvas.drawPath(path, fill)
            }
            Mood.WAITING -> arc(50f, cy + 9f, 5f, 4f, 25f, 130f, 2.4f)
            Mood.WORRIED -> arc(50f, cy + 12f, 2.8f, 2.8f, 0f, 360f, 2.2f)
            Mood.SAD -> arc(50f, cy + 15f, 6f, 5f, 205f, 130f, 2.6f)
        }

        when (mood) {
            // A drop of sweat: time is running out.
            Mood.WORRIED -> oval(77f, cy - 15f, 2.4f, 3.4f, DROP)
            // A tear.
            Mood.SAD -> oval(66.5f, cy + 8.5f, 2.2f, 3.2f, DROP)
            Mood.PARTY -> {
                // Sparkles around it, pulsing with the hop.
                val s = 3.2f + abs(bob) * 1.6f
                listOf(17f to 24f, 83f to 28f, 13f to 60f, 87f to 62f).forEachIndexed { index, (x, y) ->
                    val color = if (index % 2 == 0) COIN else BODY_SHADE
                    stroke(x - s, y, x + s, y, 1.8f, color)
                    stroke(x, y - s, x, y + s, 1.8f, color)
                }
            }
            else -> Unit
        }
    }
}

/** Ready-made pictures for widgets and notifications. */
object HabitArt {

    fun mascot(mood: Mood, px: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        MascotPainter().draw(Canvas(bitmap), px.toFloat(), mood)
        return bitmap
    }

    /**
     * The streak tree at one of its six stages (see domain/Habit.kt).
     *
     * @param thirsty today has not been logged yet: the leaves are drawn dry
     */
    fun tree(stage: Int, thirsty: Boolean, px: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val u = px / 100f
        val leaf = if (thirsty) 0xFFC2B45C.toInt() else 0xFF45C486.toInt()
        val leafDeep = if (thirsty) 0xFF9C9248.toInt() else 0xFF2E9E66.toInt()
        val trunk = 0xFFB08A63.toInt()
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            color = trunk
        }
        fun blob(x: Float, y: Float, r: Float, color: Int) {
            fill.color = color
            canvas.drawCircle(x * u, y * u, r * u, fill)
        }
        fun stem(toY: Float, width: Float) {
            line.strokeWidth = width * u
            canvas.drawLine(50f * u, 87f * u, 50f * u, toY * u, line)
        }
        // The soil.
        fill.color = 0x33FFFFFF
        canvas.drawOval(RectF(14f * u, 81f * u, 86f * u, 94f * u), fill)
        when (stage.coerceIn(0, 5)) {
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
                // Fruit: what a long streak earns. A thirsty tree keeps its fruit.
                listOf(30f to 48f, 44f to 30f, 60f to 44f, 70f to 36f, 52f to 54f, 38f to 40f).forEach { (x, y) -> blob(x, y, 3.4f, BODY) }
            }
        }
        return bitmap
    }
}

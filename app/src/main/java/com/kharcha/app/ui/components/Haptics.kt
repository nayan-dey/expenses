package com.kharcha.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Small vibrations that confirm a touch, the way Google's own apps do.
 * Android only plays them when "Touch feedback" is switched on in the phone's settings,
 * so there is no separate switch inside Kharcha.
 */
class Haptics(private val feedback: HapticFeedback) {
    /** A light tick: picking a chip, a segment, a keypad key. */
    fun tick() = feedback.performHapticFeedback(HapticFeedbackType.SegmentTick)

    /** A soft click for buttons. */
    fun click() = feedback.performHapticFeedback(HapticFeedbackType.ContextClick)

    /** A clear "done": an item saved, a backup written. */
    fun confirm() = feedback.performHapticFeedback(HapticFeedbackType.Confirm)

    /** A "no": something could not be done, or was deleted. */
    fun reject() = feedback.performHapticFeedback(HapticFeedbackType.Reject)

    /** A long press took hold (select mode). */
    fun longPress() = feedback.performHapticFeedback(HapticFeedbackType.LongPress)

    /** Switches: a different feel for on and off. */
    fun toggle(on: Boolean) =
        feedback.performHapticFeedback(if (on) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
}

@Composable
fun rememberHaptics(): Haptics {
    val feedback = LocalHapticFeedback.current
    return remember(feedback) { Haptics(feedback) }
}

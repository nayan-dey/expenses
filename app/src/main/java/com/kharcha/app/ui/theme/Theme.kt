package com.kharcha.app.ui.theme

import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Corner sizes. Kharcha is soft and round: nothing has a sharp corner. */
object KharchaRadius {
    val xs = 8.dp
    val s = 14.dp
    val m = 22.dp
    val l = 28.dp
    val xl = 36.dp
}

private val KharchaShapes = Shapes(
    extraSmall = RoundedCornerShape(KharchaRadius.xs),
    small = RoundedCornerShape(KharchaRadius.s),
    medium = RoundedCornerShape(KharchaRadius.m),
    large = RoundedCornerShape(KharchaRadius.l),
    extraLarge = RoundedCornerShape(KharchaRadius.xl),
)

val LocalKharchaExtras = staticCompositionLocalOf { kharchaExtras(dark = true) }
val LocalDarkTheme = staticCompositionLocalOf { true }

/** True while "Sensitive mode" is on: every amount is drawn as dots. */
val LocalSensitive = staticCompositionLocalOf { false }

object KharchaTheme {
    /** The colours Material has no role for: the iris hero, money in, money out. */
    val extras: KharchaExtras
        @Composable @ReadOnlyComposable get() = LocalKharchaExtras.current

    val dark: Boolean
        @Composable @ReadOnlyComposable get() = LocalDarkTheme.current
}

/** Wallpaper-based colours exist from Android 12 (API 31). */
fun supportsDynamicColor(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * The app's look. `dynamicColor` swaps Kharcha's iris for the colours of the phone's
 * wallpaper (Material You), the way Google's own apps do.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun KharchaTheme(dark: Boolean, dynamicColor: Boolean, sensitive: Boolean, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val useDynamic = dynamicColor && supportsDynamicColor()
    val scheme = remember(dark, useDynamic, context) {
        when {
            useDynamic && dark -> dynamicDarkColorScheme(context)
            useDynamic -> dynamicLightColorScheme(context)
            dark -> DarkScheme
            else -> LightScheme
        }
    }
    val extras = remember(dark, useDynamic, scheme) {
        val own = kharchaExtras(dark)
        // With phone colours, the hero takes the phone's own accent. Green and coral keep their meaning.
        if (useDynamic) own.copy(hero = scheme.primary, onHero = scheme.onPrimary) else own
    }
    CompositionLocalProvider(
        LocalKharchaExtras provides extras,
        LocalDarkTheme provides dark,
        LocalSensitive provides sensitive,
    ) {
        // Material 3 Expressive: the usual colour roles, type and shapes, plus springy motion.
        MaterialExpressiveTheme(
            colorScheme = scheme,
            motionScheme = MotionScheme.expressive(),
            shapes = KharchaShapes,
            typography = KharchaTypography,
            content = content,
        )
    }
}

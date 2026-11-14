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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.kharcha.app.core.model.Accent
import com.kharcha.app.core.model.Corners
import com.kharcha.app.core.model.FontChoice
import com.kharcha.app.core.model.TextSize

/** Corner sizes at their roundest. The "Corners" setting scales them down. */
object KharchaRadius {
    val xs = 8.dp
    val s = 14.dp
    val m = 22.dp
    val l = 28.dp
    val xl = 36.dp
}

private fun shapes(factor: Float) = Shapes(
    extraSmall = RoundedCornerShape(KharchaRadius.xs * factor),
    small = RoundedCornerShape(KharchaRadius.s * factor),
    medium = RoundedCornerShape(KharchaRadius.m * factor),
    large = RoundedCornerShape(KharchaRadius.l * factor),
    extraLarge = RoundedCornerShape(KharchaRadius.xl * factor),
)

val LocalKharchaExtras = staticCompositionLocalOf { kharchaExtras(dark = true) }
val LocalDarkTheme = staticCompositionLocalOf { true }

/** True while "Sensitive mode" is on: every amount is drawn as dots. */
val LocalSensitive = staticCompositionLocalOf { false }

object KharchaTheme {
    /** The colours Material has no role for: the hero card, money in, money out. */
    val extras: KharchaExtras
        @Composable @ReadOnlyComposable get() = LocalKharchaExtras.current

    val dark: Boolean
        @Composable @ReadOnlyComposable get() = LocalDarkTheme.current
}

/** Wallpaper-based colours exist from Android 12 (API 31). */
fun supportsDynamicColor(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/** Everything about the look that the person can choose in Settings, Appearance. */
data class Look(
    val dark: Boolean,
    /** Take the colours from the phone's wallpaper (Material You). Wins over [accent]. */
    val dynamicColor: Boolean = false,
    val accent: Accent = Accent.IRIS,
    val pureBlack: Boolean = false,
    val font: FontChoice = FontChoice.JAKARTA,
    val textSize: TextSize = TextSize.NORMAL,
    val corners: Corners = Corners.ROUND,
)

/** The app's look: Material 3 Expressive, dressed in the colours, typeface, text size and corners chosen. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun KharchaTheme(look: Look, sensitive: Boolean, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = look.dark
    val useDynamic = look.dynamicColor && supportsDynamicColor()
    val hue = look.accent.hue
    val scheme = remember(dark, useDynamic, hue, look.pureBlack, context) {
        val base = when {
            useDynamic && dark -> dynamicDarkColorScheme(context)
            useDynamic -> dynamicLightColorScheme(context)
            dark -> if (hue == null) DarkScheme else DarkScheme.withAccent(hue)
            else -> if (hue == null) LightScheme else LightScheme.withAccent(hue)
        }
        if (dark && look.pureBlack) base.onBlack() else base
    }
    val extras = remember(dark, useDynamic, hue, scheme) {
        val own = kharchaExtras(dark)
        when {
            // With phone colours, the hero takes the phone's own accent. Green and coral keep their meaning.
            useDynamic -> own.copy(hero = scheme.primary, onHero = scheme.onPrimary)
            hue != null -> own.copy(hero = own.hero.withHue(hue), onHero = if (own.onHero == Color.White) Color.White else own.onHero.withHue(hue))
            else -> own
        }
    }
    val family = remember(look.font) { fontFamily(look.font) }
    val typography = remember(family) { kharchaTypography(family) }
    val named = remember(family) { KharchaTypeSet(family) }
    val shapes = remember(look.corners) { shapes(look.corners.factor) }
    // Respect the phone's font size, then apply Kharcha's own text-size setting on top.
    val base = LocalDensity.current
    val density = remember(base, look.textSize) { Density(density = base.density, fontScale = base.fontScale * look.textSize.scale) }

    CompositionLocalProvider(
        LocalKharchaExtras provides extras,
        LocalDarkTheme provides dark,
        LocalSensitive provides sensitive,
        LocalKharchaType provides named,
        LocalDensity provides density,
    ) {
        // Material 3 Expressive: the usual colour roles, type and shapes, plus springy motion.
        MaterialExpressiveTheme(
            colorScheme = scheme,
            motionScheme = MotionScheme.expressive(),
            shapes = shapes,
            typography = typography,
            content = content,
        )
    }
}

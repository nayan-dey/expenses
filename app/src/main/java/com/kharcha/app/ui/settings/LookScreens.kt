package com.kharcha.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kharcha.app.core.model.AppIcon
import com.kharcha.app.core.model.PrintOptions
import com.kharcha.app.core.model.ThemeChoice
import com.kharcha.app.platform.AppIcons
import com.kharcha.app.ui.components.AppBar
import com.kharcha.app.ui.components.ButtonGroup
import com.kharcha.app.ui.components.CheckRow
import com.kharcha.app.ui.components.Foot
import com.kharcha.app.ui.components.Gap
import com.kharcha.app.ui.components.GroupOption
import com.kharcha.app.ui.components.KGroup
import com.kharcha.app.ui.components.KharchaLogo
import com.kharcha.app.ui.components.ScreenColumn
import com.kharcha.app.ui.components.SectionHeader
import com.kharcha.app.ui.components.SwitchRow
import com.kharcha.app.ui.icons.Ic
import com.kharcha.app.ui.nav.LocalContainer
import com.kharcha.app.ui.nav.LocalMessenger
import com.kharcha.app.ui.nav.LocalNav
import com.kharcha.app.ui.theme.supportsDynamicColor

/** Light, dark or automatic, and whether to take colours from the phone's wallpaper. */
@Composable
fun AppearanceScreen() {
    val app = LocalContainer.current
    val nav = LocalNav.current
    val settings by app.settings.settings.collectAsStateWithLifecycle()
    val choices = listOf(ThemeChoice.SYSTEM, ThemeChoice.LIGHT, ThemeChoice.DARK)

    ScreenColumn {
        AppBar(onBack = { nav.back() }, title = "Appearance")
        SectionHeader("Theme")
        ButtonGroup(
            options = listOf(GroupOption("Automatic", Ic.brightness_auto), GroupOption("Light", Ic.light_mode), GroupOption("Dark", Ic.dark_mode)),
            selectedIndex = choices.indexOf(settings.theme),
            onSelect = { index -> app.settings.update { it.copy(theme = choices[index]) } },
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Foot("Automatic follows the phone's own light or dark setting.")

        if (supportsDynamicColor()) {
            SectionHeader("Colours")
            KGroup {
                SwitchRow(
                    0, 1, "Phone colours", settings.dynamicColor, { on -> app.settings.update { it.copy(dynamicColor = on) } },
                    icon = Ic.palette,
                    subtitle = "Use your wallpaper's colours instead of Kharcha iris",
                )
            }
        }
        Foot("Touch vibrations follow the phone's own \"Touch feedback\" setting.")
    }
}

/** The colours of each launcher icon, for the small previews: background, ring, slice. */
private fun iconColors(icon: AppIcon): Triple<Color, Color, Color> = when (icon) {
    AppIcon.IRIS -> Triple(Color(0xFF565EF0), Color.White, Color(0xFFFFC940))
    AppIcon.INK -> Triple(Color(0xFF111118), Color(0xFFB4B8FF), Color(0xFFFFC940))
    AppIcon.CORAL -> Triple(Color(0xFFF2604F), Color.White, Color(0xFFFFC940))
    AppIcon.MINT -> Triple(Color(0xFF17A673), Color.White, Color(0xFFFFC940))
    AppIcon.MARIGOLD -> Triple(Color(0xFFFFC940), Color(0xFF16163A), Color(0xFF565EF0))
    AppIcon.BLUEPRINT -> Triple(Color(0xFF1F5EEA), Color.White, Color(0xFFBFD4FF))
}

/** Choose the icon shown on the phone's home screen. */
@Composable
fun AppIconScreen() {
    val app = LocalContainer.current
    val nav = LocalNav.current
    val messenger = LocalMessenger.current
    val context = LocalContext.current
    val settings by app.settings.settings.collectAsStateWithLifecycle()
    val icons = AppIcon.entries

    ScreenColumn {
        AppBar(onBack = { nav.back() }, title = "App icon")
        Gap(4.dp)
        KGroup {
            icons.forEachIndexed { index, icon ->
                val (background, ring, slice) = iconColors(icon)
                CheckRow(
                    index, icons.size, icon.label,
                    selected = settings.appIcon == icon,
                    onClick = {
                        if (settings.appIcon != icon) {
                            app.settings.update { it.copy(appIcon = icon) }
                            AppIcons.apply(context, icon)
                            messenger.show("Icon changed. The home screen may take a few seconds to show it.")
                        }
                    },
                    leading = {
                        Box(
                            Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(15.dp))
                                .background(background),
                            contentAlignment = Alignment.Center,
                        ) {
                            KharchaLogo(40.dp, ring = ring, slice = slice)
                        }
                    },
                )
            }
        }
        Foot("Android may close the app when the icon changes. Nothing is lost: open it again from the new icon. Shortcuts you placed on the home screen earlier may need to be added again.")
    }
}

/** What a printed sheet (or a PDF of it) contains. */
@Composable
fun PrintScreen() {
    val app = LocalContainer.current
    val nav = LocalNav.current
    val settings by app.settings.settings.collectAsStateWithLifecycle()
    val options = settings.print
    fun change(next: PrintOptions) = app.settings.update { it.copy(print = next) }

    ScreenColumn {
        AppBar(onBack = { nav.back() }, title = "Print settings")
        Gap(4.dp)
        KGroup {
            SwitchRow(0, 4, "Summary", options.summary, { change(options.copy(summary = it)) }, subtitle = "Balance, expense and income at the top")
            SwitchRow(1, 4, "Spending by category", options.byCategory, { change(options.copy(byCategory = it)) })
            SwitchRow(2, 4, "Notes column", options.notes, { change(options.copy(notes = it)) })
            SwitchRow(3, 4, "Merchant column", options.merchant, { change(options.copy(merchant = it)) })
        }
        Foot("Printing is on a sheet's menu. It prints what the sheet is showing, so set the period, search or filters first. Choose \"Save as PDF\" on Android's print screen to get a PDF.")
    }
}

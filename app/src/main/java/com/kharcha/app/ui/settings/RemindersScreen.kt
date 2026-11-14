package com.kharcha.app.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kharcha.app.core.util.Dates
import com.kharcha.app.platform.AutoExport
import com.kharcha.app.platform.Reminders
import com.kharcha.app.platform.dailyNudge
import com.kharcha.app.ui.components.AppBar
import com.kharcha.app.ui.components.Btn
import com.kharcha.app.ui.components.BtnSize
import com.kharcha.app.ui.components.BtnStyle
import com.kharcha.app.ui.components.Foot
import com.kharcha.app.ui.components.Gap
import com.kharcha.app.ui.components.KGroup
import com.kharcha.app.ui.components.ScreenColumn
import com.kharcha.app.ui.components.SectionHeader
import com.kharcha.app.ui.components.SettingRow
import com.kharcha.app.ui.components.SwitchRow
import com.kharcha.app.ui.components.TimeDialog
import com.kharcha.app.ui.icons.Ic
import com.kharcha.app.ui.nav.LocalContainer
import com.kharcha.app.ui.nav.LocalMessenger
import com.kharcha.app.ui.nav.LocalNav
import java.time.LocalTime

/**
 * The daily reminder and the automatic backup. Both are run by the phone's own alarm clock,
 * once a day at the chosen time. No server is involved.
 */
@Composable
fun RemindersScreen() {
    val app = LocalContainer.current
    val nav = LocalNav.current
    val messenger = LocalMessenger.current
    val context = LocalContext.current
    val settings by app.settings.settings.collectAsStateWithLifecycle()
    val rates by app.rates.rates.collectAsStateWithLifecycle()
    var pickTime by remember { mutableStateOf(false) }
    val time = LocalTime.of(settings.reminderHour.coerceIn(0, 23), settings.reminderMinute.coerceIn(0, 59))

    fun turnOn() {
        app.settings.update { it.copy(reminder = true) }
        app.scheduleDaily()
        messenger.show("Reminder set for ${Dates.time(Dates.millis(Dates.dateOf(app.now()), time))} every day")
    }
    // From Android 13 an app has to ask before it may show notifications.
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) turnOn() else messenger.show("Notifications are not allowed. Allow them for Kharcha in Android's settings.")
    }

    ScreenColumn {
        AppBar(onBack = { nav.back() }, title = "Reminders and backup")
        SectionHeader("Daily reminder")
        KGroup {
            SwitchRow(
                0, 2, "Remind me every day", settings.reminder,
                { on ->
                    when {
                        !on -> {
                            app.settings.update { it.copy(reminder = false) }
                            app.scheduleDaily()
                        }
                        Build.VERSION.SDK_INT >= 33 && !Reminders.allowed(context) -> ask.launch(Manifest.permission.POST_NOTIFICATIONS)
                        !Reminders.allowed(context) -> messenger.show("Notifications are switched off for Kharcha in Android's settings.")
                        else -> turnOn()
                    }
                },
                icon = Ic.notifications,
                iconColor = Color(0xFFF59B42),
                subtitle = "One notification a day to log your spending and keep your streak",
            )
            SettingRow(1, 2, "Time", icon = Ic.schedule, iconColor = Color(0xFF3F8EF5), value = Dates.time(Dates.millis(Dates.dateOf(app.now()), time)), onClick = { pickTime = true })
        }
        Gap(8.dp)
        Btn(
            "Show me one now",
            {
                if (Reminders.allowed(context)) {
                    val nudge = dailyNudge(app.ledger.current, settings, rates, app.now())
                    Reminders.show(context, nudge.title, nudge.text, nudge.action)
                } else {
                    messenger.show("Switch the reminder on first, and allow notifications.")
                }
            },
            modifier = Modifier.padding(horizontal = 16.dp),
            icon = Ic.notifications,
            style = BtnStyle.TONAL,
            size = BtnSize.SM,
        )
        Foot("Most days it asks you to log the day, or tells you what you spent. On Sundays it sums up the week, on the 1st the month, and on a goal's day it reminds you of the monthly amount. Android may deliver it a few minutes late to save battery.")

        SectionHeader("Automatic backup")
        if (AutoExport.supported()) {
            KGroup {
                SwitchRow(
                    0, 1, "Daily backup to Downloads", settings.autoExport,
                    { on ->
                        app.settings.update { it.copy(autoExport = on) }
                        app.scheduleDaily()
                        if (on) messenger.show("A backup will be written once a day")
                    },
                    icon = Ic.backup,
                    iconColor = Color(0xFF45C466),
                    subtitle = if (settings.lastAutoExportDay.isBlank()) "Nothing written yet" else "Last written on ${settings.lastAutoExportDay}",
                )
            }
            Foot("Once a day a full backup file is written into the phone's Downloads folder, in a folder called Kharcha. The newest seven are kept. It is written when the daily time comes round, or the next time you open the app.")
        } else {
            Foot("The automatic backup needs Android 10 or newer. On this phone, save a backup by hand under Settings, Backup.")
        }
    }

    if (pickTime) {
        TimeDialog(
            initial = time,
            onPick = { picked ->
                app.settings.update { it.copy(reminderHour = picked.hour, reminderMinute = picked.minute) }
                app.scheduleDaily()
            },
            onDismiss = { pickTime = false },
        )
    }
}

package com.kharcha.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kharcha.app.BuildConfig
import com.kharcha.app.platform.Biometrics
import com.kharcha.app.platform.findActivity
import com.kharcha.app.ui.components.AppBar
import com.kharcha.app.ui.components.Foot
import com.kharcha.app.ui.components.Gap
import com.kharcha.app.ui.components.KGroup
import com.kharcha.app.ui.components.KharchaLogo
import com.kharcha.app.ui.components.ScreenColumn
import com.kharcha.app.ui.components.SectionHeader
import com.kharcha.app.ui.components.SettingRow
import com.kharcha.app.ui.components.SwitchRow
import com.kharcha.app.ui.icons.Ic
import com.kharcha.app.ui.nav.LocalContainer
import com.kharcha.app.ui.nav.LocalMessenger
import com.kharcha.app.ui.nav.LocalNav

/** The lock, screenshots, hidden amounts, and the one thing the app goes online for. */
@Composable
fun PrivacyScreen() {
    val app = LocalContainer.current
    val nav = LocalNav.current
    val messenger = LocalMessenger.current
    val context = LocalContext.current
    val settings by app.settings.settings.collectAsStateWithLifecycle()
    val canLock = Biometrics.available(context)

    ScreenColumn {
        AppBar(onBack = { nav.back() }, title = "Privacy")
        Gap(4.dp)
        KGroup {
            SwitchRow(
                0, 3, "App lock", settings.appLock,
                { on ->
                    val activity = context.findActivity()
                    when {
                        !on -> app.settings.update { it.copy(appLock = false) }
                        !canLock || activity == null -> messenger.show("Set a screen lock on the phone first (Android 11 or newer).")
                        // Prove it works before switching it on, so nobody gets locked out.
                        else -> Biometrics.ask(activity, "Turn on app lock") { ok ->
                            if (ok) {
                                app.lock.unlock()
                                app.settings.update { it.copy(appLock = true) }
                            } else {
                                messenger.show("App lock was not turned on")
                            }
                        }
                    }
                },
                icon = Ic.fingerprint,
                iconColor = Color(0xFFC63FE8),
                subtitle = "Ask for your fingerprint, face or the phone's PIN when Kharcha opens",
            )
            SwitchRow(
                1, 3, "Block screenshots", settings.blockScreenshots, { on -> app.settings.update { it.copy(blockScreenshots = on) } },
                icon = Ic.screenshot,
                iconColor = Color(0xFF3F8EF5),
                subtitle = "Also blanks the app in the recent-apps view",
            )
            SwitchRow(
                2, 3, "Sensitive mode", settings.sensitive, { on -> app.settings.update { it.copy(sensitive = on) } },
                icon = Ic.visibility_off,
                iconColor = Color(0xFFBE9A60),
                subtitle = "Hide every amount behind dots",
            )
        }

        SectionHeader("Internet")
        KGroup {
            SwitchRow(
                0, 1, "Download exchange rates", settings.autoRates, { on -> app.settings.update { it.copy(autoRates = on) } },
                icon = Ic.currency_exchange,
                iconColor = Color(0xFF6C7CF5),
                subtitle = "About twice a day, from a public file of rates",
            )
        }
        Foot("This is the only thing Kharcha goes online for. It downloads a list of rates and sends nothing about you or your money. Switched off, the app never touches the internet, and you can still type rates in by hand.")
    }
}

/** What the app is, and whose work it stands on. */
@Composable
fun AboutScreen() {
    val nav = LocalNav.current
    val scheme = MaterialTheme.colorScheme

    ScreenColumn {
        AppBar(onBack = { nav.back() }, title = "About")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            KharchaLogo(64.dp)
            Column(Modifier.weight(1f)) {
                Text("Kharcha", style = MaterialTheme.typography.headlineSmall)
                Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            }
        }
        Text(
            "Kharcha means \"expense\". It is a private money tracker: sheets, categories, any currency, repeats, pictures, stats and trends. Everything is stored in three small files on this phone. There is no account and no server.",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp),
        )

        SectionHeader("Built with")
        KGroup {
            SettingRow(0, 4, "Material 3 Expressive", icon = Ic.palette, iconColor = Color(0xFF6C7CF5), subtitle = "Google's design system and components. Apache 2.0.")
            SettingRow(1, 4, "Material Symbols", icon = Ic.category, iconColor = Color(0xFF45C466), subtitle = "The icons, by Google. Apache 2.0.")
            SettingRow(2, 4, "Plus Jakarta Sans", icon = Ic.notes, iconColor = Color(0xFFF59B42), subtitle = "The typeface, by Tokotype. SIL Open Font License.")
            SettingRow(3, 4, "Exchange rates", icon = Ic.currency_exchange, iconColor = Color(0xFFEC4A63), subtitle = "fawazahmed0/exchange-api, with open.er-api.com as a fallback.")
        }
        Foot("Exchange rates are for guidance only. Kharcha is not financial advice.")
    }
}

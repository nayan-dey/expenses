@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.kharcha.app.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kharcha.app.core.model.SheetSort
import com.kharcha.app.core.model.ThemeChoice
import com.kharcha.app.core.util.Currencies
import com.kharcha.app.core.util.Dates
import com.kharcha.app.ui.components.AppBar
import com.kharcha.app.ui.components.ChoiceSheet
import com.kharcha.app.ui.components.CurrencySheet
import com.kharcha.app.ui.components.Gap
import com.kharcha.app.ui.components.HeroCard
import com.kharcha.app.ui.components.KGroup
import com.kharcha.app.ui.components.KIcon
import com.kharcha.app.ui.components.ScreenColumn
import com.kharcha.app.ui.components.SettingRow
import com.kharcha.app.ui.components.SwitchRow
import com.kharcha.app.ui.components.rememberNow
import com.kharcha.app.ui.icons.Ic
import com.kharcha.app.ui.nav.AboutRoute
import com.kharcha.app.ui.nav.AppIconRoute
import com.kharcha.app.ui.nav.AppearanceRoute
import com.kharcha.app.ui.nav.BackupRoute
import com.kharcha.app.ui.nav.CategoriesRoute
import com.kharcha.app.ui.nav.ExportRoute
import com.kharcha.app.ui.nav.ImportRoute
import com.kharcha.app.ui.nav.LocalContainer
import com.kharcha.app.ui.nav.LocalNav
import com.kharcha.app.ui.nav.PrintRoute
import com.kharcha.app.ui.nav.PrivacyRoute
import com.kharcha.app.ui.nav.RatesRoute
import com.kharcha.app.ui.nav.TrashRoute

/** The settings menu. Each row opens its own small screen. */
@Composable
fun SettingsScreen() {
    val app = LocalContainer.current
    val nav = LocalNav.current
    val context = LocalContext.current
    val data by app.ledger.data.collectAsStateWithLifecycle()
    val settings by app.settings.settings.collectAsStateWithLifecycle()
    val now = rememberNow()
    var sortOpen by remember { mutableStateOf(false) }
    var currencyOpen by remember { mutableStateOf(false) }
    val preferred = settings.preferredCurrency.ifBlank { Currencies.ofPhone() }
    val trash = data.sheets.count { it.deletedAt != null } + data.categories.count { it.deletedAt != null } + data.items.count { it.deletedAt != null }

    ScreenColumn {
        AppBar(onBack = { nav.back() }, title = "Settings")

        // Where a paid app would have its "Pro" upsell.
        HeroCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), decoration = MaterialShapes.SoftBurst) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                KIcon(Ic.workspace_premium, fill = true, size = 34.dp)
                Column(Modifier.weight(1f)) {
                    Text("Everything is unlocked", style = MaterialTheme.typography.titleLarge)
                    Text("Unlimited sheets, repeats, pictures, stats, trends, printing, app icons. Nothing to buy, no account.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Gap(12.dp)

        KGroup {
            SettingRow(
                0, 5, "Backup", icon = Ic.backup, iconColor = Color(0xFF3F8EF5),
                subtitle = if (settings.lastBackupAt == 0L) "No backup saved yet" else "Last saved ${Dates.ago(settings.lastBackupAt, now)}",
                onClick = { nav.go(BackupRoute) },
            )
            SettingRow(1, 5, "Categories", icon = Ic.list, iconColor = Color(0xFF32B8C6), value = data.liveCategories.size.toString(), onClick = { nav.go(CategoriesRoute) })
            SettingRow(2, 5, "Export", icon = Ic.upload, iconColor = Color(0xFFF59B42), onClick = { nav.go(ExportRoute()) })
            SettingRow(3, 5, "Import", icon = Ic.download, iconColor = Color(0xFF45C466), onClick = { nav.go(ImportRoute()) })
            SettingRow(4, 5, "Trash", icon = Ic.delete, iconColor = Color(0xFFEF5350), value = if (trash == 0) "Empty" else trash.toString(), onClick = { nav.go(TrashRoute) })
        }
        Gap(14.dp)

        KGroup {
            SettingRow(
                0, 6, "Appearance", icon = Ic.palette, iconColor = Color(0xFFEC4A63),
                value = when (settings.theme) {
                    ThemeChoice.SYSTEM -> "Automatic"
                    ThemeChoice.LIGHT -> "Light"
                    ThemeChoice.DARK -> "Dark"
                },
                onClick = { nav.go(AppearanceRoute) },
            )
            SettingRow(1, 6, "App icon", icon = Ic.apps, iconColor = Color(0xFF4DB8D1), value = settings.appIcon.label, onClick = { nav.go(AppIconRoute) })
            SettingRow(2, 6, "Sort sheets by", icon = Ic.swap_vert, iconColor = Color(0xFF2FBF8F), value = settings.sheetSort.label, onClick = { sortOpen = true })
            SettingRow(3, 6, "Print settings", icon = Ic.print, iconColor = Color(0xFF8E8E93), onClick = { nav.go(PrintRoute) })
            SettingRow(4, 6, "Preferred currency", icon = Ic.payments, iconColor = Color(0xFFF59B42), value = preferred, onClick = { currencyOpen = true })
            SettingRow(5, 6, "Exchange rates", icon = Ic.currency_exchange, iconColor = Color(0xFF6C7CF5), onClick = { nav.go(RatesRoute(preferred)) })
        }
        Gap(14.dp)

        KGroup {
            SettingRow(0, 2, "Privacy", icon = Ic.lock, iconColor = Color(0xFFC63FE8), value = if (settings.appLock) "Locked" else null, onClick = { nav.go(PrivacyRoute) })
            SwitchRow(
                1, 2, "Sensitive mode", settings.sensitive, { on -> app.settings.update { it.copy(sensitive = on) } },
                icon = Ic.visibility_off,
                iconColor = Color(0xFFBE9A60),
                subtitle = "Hide every amount behind dots",
            )
        }
        Gap(14.dp)

        KGroup {
            SettingRow(0, 2, "About", icon = Ic.info, iconColor = Color(0xFF6C7CF5), onClick = { nav.go(AboutRoute) })
            SettingRow(
                1, 2, "App settings", icon = Ic.settings, iconColor = Color(0xFF8E8E93),
                subtitle = "Permissions, notifications and storage, in Android's settings",
                external = true,
                onClick = {
                    val intent = Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(intent) }
                },
            )
        }
    }

    if (sortOpen) {
        ChoiceSheet(
            title = "Sort sheets by",
            options = SheetSort.entries.toList(),
            selected = settings.sheetSort,
            label = { it.label },
            onPick = { choice -> app.settings.update { it.copy(sheetSort = choice) } },
            onDismiss = { sortOpen = false },
        )
    }
    if (currencyOpen) {
        CurrencySheet(
            selected = preferred,
            pinned = listOf(preferred) + data.liveSheets.map { it.currency },
            onPick = { code -> app.settings.update { it.copy(preferredCurrency = code) } },
            onDismiss = { currencyOpen = false },
            title = "Preferred currency",
        )
    }
}

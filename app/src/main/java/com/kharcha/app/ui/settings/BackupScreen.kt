package com.kharcha.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kharcha.app.core.util.Dates
import com.kharcha.app.data.KharchaFile
import com.kharcha.app.data.Seed
import com.kharcha.app.ui.components.AppBar
import com.kharcha.app.ui.components.Btn
import com.kharcha.app.ui.components.BtnSize
import com.kharcha.app.ui.components.BtnStyle
import com.kharcha.app.ui.components.ConfirmDialog
import com.kharcha.app.ui.components.Foot
import com.kharcha.app.ui.components.Gap
import com.kharcha.app.ui.components.KGroup
import com.kharcha.app.ui.components.ScreenColumn
import com.kharcha.app.ui.components.SectionHeader
import com.kharcha.app.ui.components.SettingRow
import com.kharcha.app.ui.components.rememberHaptics
import com.kharcha.app.ui.components.rememberNow
import com.kharcha.app.ui.icons.Ic
import com.kharcha.app.ui.nav.LocalContainer
import com.kharcha.app.ui.nav.LocalMessenger
import com.kharcha.app.ui.nav.LocalNav
import kotlinx.coroutines.launch

/**
 * Backup and restore. Kharcha has no cloud of its own, so this takes the place of "sync":
 * save everything into one file, keep it wherever you like, and open it on any phone.
 * Opening a file can also MERGE it, which is how two phones are brought back in step.
 */
@Composable
fun BackupScreen() {
    val app = LocalContainer.current
    val nav = LocalNav.current
    val messenger = LocalMessenger.current
    val haptics = rememberHaptics()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val data by app.ledger.data.collectAsStateWithLifecycle()
    val settings by app.settings.settings.collectAsStateWithLifecycle()
    val now = rememberNow()
    /** A file that was opened and is waiting for "merge or replace?". */
    var opened by remember { mutableStateOf<KharchaFile?>(null) }
    var confirmErase by remember { mutableStateOf(false) }
    var confirmSample by remember { mutableStateOf(false) }

    // Saving: Android's own "where should this file go?" screen.
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = runCatching {
                    (context.contentResolver.openOutputStream(uri) ?: error("Could not open the file")).use { app.exchange.writeBackup(it) }
                }.isSuccess
                if (ok) {
                    app.settings.update { it.copy(lastBackupAt = app.now()) }
                    haptics.confirm()
                }
                messenger.show(if (ok) "Backup saved" else "Could not save the backup")
            }
        }
    }
    // Opening: Android's own "pick a file" screen.
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val file = runCatching { context.contentResolver.openInputStream(uri)?.use { app.exchange.readFile(it) } }.getOrNull()
                if (file == null) messenger.show("That is not a Kharcha file") else opened = file
            }
        }
    }

    ScreenColumn {
        AppBar(onBack = { nav.back() }, title = "Backup")
        Text(
            "Everything lives on this phone only. Save a backup now and then, and keep the file somewhere safe (your Drive, your computer, a message to yourself), so a lost or broken phone does not take your records with it.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp),
        )
        Gap(6.dp)
        KGroup {
            SettingRow(
                0, 2, "Save a backup", icon = Ic.backup, iconColor = Color(0xFF3F8EF5),
                subtitle = if (settings.lastBackupAt == 0L) "Never saved" else "Last saved ${Dates.ago(settings.lastBackupAt, now)}",
                onClick = { save.launch("kharcha-backup-${Dates.iso(app.now())}.zip") },
            )
            SettingRow(
                1, 2, "Open a backup or shared sheet", icon = Ic.file_open, iconColor = Color(0xFF45C466),
                subtitle = "Merge it into what is here, or replace everything",
                onClick = { open.launch(arrayOf("application/zip", "application/json", "application/octet-stream", "*/*")) },
            )
        }
        Foot("The file holds ${data.liveSheets.size} sheets, ${data.items.count { it.deletedAt == null }} items, the categories and the pictures. If Google backup is switched on in the phone's own settings, Android also keeps a copy of the app's data for you.")

        SectionHeader("Two phones")
        Text(
            "To share one sheet, open it and tap the share button at the top: that sends a file. The other person opens it here with \"Open a backup or shared sheet\" and chooses Merge. Sending it back and merging again keeps both phones in step: for anything changed on both, the latest change wins.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 22.dp),
        )

        SectionHeader("Start over")
        androidx.compose.foundation.layout.Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            Btn("Add sample data", { confirmSample = true }, icon = Ic.science, style = BtnStyle.TONAL, size = BtnSize.SM)
            Btn("Erase everything", { confirmErase = true }, icon = Ic.delete_forever, style = BtnStyle.DANGER, size = BtnSize.SM)
        }
    }

    val file = opened
    if (file != null) {
        val incoming = file.data
        AlertDialog(
            onDismissRequest = { opened = null },
            title = { Text(if (file.kind == "sheet") "Shared sheet" else "Backup") },
            text = {
                Text(
                    "This file has ${incoming.sheets.size} ${if (incoming.sheets.size == 1) "sheet" else "sheets"} and ${incoming.items.size} items" +
                        (if (file.exportedAt > 0L) ", saved ${Dates.ago(file.exportedAt, now)}" else "") +
                        ".\n\nMerge adds it to what you have; where both have the same item, the one changed last is kept." +
                        (if (file.kind == "sheet") "" else "\n\nReplace throws away everything in the app now and uses the file instead."),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    opened = null
                    val summary = app.ledger.merge(incoming)
                    haptics.confirm()
                    messenger.show("Merged: ${summary.items} items and ${summary.sheets} sheets added or updated")
                }) { Text("Merge") }
            },
            dismissButton = {
                if (file.kind != "sheet") {
                    TextButton(onClick = {
                        opened = null
                        app.ledger.replaceAll(incoming)
                        messenger.show("Replaced with the backup")
                    }) { Text("Replace", color = MaterialTheme.colorScheme.error) }
                }
            },
        )
    }
    if (confirmErase) {
        ConfirmDialog(
            title = "Erase everything?",
            text = "Every sheet, item, category and picture will be deleted from this phone for good. Save a backup first if you might want them back.",
            confirm = "Erase",
            danger = true,
            onConfirm = {
                app.ledger.eraseAll()
                app.ledger.replaceAll(com.kharcha.app.core.model.AppData(categories = Seed.defaultCategories))
                messenger.show("Everything erased")
            },
            onDismiss = { confirmErase = false },
        )
    }
    if (confirmSample) {
        ConfirmDialog(
            title = "Add sample data?",
            text = "Two made-up sheets with a few months of items are added next to your own, so you can try the app out. Delete those two sheets when you are done.",
            confirm = "Add",
            onConfirm = {
                val currency = settings.preferredCurrency.ifBlank { data.liveSheets.firstOrNull()?.currency ?: "USD" }
                val summary = app.ledger.merge(Seed.sample(currency, app.now()))
                messenger.show("Added ${summary.sheets} sample sheets")
            },
            onDismiss = { confirmSample = false },
        )
    }
}

package com.kharcha.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kharcha.app.core.model.Kind
import com.kharcha.app.core.util.Dates
import com.kharcha.app.core.util.Range
import com.kharcha.app.data.CsvPreview
import com.kharcha.app.platform.Share
import com.kharcha.app.ui.components.AppBar
import com.kharcha.app.ui.components.Btn
import com.kharcha.app.ui.components.BtnStyle
import com.kharcha.app.ui.components.DateDialog
import com.kharcha.app.ui.components.Foot
import com.kharcha.app.ui.components.Gap
import com.kharcha.app.ui.components.KGroup
import com.kharcha.app.ui.components.ScreenColumn
import com.kharcha.app.ui.components.SectionHeader
import com.kharcha.app.ui.components.SettingRow
import com.kharcha.app.ui.components.SheetPickSheet
import com.kharcha.app.ui.components.SwitchRow
import com.kharcha.app.ui.components.rememberHaptics
import com.kharcha.app.ui.icons.Ic
import com.kharcha.app.ui.nav.ExportRoute
import com.kharcha.app.ui.nav.ImportRoute
import com.kharcha.app.ui.nav.LocalContainer
import com.kharcha.app.ui.nav.LocalMessenger
import com.kharcha.app.ui.nav.LocalNav
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Export items as a CSV file (a table any spreadsheet opens). With "Include pictures" the
 * CSV and the receipt pictures go into one ZIP.
 */
@Composable
fun ExportScreen(key: ExportRoute) {
    val app = LocalContainer.current
    val nav = LocalNav.current
    val messenger = LocalMessenger.current
    val haptics = rememberHaptics()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val data by app.ledger.data.collectAsStateWithLifecycle()
    val settings by app.settings.settings.collectAsStateWithLifecycle()

    var sheetId by rememberSaveable { mutableStateOf(key.sheetId) }
    var allTime by rememberSaveable { mutableStateOf(true) }
    var from by rememberSaveable { mutableStateOf(Dates.startOfDay(Dates.dateOf(app.now()).withDayOfMonth(1))) }
    var to by rememberSaveable { mutableStateOf(Dates.startOfDay(app.now())) }
    var pickSheet by remember { mutableStateOf(false) }
    // Which date is being picked: "from", "to" or null.
    var picking by remember { mutableStateOf<String?>(null) }

    val sheet = data.sheet(sheetId)
    val range = if (allTime) Range(Long.MIN_VALUE, Long.MAX_VALUE) else Range(from, Dates.startOfDay(Dates.dateOf(to).plusDays(1)))
    val items = remember(data, sheetId, range) { app.exchange.itemsFor(sheetId?.let { setOf(it) }, range) }
    val pictures = items.sumOf { it.images.size }
    val zip = settings.exportImages && pictures > 0
    val name = "kharcha-" + (sheet?.name?.replace(Regex("[^A-Za-z0-9]+"), "-")?.trim('-')?.lowercase()?.ifBlank { null } ?: "all") + "-" + Dates.iso(app.now())

    suspend fun write(out: java.io.OutputStream) {
        if (zip) app.exchange.writeCsvZip(out, items) else withContext(Dispatchers.IO) { out.write(app.exchange.csv(items).toByteArray(Charsets.UTF_8)) }
    }

    val saveCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch {
            val ok = runCatching { (context.contentResolver.openOutputStream(uri) ?: error("no file")).use { write(it) } }.isSuccess
            if (ok) haptics.confirm()
            messenger.show(if (ok) "Exported ${items.size} items" else "Could not save the file")
        }
    }
    val saveZip = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch {
            val ok = runCatching { (context.contentResolver.openOutputStream(uri) ?: error("no file")).use { write(it) } }.isSuccess
            if (ok) haptics.confirm()
            messenger.show(if (ok) "Exported ${items.size} items and $pictures pictures" else "Could not save the file")
        }
    }

    ScreenColumn {
        AppBar(onBack = { nav.back() }, title = "Export")
        Gap(4.dp)
        KGroup {
            SettingRow(0, 1, "Sheet", icon = Ic.description, iconColor = Color(0xFF6C7CF5), value = sheet?.name ?: "All sheets", onClick = { pickSheet = true })
        }
        Gap(12.dp)
        KGroup {
            val rows = if (allTime) 1 else 3
            SwitchRow(0, rows, "All time", allTime, { allTime = it })
            if (!allTime) {
                SettingRow(1, rows, "From", value = Dates.medium(from), onClick = { picking = "from" })
                SettingRow(2, rows, "To", value = Dates.medium(to), onClick = { picking = "to" })
            }
        }
        Gap(12.dp)
        KGroup {
            SwitchRow(
                0, 1, "Include pictures", settings.exportImages, { on -> app.settings.update { it.copy(exportImages = on) } },
                subtitle = if (pictures == 0) "None of these items has a picture" else "$pictures pictures, packed with the CSV into one ZIP",
            )
        }
        Gap(18.dp)
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Btn(
                "Save file",
                { if (zip) saveZip.launch("$name.zip") else saveCsv.launch("$name.csv") },
                modifier = Modifier.weight(1f),
                icon = Ic.download,
                enabled = items.isNotEmpty(),
            )
            Btn(
                "Share",
                {
                    scope.launch {
                        val (file, uri) = Share.newFile(context, if (zip) "$name.zip" else "$name.csv")
                        val ok = runCatching { file.outputStream().use { write(it) } }.isSuccess
                        if (ok) Share.send(context, uri, if (zip) "application/zip" else "text/csv", "Kharcha export") else messenger.show("Could not make the file")
                    }
                },
                modifier = Modifier.weight(1f),
                icon = Ic.share,
                style = BtnStyle.TONAL,
                enabled = items.isNotEmpty(),
            )
        }
        Foot(if (items.size == 1) "1 item will be exported." else "${items.size} items will be exported. The columns include the date, time, type, amount, currency, category, notes, merchant, sheet, how it was paid, who paid and what friends owe.")
    }

    if (pickSheet) {
        SheetPickSheet("Export which sheet?", data.liveSheets, sheetId, onPick = { sheetId = it.id }, onDismiss = { pickSheet = false }, onPickAll = { sheetId = null })
    }
    val which = picking
    if (which != null) {
        DateDialog(
            initial = Dates.dateOf(if (which == "from") from else to),
            onPick = { date -> if (which == "from") from = Dates.startOfDay(date) else to = Dates.startOfDay(date) },
            onDismiss = { picking = null },
        )
    }
}

/** Import items from a CSV made by another app or a spreadsheet. */
@Composable
fun ImportScreen(key: ImportRoute) {
    val app = LocalContainer.current
    val nav = LocalNav.current
    val messenger = LocalMessenger.current
    val haptics = rememberHaptics()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme
    val data by app.ledger.data.collectAsStateWithLifecycle()

    var sheetId by rememberSaveable { mutableStateOf(key.sheetId ?: app.ledger.current.liveSheets.maxByOrNull { it.editedAt }?.id) }
    var preview by remember { mutableStateOf<CsvPreview?>(null) }
    var fileText by remember { mutableStateOf<String?>(null) }
    /** New categories the person has switched OFF (they are created unless listed here). */
    var skipped by remember { mutableStateOf<Set<Pair<String, Kind>>>(emptySet()) }
    var pickSheet by remember { mutableStateOf(false) }
    val sheet = data.sheet(sheetId)

    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val text = runCatching {
                withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } }
            }.getOrNull()
            if (text == null) messenger.show("Could not read that file") else fileText = text
        }
    }
    // Read the file again whenever the file or the target sheet changes (the sheet decides the default currency).
    val text = fileText
    androidx.compose.runtime.LaunchedEffect(text, sheet?.id, sheet?.currency) {
        preview = if (text != null && sheet != null) app.exchange.previewCsv(text, sheet) else null
        skipped = emptySet()
    }
    val current = preview

    ScreenColumn {
        AppBar(onBack = { nav.back() }, title = "Import")
        Gap(4.dp)
        KGroup {
            SettingRow(0, 1, "Sheet", icon = Ic.description, iconColor = Color(0xFF6C7CF5), value = sheet?.name ?: "Choose", onClick = { pickSheet = true })
        }
        Gap(12.dp)
        Btn(
            if (text == null) "Select CSV file" else "Select another file",
            { open.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel", "application/octet-stream")) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            icon = Ic.file_open,
            style = BtnStyle.TONAL,
        )
        Foot("The first row of the file must name the columns. It needs a Date and an Amount column; Type, Currency, Category, Notes, Merchant and Time are used when they are there. A minus sign, or an \"Expense\" type, marks money going out.")

        if (current != null) {
            val problem = current.problem
            if (problem != null) {
                Text(problem, style = MaterialTheme.typography.bodyLarge, color = scheme.error, modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp))
            } else {
                KGroup {
                    SettingRow(0, 2, "Expense", value = current.expenses.toString())
                    SettingRow(1, 2, "Income", value = current.incomes.toString())
                }
                if (current.skipped > 0) Foot("${current.skipped} rows could not be understood (no date or no amount) and will be left out.")

                if (current.newCategories.isNotEmpty()) {
                    SectionHeader("Review categories · ${current.newCategories.size}")
                    KGroup {
                        current.newCategories.forEachIndexed { index, entry ->
                            SwitchRow(
                                index, current.newCategories.size, entry.first, entry !in skipped,
                                { on -> skipped = if (on) skipped - entry else skipped + entry },
                                subtitle = if (entry.second == Kind.EXPENSE) "New expense category" else "New income category",
                            )
                        }
                    }
                    Foot("These categories are in the file but not in the app. Switched on, they are created. Switched off, their items come in with no category.")
                }
                Gap(14.dp)
                Btn(
                    if (current.rows.size == 1) "Import 1 item" else "Import ${current.rows.size} items",
                    {
                        val target = sheet
                        if (target != null) {
                            val count = app.exchange.importCsv(current, target, current.newCategories.filter { it !in skipped }.toSet()) { code ->
                                app.rates.rate(code, target.currency) ?: 1.0
                            }
                            haptics.confirm()
                            messenger.show("Imported $count items into ${target.name}")
                            nav.back()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    icon = Ic.download,
                    enabled = current.rows.isNotEmpty() && sheet != null,
                )
            }
        }
        Gap(8.dp)
        Foot("To bring in a Kharcha backup or a sheet someone shared with you, use Settings, Backup instead.")
    }

    if (pickSheet) {
        SheetPickSheet("Import into", data.liveSheets, sheetId, onPick = { sheetId = it.id }, onDismiss = { pickSheet = false })
    }
}

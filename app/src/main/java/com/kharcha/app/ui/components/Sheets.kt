@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.kharcha.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kharcha.app.core.model.Category
import com.kharcha.app.core.model.Kind
import com.kharcha.app.core.model.Sheet
import com.kharcha.app.core.util.Currencies
import com.kharcha.app.core.util.Dates
import com.kharcha.app.ui.icons.Ic
import java.time.LocalDate
import java.time.LocalTime

/*
 * Things that slide up or pop up to ask one question: pick a currency, a category, a date.
 * Sheets are Material's ModalBottomSheet; dialogs are Material's AlertDialog, DatePickerDialog
 * and TimePickerDialog.
 */

/** A bottom sheet with an optional title. The content decides how it scrolls. */
@Composable
fun KSheet(onDismiss: () -> Unit, title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        if (title != null) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .padding(start = 24.dp, end = 24.dp, bottom = 14.dp)
                    .semantics { heading() },
            )
        }
        content()
    }
}

/** The colour rows take inside a bottom sheet, one step lighter than the sheet itself. */
@Composable
private fun sheetRow() = MaterialTheme.colorScheme.surfaceContainerHigh

/** Pick one from a short list ("This month", "Every week"). */
@Composable
fun <T> ChoiceSheet(
    title: String,
    options: List<T>,
    selected: T?,
    label: (T) -> String,
    onPick: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    KSheet(onDismiss, title) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(bottom = 20.dp),
        ) {
            KGroup {
                options.forEachIndexed { index, option ->
                    CheckRow(
                        index, options.size, label(option), selected = option == selected,
                        onClick = {
                            onPick(option)
                            onDismiss()
                        },
                        background = sheetRow(),
                    )
                }
            }
        }
    }
}

/** Pick a currency. `pinned` codes (the sheet's own, ones already used) are listed first. */
@Composable
fun CurrencySheet(
    selected: String?,
    pinned: List<String>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
    title: String = "Currency",
    /** Adds an "All currencies" row at the top that reports null. Used by the filter. */
    onPickAll: (() -> Unit)? = null,
) {
    var query by remember { mutableStateOf("") }
    val top = remember(pinned) { pinned.distinct().map { Currencies.info(it) } }
    val rest = remember(query, pinned) { Currencies.all.filter { it.matches(query) && (query.isNotBlank() || it.code !in pinned) } }
    KSheet(onDismiss, title) {
        SearchField(query, { query = it }, "Search by name or code", Modifier.padding(horizontal = 16.dp))
        Gap(10.dp)
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            if (query.isBlank()) {
                if (onPickAll != null) {
                    item(key = "all") {
                        CheckRow(0, 1, "All currencies", selected = selected == null, onClick = { onPickAll(); onDismiss() }, background = sheetRow())
                        Gap(10.dp)
                    }
                }
                itemsIndexed(top, key = { _, info -> "top-" + info.code }) { index, info ->
                    CheckRow(
                        index, top.size, info.code, selected = info.code == selected,
                        onClick = { onPick(info.code); onDismiss() },
                        subtitle = "${info.name} · ${info.symbol}",
                        background = sheetRow(),
                    )
                }
                if (top.isNotEmpty()) item(key = "gap") { Gap(10.dp) }
            }
            itemsIndexed(rest, key = { _, info -> info.code }) { index, info ->
                CheckRow(
                    index, rest.size, info.code, selected = info.code == selected,
                    onClick = { onPick(info.code); onDismiss() },
                    subtitle = "${info.name} · ${info.symbol}",
                    background = sheetRow(),
                )
            }
        }
    }
}

/** Pick a category of one kind, or "No category". `onNew` adds a "New category" button. */
@Composable
fun CategorySheet(
    kind: Kind,
    categories: List<Category>,
    selectedId: String?,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
    onNew: (() -> Unit)? = null,
) {
    var query by remember { mutableStateOf("") }
    val shown = remember(query, categories, kind) {
        categories.filter { it.kind == kind && it.deletedAt == null && it.name.contains(query.trim(), ignoreCase = true) }.sortedBy { it.order }
    }
    KSheet(onDismiss, if (kind == Kind.EXPENSE) "Expense category" else "Income category") {
        SearchField(query, { query = it }, "Search categories", Modifier.padding(horizontal = 16.dp))
        Gap(10.dp)
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            if (query.isBlank()) {
                item(key = "none") {
                    CheckRow(
                        0, 1, "No category", selected = selectedId == null,
                        onClick = { onPick(null); onDismiss() },
                        background = sheetRow(),
                        leading = { CategoryDot(null, size = 40.dp) },
                    )
                    Gap(10.dp)
                }
            }
            itemsIndexed(shown, key = { _, category -> category.id }) { index, category ->
                CheckRow(
                    index, shown.size, category.name, selected = category.id == selectedId,
                    onClick = { onPick(category.id); onDismiss() },
                    background = sheetRow(),
                    leading = { CategoryDot(category, size = 40.dp) },
                )
            }
            if (onNew != null) {
                item(key = "new") {
                    Gap(12.dp)
                    Btn("New category", { onNew(); onDismiss() }, icon = Ic.add, style = BtnStyle.TONAL, size = BtnSize.SM)
                }
            }
        }
    }
}

/** Pick one of the sheets. */
@Composable
fun SheetPickSheet(
    title: String,
    sheets: List<Sheet>,
    selectedId: String?,
    onPick: (Sheet) -> Unit,
    onDismiss: () -> Unit,
    /** Adds an "All sheets" row at the top. */
    onPickAll: (() -> Unit)? = null,
) {
    KSheet(onDismiss, title) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(bottom = 20.dp),
        ) {
            if (onPickAll != null) {
                KGroup { CheckRow(0, 1, "All sheets", selected = selectedId == null, onClick = { onPickAll(); onDismiss() }, background = sheetRow()) }
                Gap(10.dp)
            }
            KGroup {
                sheets.forEachIndexed { index, sheet ->
                    CheckRow(
                        index, sheets.size, sheet.name, selected = sheet.id == selectedId,
                        onClick = { onPick(sheet); onDismiss() },
                        subtitle = sheet.currency,
                        background = sheetRow(),
                    )
                }
            }
        }
    }
}

/** Type a merchant, or pick one used before. An empty answer means "no merchant". */
@Composable
fun MerchantSheet(current: String, known: List<String>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(current) }
    val matches = remember(text, known) { known.filter { it.contains(text.trim(), ignoreCase = true) && !it.equals(text.trim(), ignoreCase = true) }.take(30) }
    KSheet(onDismiss, "Merchant") {
        KField(text, { text = it }, "Shop, person or company", Modifier.padding(horizontal = 16.dp), kind = FieldKind.NAME, last = true)
        Gap(10.dp)
        Column(
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 20.dp),
        ) {
            androidx.compose.foundation.layout.Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Btn(if (text.isBlank()) "No merchant" else "Use \"${text.trim()}\"", { onPick(text.trim()); onDismiss() }, size = BtnSize.SM)
                if (current.isNotBlank()) Btn("Clear", { onPick(""); onDismiss() }, style = BtnStyle.TONAL, size = BtnSize.SM)
            }
            if (matches.isNotEmpty()) {
                Gap(14.dp)
                KGroup {
                    matches.forEachIndexed { index, name ->
                        GroupItem(index, matches.size, onClick = { onPick(name); onDismiss() }, background = sheetRow()) {
                            RowContent(title = name, leading = { IconTile(Ic.storefront, MaterialTheme.colorScheme.primary, size = 36.dp) })
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Dialogs
// ---------------------------------------------------------------------------

/** "Are you sure?" */
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    danger: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = { onDismiss(); onConfirm() }) {
                Text(confirm, color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Asks for one piece of text or one number. */
@Composable
fun PromptDialog(
    title: String,
    label: String,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    kind: FieldKind = FieldKind.TEXT,
    text: String? = null,
    confirm: String = "Save",
    prefix: String? = null,
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (text != null) Text(text)
                KField(value, { value = it }, label, kind = kind, prefix = prefix, last = true)
            }
        },
        confirmButton = { TextButton(onClick = { onDismiss(); onConfirm(value.trim()) }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Material's calendar. */
@Composable
fun DateDialog(initial: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = Dates.pickerMillis(initial))
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(Dates.fromPicker(it)) }
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DatePicker(state = state)
    }
}

/** Material's clock. It follows the phone's 12 or 24 hour setting. */
@Composable
fun TimeDialog(initial: LocalTime, onPick: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute)
    TimePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onPick(LocalTime.of(state.hour, state.minute))
                onDismiss()
            }) { Text("OK") }
        },
        title = { Text("Time") },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        TimePicker(state = state)
    }
}

/**
 * Pick the stretch of time a total covers. "Custom" opens two date rows; the other choices
 * apply at once.
 */
@Composable
fun PeriodSheet(
    kind: com.kharcha.app.core.model.PeriodKind,
    from: Long?,
    to: Long?,
    now: Long,
    onChange: (com.kharcha.app.core.model.PeriodKind, Long?, Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    val customKind = com.kharcha.app.core.model.PeriodKind.CUSTOM
    var custom by remember { mutableStateOf(kind == customKind) }
    var start by remember { mutableStateOf(from) }
    var end by remember { mutableStateOf(to) }
    // Which of the two dates is being picked: "from", "to", or null.
    var picking by remember { mutableStateOf<String?>(null) }
    val options = com.kharcha.app.core.model.PeriodKind.entries

    KSheet(onDismiss, "Period") {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(bottom = 20.dp),
        ) {
            KGroup {
                options.forEachIndexed { index, option ->
                    CheckRow(
                        index, options.size, option.label,
                        selected = if (custom) option == customKind else option == kind,
                        onClick = {
                            if (option == customKind) {
                                custom = true
                                if (start == null && end == null) {
                                    start = Dates.startOfDay(Dates.dateOf(now).withDayOfMonth(1))
                                    end = Dates.startOfDay(now)
                                }
                            } else {
                                onChange(option, null, null)
                                onDismiss()
                            }
                        },
                        background = sheetRow(),
                    )
                }
            }
            if (custom) {
                Gap(12.dp)
                KGroup {
                    SettingRow(0, 2, "From", value = start?.let { Dates.medium(it) } ?: "The start", background = sheetRow(), onClick = { picking = "from" })
                    SettingRow(1, 2, "To", value = end?.let { Dates.medium(it) } ?: "No end", background = sheetRow(), onClick = { picking = "to" })
                }
                Gap(14.dp)
                Btn(
                    "Use these dates",
                    {
                        onChange(customKind, start, end)
                        onDismiss()
                    },
                    modifier = Modifier.padding(horizontal = 16.dp),
                    icon = Ic.check,
                    size = BtnSize.SM,
                )
            }
        }
    }

    val which = picking
    if (which != null) {
        DateDialog(
            initial = Dates.dateOf((if (which == "from") start else end) ?: now),
            onPick = { date ->
                val millis = Dates.startOfDay(date)
                if (which == "from") start = millis else end = millis
            },
            onDismiss = { picking = null },
        )
    }
}

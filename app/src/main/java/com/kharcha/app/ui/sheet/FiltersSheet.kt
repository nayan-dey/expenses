@file:OptIn(ExperimentalLayoutApi::class)

package com.kharcha.app.ui.sheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kharcha.app.core.model.AppData
import com.kharcha.app.core.model.Kind
import com.kharcha.app.core.model.Sheet
import com.kharcha.app.core.util.Dates
import com.kharcha.app.core.util.Money
import com.kharcha.app.domain.AmountTest
import com.kharcha.app.domain.Filter
import com.kharcha.app.ui.components.Btn
import com.kharcha.app.ui.components.BtnSize
import com.kharcha.app.ui.components.BtnStyle
import com.kharcha.app.ui.components.ButtonGroup
import com.kharcha.app.ui.components.CheckRow
import com.kharcha.app.ui.components.CurrencySheet
import com.kharcha.app.ui.components.DateDialog
import com.kharcha.app.ui.components.FieldKind
import com.kharcha.app.ui.components.FilterChip
import com.kharcha.app.ui.components.Gap
import com.kharcha.app.ui.components.GroupOption
import com.kharcha.app.ui.components.KField
import com.kharcha.app.ui.components.KGroup
import com.kharcha.app.ui.components.KSheet
import com.kharcha.app.ui.components.SectionHeader
import com.kharcha.app.ui.components.SettingRow
import com.kharcha.app.ui.components.SwitchRow
import com.kharcha.app.ui.icons.Ic

/**
 * Narrow a sheet's list: by picture, repeat, type, amount, currency, words in the note,
 * category and date. Every change applies at once; the button at the bottom shows how many
 * items are left.
 *
 * @param count how many items a given filter would show
 */
@Composable
fun FiltersSheet(
    filter: Filter,
    sheet: Sheet,
    data: AppData,
    now: Long,
    count: (Filter) -> Int,
    onChange: (Filter) -> Unit,
    onDismiss: () -> Unit,
) {
    val row = MaterialTheme.colorScheme.surfaceContainerHigh
    var amountText by remember { mutableStateOf(filter.amountMinor?.let { Money.plain(it, sheet.currency) }.orEmpty()) }
    var pickCurrency by remember { mutableStateOf(false) }
    // Which date is being picked: "from", "to" or null.
    var picking by remember { mutableStateOf<String?>(null) }
    val used = remember(data.items, sheet.id) { data.itemsOf(sheet.id) }
    val currencies = remember(used) { used.map { it.currency }.distinct() }
    val categories = remember(used, data.categories) {
        val ids = used.mapNotNull { it.categoryId }.toSet()
        data.categories.filter { it.id in ids }.sortedBy { it.name.lowercase() }
    }
    val shown = count(filter)

    KSheet(onDismiss, "Filters") {
        Column(
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
        ) {
            KGroup {
                CheckRow(
                    0, 1, "No filter", selected = !filter.active,
                    onClick = {
                        amountText = ""
                        onChange(Filter())
                    },
                    background = row,
                )
            }
            Gap(12.dp)
            KGroup {
                SwitchRow(0, 2, "Has a picture", filter.withPicture, { onChange(filter.copy(withPicture = it)) }, icon = Ic.image, background = row)
                SwitchRow(1, 2, "Repeats", filter.repeating, { onChange(filter.copy(repeating = it)) }, icon = Ic.repeat, background = row)
            }

            SectionHeader("Type")
            ButtonGroup(
                options = listOf(GroupOption("All"), GroupOption("Expense"), GroupOption("Income")),
                selectedIndex = when (filter.kind) {
                    null -> 0
                    Kind.EXPENSE -> 1
                    Kind.INCOME -> 2
                },
                onSelect = { index -> onChange(filter.copy(kind = listOf(null, Kind.EXPENSE, Kind.INCOME)[index])) },
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            SectionHeader("Amount")
            ButtonGroup(
                options = AmountTest.entries.map { GroupOption(it.label) },
                selectedIndex = filter.amountTest.ordinal,
                onSelect = { index -> onChange(filter.copy(amountTest = AmountTest.entries[index])) },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Gap(8.dp)
            KField(
                amountText,
                { text ->
                    amountText = text
                    val value = text.replace(",", "").toBigDecimalOrNull()
                    onChange(filter.copy(amountMinor = value?.let { Money.toMinor(it, sheet.currency) }))
                },
                "Amount in ${sheet.currency}",
                Modifier.padding(horizontal = 16.dp),
                kind = FieldKind.MONEY,
                prefix = Money.symbol(sheet.currency),
                last = true,
            )

            SectionHeader("Words")
            KField(filter.note, { onChange(filter.copy(note = it)) }, "In the notes or the merchant", Modifier.padding(horizontal = 16.dp), last = true)

            if (currencies.size > 1) {
                SectionHeader("Currency")
                KGroup {
                    SettingRow(0, 1, "Currency", icon = Ic.payments, value = filter.currency ?: "All", background = row, onClick = { pickCurrency = true })
                }
            }

            if (categories.isNotEmpty()) {
                SectionHeader("Category")
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip("No category", filter.noCategory, { onChange(filter.copy(noCategory = !filter.noCategory)) })
                    categories.forEach { category ->
                        val on = category.id in filter.categoryIds
                        FilterChip(
                            category.name,
                            on,
                            { onChange(filter.copy(categoryIds = if (on) filter.categoryIds - category.id else filter.categoryIds + category.id)) },
                        )
                    }
                }
            }

            SectionHeader("Date")
            KGroup {
                SettingRow(0, 2, "From", icon = Ic.calendar_month, value = filter.from?.let { Dates.medium(it) } ?: "Any", background = row, onClick = { picking = "from" })
                SettingRow(1, 2, "To", icon = Ic.calendar_month, value = filter.to?.let { Dates.medium(it) } ?: "Any", background = row, onClick = { picking = "to" })
            }
            if (filter.from != null || filter.to != null) {
                Gap(6.dp)
                Btn("Any date", { onChange(filter.copy(from = null, to = null)) }, modifier = Modifier.padding(horizontal = 16.dp), style = BtnStyle.TEXT, size = BtnSize.SM)
            }
            Gap(16.dp)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Btn(if (shown == 1) "Show 1 item" else "Show $shown items", onDismiss, modifier = Modifier.weight(1f), icon = Ic.check)
        }
    }

    if (pickCurrency) {
        CurrencySheet(
            selected = filter.currency,
            pinned = currencies,
            onPick = { onChange(filter.copy(currency = it)) },
            onDismiss = { pickCurrency = false },
            title = "Currency",
            onPickAll = { onChange(filter.copy(currency = null)) },
        )
    }
    val which = picking
    if (which != null) {
        DateDialog(
            initial = Dates.dateOf((if (which == "from") filter.from else filter.to) ?: now),
            onPick = { date ->
                val millis = Dates.startOfDay(date)
                onChange(if (which == "from") filter.copy(from = millis) else filter.copy(to = millis))
            },
            onDismiss = { picking = null },
        )
    }
}

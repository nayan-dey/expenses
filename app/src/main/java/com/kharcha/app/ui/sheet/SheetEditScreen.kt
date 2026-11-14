package com.kharcha.app.ui.sheet

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kharcha.app.core.model.Sheet
import com.kharcha.app.core.util.Currencies
import com.kharcha.app.core.util.Dates
import com.kharcha.app.core.util.Money
import com.kharcha.app.core.util.newId
import com.kharcha.app.ui.components.AppBar
import com.kharcha.app.ui.components.Btn
import com.kharcha.app.ui.components.BtnStyle
import com.kharcha.app.ui.components.ConfirmDialog
import com.kharcha.app.ui.components.CurrencySheet
import com.kharcha.app.ui.components.FieldKind
import com.kharcha.app.ui.components.Foot
import com.kharcha.app.ui.components.Gap
import com.kharcha.app.ui.components.IconBtn
import com.kharcha.app.ui.components.IconBtnStyle
import com.kharcha.app.ui.components.KGroup
import com.kharcha.app.ui.components.PeriodSheet
import com.kharcha.app.ui.components.PromptDialog
import com.kharcha.app.ui.components.ScreenColumn
import com.kharcha.app.ui.components.SectionHeader
import com.kharcha.app.ui.components.SettingRow
import com.kharcha.app.ui.components.SwitchRow
import com.kharcha.app.ui.components.jsonSaver
import com.kharcha.app.ui.components.rememberHaptics
import com.kharcha.app.ui.components.rememberNow
import com.kharcha.app.ui.icons.Ic
import com.kharcha.app.ui.nav.LocalContainer
import com.kharcha.app.ui.nav.LocalMessenger
import com.kharcha.app.ui.nav.LocalNav
import com.kharcha.app.ui.nav.SheetEditRoute
import com.kharcha.app.ui.nav.SheetRoute
import java.math.BigDecimal

/**
 * New sheet, or the settings of an existing one: its name, its currency, what its big
 * number shows, and which boxes the "new item" form has.
 */
@Composable
fun SheetEditScreen(key: SheetEditRoute) {
    val app = LocalContainer.current
    val nav = LocalNav.current
    val messenger = LocalMessenger.current
    val haptics = rememberHaptics()
    val scheme = MaterialTheme.colorScheme
    val data by app.ledger.data.collectAsStateWithLifecycle()
    val settings by app.settings.settings.collectAsStateWithLifecycle()
    val now = rememberNow()
    val existing = remember(key.sheetId) { app.ledger.current.sheet(key.sheetId) }
    val isNew = existing == null

    var draft by rememberSaveable(stateSaver = jsonSaver(Sheet.serializer())) {
        mutableStateOf(
            existing ?: Sheet(
                id = newId(),
                name = "",
                currency = settings.preferredCurrency.ifBlank { Currencies.ofPhone() },
            ),
        )
    }
    var pickCurrency by remember { mutableStateOf(false) }
    var pickPeriod by remember { mutableStateOf(false) }
    var askBudget by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun save() {
        val named = draft.copy(name = draft.name.trim().ifBlank { "Untitled sheet" })
        if (existing != null && existing.currency != named.currency) {
            // Items in the sheet were priced for the old currency: re-price them, or refuse.
            val ok = app.ledger.changeCurrency(named.id, named.currency) { from -> app.rates.rate(from, named.currency) }
            if (!ok) {
                messenger.show("No exchange rate to ${named.currency} yet for some items. Set one under Exchange rates first.")
                return
            }
        }
        app.ledger.saveSheet(named)
        haptics.confirm()
        if (isNew) nav.replaceTop(SheetRoute(named.id)) else nav.back()
    }

    ScreenColumn {
        AppBar(
            onBack = { nav.back() },
            title = if (isNew) "New sheet" else "Edit sheet",
            actions = { IconBtn(Ic.check, "Save", { save() }, style = IconBtnStyle.FILLED) },
        )
        Gap(8.dp)

        // The name, large and centred, like a title being written.
        TextField(
            value = draft.name,
            onValueChange = { draft = draft.copy(name = it) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            textStyle = MaterialTheme.typography.headlineSmall.copy(textAlign = TextAlign.Center),
            placeholder = {
                Text("Untitled sheet", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = scheme.surfaceContainer,
                unfocusedContainerColor = scheme.surfaceContainer,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            ),
        )
        Gap(14.dp)

        KGroup {
            SettingRow(
                0, 1, draft.currency,
                icon = Ic.payments,
                iconColor = Color(0xFFF59B42),
                value = Currencies.name(draft.currency),
                onClick = { pickCurrency = true },
            )
        }

        SectionHeader("Totals")
        KGroup {
            SwitchRow(
                0, 3, "Total balance", draft.showTotalBalance, { draft = draft.copy(showTotalBalance = it) },
                subtitle = if (draft.showTotalBalance) "The big number is income minus expense" else "The big number is what was spent",
            )
            SettingRow(1, 3, "Total period", value = Dates.label(draft.period, draft.customFrom, draft.customTo), onClick = { pickPeriod = true })
            SettingRow(
                2, 3, "Monthly budget",
                value = if (draft.monthlyBudgetMinor > 0) Money.format(draft.monthlyBudgetMinor, draft.currency) else "None",
                onClick = { askBudget = true },
            )
        }

        SectionHeader("Input")
        KGroup {
            SwitchRow(0, 4, "Show currency selection", draft.showCurrency, { draft = draft.copy(showCurrency = it) })
            SwitchRow(1, 4, "Show merchant", draft.showMerchant, { draft = draft.copy(showMerchant = it) })
            SwitchRow(2, 4, "Show time", draft.showTime, { draft = draft.copy(showTime = it) })
            SwitchRow(3, 4, "Show category suggestions", draft.showSuggestions, { draft = draft.copy(showSuggestions = it) })
        }
        Foot("These choose which boxes the new item form shows for this sheet.")

        if (!isNew) {
            Gap(8.dp)
            Btn(
                "Delete sheet",
                { confirmDelete = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                icon = Ic.delete,
                style = BtnStyle.DANGER,
            )
            val made = existing?.createdAt ?: 0L
            if (made > 0L) Foot("Created: ${Dates.full(made)}")
        }
    }

    if (pickCurrency) {
        val used = remember(data.items, draft.id) { data.items.filter { it.sheetId == draft.id }.map { it.currency }.distinct() }
        CurrencySheet(
            selected = draft.currency,
            pinned = listOf(draft.currency) + used + listOfNotNull(settings.preferredCurrency.takeIf { it.isNotBlank() }),
            onPick = { draft = draft.copy(currency = it) },
            onDismiss = { pickCurrency = false },
            title = "Sheet currency",
        )
    }
    if (pickPeriod) {
        PeriodSheet(
            kind = draft.period,
            from = draft.customFrom,
            to = draft.customTo,
            now = now,
            onChange = { kind, from, to -> draft = draft.copy(period = kind, customFrom = from, customTo = to) },
            onDismiss = { pickPeriod = false },
        )
    }
    if (askBudget) {
        PromptDialog(
            title = "Monthly budget",
            label = "Amount",
            initial = if (draft.monthlyBudgetMinor > 0) Money.plain(draft.monthlyBudgetMinor, draft.currency) else "",
            text = "How much you plan to spend from this sheet each month. Leave it empty for no budget.",
            kind = FieldKind.MONEY,
            prefix = Money.symbol(draft.currency),
            onConfirm = { typed ->
                val value = typed.replace(",", "").toBigDecimalOrNull() ?: BigDecimal.ZERO
                draft = draft.copy(monthlyBudgetMinor = Money.toMinor(value.max(BigDecimal.ZERO), draft.currency))
            },
            onDismiss = { askBudget = false },
        )
    }
    if (confirmDelete && existing != null) {
        ConfirmDialog(
            title = "Delete \"${existing.name}\"?",
            text = "The sheet and its items move to the trash. You can bring them back from Settings, Trash, for 30 days.",
            confirm = "Delete",
            danger = true,
            onConfirm = {
                app.ledger.deleteSheet(existing.id)
                haptics.reject()
                nav.leaveSheet(existing.id)
                messenger.show("Moved to trash", "Undo") { app.ledger.restoreSheet(existing.id) }
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.kharcha.app.ui.rates

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kharcha.app.core.util.Currencies
import com.kharcha.app.core.util.CurrencyInfo
import com.kharcha.app.core.util.Dates
import com.kharcha.app.core.util.Money
import com.kharcha.app.ui.components.AppBar
import com.kharcha.app.ui.components.EmptyState
import com.kharcha.app.ui.components.FieldKind
import com.kharcha.app.ui.components.Gap
import com.kharcha.app.ui.components.GroupItem
import com.kharcha.app.ui.components.IconBtn
import com.kharcha.app.ui.components.KField
import com.kharcha.app.ui.components.KGroup
import com.kharcha.app.ui.components.PromptDialog
import com.kharcha.app.ui.components.SearchField
import com.kharcha.app.ui.components.SwitchRow
import com.kharcha.app.ui.components.Tag
import com.kharcha.app.ui.components.TagKind
import com.kharcha.app.ui.components.rememberNow
import com.kharcha.app.ui.icons.Ic
import com.kharcha.app.ui.nav.LocalContainer
import com.kharcha.app.ui.nav.LocalMessenger
import com.kharcha.app.ui.nav.LocalNav
import com.kharcha.app.ui.nav.RatesRoute
import com.kharcha.app.ui.theme.KharchaType
import kotlinx.coroutines.launch
import java.math.BigDecimal

/**
 * What every currency is worth in the sheet's own. Type an amount to convert it. Tap a
 * currency to set its rate by hand; your rate then wins over the downloaded one.
 */
@Composable
fun RatesScreen(key: RatesRoute) {
    val app = LocalContainer.current
    val nav = LocalNav.current
    val messenger = LocalMessenger.current
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme
    val rates by app.rates.rates.collectAsStateWithLifecycle()
    val busy by app.rates.busy.collectAsStateWithLifecycle()
    val settings by app.settings.settings.collectAsStateWithLifecycle()
    val now = rememberNow()
    val base = key.base

    var amountText by rememberSaveable { mutableStateOf("1") }
    var query by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<CurrencyInfo?>(null) }
    val amount = amountText.replace(",", "").toBigDecimalOrNull() ?: BigDecimal.ONE
    val shown = remember(query, base) { Currencies.all.filter { it.code != base && it.matches(query) } }

    fun refresh() {
        scope.launch {
            val ok = app.rates.refresh()
            messenger.show(if (ok) "Rates updated" else "Could not download rates. Check the internet connection.")
        }
    }
    // First visit with no rates at all: fetch them without being asked.
    LaunchedEffect(Unit) { if (rates.perBase.isEmpty() && settings.autoRates) app.rates.refresh() }

    Column(
        Modifier
            .fillMaxSize()
            .background(scheme.surface)
            .statusBarsPadding(),
    ) {
        AppBar(
            onBack = { nav.back() },
            title = "Exchange rates",
            subtitle = if (rates.fetchedAt == 0L) "Not downloaded yet" else "Updated ${Dates.ago(rates.fetchedAt, now)}",
            actions = {
                if (busy) {
                    LoadingIndicator(Modifier.padding(12.dp).size(24.dp))
                } else {
                    IconBtn(Ic.refresh, "Download the latest rates", { refresh() })
                }
            },
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            item(key = "amount") {
                KField(amountText, { amountText = it }, "Amount", kind = FieldKind.MONEY, supporting = "Shown below in ${Currencies.name(base)} ($base).", last = true)
                Gap(8.dp)
                SearchField(query, { query = it }, "Search currencies")
                Gap(8.dp)
                KGroup(horizontal = 0.dp) {
                    SwitchRow(
                        0, 1, "Update by itself", settings.autoRates, { on -> app.settings.update { it.copy(autoRates = on) } },
                        icon = Ic.sync,
                        subtitle = "Downloads rates about twice a day. This is the only thing Kharcha uses the internet for.",
                    )
                }
                Gap(10.dp)
            }
            if (shown.isEmpty()) {
                item(key = "none") { EmptyState("No such currency", "Nothing matches \"${query.trim()}\".", Ic.search_off) }
            }
            itemsIndexed(shown, key = { _, info -> info.code }) { index, info ->
                val rate = app.rates.rate(info.code, base, rates)
                val custom = app.rates.isCustom(info.code, base, rates)
                GroupItem(index, shown.size, onClick = { editing = info }) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("${amount.stripTrailingZeros().toPlainString()} ${info.code}", style = MaterialTheme.typography.titleMedium, maxLines = 1)
                                if (custom) Tag("Your rate", TagKind.WARN)
                            }
                            Text("${info.name} · ${info.symbol}", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                if (rate == null) "No rate" else Money.format(Money.toMinor(amount.multiply(BigDecimal.valueOf(rate)), base), base),
                                style = KharchaType.amount,
                                color = if (rate == null) scheme.onSurfaceVariant else scheme.onSurface,
                                maxLines = 1,
                            )
                            if (rate != null) Text(Money.rateText(rate), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant, maxLines = 1)
                        }
                    }
                }
            }
            item(key = "credit") {
                Text(
                    if (rates.source.isBlank()) "Rates come from a free public data set and are for guidance only." else "Rates from ${rates.source}. For guidance only: your bank's rate will differ.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 12.dp),
                )
            }
        }
    }

    val target = editing
    if (target != null) {
        val current = app.rates.rate(target.code, base, rates)
        PromptDialog(
            title = "1 ${target.code} in $base",
            label = "Rate",
            initial = if (app.rates.isCustom(target.code, base, rates) && current != null) Money.rateText(current) else "",
            text = "Type your own rate for ${target.name}. Leave it empty to go back to the downloaded rate" + (if (current != null) " (${Money.rateText(current)})." else "."),
            kind = FieldKind.MONEY,
            onConfirm = { typed ->
                val value = typed.replace(",", "").toDoubleOrNull()
                app.rates.setCustom(target.code, base, value)
                messenger.show(if (value == null || value <= 0.0) "Using the downloaded rate" else "Your rate for ${target.code} is saved")
            },
            onDismiss = { editing = null },
        )
    }
}

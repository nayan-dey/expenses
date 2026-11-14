@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.kharcha.app.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kharcha.app.core.model.Kind
import com.kharcha.app.core.model.PeriodKind
import com.kharcha.app.core.util.Dates
import com.kharcha.app.domain.isReal
import com.kharcha.app.domain.own
import com.kharcha.app.domain.settled
import com.kharcha.app.domain.slices
import com.kharcha.app.domain.titleOf
import com.kharcha.app.ui.components.AppBar
import com.kharcha.app.ui.components.Btn
import com.kharcha.app.ui.components.BtnSize
import com.kharcha.app.ui.components.BtnStyle
import com.kharcha.app.ui.components.ButtonGroup
import com.kharcha.app.ui.components.CategoryDot
import com.kharcha.app.ui.components.EmptyState
import com.kharcha.app.ui.components.Eyebrow
import com.kharcha.app.ui.components.Gap
import com.kharcha.app.ui.components.GroupItem
import com.kharcha.app.ui.components.GroupOption
import com.kharcha.app.ui.components.IconBtn
import com.kharcha.app.ui.components.KGroup
import com.kharcha.app.ui.components.PeriodSheet
import com.kharcha.app.ui.components.RingChart
import com.kharcha.app.ui.components.RingPart
import com.kharcha.app.ui.components.RollingText
import com.kharcha.app.ui.components.ScreenColumn
import com.kharcha.app.ui.components.SectionHeader
import com.kharcha.app.ui.components.StatTile
import com.kharcha.app.ui.components.money
import com.kharcha.app.ui.components.rememberNow
import com.kharcha.app.ui.icons.Ic
import com.kharcha.app.ui.nav.LocalContainer
import com.kharcha.app.ui.nav.LocalNav
import com.kharcha.app.ui.nav.StatsRoute
import com.kharcha.app.ui.nav.TrendsRoute
import com.kharcha.app.ui.theme.KharchaType

private val NoCategoryColor = Color(0xFF8E8E93)
private const val DAY_MS = 24L * 60 * 60 * 1000

/** Where the money went (or came from): a ring split by category, and the list behind it. */
@Composable
fun StatsScreen(key: StatsRoute) {
    val app = LocalContainer.current
    val nav = LocalNav.current
    val scheme = MaterialTheme.colorScheme
    val data by app.ledger.data.collectAsStateWithLifecycle()
    val now = rememberNow()
    val sheet = data.sheet(key.sheetId)
    if (sheet == null) {
        LaunchedEffect(Unit) { nav.back() }
        return
    }

    var kindIndex by rememberSaveable { mutableStateOf(0) }
    var period by rememberSaveable { mutableStateOf(PeriodKind.THIS_MONTH) }
    var from by rememberSaveable { mutableStateOf<Long?>(null) }
    var to by rememberSaveable { mutableStateOf<Long?>(null) }
    var periodOpen by remember { mutableStateOf(false) }
    val kind = if (kindIndex == 0) Kind.EXPENSE else Kind.INCOME

    val range = remember(period, from, to, now) { Dates.range(period, now, from, to) }
    val items = remember(data, sheet.id, range) { data.settled(sheet, range) }
    val parts = remember(items, kind, data.categories) { slices(items, sheet, kind, data) }
    val relevant = remember(items, kind) { items.filter { it.kind == kind && it.isReal } }
    val total = parts.sumOf { it.amount }
    val biggest = remember(relevant) { relevant.maxByOrNull { it.own(sheet) } }
    // How many days the period has had so far, for the daily average.
    val days = remember(relevant, range, now) {
        val start = maxOf(range.from, relevant.minOfOrNull { it.at } ?: now)
        val end = minOf(range.to, Dates.startOfDay(Dates.dateOf(now).plusDays(1)))
        ((end - Dates.startOfDay(start)) / DAY_MS).coerceAtLeast(1L)
    }
    // "This month" also says how last month went.
    val before = remember(data, sheet.id, period, kind, now) {
        if (period != PeriodKind.THIS_MONTH) null else {
            data.settled(sheet, Dates.range(PeriodKind.LAST_MONTH, now)).filter { it.kind == kind && it.isReal }.sumOf { it.own(sheet) }
        }
    }

    ScreenColumn {
        AppBar(
            onBack = { nav.back() },
            title = "Stats",
            subtitle = sheet.name,
            actions = { IconBtn(Ic.show_chart, "Trends", { nav.go(TrendsRoute(sheet.id)) }) },
        )
        ButtonGroup(
            options = listOf(GroupOption("Expense", Ic.arrow_upward), GroupOption("Income", Ic.arrow_downward)),
            selectedIndex = kindIndex,
            onSelect = { kindIndex = it },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
            Btn(Dates.label(period, from, to), { periodOpen = true }, icon = Ic.calendar_month, trailing = Ic.expand_more, style = BtnStyle.TONAL, size = BtnSize.SM)
        }

        if (parts.isEmpty()) {
            EmptyState(
                title = if (kind == Kind.EXPENSE) "No spending here" else "No income here",
                text = "Nothing was recorded in this period. Try another period above.",
                icon = Ic.donut_small,
            )
        } else {
            RingChart(
                parts = parts.map { RingPart(it.category?.let { category -> Color(category.color) } ?: NoCategoryColor, it.share) },
                modifier = Modifier
                    .padding(horizontal = 56.dp, vertical = 12.dp)
                    .fillMaxWidth(),
                description = "Ring chart of ${parts.size} categories",
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Eyebrow(if (kind == Kind.EXPENSE) "Spent" else "Received")
                    RollingText(money(total, sheet.currency), KharchaType.figure.copy(fontSize = KharchaType.figure.fontSize * if (money(total, sheet.currency).length > 11) 0.72f else 1f))
                    Text(if (relevant.size == 1) "1 item" else "${relevant.size} items", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
            }

            Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("A day", money(total / days, sheet.currency), Modifier.weight(1f), icon = Ic.today)
                StatTile(
                    "Largest",
                    if (biggest == null) "" else money(biggest.own(sheet), sheet.currency),
                    Modifier.weight(1f),
                    icon = Ic.arrow_outward,
                )
            }
            if (biggest != null) {
                Text(
                    "Largest: ${data.titleOf(biggest)}${if (biggest.note.isNotBlank()) ", " + biggest.note else ""} on ${Dates.dayMonth(biggest.at)}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 2.dp),
                )
            }
            if (before != null && before > 0L) {
                val change = Math.round((total - before) * 100.0 / before)
                Text(
                    when {
                        change > 0 -> "$change% more than last month (${money(before, sheet.currency)})."
                        change < 0 -> "${-change}% less than last month (${money(before, sheet.currency)})."
                        else -> "The same as last month."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 2.dp),
                )
            }

            SectionHeader("By category")
            KGroup {
                parts.forEachIndexed { index, slice ->
                    val color = slice.category?.let { Color(it.color) } ?: NoCategoryColor
                    GroupItem(index, parts.size) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CategoryDot(slice.category, size = 40.dp)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(slice.category?.name ?: "No category", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    Text(money(slice.amount, sheet.currency), style = KharchaType.amount, maxLines = 1)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    LinearProgressIndicator(
                                        progress = { slice.share },
                                        modifier = Modifier
                                            .weight(1f)
                                            .clearAndSetSemantics { },
                                        color = color,
                                        trackColor = scheme.surfaceContainerHighest,
                                        drawStopIndicator = {},
                                    )
                                    Text(
                                        "${Math.round(slice.share * 100)}% · ${slice.count}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = scheme.onSurfaceVariant,
                                        maxLines = 1,
                                        modifier = Modifier.width(64.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        Gap(8.dp)
    }

    if (periodOpen) {
        PeriodSheet(
            kind = period,
            from = from,
            to = to,
            now = now,
            onChange = { chosen, start, end ->
                period = chosen
                from = start
                to = end
            },
            onDismiss = { periodOpen = false },
        )
    }
}

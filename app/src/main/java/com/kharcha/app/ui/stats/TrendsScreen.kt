package com.kharcha.app.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kharcha.app.core.util.Dates
import com.kharcha.app.domain.Bucket
import com.kharcha.app.domain.inSheet
import com.kharcha.app.domain.trend
import com.kharcha.app.ui.components.AmountRow
import com.kharcha.app.ui.components.AppBar
import com.kharcha.app.ui.components.Bar
import com.kharcha.app.ui.components.BarChart
import com.kharcha.app.ui.components.ButtonGroup
import com.kharcha.app.ui.components.EmptyState
import com.kharcha.app.ui.components.Eyebrow
import com.kharcha.app.ui.components.Gap
import com.kharcha.app.ui.components.GroupOption
import com.kharcha.app.ui.components.IconBtn
import com.kharcha.app.ui.components.KCard
import com.kharcha.app.ui.components.LineChart
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
import com.kharcha.app.ui.theme.KharchaTheme
import com.kharcha.app.core.model.Kind

/** How spending and income move over time: bars per day, week, month or year, and the running balance. */
@Composable
fun TrendsScreen(key: TrendsRoute) {
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

    var bucketIndex by rememberSaveable { mutableStateOf(Bucket.MONTH.ordinal) }
    var series by rememberSaveable { mutableStateOf(0) }
    val bucket = Bucket.entries[bucketIndex]
    // Everything settled up to now; pending items are not money that has moved yet.
    val items = remember(data, sheet.id, now) { data.itemsOf(sheet.id).filter { !it.pending && it.at <= now } }
    val points = remember(items, bucket, now) { trend(items, sheet, bucket, now) }
    var selected by rememberSaveable(bucketIndex) { mutableStateOf(bucket.count - 1) }
    val point = points.getOrNull(selected) ?: points.last()
    val values = points.map { if (series == 0) it.expense else it.income }
    val active = values.filter { it > 0 }

    // The balance at the end of each bar, counting everything that came before the first bar too.
    val balances = remember(items, points) {
        var running = items.filter { it.at < points.first().start }.sumOf { if (it.kind == Kind.EXPENSE) -it.inSheet(sheet) else it.inSheet(sheet) }
        points.map { step ->
            running += step.net
            running
        }
    }

    ScreenColumn {
        AppBar(
            onBack = { nav.back() },
            title = "Trends",
            subtitle = sheet.name,
            actions = { IconBtn(Ic.pie_chart, "Stats", { nav.go(StatsRoute(sheet.id)) }) },
        )
        ButtonGroup(
            options = Bucket.entries.map { GroupOption(it.label) },
            selectedIndex = bucketIndex,
            onSelect = { bucketIndex = it },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        Gap(4.dp)
        ButtonGroup(
            options = listOf(GroupOption("Expense", Ic.arrow_upward), GroupOption("Income", Ic.arrow_downward)),
            selectedIndex = series,
            onSelect = { series = it },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )

        if (items.isEmpty()) {
            EmptyState("Nothing to chart yet", "Add a few items and their ups and downs will show here.", Ic.show_chart)
        } else {
            KCard(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Eyebrow(if (series == 0) "Spent per ${bucket.label.lowercase().dropLast(1)}" else "Received per ${bucket.label.lowercase().dropLast(1)}")
                Gap(12.dp)
                BarChart(
                    bars = points.mapIndexed { index, step -> Bar(step.label, values[index]) },
                    selected = selected,
                    onSelect = { selected = it },
                    description = "Bar chart of the last ${points.size} ${bucket.label.lowercase()}",
                    barColor = if (series == 0) KharchaTheme.extras.expense else KharchaTheme.extras.income,
                )
            }

            KCard(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Eyebrow(
                    when (bucket) {
                        Bucket.DAY -> Dates.medium(point.start)
                        Bucket.WEEK -> "Week of ${Dates.medium(point.start)}"
                        Bucket.MONTH -> Dates.monthYear(point.start)
                        Bucket.YEAR -> Dates.dateOf(point.start).year.toString()
                    },
                )
                Gap(6.dp)
                AmountRow("Expense", money(-point.expense, sheet.currency), color = KharchaTheme.extras.expense)
                AmountRow("Income", money(point.income, sheet.currency), color = KharchaTheme.extras.income)
                AmountRow("Difference", money(point.net, sheet.currency), strong = true)
            }

            Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Average", money(if (active.isEmpty()) 0L else active.sum() / active.size, sheet.currency), Modifier.weight(1f), icon = Ic.speed)
                StatTile("Highest", money(values.maxOrNull() ?: 0L, sheet.currency), Modifier.weight(1f), icon = Ic.trending_up)
            }
            Text(
                "The average counts only the ${bucket.label.lowercase()} that had something in them.",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 2.dp),
            )

            SectionHeader("Balance over time")
            KCard(Modifier.padding(horizontal = 16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("From " + money(balances.first(), sheet.currency), style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    Text(money(balances.last(), sheet.currency), style = MaterialTheme.typography.labelLarge, color = scheme.primary)
                }
                Gap(6.dp)
                LineChart(balances, description = "Line chart of the balance over the last ${points.size} ${bucket.label.lowercase()}")
            }
        }
        Gap(8.dp)
    }
}

package com.kharcha.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kharcha.app.core.model.PayMethod
import com.kharcha.app.core.util.Dates
import com.kharcha.app.domain.Dashboard
import com.kharcha.app.domain.MASCOT_NAME
import com.kharcha.app.domain.Mood
import com.kharcha.app.ui.components.Bar
import com.kharcha.app.ui.components.BarChart
import com.kharcha.app.ui.components.Eyebrow
import com.kharcha.app.ui.components.Gap
import com.kharcha.app.ui.components.IconBtn
import com.kharcha.app.ui.components.IconBtnStyle
import com.kharcha.app.ui.components.KCard
import com.kharcha.app.ui.components.RingChart
import com.kharcha.app.ui.components.RingPart
import com.kharcha.app.ui.components.Tag
import com.kharcha.app.ui.components.TagKind
import com.kharcha.app.ui.components.money
import com.kharcha.app.ui.habit.Mascot
import com.kharcha.app.ui.icons.Ic
import com.kharcha.app.ui.theme.KharchaType
import kotlin.math.roundToInt

/*
 * The cards of the home dashboard. Each one answers a single question with a picture:
 *   GulluCard      has today been logged?
 *   WeekCard       what did each of the last seven days cost?
 *   CategoriesCard where did this month's money go?
 *   MonthCard      is this month running above or below the last one?
 *   MethodsCard    how was it paid (UPI, cash, card)?
 * The sums come from domain/Dashboard.kt; the charts are ui/components/Charts.kt.
 */

/** Gullu and what it has to say. Tapping it opens the habit screen; the + adds today's first item. */
@Composable
fun GulluCard(mood: Mood, line: String, logged: Boolean, onOpen: () -> Unit, onAdd: () -> Unit, modifier: Modifier = Modifier) {
    KCard(modifier.fillMaxWidth(), onClick = onOpen, padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Mascot(mood, size = 64.dp)
            Column(Modifier.weight(1f)) {
                Eyebrow(MASCOT_NAME)
                Text(line, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            if (!logged) IconBtn(Ic.add, "Add today's spending", onAdd, style = IconBtnStyle.FILLED)
        }
    }
}

/** The last seven days as bars. Tapping a bar shows that day's amount at the top right. */
@Composable
fun WeekCard(dash: Dashboard, currency: String, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val picked = dash.week.getOrNull(selected) ?: dash.week.last()
    val index = dash.week.indexOf(picked)
    KCard(modifier.fillMaxWidth(), padding = 16.dp) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Eyebrow("Last 7 days")
                Text(money(dash.weekTotal, currency), style = MaterialTheme.typography.headlineSmall, maxLines = 1)
            }
            Column(horizontalAlignment = Alignment.End) {
                Eyebrow(
                    when (index) {
                        dash.week.lastIndex -> "Today"
                        dash.week.lastIndex - 1 -> "Yesterday"
                        else -> Dates.dayMonth(picked.start)
                    },
                )
                Text(money(picked.amount, currency), style = KharchaType.amount, maxLines = 1)
            }
        }
        Gap(12.dp)
        BarChart(
            bars = dash.week.map { Bar(it.label, it.amount) },
            selected = index,
            onSelect = onSelect,
            height = 116.dp,
            description = "Spending on each of the last 7 days",
        )
        Gap(8.dp)
        Text(
            "About ${money(dash.weekTotal / 7, currency)} a day.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** This month's spending as a ring, with the largest categories named beside it. */
@Composable
fun CategoriesCard(dash: Dashboard, currency: String, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    KCard(modifier.fillMaxWidth(), onClick = onOpen, padding = 16.dp) {
        Eyebrow("Where it went this month")
        Gap(12.dp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            RingChart(
                parts = dash.categories.map { RingPart(Color(it.color), it.share) },
                modifier = Modifier.size(124.dp),
                thickness = 18.dp,
                description = "Ring chart of this month's spending by category",
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(dash.count.toString(), style = MaterialTheme.typography.titleLarge)
                    Text(if (dash.count == 1) "expense" else "expenses", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                dash.categories.forEach { part ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color(part.color))
                                .clearAndSetSemantics { },
                        )
                        Text(part.name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text("${(part.share * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                    }
                }
            }
        }
        val first = dash.categories.firstOrNull()
        if (first != null) {
            Gap(10.dp)
            Text(
                "Most on ${first.name}: ${money(first.amount, currency)}.",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

/** This month against the same point of last month, and where it is heading. */
@Composable
fun MonthCard(dash: Dashboard, currency: String, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    KCard(modifier.fillMaxWidth(), padding = 16.dp) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f)) {
                Eyebrow("This month so far")
                Text(money(dash.month, currency), style = MaterialTheme.typography.titleLarge, maxLines = 1)
            }
            Column(Modifier.weight(1f)) {
                Eyebrow("By now last month")
                Text(money(dash.lastMonthByNow, currency), style = MaterialTheme.typography.titleLarge, color = scheme.onSurfaceVariant, maxLines = 1)
            }
        }
        if (dash.lastMonthByNow > 0L && dash.month > 0L) {
            val change = ((dash.month - dash.lastMonthByNow) * 100 / dash.lastMonthByNow).toInt()
            Gap(10.dp)
            when {
                change <= -3 -> Tag("${-change}% less than last month by this day", TagKind.GOOD, icon = Ic.arrow_downward)
                change >= 3 -> Tag("$change% more than last month by this day", TagKind.WARN, icon = Ic.arrow_upward)
                else -> Tag("About the same as last month", TagKind.NEUTRAL)
            }
        }
        Gap(10.dp)
        Text(
            listOfNotNull(
                if (dash.projected > 0L) "At this pace the month ends near ${money(dash.projected, currency)}." else null,
                if (dash.lastMonth > 0L) "Last month came to ${money(dash.lastMonth, currency)}." else null,
                dash.biggest?.let { (what, amount) -> "Largest so far: $what, ${money(amount, currency)}." },
            ).joinToString(" "),
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )
    }
}

/** How this month's spending was paid, as one bar cut into parts. */
@Composable
fun MethodsCard(dash: Dashboard, currency: String, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    fun colorOf(method: PayMethod): Color = when (method) {
        PayMethod.UPI -> scheme.primary
        PayMethod.CASH -> Color(0xFF2FA866)
        PayMethod.CARD -> Color(0xFFF59B42)
        PayMethod.BANK -> Color(0xFF3F8EF5)
        PayMethod.NONE -> scheme.outlineVariant
    }
    KCard(modifier.fillMaxWidth(), padding = 16.dp) {
        Eyebrow("How you paid this month")
        Gap(12.dp)
        Row(
            Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(CircleShape)
                .clearAndSetSemantics { },
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            dash.methods.forEach { part ->
                Box(
                    Modifier
                        .weight(part.share.coerceAtLeast(0.02f))
                        .fillMaxHeight()
                        .background(colorOf(part.method)),
                )
            }
        }
        Gap(12.dp)
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            dash.methods.forEach { part ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(colorOf(part.method))
                            .clearAndSetSemantics { },
                    )
                    Text(
                        if (part.method == PayMethod.NONE) "Not set" else part.method.label,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    Text(money(part.amount, currency), style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                    Text("${(part.share * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.kharcha.app.ui.habit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kharcha.app.core.model.Goal
import com.kharcha.app.core.model.GoalKind
import com.kharcha.app.core.util.Currencies
import com.kharcha.app.core.util.Dates
import com.kharcha.app.core.util.Money
import com.kharcha.app.core.util.newId
import com.kharcha.app.data.between
import com.kharcha.app.domain.report
import com.kharcha.app.domain.GEMS_PER_DAY
import com.kharcha.app.domain.GEMS_PER_REPAIR
import com.kharcha.app.domain.GEMS_PER_WEEK
import com.kharcha.app.domain.MAX_REPAIR_DAYS
import com.kharcha.app.domain.daysToNextStage
import com.kharcha.app.domain.gems
import com.kharcha.app.domain.mascotLine
import com.kharcha.app.domain.moodOf
import com.kharcha.app.domain.repairOffer
import com.kharcha.app.domain.streak
import com.kharcha.app.domain.treeName
import com.kharcha.app.domain.treeStage
import com.kharcha.app.ui.components.CardKind
import com.kharcha.app.ui.components.IconTile
import androidx.compose.ui.graphics.Color
import com.kharcha.app.ui.components.AppBar
import com.kharcha.app.ui.components.Btn
import com.kharcha.app.ui.components.BtnSize
import com.kharcha.app.ui.components.BtnStyle
import com.kharcha.app.ui.components.ButtonGroup
import com.kharcha.app.ui.components.ConfirmDialog
import com.kharcha.app.ui.components.Eyebrow
import com.kharcha.app.ui.components.FieldKind
import com.kharcha.app.ui.components.Foot
import com.kharcha.app.ui.components.Gap
import com.kharcha.app.ui.components.GroupOption
import com.kharcha.app.ui.components.HIDDEN_AMOUNT
import com.kharcha.app.ui.components.IconBtn
import com.kharcha.app.ui.components.KCard
import com.kharcha.app.ui.components.KField
import com.kharcha.app.ui.components.KIcon
import com.kharcha.app.ui.components.PromptDialog
import com.kharcha.app.ui.components.ScreenColumn
import com.kharcha.app.ui.components.SectionHeader
import com.kharcha.app.ui.components.StatTile
import com.kharcha.app.ui.components.Tag
import com.kharcha.app.ui.components.TagKind
import com.kharcha.app.ui.components.rememberHaptics
import com.kharcha.app.ui.components.rememberNow
import com.kharcha.app.ui.icons.Ic
import com.kharcha.app.ui.nav.ItemRoute
import com.kharcha.app.ui.nav.LocalContainer
import com.kharcha.app.ui.nav.LocalMessenger
import com.kharcha.app.ui.nav.LocalNav
import com.kharcha.app.ui.nav.SheetEditRoute
import com.kharcha.app.ui.theme.KharchaTheme
import com.kharcha.app.ui.theme.LocalSensitive
import java.math.BigDecimal

/**
 * The habit: a streak that grows a tree, and goals to put money into. Logging something
 * every day (or ticking "nothing spent") keeps the streak; the tree grows with it.
 */
@Composable
fun HabitScreen() {
    val app = LocalContainer.current
    val nav = LocalNav.current
    val messenger = LocalMessenger.current
    val haptics = rememberHaptics()
    val scheme = MaterialTheme.colorScheme
    val hidden = LocalSensitive.current
    val data by app.ledger.data.collectAsStateWithLifecycle()
    val settings by app.settings.settings.collectAsStateWithLifecycle()
    val rates by app.rates.rates.collectAsStateWithLifecycle()
    val now = rememberNow()

    val streak = remember(data, now) { data.streak(now) }
    val today = Dates.iso(now)
    val tickedToday = today in data.noSpendDays
    val gems = remember(data) { data.gems() }
    val repair = remember(data, now) { data.repairOffer(now) }
    val mood = moodOf(streak, Dates.dateTimeOf(now).hour, repair)
    val stage = treeStage(streak.current)
    val next = daysToNextStage(streak.current)
    val currency = settings.preferredCurrency.ifBlank { data.liveSheets.firstOrNull()?.currency ?: Currencies.ofPhone() }
    val show: (Long, String) -> String = { amount, code -> if (hidden) HIDDEN_AMOUNT else Money.format(amount, code) }
    // What was left over in the last 30 days: the room there is for a goal.
    val month = remember(data, rates, currency, now) { data.report(30, now, currency, { from -> rates.between(from, currency) }, { "" }) }
    val left = month.earned - month.spent

    /** The goal being edited, or a blank one for "new". Null when the dialog is closed. */
    var editing by remember { mutableStateOf<Goal?>(null) }
    var adding by remember { mutableStateOf<Goal?>(null) }
    var deleting by remember { mutableStateOf<Goal?>(null) }

    ScreenColumn {
        AppBar(onBack = { nav.back() }, title = "Habit and goals")

        KCard(Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth(), padding = 20.dp) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                MascotSays(mood, mascotLine(mood, streak, repair, gems.balance), Modifier.fillMaxWidth())
                Gap(6.dp)
                // Dry leaves until today is logged: it is watered by adding an item.
                StreakTree(streak.current, size = 170.dp, thirsty = !streak.today && streak.current > 0)
                Text(
                    when (streak.current) {
                        0 -> "No streak yet"
                        1 -> "1 day"
                        else -> "${streak.current} days in a row"
                    },
                    style = MaterialTheme.typography.displaySmall,
                    textAlign = TextAlign.Center,
                )
                Gap(4.dp)
                Text(
                    when {
                        streak.current == 0 -> "Log what you spend today and a seed goes in the ground."
                        streak.today && next != null -> "Today is done. $next more ${if (next == 1) "day" else "days"} and it becomes ${treeName(stage + 1)}."
                        streak.today -> "Today is done. Your tree is fully grown. Keep it alive."
                        else -> "Not watered yet today. Log something before midnight to keep the streak."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Gap(16.dp)
                // The last seven days: a filled dot is a day that counted.
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.clearAndSetSemantics { }) {
                    streak.week.forEachIndexed { index, done ->
                        val day = Dates.dateOf(now).minusDays((6 - index).toLong())
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(
                                Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(if (done) KharchaTheme.extras.income else scheme.surfaceContainerHighest),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (done) KIcon(Ic.check, size = 18.dp, tint = scheme.surface)
                            }
                            Text(
                                day.dayOfWeek.getDisplayName(java.time.format.TextStyle.NARROW, java.util.Locale.getDefault()),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (index == 6) scheme.primary else scheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        // A streak that broke in the last few days can be bought back with gems.
        if (repair != null) {
            val enough = gems.balance >= repair.cost
            KCard(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth(), kind = CardKind.WARN, padding = 16.dp) {
                Text("Your ${repair.streakBefore}-day streak broke", style = MaterialTheme.typography.titleMedium)
                Gap(4.dp)
                Text(
                    (if (repair.days.size == 1) "You missed one day. " else "You missed ${repair.days.size} days. ") +
                        (if (enough) "${repair.cost} gems bring it back, and it carries on at ${repair.streakAfter} days." else "Bringing it back costs ${repair.cost} gems and you have ${gems.balance}. Log ${(repair.cost - gems.balance + GEMS_PER_DAY - 1) / GEMS_PER_DAY} more days, or start again from today."),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (enough) {
                    Gap(10.dp)
                    Btn(
                        "Use ${repair.cost} gems",
                        {
                            app.ledger.repairStreak(repair.days, repair.cost)
                            haptics.confirm()
                            messenger.show("Streak is back: ${repair.streakAfter} days")
                        },
                        icon = Ic.diamond,
                        size = BtnSize.SM,
                    )
                }
            }
        }

        Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Best streak", if (streak.best == 1) "1 day" else "${streak.best} days", Modifier.weight(1f), icon = Ic.emoji_events)
            StatTile("Days logged", streak.total.toString(), Modifier.weight(1f), icon = Ic.today)
        }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!streak.today) {
                Btn(
                    "Add today's spending",
                    {
                        val sheet = data.liveSheets.maxByOrNull { it.editedAt }
                        if (sheet == null) nav.go(SheetEditRoute()) else nav.go(ItemRoute(sheet.id))
                    },
                    icon = Ic.add,
                    size = BtnSize.SM,
                )
                Btn(
                    "Nothing spent today",
                    {
                        app.ledger.setNoSpend(today, true)
                        haptics.confirm()
                        messenger.show("Nice. Today counts.")
                    },
                    icon = Ic.spa,
                    style = BtnStyle.TONAL,
                    size = BtnSize.SM,
                )
            } else if (tickedToday) {
                Btn("Undo \"nothing spent\"", { app.ledger.setNoSpend(today, false) }, icon = Ic.undo, style = BtnStyle.TEXT, size = BtnSize.SM)
            }
        }
        KCard(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth(), padding = 14.dp) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconTile(Ic.diamond, Color(0xFF4DB8D1), size = 44.dp)
                Column(Modifier.weight(1f)) {
                    Text(if (gems.balance == 1) "1 gem" else "${gems.balance} gems", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "$GEMS_PER_DAY for every day you log, and $GEMS_PER_WEEK more for every 7 days. $GEMS_PER_REPAIR bring back a missed day.",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }
        Foot("A day counts when you add at least one item on it, or tick \"nothing spent\". Miss a day and the tree starts again from a seed, unless you buy the day back with gems within $MAX_REPAIR_DAYS days. The stages are at 1, 3, 7, 14 and 30 days.")

        SectionHeader("Goals")
        if (left > 0L && !hidden) {
            Text(
                "About ${Money.format(left, currency)} was left over in the last 30 days. Moving a fixed part of it into a goal on the same day every month is how saving stops depending on willpower.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 4.dp),
            )
        }
        Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            data.liveGoals.forEach { goal ->
                val monthStart = Dates.startOfDay(Dates.dateOf(now).withDayOfMonth(1))
                val paidThisMonth = goal.deposits.filter { it.at >= monthStart }.sumOf { it.amountMinor }
                val doneThisMonth = goal.monthlyMinor > 0L && paidThisMonth >= goal.monthlyMinor
                val late = goal.monthlyMinor > 0L && !doneThisMonth && Dates.dateOf(now).dayOfMonth > goal.dayOfMonth
                KCard(Modifier.fillMaxWidth(), padding = 16.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f)) {
                            Eyebrow(goal.kind.label)
                            Text(goal.name, style = MaterialTheme.typography.titleLarge, maxLines = 2)
                        }
                        IconBtn(Ic.edit, "Edit ${goal.name}", { editing = goal })
                        IconBtn(Ic.delete, "Delete ${goal.name}", { deleting = goal })
                    }
                    Gap(8.dp)
                    Text(
                        if (goal.targetMinor > 0L) "${show(goal.saved, goal.currency)} of ${show(goal.targetMinor, goal.currency)}" else "${show(goal.saved, goal.currency)} so far",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (goal.targetMinor > 0L) {
                        Gap(8.dp)
                        LinearWavyProgressIndicator(
                            progress = { (goal.saved.toFloat() / goal.targetMinor).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clearAndSetSemantics { },
                            color = KharchaTheme.extras.income,
                            trackColor = scheme.surfaceContainerHighest,
                        )
                    }
                    if (goal.monthlyMinor > 0L) {
                        Gap(10.dp)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            when {
                                doneThisMonth -> Tag("This month: done", TagKind.GOOD, icon = Ic.check_circle)
                                late -> Tag("This month: not yet", TagKind.WARN, icon = Ic.hourglass_top)
                                else -> Tag("Due on day ${goal.dayOfMonth}", TagKind.NEUTRAL, icon = Ic.event)
                            }
                            Text("${show(goal.monthlyMinor, goal.currency)} a month", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                        }
                        if (goal.targetMinor > goal.saved) {
                            val months = (goal.targetMinor - goal.saved + goal.monthlyMinor - 1) / goal.monthlyMinor
                            Gap(4.dp)
                            Text("At this pace: $months more ${if (months == 1L) "month" else "months"}.", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                        }
                    }
                    Gap(12.dp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (goal.monthlyMinor > 0L && !doneThisMonth) {
                            Btn(
                                "Done for this month",
                                {
                                    app.ledger.deposit(goal.id, goal.monthlyMinor - paidThisMonth)
                                    haptics.confirm()
                                    messenger.show("Added to ${goal.name}")
                                },
                                icon = Ic.check,
                                size = BtnSize.SM,
                            )
                        }
                        Btn("Add money", { adding = goal }, icon = Ic.add, style = BtnStyle.TONAL, size = BtnSize.SM)
                    }
                }
            }
            Btn("New goal", { editing = Goal(id = "", name = "", currency = currency) }, icon = Ic.flag, style = BtnStyle.TONAL, size = BtnSize.SM)
        }
        Foot("A goal only keeps count: it does not move money. When you put money aside or pay into a fund, record it here. A SIP (systematic investment plan) is investing the same amount on the same day every month. Which fund or share suits you is for you or a registered adviser to decide. Kharcha does not give investment advice.")
    }

    val draft = editing
    if (draft != null) {
        GoalDialog(
            goal = draft,
            onSave = { goal -> app.ledger.saveGoal(if (goal.id.isEmpty()) goal.copy(id = newId()) else goal) },
            onDismiss = { editing = null },
        )
    }
    val target = adding
    if (target != null) {
        PromptDialog(
            title = "Add to ${target.name}",
            label = "Amount",
            initial = "",
            text = "Type a minus sign in front to take money back out.",
            kind = FieldKind.TEXT,
            prefix = Money.symbol(target.currency),
            confirm = "Add",
            onConfirm = { typed ->
                val value = typed.replace(",", "").toBigDecimalOrNull()
                if (value != null && value.signum() != 0) {
                    app.ledger.deposit(target.id, Money.toMinor(value, target.currency))
                    haptics.confirm()
                }
            },
            onDismiss = { adding = null },
        )
    }
    val doomed = deleting
    if (doomed != null) {
        ConfirmDialog(
            title = "Delete \"${doomed.name}\"?",
            text = "The goal and its history of additions are removed. It can be brought back for a moment with Undo.",
            confirm = "Delete",
            danger = true,
            onConfirm = {
                app.ledger.deleteGoal(doomed.id)
                messenger.show("Goal deleted", "Undo") { app.ledger.restoreGoal(doomed.id) }
            },
            onDismiss = { deleting = null },
        )
    }
}

/** New goal, or edit one: a name, saving or investing, the target, and the monthly plan. */
@Composable
private fun GoalDialog(goal: Goal, onSave: (Goal) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(goal.name) }
    var kind by remember { mutableStateOf(goal.kind) }
    var target by remember { mutableStateOf(if (goal.targetMinor > 0L) Money.plain(goal.targetMinor, goal.currency) else "") }
    var monthly by remember { mutableStateOf(if (goal.monthlyMinor > 0L) Money.plain(goal.monthlyMinor, goal.currency) else "") }
    var day by remember { mutableStateOf(goal.dayOfMonth.toString()) }
    fun minor(text: String): Long = text.replace(",", "").toBigDecimalOrNull()?.max(BigDecimal.ZERO)?.let { Money.toMinor(it, goal.currency) } ?: 0L

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (goal.id.isEmpty()) "New goal" else "Edit goal") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                KField(name, { name = it }, "What is it for?", kind = FieldKind.NAME, supporting = "Emergency fund, a trip, a monthly SIP")
                ButtonGroup(
                    options = GoalKind.entries.map { GroupOption(it.label) },
                    selectedIndex = kind.ordinal,
                    onSelect = { kind = GoalKind.entries[it] },
                )
                KField(target, { target = it }, "Target (optional)", kind = FieldKind.MONEY, prefix = Money.symbol(goal.currency))
                KField(monthly, { monthly = it }, "Every month (optional)", kind = FieldKind.MONEY, prefix = Money.symbol(goal.currency))
                KField(day, { day = it }, "On which day of the month", kind = FieldKind.QUANTITY, supporting = "1 to 28", last = true)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) {
                    onDismiss()
                    onSave(
                        goal.copy(
                            name = name.trim(),
                            kind = kind,
                            targetMinor = minor(target),
                            monthlyMinor = minor(monthly),
                            dayOfMonth = (day.trim().toIntOrNull() ?: 1).coerceIn(1, 28),
                        ),
                    )
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

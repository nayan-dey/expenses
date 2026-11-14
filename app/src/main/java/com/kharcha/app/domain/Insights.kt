package com.kharcha.app.domain

import com.kharcha.app.core.model.AppData
import com.kharcha.app.core.model.Item
import com.kharcha.app.core.model.Kind
import com.kharcha.app.core.model.PayMethod
import com.kharcha.app.core.model.PeriodKind
import com.kharcha.app.core.util.Dates
import com.kharcha.app.core.util.Money
import com.kharcha.app.core.util.Range
import java.time.DayOfWeek

/*
 * "Where could I save?" Worked out on the phone with plain rules, no AI and no internet:
 * compare this stretch with the one before, find where the money pooled, and say what a
 * small change would be worth. Everything is added up across sheets in one currency.
 */

enum class Tone { GOOD, NEUTRAL, WARN }

/** One finding. `icon` is the name of an icon in ui/icons/Ic.kt. `saving` is what acting on it could keep. */
data class Insight(val icon: String, val title: String, val text: String, val tone: Tone = Tone.NEUTRAL, val saving: Long = 0)

data class Report(
    val currency: String,
    val days: Int,
    val spent: Long,
    val earned: Long,
    val spentBefore: Long,
    val insights: List<Insight>,
    /** What the suggestions add up to for this stretch. */
    val potential: Long,
    /** Largest categories with their amounts. */
    val top: List<Pair<String, Long>>,
    val merchants: List<Pair<String, Long>>,
    val byMethod: List<Pair<String, Long>>,
)

/** An item with its cost to you in the report's currency. */
private data class Spend(val item: Item, val amount: Long, val category: String, val essential: Boolean)

/** Categories that are hard to cut. Everything else is treated as a choice. */
private val essentials = setOf("cat-rent", "cat-bills", "cat-internet", "cat-health", "cat-groceries")

private fun AppData.spends(range: Range, currency: String, rate: (String) -> Double?, kind: Kind): List<Spend> {
    val result = ArrayList<Spend>()
    for (sheet in liveSheets) {
        val factor = rate(sheet.currency) ?: continue
        for (item in items) {
            if (item.sheetId != sheet.id || item.deletedAt != null || item.pending || item.kind != kind || !item.isReal || item.at !in range) continue
            val amount = Money.convert(if (kind == Kind.EXPENSE) item.own(sheet) else item.inSheet(sheet), sheet.currency, currency, factor)
            result += Spend(item, amount, category(item.categoryId)?.name ?: "No category", item.categoryId in essentials)
        }
    }
    return result
}

/**
 * The report for the last `days` days (7 for a week, 30 for a month), compared with the same
 * number of days before.
 *
 * @param rate how many units of `currency` one unit of a sheet's currency is worth, or null
 * @param show turns an amount into text (so Sensitive mode can hide it)
 */
fun AppData.report(days: Int, now: Long, currency: String, rate: (String) -> Double?, show: (Long) -> String): Report {
    val today = Dates.dateOf(now)
    val end = Dates.startOfDay(today.plusDays(1))
    val start = Dates.startOfDay(today.minusDays(days - 1L))
    val before = Dates.startOfDay(today.minusDays(days * 2 - 1L))
    val current = spends(Range(start, end), currency, rate, Kind.EXPENSE)
    val previous = spends(Range(before, start), currency, rate, Kind.EXPENSE)
    val income = spends(Range(start, end), currency, rate, Kind.INCOME)

    val spent = current.sumOf { it.amount }
    val spentBefore = previous.sumOf { it.amount }
    val earned = income.sumOf { it.amount }
    val byCategory = current.groupBy { it.category }.mapValues { (_, list) -> list.sumOf { it.amount } }.entries.sortedByDescending { it.value }
    val beforeByCategory = previous.groupBy { it.category }.mapValues { (_, list) -> list.sumOf { it.amount } }
    val stretch = if (days <= 7) "week" else "month"
    val insights = ArrayList<Insight>()

    fun percent(part: Long, whole: Long): Int = if (whole <= 0L) 0 else Math.round(part * 100.0 / whole).toInt()

    if (current.isEmpty()) {
        insights += Insight("receipt_long", "Nothing logged yet", "Add what you spend for a few days and this page will show where it goes and where you could hold back.")
        return Report(currency, days, 0, earned, spentBefore, insights, 0, emptyList(), emptyList(), emptyList())
    }

    // 1. Up or down on the stretch before.
    if (spentBefore > 0L) {
        val change = percent(spent - spentBefore, spentBefore)
        when {
            change >= 10 -> insights += Insight("trending_up", "Spending is up $change%", "You spent ${show(spent)}, which is ${show(spent - spentBefore)} more than the $days days before.", Tone.WARN)
            change <= -10 -> insights += Insight("trending_down", "Spending is down ${-change}%", "You spent ${show(spent)}, which is ${show(spentBefore - spent)} less than the $days days before. Keep it up.", Tone.GOOD)
        }
    }

    // 2. The biggest thing that is a choice, and what a fifth of it is worth.
    var potential = 0L
    val choices = current.filter { !it.essential }.groupBy { it.category }.mapValues { (_, list) -> list.sumOf { it.amount } }.entries.sortedByDescending { it.value }
    val biggest = choices.firstOrNull()
    if (biggest != null && biggest.value > 0L) {
        val fifth = biggest.value / 5
        potential += fifth
        insights += Insight(
            "donut_small",
            "${biggest.key} took ${percent(biggest.value, spent)}%",
            "${show(biggest.value)} went on ${biggest.key} this $stretch. Cutting it by a fifth keeps ${show(fifth)}.",
            Tone.WARN,
            fifth,
        )
    }

    // 3. The category that grew the most.
    val jump = byCategory
        .map { entry -> entry to (entry.value - (beforeByCategory[entry.key] ?: 0L)) }
        .filter { (entry, growth) -> growth > 0L && entry.key != biggest?.key && growth * 20 >= spent && (beforeByCategory[entry.key] ?: 0L) > 0L }
        .maxByOrNull { it.second }
    if (jump != null) {
        val was = beforeByCategory[jump.first.key] ?: 0L
        insights += Insight("arrow_outward", "${jump.first.key} grew", "${show(jump.first.value)} this $stretch against ${show(was)} before: ${show(jump.second)} more.", Tone.WARN)
    }

    // 4. Small buys that add up.
    val smallLimit = (spent / 60).coerceAtLeast(1L)
    val small = current.filter { !it.essential && it.amount <= smallLimit }
    if (small.size >= 5) {
        val sum = small.sumOf { it.amount }
        val mostly = small.groupBy { it.category }.maxByOrNull { it.value.size }?.key
        val third = sum / 3
        potential += third
        insights += Insight(
            "shopping_basket",
            "${small.size} small buys: ${show(sum)}",
            "Little amounts${if (mostly != null) ", mostly $mostly," else ""} quietly added up. Skipping one in three keeps ${show(third)}.",
            Tone.NEUTRAL,
            third,
        )
    }

    // 5. Weekends.
    val weekend = current.filter { Dates.dateOf(it.item.at).dayOfWeek.let { day -> day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY } }.sumOf { it.amount }
    if (percent(weekend, spent) >= 45 && spent > 0L) {
        insights += Insight("celebration", "Weekends took ${percent(weekend, spent)}%", "${show(weekend)} of this $stretch's spending was on Saturdays and Sundays. Deciding a weekend amount beforehand helps.", Tone.NEUTRAL)
    }

    // 6. The place you paid most often.
    val merchants = current.filter { it.item.merchant.isNotBlank() }.groupBy { it.item.merchant }
    val regular = merchants.maxByOrNull { it.value.size }
    if (regular != null && regular.value.size >= 3) {
        insights += Insight("storefront", "${regular.key}: ${regular.value.size} times", "You paid ${regular.key} ${show(regular.value.sumOf { it.amount })} in ${regular.value.size} visits.", Tone.NEUTRAL)
    }

    // 7. Money friends still owe you.
    val owed = dues().values.sumOf { perCurrency -> (perCurrency[currency] ?: 0L).coerceAtLeast(0L) }
    if (owed > 0L) {
        insights += Insight("group", "Friends owe you ${show(owed)}", "That is your money sitting with friends. Open Friends to remind them or settle up.", Tone.NEUTRAL)
    }

    // 8. How much of what came in was kept (months only: a week is too short to judge).
    if (days >= 28 && earned > 0L) {
        val kept = percent(earned - spent, earned)
        when {
            kept >= 20 -> insights += Insight("savings", "You kept $kept% of your income", "${show(earned - spent)} was left after spending. That is money you can put to work in a goal.", Tone.GOOD)
            kept >= 0 -> insights += Insight("savings", "You kept $kept% of your income", "A common aim is to keep a fifth. That would be ${show(earned / 5 - (earned - spent))} more than you kept.", Tone.WARN)
            else -> insights += Insight("warning", "You spent more than came in", "${show(spent - earned)} more went out than came in over these $days days.", Tone.WARN)
        }
    }

    // 9. Days with nothing spent.
    val spendDays = current.map { Dates.dateOf(it.item.at) }.toSet().size
    val quiet = days - spendDays
    if (quiet >= 2) {
        insights += Insight("spa", "$quiet days with no spending", "Days you spend nothing are the easiest saving there is.", Tone.GOOD)
    }

    val byMethod = current.filter { it.item.method != PayMethod.NONE }.groupBy { it.item.method.label }.mapValues { (_, list) -> list.sumOf { it.amount } }.entries.sortedByDescending { it.value }
    return Report(
        currency = currency,
        days = days,
        spent = spent,
        earned = earned,
        spentBefore = spentBefore,
        insights = insights,
        potential = potential,
        top = byCategory.take(6).map { it.key to it.value },
        merchants = merchants.entries.sortedByDescending { entry -> entry.value.sumOf { it.amount } }.take(5).map { entry -> entry.key to entry.value.sumOf { it.amount } },
        byMethod = byMethod.map { it.key to it.value },
    )
}

/**
 * The report as a message for an AI assistant: the numbers, then the question. The person
 * reads this before it leaves the phone.
 */
fun Report.asPrompt(show: (Long) -> String, goals: List<String>): String = buildString {
    appendLine("I track my spending in an app. Here is a summary of my last $days days, in $currency.")
    appendLine()
    appendLine("Spent: ${show(spent)} (the $days days before: ${show(spentBefore)})")
    if (earned > 0L) appendLine("Income: ${show(earned)}")
    if (top.isNotEmpty()) {
        appendLine()
        appendLine("By category:")
        top.forEach { (name, amount) -> appendLine("- $name: ${show(amount)}") }
    }
    if (merchants.isNotEmpty()) {
        appendLine()
        appendLine("Where I paid most:")
        merchants.forEach { (name, amount) -> appendLine("- $name: ${show(amount)}") }
    }
    if (byMethod.isNotEmpty()) {
        appendLine()
        appendLine("How I paid: " + byMethod.joinToString(", ") { (name, amount) -> "$name ${show(amount)}" })
    }
    if (insights.isNotEmpty()) {
        appendLine()
        appendLine("What my app noticed:")
        insights.forEach { appendLine("- ${it.title}. ${it.text}") }
    }
    if (goals.isNotEmpty()) {
        appendLine()
        appendLine("My goals: " + goals.joinToString("; "))
    }
    appendLine()
    append("I spend most of my money on food and going out with friends and I find it hard to save. Where can I cut back without making life miserable, and what simple habit would help me start saving and investing a fixed amount every month? Please be specific to these numbers.")
}

// ---------------------------------------------------------------------------
// The quick look: for the home screen, the widgets and the daily reminder
// ---------------------------------------------------------------------------

data class QuickLook(
    val currency: String,
    /** What you spent today and this calendar month, across sheets. */
    val today: Long,
    val month: Long,
    /** All sheets' monthly budgets added up. 0 = none set. */
    val budget: Long,
    val streak: Streak,
    /** What friends owe you, and what you owe, in this currency. */
    val owedToYou: Long,
    val youOwe: Long,
)

fun AppData.quickLook(now: Long, currency: String, rate: (String) -> Double?): QuickLook {
    val todayRange = Dates.range(PeriodKind.TODAY, now)
    val monthRange = Dates.range(PeriodKind.THIS_MONTH, now)
    val monthSpends = spends(monthRange, currency, rate, Kind.EXPENSE)
    var budget = 0L
    for (sheet in liveSheets) {
        val factor = rate(sheet.currency) ?: continue
        if (sheet.monthlyBudgetMinor > 0L) budget += Money.convert(sheet.monthlyBudgetMinor, sheet.currency, currency, factor)
    }
    val dues = dues()
    return QuickLook(
        currency = currency,
        today = monthSpends.filter { it.item.at in todayRange }.sumOf { it.amount },
        month = monthSpends.sumOf { it.amount },
        budget = budget,
        streak = streak(now),
        owedToYou = dues.values.sumOf { (it[currency] ?: 0L).coerceAtLeast(0L) },
        youOwe = dues.values.sumOf { (-(it[currency] ?: 0L)).coerceAtLeast(0L) },
    )
}

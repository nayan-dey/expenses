package com.kharcha.app.platform

import com.kharcha.app.core.model.AppData
import com.kharcha.app.core.model.Rates
import com.kharcha.app.core.model.Settings
import com.kharcha.app.core.util.Currencies
import com.kharcha.app.core.util.Dates
import com.kharcha.app.core.util.Money
import com.kharcha.app.data.between
import com.kharcha.app.domain.quickLook
import com.kharcha.app.domain.report
import java.time.DayOfWeek

/** Where a notification or a widget tap should land in the app. MainActivity reads these. */
object Launch {
    const val NEW_ITEM = "com.kharcha.app.NEW_ITEM"
    const val INSIGHTS = "com.kharcha.app.INSIGHTS"
    const val HABIT = "com.kharcha.app.HABIT"
    const val OPEN = "com.kharcha.app.OPEN"
}

/** The words of one notification, and where tapping it goes. */
data class Nudge(val title: String, val text: String, val action: String)

/** The currency totals across sheets are shown in. */
fun preferredCurrency(data: AppData, settings: Settings): String =
    settings.preferredCurrency.ifBlank { data.liveSheets.firstOrNull()?.currency ?: Currencies.ofPhone() }

/**
 * What today's reminder should say. In order: the weekly round-up on Sundays, the monthly one
 * on the 1st, a goal whose monthly payment is due today, a streak that is not safe yet, and
 * otherwise a short summary of the day.
 */
fun dailyNudge(data: AppData, settings: Settings, rates: Rates, now: Long): Nudge {
    val currency = preferredCurrency(data, settings)
    val rate: (String) -> Double? = { from -> rates.between(from, currency) }
    // With Sensitive mode on, amounts stay out of notifications too: they show on the lock screen.
    val show: (Long) -> String = { amount -> if (settings.sensitive) "your spending" else Money.format(amount, currency) }
    val look = data.quickLook(now, currency, rate)
    val today = Dates.dateOf(now)

    if (today.dayOfWeek == DayOfWeek.SUNDAY || today.dayOfMonth == 1) {
        val monthly = today.dayOfMonth == 1
        val report = data.report(if (monthly) 30 else 7, now, currency, rate, show)
        if (report.spent > 0L) {
            val lead = report.insights.firstOrNull { it.saving > 0L } ?: report.insights.firstOrNull()
            return Nudge(
                title = if (settings.sensitive) (if (monthly) "Your month in review" else "Your week in review") else "${if (monthly) "Last 30 days" else "This week"}: ${show(report.spent)} spent",
                text = (lead?.let { "${it.title}. " } ?: "") + "Tap to see where you could save.",
                action = Launch.INSIGHTS,
            )
        }
    }

    val monthStart = Dates.startOfDay(today.withDayOfMonth(1))
    val due = data.liveGoals.firstOrNull { goal ->
        goal.monthlyMinor > 0L && goal.dayOfMonth == today.dayOfMonth && goal.deposits.filter { it.at >= monthStart }.sumOf { it.amountMinor } < goal.monthlyMinor
    }
    if (due != null) {
        return Nudge(
            title = "Today: ${due.name}",
            text = "It is the day for your monthly ${if (settings.sensitive) "amount" else Money.format(due.monthlyMinor, due.currency)}. Put it aside, then mark it done.",
            action = Launch.HABIT,
        )
    }

    if (!look.streak.today) {
        return Nudge(
            title = if (look.streak.current > 0) "Keep your ${look.streak.current}-day streak" else "What did you spend today?",
            text = "Add today's spending, or tick \"nothing spent\". It takes ten seconds and waters your tree.",
            action = Launch.NEW_ITEM,
        )
    }

    val left = look.budget - look.month
    return Nudge(
        title = if (settings.sensitive) "Today is logged" else "Today: ${show(look.today)} spent",
        text = "Streak: ${look.streak.current} ${if (look.streak.current == 1) "day" else "days"}." +
            (if (look.budget > 0L && !settings.sensitive) (if (left >= 0L) " ${show(left)} left in this month's budget." else " ${show(-left)} over this month's budget.") else ""),
        action = Launch.OPEN,
    )
}

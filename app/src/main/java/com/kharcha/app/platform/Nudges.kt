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
import com.kharcha.app.domain.MASCOT_NAME
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime

/** Where a notification or a widget tap should land in the app. MainActivity reads these. */
object Launch {
    const val NEW_ITEM = "com.kharcha.app.NEW_ITEM"
    const val INSIGHTS = "com.kharcha.app.INSIGHTS"
    const val HABIT = "com.kharcha.app.HABIT"
    const val OPEN = "com.kharcha.app.OPEN"
}

/** The words of one notification, and where tapping it goes. */
data class Nudge(val title: String, val text: String, val action: String)

/** What a reminder is about, by the time of day it comes at. */
enum class SlotKind { MORNING, LUNCH, AFTERNOON, EVENING, MAIN, LAST_CALL }

/** One reminder time. */
data class Slot(val time: LocalTime, val kind: SlotKind)

/**
 * The times of day reminders come at: one, three or six of them. The MAIN one is the time
 * chosen in settings (9 pm unless changed); the others are fixed, at the moments money is
 * usually spent. A fixed time that falls within 40 minutes of the chosen one is left out.
 */
fun nudgeSlots(settings: Settings): List<Slot> {
    val main = LocalTime.of(settings.reminderHour.coerceIn(0, 23), settings.reminderMinute.coerceIn(0, 59))
    val fixed = when {
        settings.nudges >= 6 -> listOf(
            Slot(LocalTime.of(9, 0), SlotKind.MORNING),
            Slot(LocalTime.of(13, 0), SlotKind.LUNCH),
            Slot(LocalTime.of(16, 0), SlotKind.AFTERNOON),
            Slot(LocalTime.of(19, 0), SlotKind.EVENING),
            Slot(LocalTime.of(22, 30), SlotKind.LAST_CALL),
        )
        settings.nudges >= 3 -> listOf(Slot(LocalTime.of(13, 0), SlotKind.LUNCH), Slot(LocalTime.of(18, 30), SlotKind.EVENING))
        else -> emptyList()
    }
    val kept = fixed.filter { kotlin.math.abs(Duration.between(it.time, main).toMinutes()) >= 40 }
    return (kept + Slot(main, SlotKind.MAIN)).sortedBy { it.time }
}

/**
 * The reminder that is due at this moment: the latest one whose time has come, as long as it
 * is not more than an hour and a half late (Android may deliver alarms late; a morning
 * reminder is no use in the afternoon). Null between reminders.
 */
fun dueSlot(settings: Settings, now: Long): Slot? {
    val time = Dates.dateTimeOf(now).toLocalTime()
    val minute = time.toSecondOfDay() / 60
    return nudgeSlots(settings).lastOrNull { slot ->
        val at = slot.time.toSecondOfDay() / 60
        minute >= at - 2 && minute <= at + 90
    }
}

/**
 * What a reminder says. MAIN is the day's main one (see [dailyNudge]). The others are short
 * prompts in Gullu's voice. Null = say nothing this time: the late "last call" is skipped
 * when today is already logged, and so is everything else if "quiet once logged" is on.
 */
fun nudgeFor(kind: SlotKind, data: AppData, settings: Settings, rates: Rates, now: Long): Nudge? {
    if (kind == SlotKind.MAIN) return dailyNudge(data, settings, rates, now)
    val currency = preferredCurrency(data, settings)
    val look = data.quickLook(now, currency) { from -> rates.between(from, currency) }
    val logged = look.streak.today
    if (logged && (settings.nudgeQuiet || kind == SlotKind.LAST_CALL)) return null
    val streak = look.streak.current
    // With Sensitive mode on, amounts stay out of notifications: they show on the lock screen.
    val soFar = if (settings.sensitive || look.today <= 0L) "" else " Today so far: ${Money.format(look.today, currency)}."
    return when (kind) {
        SlotKind.MORNING -> {
            val left = look.budget - look.month
            Nudge(
                title = "Good morning from $MASCOT_NAME",
                text = (if (look.budget > 0L && !settings.sensitive && left >= 0L) "${Money.format(left, currency)} left in this month's budget. " else "") +
                    (if (streak > 0) "Day ${streak + 1} of your streak starts with the first thing you log." else "Log one thing today and a streak begins."),
                action = Launch.OPEN,
            )
        }
        SlotKind.LUNCH -> Nudge(
            title = "Lunch done?",
            text = (if (logged) "Add it while you remember." else "Add it now and today is safe.") + soFar,
            action = Launch.NEW_ITEM,
        )
        SlotKind.AFTERNOON -> Nudge(
            title = "Tea, snacks, an auto ride?",
            text = "The small ones are the easiest to forget. Ten seconds to add.$soFar",
            action = Launch.NEW_ITEM,
        )
        SlotKind.EVENING -> Nudge(
            title = if (logged) "Evening check" else "Nothing logged yet today",
            text = when {
                logged -> "Anything since this afternoon?$soFar"
                streak > 0 -> "Your $streak-day streak needs one entry before midnight."
                else -> "One entry starts a streak."
            },
            action = Launch.NEW_ITEM,
        )
        else -> Nudge(
            title = if (streak > 0) "Last call: your $streak-day streak ends at midnight" else "Still time to log today",
            text = "Add one item, or tick \"nothing spent\". $MASCOT_NAME is waiting.",
            action = Launch.HABIT,
        )
    }
}

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

package com.kharcha.app.domain

import com.kharcha.app.core.model.AppData
import com.kharcha.app.core.model.Kind
import com.kharcha.app.core.model.PayMethod
import com.kharcha.app.core.util.Dates
import com.kharcha.app.core.util.Range

/*
 * The numbers behind the home screen's charts: the last seven days, where this month's
 * money went, how it was paid, and how the month compares with the one before.
 *
 * All of it is the "spending view" (what things cost YOU, across every sheet, in one
 * currency), the same sums the insights use. Nothing here is stored.
 */

/** One day's bar. */
data class DayBar(val start: Long, val label: String, val amount: Long)

/** One slice of "where it went". `color` is 0xAARRGGBB; a null `categoryId` is "No category" or "Everything else". */
data class CategoryPart(val categoryId: String?, val name: String, val color: Long, val amount: Long, val share: Float)

/** One part of "how it was paid". */
data class MethodPart(val method: PayMethod, val amount: Long, val share: Float)

data class Dashboard(
    /** The last seven days, oldest first. The last one is today. */
    val week: List<DayBar>,
    /** Spent so far this calendar month. */
    val month: Long,
    /** Spent in the whole of last month, and in last month up to the same day of the month as today. */
    val lastMonth: Long,
    val lastMonthByNow: Long,
    /** Where this month would end if every remaining day cost what the days so far did on average. */
    val projected: Long,
    /** This month's spending by category: the four largest, then everything else as one. */
    val categories: List<CategoryPart>,
    /** This month's spending by the way it was paid, largest first. */
    val methods: List<MethodPart>,
    /** The single largest expense this month: what it was, and how much. */
    val biggest: Pair<String, Long>?,
    /** How many expenses were logged this month. */
    val count: Int,
) {
    val weekTotal: Long get() = week.sumOf { it.amount }

    /** True when there is nothing to draw yet. */
    val empty: Boolean get() = month == 0L && weekTotal == 0L
}

private const val GREY = 0xFF8E8E93

fun AppData.homeDashboard(now: Long, currency: String, rate: (String) -> Double?): Dashboard {
    val today = Dates.dateOf(now)
    val monthStart = today.withDayOfMonth(1)
    val lastStart = monthStart.minusMonths(1)
    val weekStart = today.minusDays(6)
    val from = if (lastStart.isBefore(weekStart)) lastStart else weekStart
    val all = spends(Range(Dates.startOfDay(from), Dates.startOfDay(today.plusDays(1))), currency, rate, Kind.EXPENSE)

    val week = (6 downTo 0).map { back ->
        val day = today.minusDays(back.toLong())
        val start = Dates.startOfDay(day)
        val end = Dates.startOfDay(day.plusDays(1))
        DayBar(start, Dates.weekday(start).take(2), all.filter { it.item.at >= start && it.item.at < end }.sumOf { it.amount })
    }

    val monthFrom = Dates.startOfDay(monthStart)
    val thisMonth = all.filter { it.item.at >= monthFrom }
    val month = thisMonth.sumOf { it.amount }
    val lastFrom = Dates.startOfDay(lastStart)
    val last = all.filter { it.item.at >= lastFrom && it.item.at < monthFrom }
    // "By now" last month: up to the same day number, or the month's last day if it was shorter.
    val sameDay = lastStart.plusDays((today.dayOfMonth).toLong().coerceAtMost(lastStart.lengthOfMonth().toLong()))
    val byNow = Dates.startOfDay(sameDay)

    val total = month.coerceAtLeast(1L)
    val grouped = thisMonth.groupBy { it.item.categoryId }
        .map { (id, list) ->
            val category = category(id)
            CategoryPart(category?.id, category?.name ?: "No category", category?.color ?: GREY, list.sumOf { it.amount }, 0f)
        }
        .filter { it.amount > 0L }
        .sortedByDescending { it.amount }
    val top = grouped.take(4)
    val rest = grouped.drop(4).sumOf { it.amount }
    val categories = (top + if (rest > 0L) listOf(CategoryPart(null, "Everything else", GREY, rest, 0f)) else emptyList())
        .map { it.copy(share = it.amount.toFloat() / total) }

    val methods = thisMonth.groupBy { it.item.method }
        .map { (method, list) -> MethodPart(method, list.sumOf { it.amount }, 0f) }
        .filter { it.amount > 0L }
        .sortedByDescending { it.amount }
        .map { it.copy(share = it.amount.toFloat() / total) }

    val largest = thisMonth.maxByOrNull { it.amount }?.takeIf { it.amount > 0L }

    return Dashboard(
        week = week,
        month = month,
        lastMonth = last.sumOf { it.amount },
        lastMonthByNow = last.filter { it.item.at < byNow }.sumOf { it.amount },
        projected = if (month <= 0L) 0L else month / today.dayOfMonth * today.lengthOfMonth(),
        categories = categories,
        methods = methods,
        biggest = largest?.let { (it.item.note.ifBlank { it.category }) to it.amount },
        count = thisMonth.size,
    )
}

package com.kharcha.app.domain

import com.kharcha.app.core.model.AppData
import com.kharcha.app.core.model.Category
import com.kharcha.app.core.model.Item
import com.kharcha.app.core.model.Kind
import com.kharcha.app.core.model.Sheet
import com.kharcha.app.core.model.SheetSort
import com.kharcha.app.core.util.Dates
import com.kharcha.app.core.util.Money
import com.kharcha.app.core.util.Range

/*
 * The sums. Pure functions: they take data and give numbers back, and touch nothing else.
 * Every amount here is in the SHEET's currency, in its smallest unit.
 */

/** An item's amount in its sheet's currency, using the rate frozen on the item. */
fun Item.inSheet(sheet: Sheet): Long = Money.convert(amountMinor, currency, sheet.currency, rate)

/** Money out, money in, and the difference. */
data class Totals(val expense: Long = 0, val income: Long = 0, val count: Int = 0) {
    val balance: Long get() = income - expense
}

fun totals(items: List<Item>, sheet: Sheet): Totals {
    var expense = 0L
    var income = 0L
    for (item in items) {
        val amount = item.inSheet(sheet)
        if (item.kind == Kind.EXPENSE) expense += amount else income += amount
    }
    return Totals(expense, income, items.size)
}

/** The items a sheet counts in a period: not in the trash, not pending, inside the dates. */
fun AppData.settled(sheet: Sheet, range: Range): List<Item> =
    items.filter { it.sheetId == sheet.id && it.deletedAt == null && !it.pending && it.at in range }

fun AppData.pending(sheet: Sheet): List<Item> =
    items.filter { it.sheetId == sheet.id && it.deletedAt == null && it.pending }

/** What a sheet's card and header show: its totals for the period chosen on the sheet. */
fun AppData.sheetTotals(sheet: Sheet, now: Long): Totals =
    totals(settled(sheet, Dates.range(sheet.period, now, sheet.customFrom, sheet.customTo)), sheet)

/** The headline number of a sheet: the balance, or just what was spent. */
fun Sheet.headline(totals: Totals): Long = if (showTotalBalance) totals.balance else -totals.expense

fun AppData.sortedSheets(sort: SheetSort): List<Sheet> = when (sort) {
    SheetSort.MANUAL -> liveSheets.sortedBy { it.order }
    SheetSort.EDITED -> liveSheets.sortedByDescending { it.editedAt }
    SheetSort.CREATED -> liveSheets.sortedByDescending { it.createdAt }
    SheetSort.NAME_ASC -> liveSheets.sortedBy { it.name.lowercase() }
    SheetSort.NAME_DESC -> liveSheets.sortedByDescending { it.name.lowercase() }
}

// ---------------------------------------------------------------------------
// The list on a sheet: items grouped by day
// ---------------------------------------------------------------------------

/** One day of a sheet's list. `net` is that day's income minus expense. */
data class DayGroup(val day: Long, val items: List<Item>, val net: Long)

fun groupByDay(items: List<Item>, sheet: Sheet, newestFirst: Boolean): List<DayGroup> {
    val sorted = if (newestFirst) items.sortedByDescending { it.at } else items.sortedBy { it.at }
    return sorted.groupBy { Dates.startOfDay(it.at) }.map { (day, dayItems) ->
        DayGroup(day, dayItems, totals(dayItems, sheet).balance)
    }
}

// ---------------------------------------------------------------------------
// Filters and search
// ---------------------------------------------------------------------------

@kotlinx.serialization.Serializable
enum class AmountTest(val label: String) { EXACTLY("Exactly"), MORE("More than"), LESS("Less than") }

/** What the Filters sheet can narrow a list by. Every part left at its default lets everything through. */
@kotlinx.serialization.Serializable
data class Filter(
    val withPicture: Boolean = false,
    val repeating: Boolean = false,
    val kind: Kind? = null,
    val amountTest: AmountTest = AmountTest.EXACTLY,
    /** In the smallest unit of the sheet's currency. */
    val amountMinor: Long? = null,
    val currency: String? = null,
    val note: String = "",
    val categoryIds: Set<String> = emptySet(),
    /** true = also items with no category. */
    val noCategory: Boolean = false,
    val from: Long? = null,
    val to: Long? = null,
) {
    val active: Boolean get() = this != Filter()

    /** How many parts of the filter are switched on, for the badge on the filter button. */
    val count: Int
        get() = listOf(
            withPicture, repeating, kind != null, amountMinor != null, currency != null, note.isNotBlank(),
            categoryIds.isNotEmpty() || noCategory, from != null || to != null,
        ).count { it }

    fun matches(item: Item, sheet: Sheet): Boolean {
        if (withPicture && item.images.isEmpty()) return false
        if (repeating && item.seriesId == null && item.repeat == com.kharcha.app.core.model.Repeat.NEVER) return false
        if (kind != null && item.kind != kind) return false
        if (amountMinor != null) {
            val amount = item.inSheet(sheet)
            val ok = when (amountTest) {
                AmountTest.EXACTLY -> amount == amountMinor
                AmountTest.MORE -> amount > amountMinor
                AmountTest.LESS -> amount < amountMinor
            }
            if (!ok) return false
        }
        if (currency != null && item.currency != currency) return false
        if (note.isNotBlank() && !item.note.contains(note.trim(), ignoreCase = true) && !item.merchant.contains(note.trim(), ignoreCase = true)) return false
        if (categoryIds.isNotEmpty() || noCategory) {
            val hit = (item.categoryId != null && item.categoryId in categoryIds) || (noCategory && item.categoryId == null)
            if (!hit) return false
        }
        if (from != null && item.at < from) return false
        if (to != null && item.at >= Dates.startOfDay(Dates.dateOf(to).plusDays(1))) return false
        return true
    }
}

/** Does this item match what was typed in the search box? Looks at the note, merchant, category and amount. */
fun Item.matchesSearch(query: String, category: Category?): Boolean {
    val q = query.trim()
    if (q.isEmpty()) return true
    return note.contains(q, ignoreCase = true) ||
        merchant.contains(q, ignoreCase = true) ||
        (category?.name?.contains(q, ignoreCase = true) == true) ||
        Money.plain(amountMinor, currency).contains(q)
}

// ---------------------------------------------------------------------------
// Stats and trends
// ---------------------------------------------------------------------------

/** One slice of the "where did it go" chart. `category` is null for items with no category. */
data class Slice(val category: Category?, val amount: Long, val count: Int, val share: Float)

/** Spending (or income) split by category, largest first. Transfers are left out: they are not spending. */
fun slices(items: List<Item>, sheet: Sheet, kind: Kind, data: AppData): List<Slice> {
    val relevant = items.filter { it.kind == kind && !it.isTransfer }
    val total = relevant.sumOf { it.inSheet(sheet) }.coerceAtLeast(1L)
    return relevant.groupBy { it.categoryId }
        .map { (categoryId, group) ->
            val amount = group.sumOf { it.inSheet(sheet) }
            Slice(data.category(categoryId), amount, group.size, amount.toFloat() / total)
        }
        .sortedByDescending { it.amount }
}

enum class Bucket(val label: String, val count: Int) { DAY("Days", 14), WEEK("Weeks", 12), MONTH("Months", 12), YEAR("Years", 6) }

/** One bar of the trends chart. */
data class TrendPoint(val start: Long, val label: String, val expense: Long, val income: Long) {
    val net: Long get() = income - expense
}

/** Money out and in for each of the last few days, weeks, months or years, oldest first. */
fun trend(items: List<Item>, sheet: Sheet, bucket: Bucket, now: Long): List<TrendPoint> {
    val today = Dates.dateOf(now)
    val starts = (bucket.count - 1 downTo 0).map { back ->
        when (bucket) {
            Bucket.DAY -> today.minusDays(back.toLong())
            Bucket.WEEK -> Dates.weekStart(today).minusWeeks(back.toLong())
            Bucket.MONTH -> today.withDayOfMonth(1).minusMonths(back.toLong())
            Bucket.YEAR -> today.withDayOfYear(1).minusYears(back.toLong())
        }
    }
    val real = items.filter { !it.isTransfer }
    return starts.map { start ->
        val end = when (bucket) {
            Bucket.DAY -> start.plusDays(1)
            Bucket.WEEK -> start.plusWeeks(1)
            Bucket.MONTH -> start.plusMonths(1)
            Bucket.YEAR -> start.plusYears(1)
        }
        val range = Range(Dates.startOfDay(start), Dates.startOfDay(end))
        val inside = real.filter { it.at in range }
        val sums = totals(inside, sheet)
        val label = when (bucket) {
            Bucket.DAY -> start.dayOfMonth.toString()
            Bucket.WEEK -> "${start.dayOfMonth}/${start.monthValue}"
            Bucket.MONTH -> start.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())
            Bucket.YEAR -> start.year.toString()
        }
        TrendPoint(range.from, label, sums.expense, sums.income)
    }
}

/** This month's spending against the sheet's budget. Null when the sheet has no budget. */
data class BudgetState(val spent: Long, val budget: Long) {
    val fraction: Float get() = if (budget <= 0) 0f else (spent.toFloat() / budget).coerceIn(0f, 1f)
    val over: Boolean get() = spent > budget
    val left: Long get() = budget - spent
}

fun AppData.budget(sheet: Sheet, now: Long): BudgetState? {
    if (sheet.monthlyBudgetMinor <= 0) return null
    val month = Dates.range(com.kharcha.app.core.model.PeriodKind.THIS_MONTH, now)
    val spent = settled(sheet, month).filter { it.kind == Kind.EXPENSE && !it.isTransfer }.sumOf { it.inSheet(sheet) }
    return BudgetState(spent, sheet.monthlyBudgetMinor)
}

// ---------------------------------------------------------------------------
// All sheets together
// ---------------------------------------------------------------------------

/** Every sheet added up in one currency. `leftOut` counts sheets with no known exchange rate. */
data class Overview(val balance: Long, val spent: Long, val received: Long, val leftOut: Int)

/**
 * The total across sheets, in `currency`. `rate` gives how many units of `currency` one unit
 * of a sheet's currency is worth today, or null when that is not known.
 */
fun AppData.overview(sheets: List<Sheet>, currency: String, now: Long, rate: (String) -> Double?): Overview {
    val everything = Dates.range(com.kharcha.app.core.model.PeriodKind.AS_OF_TODAY, now)
    val month = Dates.range(com.kharcha.app.core.model.PeriodKind.THIS_MONTH, now)
    var balance = 0L
    var spent = 0L
    var received = 0L
    var leftOut = 0
    for (sheet in sheets) {
        val factor = rate(sheet.currency)
        if (factor == null) {
            leftOut++
            continue
        }
        val settled = settled(sheet, everything)
        balance += Money.convert(totals(settled, sheet).balance, sheet.currency, currency, factor)
        // Transfers between sheets are not spending or earning.
        val thisMonth = totals(settled.filter { it.at in month && !it.isTransfer }, sheet)
        spent += Money.convert(thisMonth.expense, sheet.currency, currency, factor)
        received += Money.convert(thisMonth.income, sheet.currency, currency, factor)
    }
    return Overview(balance, spent, received, leftOut)
}

/** What an item is called in a list: its category, or where a transfer went. */
fun AppData.titleOf(item: Item): String = when {
    item.isTransfer && item.kind == Kind.EXPENSE -> "Transfer to ${sheet(item.transferPeerSheetId)?.name ?: "another sheet"}"
    item.isTransfer -> "Transfer from ${sheet(item.transferPeerSheetId)?.name ?: "another sheet"}"
    else -> category(item.categoryId)?.name ?: "No category"
}

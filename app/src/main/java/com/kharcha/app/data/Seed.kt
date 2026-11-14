package com.kharcha.app.data

import com.kharcha.app.core.model.AppData
import com.kharcha.app.core.model.Category
import com.kharcha.app.core.model.Item
import com.kharcha.app.core.model.Kind
import com.kharcha.app.core.model.Repeat
import com.kharcha.app.core.model.Sheet
import com.kharcha.app.core.util.Dates
import com.kharcha.app.core.util.Money
import com.kharcha.app.core.util.newId
import java.math.BigDecimal
import java.time.LocalTime
import kotlin.random.Random

/** What a brand-new install starts with, and the sample data for trying the app out. */
object Seed {

    /** The eighteen colours offered for a category, as 0xAARRGGBB. */
    val palette: List<Long> = listOf(
        0xFFEF5350, 0xFFF4703A, 0xFFF59B42, 0xFFF7D046, 0xFFD3E04F, 0xFF62C968,
        0xFF4DB8D1, 0xFF5BD0DD, 0xFF3F8EF5, 0xFF6C7CF5, 0xFFEC4A63, 0xFFC63FE8,
        0xFFBE9A60, 0xFF8E8E93, 0xFF636366, 0xFF48484A, 0xFF7E57C2, 0xFFE3A39C,
    )

    private fun category(id: String, name: String, kind: Kind, icon: String, color: Long, order: Int) =
        Category(id = id, name = name, kind = kind, icon = icon, color = color, order = order)

    /**
     * The starting categories. Their ids are fixed words rather than random, so two phones that
     * both start fresh and later merge do not end up with two "Groceries".
     */
    val defaultCategories: List<Category> = listOf(
        category("cat-groceries", "Groceries", Kind.EXPENSE, "shopping_cart", 0xFF5BD0DD, 0),
        category("cat-food", "Food & Drink", Kind.EXPENSE, "restaurant", 0xFF6C7CF5, 1),
        category("cat-transport", "Transport", Kind.EXPENSE, "train", 0xFF3F8EF5, 2),
        category("cat-bills", "Bills", Kind.EXPENSE, "bolt", 0xFFF59B42, 3),
        category("cat-internet", "Internet", Kind.EXPENSE, "wifi", 0xFF62C968, 4),
        category("cat-rent", "Rent", Kind.EXPENSE, "home", 0xFFF7D046, 5),
        category("cat-entertainment", "Entertainment", Kind.EXPENSE, "movie", 0xFFF4703A, 6),
        category("cat-shopping", "Shopping", Kind.EXPENSE, "redeem", 0xFFEF5350, 7),
        category("cat-health", "Health", Kind.EXPENSE, "medical_services", 0xFFEC4A63, 8),
        category("cat-travel", "Travel", Kind.EXPENSE, "flight", 0xFF4DB8D1, 9),
        category("cat-salary", "Salary", Kind.INCOME, "payments", 0xFF5BD0DD, 10),
        category("cat-freelance", "Freelance", Kind.INCOME, "work", 0xFF6C7CF5, 11),
        category("cat-gift", "Gift", Kind.INCOME, "card_giftcard", 0xFFC63FE8, 12),
        category("cat-interest", "Interest", Kind.INCOME, "savings", 0xFF62C968, 13),
    )

    fun firstSheet(name: String, currency: String): Sheet = Sheet(id = newId(), name = name.ifBlank { "Personal" }, currency = currency)

    /**
     * Two filled-in sheets, so every screen has something to show. The amounts are sized for
     * the given currency (rupees get larger numbers than dollars).
     */
    fun sample(currency: String, now: Long): AppData {
        val random = Random(42)
        // Roughly "one unit of everyday spending" in this currency.
        val unit: Double = when (currency) {
            "INR", "PKR", "LKR", "NPR", "BDT", "RUB", "PHP", "THB" -> 80.0
            "JPY", "KRW" -> 150.0
            "IDR", "VND" -> 15000.0
            else -> 1.0
        }
        fun amount(base: Double): Long = Money.toMinor(BigDecimal.valueOf(Math.round(base * unit).toDouble()), currency)
        val personal = Sheet(id = newId(), name = "Personal", currency = currency, monthlyBudgetMinor = amount(900.0), order = 0)
        val tripCurrency = if (currency == "USD") "EUR" else "USD"
        val trip = Sheet(id = newId(), name = "Weekend trip", currency = currency, order = 1)
        val today = Dates.dateOf(now)

        data class Line(val category: String, val notes: List<String>, val merchant: String, val low: Double, val high: Double, val every: Int)
        val lines = listOf(
            Line("cat-groceries", listOf("Vegetables", "Milk and bread", "Weekly shop", "Fruit"), "Corner store", 4.0, 38.0, 2),
            Line("cat-food", listOf("Lunch", "Coffee", "Dinner out", "Snacks"), "Cafe Bloom", 3.0, 30.0, 3),
            Line("cat-transport", listOf("Metro card", "Cab home", "Fuel"), "", 2.0, 25.0, 4),
            Line("cat-entertainment", listOf("Movie night", "Music subscription"), "", 5.0, 20.0, 11),
            Line("cat-shopping", listOf("T-shirt", "Phone case", "Birthday gift"), "City Mall", 8.0, 60.0, 13),
            Line("cat-health", listOf("Pharmacy", "Gym"), "", 6.0, 35.0, 17),
        )
        val items = ArrayList<Item>()
        fun add(sheet: Sheet, kind: Kind, minor: Long, category: String?, note: String, daysAgo: Long, hour: Int, merchant: String = "", pending: Boolean = false, repeat: Repeat = Repeat.NEVER, itemCurrency: String = sheet.currency, rate: Double = 1.0) {
            val at = Dates.millis(today.minusDays(daysAgo), LocalTime.of(hour, random.nextInt(0, 60)))
            items += Item(
                id = newId(), sheetId = sheet.id, kind = kind, amountMinor = minor, currency = itemCurrency, rate = rate,
                note = note, merchant = merchant, categoryId = category, at = at, pending = pending, repeat = repeat,
                createdAt = at, editedAt = at,
            )
        }
        for (day in 0L..74L) {
            lines.forEachIndexed { index, line ->
                if ((day + index * 3) % line.every == 0L) {
                    val value = line.low + random.nextDouble() * (line.high - line.low)
                    add(personal, Kind.EXPENSE, amount(value), line.category, line.notes[random.nextInt(line.notes.size)], day, 9 + random.nextInt(0, 12), line.merchant)
                }
            }
        }
        // Monthly things. The newest rent and salary repeat by themselves from here on.
        for (month in 0L..2L) {
            val dayOfMonthOffset = today.dayOfMonth.toLong() - 1 + month * 30
            add(personal, Kind.INCOME, amount(2400.0), "cat-salary", "Salary", dayOfMonthOffset, 10, repeat = if (month == 0L) Repeat.MONTHLY else Repeat.NEVER)
            add(personal, Kind.EXPENSE, amount(650.0), "cat-rent", "Rent", dayOfMonthOffset, 11, repeat = if (month == 0L) Repeat.MONTHLY else Repeat.NEVER)
            add(personal, Kind.EXPENSE, amount(22.0), "cat-internet", "Broadband", dayOfMonthOffset + 4, 12)
            add(personal, Kind.EXPENSE, amount(48.0), "cat-bills", "Electricity", dayOfMonthOffset + 6, 12)
        }
        add(personal, Kind.INCOME, amount(310.0), "cat-freelance", "Logo design", 9, 15)
        add(personal, Kind.INCOME, amount(40.0), "cat-gift", "From grandma", 21, 18)
        add(personal, Kind.EXPENSE, amount(120.0), "cat-shopping", "Headphones, waiting for refund", 2, 16, "City Mall", pending = true)
        add(personal, Kind.INCOME, amount(75.0), "cat-freelance", "Invoice sent, not paid yet", 1, 10, pending = true)

        // The trip sheet, with two things paid in another currency.
        add(trip, Kind.EXPENSE, amount(140.0), "cat-travel", "Train tickets", 12, 8)
        add(trip, Kind.EXPENSE, amount(210.0), "cat-travel", "Hotel, two nights", 12, 14, "Sea View Inn")
        add(trip, Kind.EXPENSE, amount(36.0), "cat-food", "Seafood dinner", 11, 20, "Harbour Grill")
        add(trip, Kind.EXPENSE, amount(12.0), "cat-transport", "Scooter rental", 11, 10)
        add(trip, Kind.EXPENSE, Money.toMinor(BigDecimal("25"), tripCurrency), "cat-shopping", "Souvenirs, paid by card", 10, 13, itemCurrency = tripCurrency, rate = if (unit > 1.0) unit * 1.05 else if (currency == "USD") 1.08 else 0.93)
        add(trip, Kind.INCOME, amount(120.0), null, "Rahul paid his share", 9, 9)

        return AppData(sheets = listOf(personal, trip), categories = defaultCategories, items = items)
    }
}

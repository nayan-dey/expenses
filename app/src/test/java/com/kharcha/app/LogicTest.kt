package com.kharcha.app

import com.kharcha.app.core.model.AppData
import com.kharcha.app.core.model.Category
import com.kharcha.app.core.model.Item
import com.kharcha.app.core.model.Kind
import com.kharcha.app.core.model.PayMethod
import com.kharcha.app.core.model.Person
import com.kharcha.app.core.model.Sheet
import com.kharcha.app.core.model.Settings
import com.kharcha.app.core.util.Calc
import com.kharcha.app.core.util.Dates
import com.kharcha.app.data.Seed
import com.kharcha.app.domain.GEMS_PER_REPAIR
import com.kharcha.app.domain.Mood
import com.kharcha.app.domain.Streak
import com.kharcha.app.domain.dues
import com.kharcha.app.domain.gems
import com.kharcha.app.domain.homeDashboard
import com.kharcha.app.domain.moodOf
import com.kharcha.app.domain.repairOffer
import com.kharcha.app.domain.streak
import com.kharcha.app.platform.SlotKind
import com.kharcha.app.platform.dueSlot
import com.kharcha.app.platform.nudgeSlots
import com.kharcha.app.domain.hints
import com.kharcha.app.domain.titleOf
import com.kharcha.app.domain.words
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime

/** The sums and guesses that need no phone: the keypad calculator, smart categories, loans. */
class LogicTest {

    private val sheet = Sheet(id = "s", name = "Personal", currency = "INR")

    private fun expense(id: String, note: String, category: String?, method: PayMethod = PayMethod.NONE) =
        Item(id = id, sheetId = "s", kind = Kind.EXPENSE, amountMinor = 1000L, currency = "INR", at = 1L, note = note, categoryId = category, method = method)

    // ---- the keypad calculator ----

    @Test
    fun calculatorWorksASumOut() {
        val sum = "20${Calc.TIMES}5${Calc.DIVIDE}2"
        assertEquals(0, BigDecimal(50).compareTo(Calc.eval(sum)))
        assertEquals(0, BigDecimal(42).compareTo(Calc.eval("$sum${Calc.MINUS}8")))
        assertFalse(Calc.belowZero(sum))
    }

    @Test
    fun calculatorSaysWhenASumGoesBelowZero() {
        val sum = "20${Calc.TIMES}5${Calc.DIVIDE}2${Calc.MINUS}58"
        assertNull(Calc.eval(sum))
        assertTrue(Calc.belowZero(sum))
        // Dividing by zero is not "below zero": it simply has no answer.
        assertNull(Calc.eval("5${Calc.DIVIDE}0"))
        assertFalse(Calc.belowZero("5${Calc.DIVIDE}0"))
    }

    @Test
    fun calculatorDropsADanglingKey() {
        assertEquals("20${Calc.TIMES}5", Calc.tidy("20${Calc.TIMES}5${Calc.DIVIDE}"))
        assertEquals("12", Calc.tidy("12."))
        assertTrue(Calc.hasOperator(Calc.tidy("20${Calc.TIMES}5")))
        assertFalse(Calc.hasOperator(Calc.tidy("20${Calc.PLUS}")))
    }

    // ---- guessing the category and the way of paying ----

    @Test
    fun notesAreCutIntoUsefulWords() {
        assertEquals(listOf("water", "tata"), words("Water at Tata"))
        assertEquals(listOf("chai"), words("2 chai for 20"))
    }

    @Test
    fun everydayWordsPickAStartingCategory() {
        val hints = AppData(sheets = listOf(sheet), categories = Seed.defaultCategories).hints()
        assertEquals("cat-food", hints.category(Kind.EXPENSE, "Water at tata"))
        assertEquals("cat-bills", hints.category(Kind.EXPENSE, "water bill"))
        assertEquals("cat-transport", hints.category(Kind.EXPENSE, "Uber to office"))
        assertEquals("cat-salary", hints.category(Kind.INCOME, "Salary for November"))
        // An income word says nothing about an expense, and gibberish says nothing at all.
        assertNull(hints.category(Kind.EXPENSE, "salary"))
        assertNull(hints.category(Kind.EXPENSE, "zzqx"))
        assertNull(hints.category(Kind.EXPENSE, ""))
    }

    @Test
    fun yourOwnHistoryBeatsTheBuiltInWords() {
        val data = AppData(
            sheets = listOf(sheet),
            categories = Seed.defaultCategories,
            items = listOf(expense("a", "chai", "cat-groceries"), expense("b", "Chai and biscuit", "cat-groceries"), expense("c", "chai", "cat-food")),
        )
        assertEquals("cat-groceries", data.hints().category(Kind.EXPENSE, "evening chai"))
    }

    @Test
    fun aCategoryNameIsAWordForIt() {
        val due = Category(id = "mine-due", name = "Due", kind = Kind.EXPENSE)
        val hints = AppData(sheets = listOf(sheet), categories = Seed.defaultCategories + due).hints()
        assertEquals("mine-due", hints.category(Kind.EXPENSE, "due rahul"))
    }

    @Test
    fun theWayOfPayingIsGuessedFromTheNoteThenHistoryThenTheSheet() {
        val empty = AppData(sheets = listOf(sheet), categories = Seed.defaultCategories).hints()
        assertEquals(PayMethod.CASH, empty.method("Auto, paid cash", "", "Personal"))
        assertEquals(PayMethod.UPI, empty.method("gpay to Rahul", "", "Cash"))
        assertEquals(PayMethod.CASH, empty.method("Vegetables", "", "Cash in hand"))
        assertEquals(PayMethod.UPI, empty.method("Vegetables", "", "HDFC Bank"))
        assertNull(empty.method("Vegetables", "", "Personal"))

        val learned = AppData(
            sheets = listOf(sheet),
            categories = Seed.defaultCategories,
            items = listOf(expense("a", "vegetables", "cat-groceries", PayMethod.CASH), expense("b", "Vegetables", "cat-groceries", PayMethod.CASH)),
        ).hints()
        assertEquals(PayMethod.CASH, learned.method("vegetables and milk", "", "HDFC Bank"))
    }

    // ---- money lent and borrowed ----

    @Test
    fun aLoanIsOwedBackAndIsNotSpending() {
        val rahul = Person(id = "p", name = "Rahul")
        val lent = Item(id = "l", sheetId = "s", kind = Kind.EXPENSE, amountMinor = 50000L, currency = "INR", at = 1L, settles = "p", loan = true)
        val borrowed = Item(id = "b", sheetId = "s", kind = Kind.INCOME, amountMinor = 20000L, currency = "INR", at = 2L, settles = "p", loan = true)
        val repaid = Item(id = "r", sheetId = "s", kind = Kind.INCOME, amountMinor = 10000L, currency = "INR", at = 3L, settles = "p")
        val data = AppData(sheets = listOf(sheet), people = listOf(rahul), items = listOf(lent, borrowed, repaid))

        // 500 lent, 200 borrowed, 100 paid back: Rahul still owes 200.
        assertEquals(20000L, data.dues()["p"]?.get("INR"))
        assertEquals("Lent to Rahul", data.titleOf(lent))
        assertEquals("Borrowed from Rahul", data.titleOf(borrowed))
        assertEquals("Rahul paid you back", data.titleOf(repaid))
        assertTrue(lent.isMovement)
    }

    // ---- the streak, gems, and bringing a streak back ----

    private val today: LocalDate = LocalDate.of(2026, 11, 17)
    private val noon: Long = Dates.millis(today, LocalTime.NOON)

    /** One item added on each of the given days (0 = today, 1 = yesterday, ...). */
    private fun loggedOn(vararg daysAgo: Int): AppData = AppData(
        sheets = listOf(sheet),
        items = daysAgo.map { ago ->
            val at = Dates.millis(today.minusDays(ago.toLong()), LocalTime.of(10, 0))
            Item(id = "d$ago", sheetId = "s", kind = Kind.EXPENSE, amountMinor = 100L, currency = "INR", at = at, createdAt = at)
        },
    )

    @Test
    fun gemsAreCountedFromTheDaysLogged() {
        assertEquals(0, AppData().gems().balance)
        // Three days: 5 each.
        assertEquals(15, loggedOn(0, 1, 2).gems().balance)
        // Eight days: 5 each, and one bonus of 15 for the first seven.
        assertEquals(55, loggedOn(0, 1, 2, 3, 4, 5, 6, 7).gems().balance)
        // Gems that were used are gone, and the balance never goes below zero.
        assertEquals(35, loggedOn(0, 1, 2, 3, 4, 5, 6, 7).copy(gemsSpent = 20).gems().balance)
        assertEquals(0, loggedOn(0).copy(gemsSpent = 500).gems().balance)
    }

    @Test
    fun aStreakThatBrokeYesterdayCanBeBoughtBack() {
        // Logged four days running, then missed yesterday. Today is still open.
        val data = loggedOn(2, 3, 4, 5)
        assertEquals(0, data.streak(noon).current)
        val offer = checkNotNull(data.repairOffer(noon))
        assertEquals(listOf(today.minusDays(1).toString()), offer.days)
        assertEquals(GEMS_PER_REPAIR, offer.cost)
        assertEquals(4, offer.streakBefore)
        assertEquals(5, offer.streakAfter)

        // After paying, the streak carries on over the missed day and there is nothing left to mend.
        val mended = data.copy(repairedDays = offer.days, gemsSpent = offer.cost)
        assertEquals(5, mended.streak(noon).current)
        assertNull(mended.repairOffer(noon))
        // A bought-back day earns no gems of its own: 4 logged days are 20, minus the 20 spent.
        assertEquals(0, mended.gems().balance)
    }

    @Test
    fun nothingIsOfferedWhenThereIsNothingToMend() {
        // The streak is alive (yesterday counts, today is open).
        assertNull(loggedOn(1, 2, 3).repairOffer(noon))
        // Too long ago: four missed days.
        assertNull(loggedOn(5, 6, 7).repairOffer(noon))
        // What broke was a single day: not worth gems.
        assertNull(loggedOn(2).repairOffer(noon))
        // Nothing logged at all.
        assertNull(AppData().repairOffer(noon))
        // Logged again today after a gap: the old run can still be joined to today's.
        val offer = checkNotNull(loggedOn(0, 2, 3).repairOffer(noon))
        assertEquals(4, offer.streakAfter)
    }

    @Test
    fun theMascotsMoodFollowsTheDay() {
        fun streak(current: Int, today: Boolean) = Streak(current, current, today, List(7) { false }, current)
        assertEquals(Mood.HAPPY, moodOf(streak(2, true), 10, null))
        assertEquals(Mood.PARTY, moodOf(streak(7, true), 10, null))
        assertEquals(Mood.WAITING, moodOf(streak(2, false), 10, null))
        assertEquals(Mood.WORRIED, moodOf(streak(2, false), 21, null))
        assertEquals(Mood.SAD, moodOf(loggedOn(2, 3, 4).streak(noon), 10, loggedOn(2, 3, 4).repairOffer(noon)))
    }

    // ---- reminders through the day ----

    @Test
    fun remindersComeOnceThreeOrSixTimesADay() {
        fun times(settings: Settings) = nudgeSlots(settings).map { it.time.toString() }
        assertEquals(listOf("09:00", "13:00", "16:00", "19:00", "21:00", "22:30"), times(Settings()))
        assertEquals(listOf("13:00", "18:30", "21:00"), times(Settings(nudges = 3)))
        assertEquals(listOf("21:00"), times(Settings(nudges = 1)))
        // A chosen time close to a fixed one replaces it instead of doubling up.
        assertEquals(listOf("09:00", "13:10", "16:00", "19:00", "22:30"), times(Settings(reminderHour = 13, reminderMinute = 10)))
        assertEquals(SlotKind.MAIN, nudgeSlots(Settings()).first { it.time == LocalTime.of(21, 0) }.kind)
    }

    @Test
    fun theReminderThatIsDueIsTheLatestOneWhoseTimeHasCome() {
        fun at(hour: Int, minute: Int) = dueSlot(Settings(), Dates.millis(today, LocalTime.of(hour, minute)))?.kind
        assertEquals(SlotKind.LUNCH, at(13, 0))
        assertEquals(SlotKind.LUNCH, at(13, 40))
        assertEquals(SlotKind.MAIN, at(21, 5))
        // Just after midnight (the widgets' own wake-up) and far too late for the morning one: nothing.
        assertNull(at(0, 5))
        assertNull(at(11, 30))
    }

    // ---- the home dashboard ----

    @Test
    fun theDashboardAddsUpTheWeekTheMonthAndHowItWasPaid() {
        fun spend(id: String, daysAgo: Int, amount: Long, category: String?, method: PayMethod) = Item(
            id = id, sheetId = "s", kind = Kind.EXPENSE, amountMinor = amount, currency = "INR",
            at = Dates.millis(today.minusDays(daysAgo.toLong()), LocalTime.of(10, 0)), categoryId = category, method = method,
        )
        val data = AppData(
            sheets = listOf(sheet),
            categories = Seed.defaultCategories,
            items = listOf(
                spend("a", 0, 30000L, "cat-food", PayMethod.UPI),
                spend("b", 1, 10000L, "cat-food", PayMethod.CASH),
                spend("c", 3, 60000L, "cat-rent", PayMethod.UPI),
                // Last month (17 October): counts for "last month", not for this one.
                spend("d", 31, 50000L, "cat-food", PayMethod.UPI),
                // Money lent is not spending and must not show up anywhere here.
                Item(id = "l", sheetId = "s", kind = Kind.EXPENSE, amountMinor = 99900L, currency = "INR", at = noon, settles = "p", loan = true),
            ),
        )
        val dash = data.homeDashboard(noon, "INR") { 1.0 }
        assertEquals(7, dash.week.size)
        assertEquals(30000L, dash.week.last().amount)
        assertEquals(10000L, dash.week[5].amount)
        assertEquals(100000L, dash.weekTotal)
        assertEquals(100000L, dash.month)
        assertEquals(50000L, dash.lastMonth)
        assertEquals(50000L, dash.lastMonthByNow)
        assertEquals(3, dash.count)
        // Rent is the largest slice, then food.
        assertEquals(listOf("cat-rent", "cat-food"), dash.categories.map { it.categoryId })
        assertEquals(0.6f, dash.categories.first().share, 0.001f)
        assertEquals(listOf(PayMethod.UPI, PayMethod.CASH), dash.methods.map { it.method })
        assertEquals(90000L, dash.methods.first().amount)
        assertFalse(dash.empty)
        assertTrue(AppData(sheets = listOf(sheet)).homeDashboard(noon, "INR") { 1.0 }.empty)
    }
}

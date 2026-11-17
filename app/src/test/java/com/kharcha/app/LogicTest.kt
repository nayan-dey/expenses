package com.kharcha.app

import com.kharcha.app.core.model.AppData
import com.kharcha.app.core.model.Category
import com.kharcha.app.core.model.Item
import com.kharcha.app.core.model.Kind
import com.kharcha.app.core.model.PayMethod
import com.kharcha.app.core.model.Person
import com.kharcha.app.core.model.Sheet
import com.kharcha.app.core.util.Calc
import com.kharcha.app.data.Seed
import com.kharcha.app.domain.dues
import com.kharcha.app.domain.hints
import com.kharcha.app.domain.titleOf
import com.kharcha.app.domain.words
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

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
}

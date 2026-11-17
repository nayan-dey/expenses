package com.kharcha.app

import com.kharcha.app.core.model.AppData
import com.kharcha.app.core.model.GoalKind
import com.kharcha.app.core.model.Item
import com.kharcha.app.core.model.Kind
import com.kharcha.app.core.model.PayMethod
import com.kharcha.app.core.model.PeriodKind
import com.kharcha.app.core.model.Repeat
import com.kharcha.app.core.model.Sheet
import com.kharcha.app.core.model.Split
import com.kharcha.app.data.KharchaFile
import com.kharcha.app.data.KharchaJson
import com.kharcha.app.data.readKharchaJson
import com.kharcha.app.data.rescueAppData
import com.kharcha.app.core.util.Calc
import com.kharcha.app.domain.dues
import com.kharcha.app.domain.titleOf
import java.math.BigDecimal
import com.kharcha.app.domain.equalShare
import com.kharcha.app.domain.splitHeads
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Old backups must open forever. Kharcha has no account and no server, so a backup file is
 * the only way a person's records get from an old install to a new one.
 *
 * The files in src/test/resources/backups are copies of what earlier versions wrote. This
 * test opens each of them with today's code, on every build (see .github/workflows/android.yml).
 * If a change to the data model breaks one, the build stops before an APK is published.
 */
class BackupCompatTest {

    private fun saved(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("backups/$name")) { "Missing saved backup: $name" }
            .bufferedReader().use { it.readText() }

    private fun open(name: String): KharchaFile = checkNotNull(readKharchaJson(saved(name))) { "$name did not open" }

    // ---- saved copies of old backups ----

    @Test
    fun firstReleaseBackupOpens() {
        val file = open("v1-first-release.json")
        val data = file.data
        assertEquals("backup", file.kind)
        assertEquals(1794650000000L, file.exportedAt)
        assertEquals(3, data.sheets.size)
        assertEquals(3, data.categories.size)
        assertEquals(6, data.items.size)

        val goa = data.sheets.first { it.id == "s-goa" }
        assertEquals(PeriodKind.THIS_MONTH, goa.period)
        assertEquals(false, goa.showTotalBalance)
        assertEquals(false, goa.showMerchant)
        assertEquals(2500000L, goa.monthlyBudgetMinor)
        assertEquals(2, data.liveSheets.size)

        val dinner = data.items.first { it.id == "i1" }
        assertEquals(Kind.EXPENSE, dinner.kind)
        assertEquals(45000L, dinner.amountMinor)
        assertEquals("Biryani House", dinner.merchant)
        assertEquals(listOf("a1b2c3.jpg"), dinner.images)
        assertEquals(Repeat.MONTHLY, data.items.first { it.id == "i2" }.repeat)
        assertEquals(83.5, data.items.first { it.id == "i3" }.rate, 0.0)
        assertTrue(data.items.first { it.id == "i3" }.pending)
        assertTrue(data.items.first { it.id == "i4" }.isTransfer)
        assertNotNull(data.items.first { it.id == "i6" }.deletedAt)

        // Everything added since then starts out empty.
        assertEquals(PayMethod.NONE, dinner.method)
        assertNull(dinner.payer)
        assertNull(dinner.settles)
        assertTrue(dinner.splits.isEmpty())
        assertEquals(0, dinner.covered)
        assertTrue(data.people.isEmpty())
        assertTrue(data.goals.isEmpty())
        assertTrue(data.noSpendDays.isEmpty())
        assertEquals("", dinner.expr)
        assertEquals(false, dinner.loan)
        assertTrue(data.repairedDays.isEmpty())
        assertEquals(0, data.gemsSpent)
    }

    @Test
    fun friendsAndGoalsBackupOpens() {
        val data = open("v2-friends-and-goals.json").data
        assertEquals(5, data.items.size)
        assertEquals(3, data.people.size)
        assertEquals(2, data.livePeople.size)
        assertEquals("rahul@upi", data.person("p-rahul")?.upi)
        assertEquals(listOf("2026-11-12", "2026-11-13"), data.noSpendDays)

        val night = data.items.first { it.id == "b1" }
        assertEquals(PayMethod.UPI, night.method)
        assertEquals(listOf(Split("p-rahul", 30000L), Split("p-aman", 30000L)), night.splits)
        assertEquals(60000L, night.lent)
        assertEquals(0, night.covered)
        assertEquals("p-rahul", data.items.first { it.id == "b2" }.payer)
        assertEquals("p-aman", data.items.first { it.id == "b3" }.settles)

        // Who owes whom still comes out the same.
        val dues = data.dues()
        assertEquals(35000L, dues["p-rahul"]?.get("INR"))
        assertEquals(20000L, dues["p-aman"]?.get("INR"))

        val sip = data.goals.first { it.id == "g-sip" }
        assertEquals(GoalKind.INVEST, sip.kind)
        assertEquals(500000L, sip.monthlyMinor)
        assertEquals(5, sip.dayOfMonth)
        assertEquals(500000L, sip.saved)
        assertEquals(GoalKind.SAVE, data.goals.first { it.id == "g-fund" }.kind)
    }

    @Test
    fun coveredPeopleBackupOpens() {
        val item = open("v3-covered-people.json").data.items.single()
        assertEquals(1, item.covered)
        assertEquals(40000L, item.lent)
        // The form shows this bill as an equal split again.
        assertEquals(item.splits.single().amountMinor, equalShare(item.amountMinor, item.splits.size, item.covered))
    }

    @Test
    fun loansSumsAndGemsBackupOpens() {
        val data = open("v4-loans-sums-gems.json").data
        // The sum typed on the keypad is kept as it was typed.
        val water = data.items.first { it.id == "e1" }
        assertEquals("20${Calc.TIMES}5${Calc.DIVIDE}2${Calc.MINUS}8", water.expr)
        assertEquals(0, BigDecimal(42).compareTo(Calc.eval(water.expr)))
        // Money lent and borrowed.
        val lent = data.items.first { it.id == "l1" }
        assertTrue(lent.loan)
        assertEquals("p-rahul", lent.settles)
        assertEquals("Lent to Rahul", data.titleOf(lent))
        assertEquals(30000L, data.dues()["p-rahul"]?.get("INR"))
        // The streak's bought-back days and the gems used.
        assertEquals(listOf("2026-11-12"), data.repairedDays)
        assertEquals(20, data.gemsSpent)
    }

    // ---- files this version has never seen ----

    @Test
    fun fieldsFromANewerVersionAreSkipped() {
        val text = """
            {"exportedAt":5,"format":9,"somethingNew":{"a":1},"data":{"format":9,"budgets":[{"id":"x"}],
            "sheets":[{"id":"s","name":"S","currency":"INR","colour":"red"}],
            "items":[{"id":"i","sheetId":"s","kind":"EXPENSE","amountMinor":100,"currency":"INR","at":1,"method":"CRYPTO","tags":["a"]}]}}
        """.trimIndent()
        val file = checkNotNull(readKharchaJson(text))
        assertEquals(9, file.format)
        assertEquals(1, file.data.sheets.size)
        val item = file.data.items.single()
        assertEquals(100L, item.amountMinor)
        // A way of paying this version does not know falls back to "not set".
        assertEquals(PayMethod.NONE, item.method)
    }

    @Test
    fun oneBrokenEntryDoesNotLoseTheRest() {
        val text = """
            {"exportedAt":5,"data":{"sheets":[{"id":"s","name":"S","currency":"INR"}],
            "items":[
              {"id":"good1","sheetId":"s","kind":"EXPENSE","amountMinor":100,"currency":"INR","at":1},
              {"id":"bad-kind","sheetId":"s","kind":"LOAN","amountMinor":100,"currency":"INR","at":2},
              {"id":"bad-amount","sheetId":"s","kind":"EXPENSE","amountMinor":"lots","currency":"INR","at":3},
              "not an item at all",
              {"id":"good2","sheetId":"s","kind":"INCOME","amountMinor":200,"currency":"INR","at":4}
            ]}}
        """.trimIndent()
        val data = checkNotNull(readKharchaJson(text)).data
        assertEquals(listOf("good1", "good2"), data.items.map { it.id })
        assertEquals(1, data.sheets.size)
        // The app's own data file on the phone is rescued the same way.
        val bare = """{"sheets":[{"id":"s","name":"S","currency":"INR"}],"items":[{"id":"bad","kind":"LOAN"},{"id":"ok","sheetId":"s","kind":"EXPENSE","amountMinor":1,"currency":"INR","at":1}]}"""
        assertEquals(listOf("ok"), rescueAppData(bare)?.items?.map { it.id })
    }

    @Test
    fun theDataFileItselfOpensAsABackup() {
        val text = """{"sheets":[{"id":"s","name":"S","currency":"INR"}],"items":[{"id":"i","sheetId":"s","kind":"EXPENSE","amountMinor":100,"currency":"INR","at":1}]}"""
        val file = checkNotNull(readKharchaJson(text))
        assertEquals("backup", file.kind)
        assertEquals(1, file.data.items.size)
    }

    @Test
    fun otherFilesAreRefused() {
        assertNull(readKharchaJson("not json"))
        assertNull(readKharchaJson(""))
        assertNull(readKharchaJson("[1,2,3]"))
        assertNull(readKharchaJson("""{"hello":"world"}"""))
        assertNull(readKharchaJson("""{"app":"another-app","exportedAt":5,"data":{"sheets":[]}}"""))
        assertNull(rescueAppData("not json"))
    }

    @Test
    fun whatIsWrittenTodayReadsBackTheSame() {
        val file = KharchaFile(
            kind = "backup",
            exportedAt = 1794900000000L,
            data = AppData(
                sheets = listOf(Sheet(id = "s", name = "Personal", currency = "INR")),
                items = listOf(
                    Item(
                        id = "i", sheetId = "s", kind = Kind.EXPENSE, amountMinor = 120000L, currency = "INR", at = 1794880000000L,
                        method = PayMethod.UPI, splits = listOf(Split("p", 40000L)), covered = 1,
                    ),
                ),
            ),
        )
        val text = KharchaJson.encodeToString(KharchaFile.serializer(), file)
        assertEquals(file, readKharchaJson(text))
    }

    // ---- the sums behind a split bill ----

    @Test
    fun equalSplitCountsPeopleYouPayFor() {
        // You, one friend and your father: three heads. The friend owes one part.
        assertEquals(3, splitHeads(friends = 1, covered = 1))
        assertEquals(40000L, equalShare(120000L, friends = 1, covered = 1))
        // Without your father it is a plain half.
        assertEquals(60000L, equalShare(120000L, friends = 1, covered = 0))
        // Two friends and two people you pay for: five heads.
        assertEquals(20000L, equalShare(100000L, friends = 2, covered = 2))
    }

    @Test
    fun equalSplitNeverAsksForMoreThanTheBill() {
        // 100.00 three ways: 33.33 each, and the spare paisa stays with you.
        val each = equalShare(10000L, friends = 2, covered = 0)
        assertEquals(3333L, each)
        assertTrue(each * 2 <= 10000L)
        assertEquals(0L, equalShare(10000L, friends = 0, covered = 3))
        assertEquals(0L, equalShare(0L, friends = 2, covered = 0))
        assertEquals(5000L, equalShare(10000L, friends = 1, covered = -4))
    }
}

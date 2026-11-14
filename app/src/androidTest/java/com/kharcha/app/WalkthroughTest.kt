package com.kharcha.app

import android.content.Intent
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A robot user. It opens the app on an emulator, walks through every screen the way a
 * person would (finding things by the words on screen and tapping them), and saves a
 * screenshot of each to /sdcard/Download/kharcha-shots. The cloud build pulls those pictures
 * so the screens can be looked at without a phone.
 *
 * It uses UI Automator, Android's tool for driving any app from the outside. A step that
 * fails is logged with the words that were on screen, and the walk continues.
 */
@RunWith(AndroidJUnit4::class)
class WalkthroughTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device: UiDevice = UiDevice.getInstance(instrumentation)
    private val dir = "/sdcard/Download/kharcha-shots"
    private val report = StringBuilder()
    private val app = "com.kharcha.app"

    private fun log(line: String) {
        Log.i("KHARCHA", line)
        report.appendLine(line)
    }

    private fun pause(ms: Long) = Thread.sleep(ms)

    private fun shot(name: String) {
        dismissSystemDialogs()
        pause(900)
        device.executeShellCommand("screencap -p $dir/$name.png")
    }

    private fun find(selector: BySelector, timeout: Long = 6000): UiObject2? = device.wait(Until.findObject(selector), timeout)

    /** Slow cloud emulators pop up "System UI isn't responding". Tap "Wait" so it gets out of the way. */
    private fun dismissSystemDialogs() {
        repeat(3) {
            if (!device.hasObject(By.textContains("responding"))) return
            device.findObject(By.text("Wait"))?.click()
            pause(1500)
        }
    }

    private fun tap(selector: BySelector, what: String) {
        var node = find(selector)
        if (node == null) {
            // A system pop-up may have appeared in the meantime: clear it and look once more.
            dismissSystemDialogs()
            node = find(selector)
        }
        if (node == null) error("not on screen: $what")
        node.click()
        pause(1300)
    }

    private fun tapText(text: String) = tap(By.pkg(app).textContains(text), text)
    private fun tapExact(text: String) = tap(By.pkg(app).text(text), text)
    private fun tapDesc(text: String) = tap(By.pkg(app).descContains(text), text)

    private fun swipeUp() {
        val x = device.displayWidth / 2
        device.swipe(x, (device.displayHeight * 0.70).toInt(), x, (device.displayHeight * 0.30).toInt(), 25)
        pause(700)
    }

    private fun swipeDown() {
        val x = device.displayWidth / 2
        device.swipe(x, (device.displayHeight * 0.35).toInt(), x, (device.displayHeight * 0.80).toInt(), 25)
        pause(700)
    }

    /** Swipes up until the text is on screen, then taps it. */
    private fun scrollAndTap(text: String) {
        repeat(8) {
            if (find(By.pkg(app).textContains(text), 700) != null) {
                tapText(text)
                return
            }
            swipeUp()
        }
        error("not found after scrolling: $text")
    }

    /** Types into the text box that is number `index` on screen, counted from the top. */
    private fun type(index: Int, text: String) {
        val boxes = device.findObjects(By.clazz("android.widget.EditText")).sortedBy { it.visibleBounds.centerY() }
        val box = boxes.getOrNull(index) ?: error("no text box number $index (found ${boxes.size})")
        box.text = text
        pause(600)
    }

    private fun back() = tapDesc("Back")

    /** Presses Back only if the keyboard is really open. With no keyboard, Back would leave the screen. */
    private fun closeKeyboard() {
        val shown = device.executeShellCommand("dumpsys input_method").contains("mInputShown=true")
        if (shown) {
            device.pressBack()
            pause(900)
        }
    }

    private fun step(name: String, action: () -> Unit) {
        try {
            dismissSystemDialogs()
            action()
            log("ok    $name")
        } catch (t: Throwable) {
            val visible = runCatching {
                device.findObjects(By.textStartsWith("")).mapNotNull { it.text }.filter { it.isNotBlank() }.take(30).joinToString(" | ")
            }.getOrDefault("")
            log("FAIL  $name: ${t.message?.lineSequence()?.firstOrNull()}  [on screen: $visible]")
            runCatching { device.executeShellCommand("screencap -p $dir/fail-${name.take(2)}.png") }
        }
    }

    @Test
    fun walkThroughEveryScreen() {
        // The app has animations that never stop (the turning shape on the hero card), so never wait long for "idle".
        Configurator.getInstance().setWaitForIdleTimeout(1200)
        device.executeShellCommand("mkdir -p $dir")

        val context = instrumentation.targetContext
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)!!
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(launch)
        device.wait(Until.hasObject(By.pkg(context.packageName).depth(0)), 15000)
        pause(4000)
        dismissSystemDialogs()

        step("01 welcome") { shot("01-welcome") }
        step("02 start with sample data") {
            scrollAndTap("Try with sample data")
            device.wait(Until.hasObject(By.pkg(app).text("Sheets")), 20000)
            pause(2500)
            shot("02-home")
        }
        step("03 search every sheet") {
            tapDesc("Search")
            pause(1200)
            type(0, "rent")
            pause(1500)
            shot("03-home-search")
            closeKeyboard()
            tapDesc("Close search")
        }
        step("04 a sheet") { tapText("Personal"); pause(2000); shot("04-sheet") }
        step("05 the sheet, further down") { swipeUp(); shot("05-sheet-list"); swipeDown(); swipeDown() }
        step("06 period picker") { tapDesc("Period:"); pause(1200); shot("06-period"); tapExact("This month"); pause(1500); shot("07-sheet-month") }
        step("08 pending tab") { tapText("Pending  "); pause(1200); shot("08-pending"); tapText("This month  "); pause(900) }
        step("09 filters") { tapDesc("Filters"); pause(1500); shot("09-filters"); tapExact("Expense"); pause(900); tapText("Show "); pause(1200); shot("10-filtered"); tapExact("Clear") }
        step("11 new item, keypad") {
            tapDesc("New item"); pause(1800)
            shot("11-new-item")
            // No zero here: "0" is also what the empty amount box shows, and tapping that closes the keypad.
            listOf("1", "2", "5", "+", "3", "5").forEach { tapExact(it) }
            pause(600)
            shot("12-new-item-sum")
        }
        step("13 new item, lower half") {
            tapDesc("Hide the keypad")
            pause(800)
            swipeUp()
            shot("13-new-item-more")
            tapDesc("Save"); pause(2000)
            shot("14-sheet-after-save")
        }
        step("15 item details") { tapText("Rent"); pause(1800); shot("15-details"); back() }
        step("16 sheet menu") { tapDesc("More"); pause(1200); shot("16-sheet-menu"); tapExact("Stats"); pause(2200); shot("17-stats") }
        step("18 stats, lower half") { swipeUp(); shot("18-stats-more") }
        step("19 trends") { tapDesc("Trends"); pause(2200); shot("19-trends"); swipeUp(); shot("20-trends-more"); back(); back() }
        step("21 exchange rates") { tapDesc("More"); pause(1000); tapExact("Exchange rates"); pause(4000); shot("21-rates"); back() }
        step("22 select mode") { tapDesc("More"); pause(1000); tapExact("Select"); pause(1200); shot("22-select"); back() }
        step("23 edit sheet") { tapDesc("More"); pause(1000); tapExact("Edit sheet"); pause(1500); shot("23-sheet-edit"); back(); back() }
        step("23a friends") {
            tapDesc("Friends"); pause(1500); shot("23a-friends-empty")
            tapText("Add a friend"); pause(1200)
            type(0, "Rahul"); type(1, "rahul@upi"); closeKeyboard()
            tapExact("Save"); pause(1200); shot("23b-friends")
            tapText("Rahul"); pause(1500); shot("23c-friend")
            back(); back()
        }
        step("23d split a bill") {
            tapDesc("New item"); pause(1800)
            listOf("9", "6").forEach { tapExact(it) }
            tapDesc("Hide the keypad"); pause(700)
            swipeUp()
            tapExact("Split with"); pause(1200); tapExact("Rahul"); pause(700); shot("23d-split-with")
            tapText("Split with 1"); pause(1000)
            shot("23e-split-form")
            tapDesc("Save"); pause(1800)
        }
        step("23f home cards, insights, habit") {
            shot("23f-home-cards")
            tapText("Where could you save"); pause(2000); shot("23g-insights"); swipeUp(); shot("23h-insights-more")
            scrollAndTap("Ask Gemini"); pause(1500); shot("23i-ask-gemini"); device.pressBack(); pause(900)
            back()
            tapExact("Streak"); pause(2000); shot("23j-habit"); swipeUp(); shot("23k-goals")
            back()
        }
        step("24 settings") { tapDesc("Settings"); pause(1500); shot("24-settings"); swipeUp(); shot("25-settings-more"); swipeDown() }
        step("26 categories") {
            tapExact("Categories"); pause(1500); shot("26-categories")
            tapDesc("New category"); pause(1500); shot("27-new-category")
            tapExact("Edit icon"); pause(1500); shot("28-icons")
            device.pressBack(); pause(900)
            back(); back()
        }
        step("29 backup") { tapExact("Backup"); pause(1500); shot("29-backup"); back() }
        step("30 export") { tapExact("Export"); pause(1500); shot("30-export"); back() }
        step("31 import") { tapExact("Import"); pause(1500); shot("31-import"); back() }
        step("32 app icon") { scrollAndTap("App icon"); pause(1500); shot("32-app-icon"); back() }
        step("33 privacy") { scrollAndTap("Privacy"); pause(1500); shot("33-privacy"); back() }
        step("33a reminders") { swipeDown(); swipeDown(); scrollAndTap("Reminders and backup"); pause(1500); shot("33a-reminders"); back() }
        step("34 dark theme") {
            swipeDown()
            scrollAndTap("Appearance"); pause(1500)
            tapExact("Dark"); pause(1500); shot("34-appearance-dark")
            swipeUp(); shot("34a-appearance-more")
            tapDesc("Teal"); pause(1200); shot("34b-appearance-teal")
            swipeDown()
            back(); back(); pause(1500)
            shot("35-home-dark")
        }
        step("36 dark sheet") { tapText("Personal"); pause(2000); shot("36-sheet-dark"); swipeUp(); shot("37-sheet-list-dark"); back() }
        step("38 new sheet form") { tapText("New sheet"); pause(1500); shot("38-new-sheet"); back() }

        println("KHARCHA-WALKTHROUGH\n$report")
    }
}

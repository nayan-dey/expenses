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

    private fun launchApp() {
        val context = instrumentation.targetContext
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)!!
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(launch)
        device.wait(Until.hasObject(By.pkg(context.packageName).depth(0)), 15000)
        pause(3000)
    }

    /**
     * Gets back to the first screen from wherever the last step ended: presses Back until the
     * home header is showing, and opens the app again if Back closed it. Every group of steps
     * starts here, so one step that loses its way does not spoil the ones after it.
     */
    private fun goHome() {
        repeat(12) {
            dismissSystemDialogs()
            if (!device.hasObject(By.pkg(app))) launchApp()
            if (device.hasObject(By.pkg(app).desc("Settings")) && device.hasObject(By.pkg(app).text("Kharcha"))) return
            device.pressBack()
            pause(900)
        }
    }

    private fun openSheet() {
        goHome()
        tapText("Personal")
        pause(1800)
    }

    private fun openSettings() {
        goHome()
        tapDesc("Settings")
        pause(1500)
    }

    @Test
    fun walkThroughEveryScreen() {
        // The app has animations that never stop (the turning shape on the hero card, the swaying tree), so never wait long for "idle".
        Configurator.getInstance().setWaitForIdleTimeout(1200)
        device.executeShellCommand("mkdir -p $dir")
        launchApp()
        dismissSystemDialogs()

        step("01 welcome") { shot("01-welcome") }
        step("02 start with sample data") {
            scrollAndTap("Try with sample data")
            device.wait(Until.hasObject(By.pkg(app).text("Sheets")), 20000)
            pause(2500)
            shot("02-home")
        }
        step("03 home, lower half") { swipeUp(); shot("03-home-more") }
        step("04 search every sheet") {
            goHome()
            tapDesc("Search"); pause(1200)
            type(0, "rent"); pause(1500)
            shot("04-home-search")
            // Close the search again, or its results stay on the home screen for every later step.
            closeKeyboard()
            tapDesc("Close search"); pause(900)
        }

        // ---- a sheet ----
        step("05 a sheet") { openSheet(); shot("05-sheet") }
        step("06 the sheet, further down") { swipeUp(); shot("06-sheet-list") }
        step("07 period picker") { openSheet(); tapDesc("Period:"); pause(1200); shot("07-period"); tapExact("This month"); pause(1500); shot("08-sheet-month") }
        step("09 pending tab") { tapText("Pending  "); pause(1200); shot("09-pending") }
        step("10 filters") { openSheet(); tapDesc("Filters"); pause(1500); shot("10-filters"); tapExact("Expense"); pause(900); tapText("Show "); pause(1200); shot("11-filtered") }
        step("12 sheet menu") { openSheet(); tapDesc("More"); pause(1200); shot("12-sheet-menu") }
        step("13 select mode") { openSheet(); tapDesc("More"); pause(1000); tapExact("Select"); pause(1200); shot("13-select") }
        step("14 edit sheet") { openSheet(); tapDesc("More"); pause(1000); tapExact("Edit sheet"); pause(1500); shot("14-sheet-edit") }
        step("15 item details") { openSheet(); tapText("Corner store"); pause(1800); shot("15-details"); swipeUp(); shot("16-details-more") }

        // ---- a new item ----
        step("17 new item, keypad") {
            openSheet()
            tapDesc("New item"); pause(1800)
            shot("17-new-item")
            // No zero here: "0" is also what the empty amount box shows, and tapping that closes the keypad.
            listOf("1", "2", "5", "+", "3", "5").forEach { tapExact(it) }
            pause(600)
            shot("18-new-item-sum")
            tapDesc("Hide the keypad"); pause(800)
            swipeUp()
            shot("19-new-item-more")
            tapDesc("Save"); pause(2000)
            shot("20-sheet-after-save")
        }

        // ---- stats, trends, rates ----
        step("21 stats") { openSheet(); tapDesc("Stats"); pause(2200); shot("21-stats"); swipeUp(); shot("22-stats-more") }
        step("23 trends") { openSheet(); tapDesc("More"); pause(1000); tapExact("Trends"); pause(2200); shot("23-trends"); swipeUp(); shot("24-trends-more") }
        step("25 exchange rates") { openSheet(); tapDesc("More"); pause(1000); tapExact("Exchange rates"); pause(4000); shot("25-rates") }

        // ---- friends and splitting ----
        step("26 friends") {
            goHome()
            tapDesc("Friends"); pause(1500); shot("26-friends-empty")
            tapDesc("Add a friend"); pause(1200)
            type(0, "Rahul"); type(1, "rahul@upi"); closeKeyboard()
            tapExact("Save"); pause(1200); shot("27-friends")
        }
        step("28 split a bill") {
            openSheet()
            tapDesc("New item"); pause(1800)
            listOf("9", "6").forEach { tapExact(it) }
            tapDesc("Hide the keypad"); pause(700)
            swipeUp()
            tapExact("Split with"); pause(1200); tapExact("Rahul"); pause(700); shot("28-split-with")
            tapText("Split with 1"); pause(1000)
            shot("29-split-form")
            tapDesc("Save"); pause(1800)
        }
        step("30 a friend") {
            goHome()
            tapDesc("Friends"); pause(1500); shot("30-friends-owed")
            tapText("Rahul"); pause(1500); shot("31-friend")
            tapExact("Settle up"); pause(1200); shot("32-settle")
        }

        // ---- insights, habit ----
        step("33 insights") {
            goHome()
            tapText("Where could you save"); pause(2000); shot("33-insights"); swipeUp(); shot("34-insights-more")
            scrollAndTap("Ask Gemini"); pause(1500); shot("35-ask-gemini")
        }
        step("36 habit and goals") {
            goHome(); tapExact("STREAK"); pause(2000); shot("36-habit"); swipeUp(); shot("37-goals")
            scrollAndTap("New goal"); pause(1200)
            type(0, "Emergency fund"); type(1, "50000"); type(2, "2000"); closeKeyboard()
            tapExact("Save"); pause(1200); shot("37a-goal")
        }

        // ---- settings ----
        step("38 settings") { openSettings(); shot("38-settings"); swipeUp(); shot("39-settings-more") }
        step("40 categories") {
            openSettings()
            scrollAndTap("Categories"); pause(1500); shot("40-categories")
            tapDesc("New category"); pause(1500); shot("41-new-category")
            tapExact("Edit icon"); pause(1500); shot("42-icons")
        }
        step("43 backup") { openSettings(); scrollAndTap("Backup"); pause(1500); shot("43-backup") }
        step("44 export") { openSettings(); scrollAndTap("Export"); pause(1500); shot("44-export") }
        step("45 import") { openSettings(); scrollAndTap("Import"); pause(1500); shot("45-import") }
        step("46 reminders") { openSettings(); scrollAndTap("Reminders and backup"); pause(1500); shot("46-reminders") }
        step("47 app icon") { openSettings(); scrollAndTap("App icon"); pause(1500); shot("47-app-icon") }
        step("48 privacy") { openSettings(); scrollAndTap("Privacy"); pause(1500); shot("48-privacy") }
        step("49 trash") { openSettings(); scrollAndTap("Trash"); pause(1500); shot("49-trash") }
        step("50 appearance") {
            openSettings()
            scrollAndTap("Appearance"); pause(1500); shot("50-appearance")
            tapExact("Dark"); pause(1500); shot("51-appearance-dark")
            swipeUp(); shot("52-appearance-more")
        }

        // ---- the same screens in the dark theme, then another accent ----
        step("53 dark home") { goHome(); shot("53-home-dark"); swipeUp(); shot("54-home-more-dark") }
        step("55 dark sheet") { openSheet(); shot("55-sheet-dark"); swipeUp(); shot("56-sheet-list-dark") }
        step("57 dark new item") { openSheet(); tapDesc("New item"); pause(1800); shot("57-new-item-dark") }
        step("58 dark stats") { openSheet(); tapDesc("Stats"); pause(2200); shot("58-stats-dark") }
        step("59 dark habit") { goHome(); tapExact("STREAK"); pause(2000); shot("59-habit-dark") }
        step("60 teal accent") {
            openSettings()
            scrollAndTap("Appearance"); pause(1500)
            swipeUp()
            tapDesc("Teal"); pause(1200)
            goHome(); shot("60-home-teal")
        }
        step("61 new sheet form") { goHome(); scrollAndTap("New sheet"); pause(1500); shot("61-new-sheet") }

        println("KHARCHA-WALKTHROUGH\n$report")
    }
}

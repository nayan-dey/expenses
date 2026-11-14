package com.kharcha.app

import android.content.Context
import com.kharcha.app.core.model.AppData
import com.kharcha.app.core.model.Rates
import com.kharcha.app.core.model.Settings
import com.kharcha.app.data.Exchange
import com.kharcha.app.data.ImageStore
import com.kharcha.app.data.JsonFile
import com.kharcha.app.data.Ledger
import com.kharcha.app.data.RatesRepository
import com.kharcha.app.core.util.Dates
import com.kharcha.app.platform.AppLock
import com.kharcha.app.platform.AutoExport
import com.kharcha.app.platform.Reminders
import com.kharcha.app.platform.Widgets
import com.kharcha.app.platform.dailyNudge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/** Settings, kept in their own small file. Read as a flow; change with [update]. */
class SettingsStore(private val file: JsonFile<Settings>) {
    val settings: StateFlow<Settings> get() = file.state
    val current: Settings get() = file.value

    /** Changes settings: `update { it.copy(sensitive = true) }`. */
    fun update(change: (Settings) -> Settings) = file.update(change)
}

/**
 * The app's parts list, built once when the app starts ("manual dependency injection").
 * Everything that needs something else gets it handed in here, in one readable place.
 *
 * Note what is NOT here: no database, no login, no server. Three JSON files hold everything.
 */
class AppContainer(context: Context) {

    /** The current time. Passed around as a function so sample data and tests can choose their own. */
    val now: () -> Long = { System.currentTimeMillis() }

    /** Work that should outlive any single screen. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val appContext: Context = context.applicationContext
    private val dir: File = context.filesDir
    // Whenever the data or the settings reach the disk, the home-screen widgets are redrawn.
    private val dataFile = JsonFile(File(dir, "kharcha.json"), AppData.serializer(), AppData(), appScope) { Widgets.refresh(appContext) }
    private val settingsFile = JsonFile(File(dir, "settings.json"), Settings.serializer(), Settings(), appScope) { Widgets.refresh(appContext) }
    private val ratesFile = JsonFile(File(dir, "rates.json"), Rates.serializer(), Rates(), appScope)

    val images = ImageStore(context)
    val ledger = Ledger(dataFile, images, now)
    val settings = SettingsStore(settingsFile)
    val rates = RatesRepository(ratesFile, now)
    val exchange = Exchange(ledger, images, now)
    val lock = AppLock()

    /** Where a tapped notification or widget wants the app to go (see platform/Launch). Null = nowhere special. */
    val pendingAction = MutableStateFlow<String?>(null)

    /** True once all three files have been read. The splash screen stays up until then. */
    val loaded: StateFlow<Boolean> =
        combine(dataFile.loaded, settingsFile.loaded, ratesFile.loaded) { a, b, c -> a && b && c }
            .stateIn(appScope, SharingStarted.Eagerly, false)

    init {
        appScope.launch {
            loaded.first { it }
            // Once per start: pictures left behind by a form that was never saved.
            runCatching { images.deleteUnused(ledger.picturesInUse()) }
            housekeeping()
        }
    }

    /** Small jobs done whenever the app comes to the front. */
    private suspend fun housekeeping() {
        // Repeating items that came due while the app was closed.
        ledger.materializeRepeats()
        // The trash empties itself after 30 days.
        ledger.emptyTrash(olderThan = now() - 30L * 24 * 60 * 60 * 1000)
        if (settings.current.autoRates) runCatching { rates.refreshIfOlderThan(12L * 60 * 60 * 1000) }
        autoExportIfDue()
        scheduleDaily()
    }

    /** Writes today's automatic backup to Downloads, once a day, when that is switched on. */
    private suspend fun autoExportIfDue() {
        val day = Dates.iso(now())
        if (!settings.current.autoExport || settings.current.lastAutoExportDay == day || ledger.current.items.isEmpty()) return
        if (AutoExport.run(appContext, exchange, day)) {
            settings.update { it.copy(lastAutoExportDay = day, lastBackupAt = now()) }
        }
    }

    /** Sets tomorrow's wake-up when the reminder or the automatic backup needs one, and removes it otherwise. */
    fun scheduleDaily() {
        val current = settings.current
        if (current.reminder || current.autoExport) {
            Reminders.schedule(appContext, current.reminderHour, current.reminderMinute)
        } else {
            Reminders.cancel(appContext)
        }
    }

    /**
     * The daily wake-up (see platform/Reminders.kt): fill in repeats, write the automatic
     * backup, post the reminder, redraw the widgets, and set the next wake-up.
     * `done` must be called when finished: Android is waiting on it.
     */
    fun dailyTick(done: () -> Unit) {
        appScope.launch {
            try {
                withTimeoutOrNull(9000L) {
                    loaded.first { it }
                    ledger.materializeRepeats()
                    autoExportIfDue()
                    if (settings.current.reminder) {
                        val nudge = dailyNudge(ledger.current, settings.current, rates.rates.value, now())
                        Reminders.show(appContext, nudge.title, nudge.text, nudge.action)
                    }
                    Widgets.refresh(appContext)
                }
                runCatching { scheduleDaily() }
            } finally {
                done()
            }
        }
    }

    /** After a restart of the phone: set the daily wake-up again. */
    fun rescheduleDaily(done: () -> Unit) {
        appScope.launch {
            try {
                withTimeoutOrNull(9000L) { loaded.first { it } }
                runCatching { scheduleDaily() }
            } finally {
                done()
            }
        }
    }

    fun onForeground() {
        lock.onForeground(now())
        if (loaded.value) appScope.launch { housekeeping() }
    }

    /** The app is leaving the screen: make sure the latest changes are on disk. */
    fun onBackground() {
        lock.onBackground(now())
        appScope.launch {
            dataFile.flush()
            settingsFile.flush()
            ratesFile.flush()
        }
    }
}

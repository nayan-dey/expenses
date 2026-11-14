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
import com.kharcha.app.platform.AppLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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

    private val dir: File = context.filesDir
    private val dataFile = JsonFile(File(dir, "kharcha.json"), AppData.serializer(), AppData(), appScope)
    private val settingsFile = JsonFile(File(dir, "settings.json"), Settings.serializer(), Settings(), appScope)
    private val ratesFile = JsonFile(File(dir, "rates.json"), Rates.serializer(), Rates(), appScope)

    val images = ImageStore(context)
    val ledger = Ledger(dataFile, images, now)
    val settings = SettingsStore(settingsFile)
    val rates = RatesRepository(ratesFile, now)
    val exchange = Exchange(ledger, images, now)
    val lock = AppLock()

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

package com.kharcha.app.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.File

/** How Kharcha reads and writes JSON everywhere: forgiving on the way in, compact on the way out. */
val KharchaJson: Json = Json {
    // A newer app may have added fields or enum values; an older app must still open the file.
    ignoreUnknownKeys = true
    coerceInputValues = true
    encodeDefaults = false
    explicitNulls = false
}

/**
 * One value kept in one JSON file. This is the app's whole storage layer: there is no database.
 *
 *   - The value lives in memory as a StateFlow, so every screen that shows it redraws by itself
 *     when it changes.
 *   - [update] changes the value at once and saves it to disk a moment later, off the main thread.
 *   - A save writes a new file first and only then swaps it in, keeping the previous file as
 *     `.bak`. If the phone dies mid-save, the old data is still there.
 */
class JsonFile<T>(
    private val file: File,
    private val serializer: KSerializer<T>,
    private val default: T,
    scope: CoroutineScope,
    /**
     * A second, more patient way to read the file, tried when the normal one fails: it may
     * keep the entries it understands and leave out the ones it does not. Null = give up.
     */
    private val rescue: (String) -> T? = { null },
    /** Called after each write to disk (the home-screen widgets redraw from here). */
    private val onSaved: () -> Unit = {},
) {
    private val backup = File(file.parentFile, file.name + ".bak")
    private val _state = MutableStateFlow(default)
    private val _loaded = MutableStateFlow(false)
    private val dirty = Channel<Unit>(Channel.CONFLATED)
    private val writeLock = Any()

    val state: StateFlow<T> = _state.asStateFlow()

    /** False until the file has been read. Screens wait for this before drawing. */
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    val value: T get() = _state.value

    init {
        scope.launch(Dispatchers.IO) {
            _state.value = read()
            _loaded.value = true
            // Saves are batched: many quick changes become one write.
            for (signal in dirty) {
                delay(200)
                runCatching { write(_state.value) }
                runCatching { onSaved() }
            }
        }
    }

    /** Changes the value: `update { it.copy(name = "New") }`. Safe to call from any thread. */
    fun update(change: (T) -> T) {
        _state.update(change)
        dirty.trySend(Unit)
    }

    /** Writes to disk now. Called when the app goes to the background. */
    suspend fun flush() {
        if (!_loaded.value) return
        withContext(Dispatchers.IO) { runCatching { write(_state.value) } }
    }

    private fun read(): T {
        for (candidate in listOf(file, backup)) {
            if (!candidate.exists()) continue
            val text = runCatching { candidate.readText() }.getOrNull() ?: continue
            val parsed = runCatching { KharchaJson.decodeFromString(serializer, text) }.getOrNull()
            if (parsed != null) return parsed
            // Not readable the normal way. Keep the file aside untouched, in case it is needed
            // later, then save what can be saved from it.
            val aside = File(file.parentFile, candidate.name + ".unreadable")
            val rescued = runCatching { rescue(text) }.getOrNull()
            if (rescued != null) {
                runCatching { candidate.copyTo(aside, overwrite = true) }
                return rescued
            }
            runCatching { candidate.renameTo(aside) }
        }
        return default
    }

    private fun write(value: T) {
        synchronized(writeLock) {
            file.parentFile?.mkdirs()
            val fresh = File(file.parentFile, file.name + ".tmp")
            fresh.writeText(KharchaJson.encodeToString(serializer, value))
            if (file.exists()) {
                backup.delete()
                file.renameTo(backup)
            }
            fresh.renameTo(file)
        }
    }
}

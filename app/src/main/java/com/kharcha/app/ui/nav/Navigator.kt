package com.kharcha.app.ui.nav

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kharcha.app.AppContainer
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Moves between screens. It owns the back stack (a plain list of [Route] keys).
 * `go` adds a screen on top; `back` removes the top one.
 */
class Navigator {
    val backStack = mutableStateListOf<Route>(HomeRoute)

    /** How the last move should animate: "push" or "pop". */
    var lastMove: String by mutableStateOf("push")
        private set

    fun go(route: Route) {
        if (backStack.lastOrNull() == route) return
        lastMove = "push"
        backStack.add(route)
    }

    /** Replaces the screen on top (the "new sheet" form hands over to the sheet it made). */
    fun replaceTop(route: Route) {
        lastMove = "push"
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
        backStack.add(route)
    }

    /** Returns false when there is nothing left to go back to (the system then closes the app). */
    fun back(): Boolean {
        lastMove = "pop"
        if (backStack.size <= 1) return false
        backStack.removeAt(backStack.lastIndex)
        return true
    }

    /** Drops every screen showing this sheet (used after the sheet is deleted). */
    fun leaveSheet(sheetId: String) {
        lastMove = "pop"
        backStack.removeAll { route ->
            when (route) {
                is SheetRoute -> route.sheetId == sheetId
                is ItemRoute -> route.sheetId == sheetId
                is StatsRoute -> route.sheetId == sheetId
                is TrendsRoute -> route.sheetId == sheetId
                is SheetEditRoute -> route.sheetId == sheetId
                else -> false
            }
        }
        if (backStack.isEmpty()) backStack.add(HomeRoute)
    }
}

/**
 * Short messages at the bottom of the screen ("Saved", "Moved to trash").
 * They are Material Snackbars; this class just makes showing one a single call.
 */
class Messenger(val host: SnackbarHostState, private val launch: (suspend () -> Unit) -> Job) {
    private var current: Job? = null

    /** Shows a message. With `action` it gets a button ("Undo") that runs `onAction` when tapped. */
    fun show(text: String, action: String? = null, onAction: (() -> Unit)? = null) {
        // A new message replaces the one on screen instead of queueing behind it.
        current?.cancel()
        current = launch {
            val result = host.showSnackbar(
                message = text,
                actionLabel = action,
                duration = if (action == null) SnackbarDuration.Short else SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) onAction?.invoke()
        }
    }
}

/** Keeps the [Navigator] and the message host alive when the screen rotates or the theme changes. */
class NavViewModel : ViewModel() {
    val navigator = Navigator()
    val messenger = Messenger(SnackbarHostState()) { block -> viewModelScope.launch { block() } }
}

val LocalNav = staticCompositionLocalOf<Navigator> { error("No Navigator provided") }
val LocalContainer = staticCompositionLocalOf<AppContainer> { error("No AppContainer provided") }
val LocalMessenger = staticCompositionLocalOf<Messenger> { error("No Messenger provided") }

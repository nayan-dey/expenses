package com.kharcha.app.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kharcha.app.data.KharchaJson
import com.kharcha.app.ui.nav.LocalContainer
import kotlinx.coroutines.delay
import kotlinx.serialization.KSerializer

/** The frame every scrolling screen sits in: full size, below the status bar, scrolls vertically. */
@Composable
fun ScreenColumn(
    modifier: Modifier = Modifier,
    scroll: ScrollState = rememberScrollState(),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .verticalScroll(scroll)
            .padding(bottom = 24.dp),
        content = content,
    )
}

/** The current time, refreshed every minute so "today" rolls over while the app is open. */
@Composable
fun rememberNow(): Long {
    val clock = LocalContainer.current.now
    val now by produceState(initialValue = clock()) {
        while (true) {
            delay(60_000L)
            value = clock()
        }
    }
    return now
}

/**
 * Lets `rememberSaveable` keep any @Serializable value (a form being filled in, a filter)
 * across a screen rotation, by storing it as a short JSON text.
 */
fun <T : Any> jsonSaver(serializer: KSerializer<T>): Saver<T, String> = Saver(
    save = { KharchaJson.encodeToString(serializer, it) },
    restore = { runCatching { KharchaJson.decodeFromString(serializer, it) }.getOrNull() },
)

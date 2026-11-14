@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.kharcha.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kharcha.app.ui.icons.Ic
import com.kharcha.app.ui.theme.KharchaTheme

/*
 * Top bars and the search box, built from Google's Material 3 components:
 *   AppBar      -> TopAppBar (with Material 3 Expressive's subtitle slot)
 *   TabHeader   -> a large heading for the home screen
 *   SearchField -> TextField, pill shaped
 */

/** Bars sit inside a screen that is already below the status bar, so they add no inset of their own. */
private val NoInsets = WindowInsets(0, 0, 0, 0)

/** The bar of every inner screen: back arrow, title, optional second line, actions. */
@Composable
fun AppBar(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "",
    subtitle: String = "",
    actions: @Composable RowScope.() -> Unit = {},
) {
    // The bar is the first thing inside a scrolling page. Moving it down by exactly the
    // distance scrolled keeps it pinned to the top, so Back is always within reach.
    val scroll = LocalScreenScroll.current
    val surface = MaterialTheme.colorScheme.surface
    val pinned = if (scroll == null) {
        Modifier
    } else {
        Modifier
            .zIndex(1f)
            .graphicsLayer { translationY = scroll.value.toFloat() }
            .background(surface)
    }
    TopAppBar(
        title = {
            if (title.isNotEmpty()) {
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() })
            }
        },
        subtitle = {
            if (subtitle.isNotEmpty()) Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        modifier = modifier.then(pinned),
        navigationIcon = { IconBtn(Ic.arrow_back, "Back", onBack) },
        actions = actions,
        windowInsets = NoInsets,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent),
    )
}

/** The big heading of the home screen: a small line above, the title, and actions on the right. */
@Composable
fun TabHeader(
    title: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 14.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            if (eyebrow != null) Eyebrow(eyebrow)
            Text(
                title,
                style = MaterialTheme.typography.displaySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
        }
        actions()
    }
}

/** The search box: a pill-shaped Material TextField with a search icon and a clear button. */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    /** true = put the cursor in the box (and open the keyboard) as soon as it appears. */
    autoFocus: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val focus = LocalFocusManager.current
    val requester = remember { FocusRequester() }
    if (autoFocus) LaunchedEffect(Unit) { runCatching { requester.requestFocus() } }
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .focusRequester(requester),
        placeholder = { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { KIcon(Ic.search) },
        trailingIcon = {
            if (value.isNotEmpty()) IconBtn(Ic.close, "Clear search", { onValueChange("") })
        },
        singleLine = true,
        shape = CircleShape,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = scheme.surfaceContainerHigh,
            unfocusedContainerColor = scheme.surfaceContainerHigh,
            // A pill has no underline.
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
    )
}

/**
 * The bar that floats at the bottom of the home screen and of a sheet: a few quick actions
 * and the "add" button. It is Material 3 Expressive's floating toolbar with its own FAB.
 */
@Composable
fun ActionDock(
    onAdd: () -> Unit,
    addLabel: String,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val haptics = rememberHaptics()
    val scheme = MaterialTheme.colorScheme
    // Marigold in both themes: the bright accent in the dark theme, its soft container in the light one.
    val dark = KharchaTheme.dark
    HorizontalFloatingToolbar(
        expanded = true,
        floatingActionButton = {
            FloatingToolbarDefaults.VibrantFloatingActionButton(
                onClick = {
                    haptics.click()
                    onAdd()
                },
                containerColor = if (dark) scheme.tertiary else scheme.tertiaryContainer,
                contentColor = if (dark) scheme.onTertiary else scheme.onTertiaryContainer,
            ) {
                KIcon(Ic.add, size = 26.dp, contentDescription = addLabel)
            }
        },
        modifier = modifier,
        content = content,
    )
}

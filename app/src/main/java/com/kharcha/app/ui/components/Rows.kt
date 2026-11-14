@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.kharcha.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kharcha.app.ui.icons.Ic

/*
 * Ready-made rows for grouped lists (settings, forms), and the overflow menu.
 * Each one is a Material SegmentedListItem (through GroupItem) with its slots filled in.
 */

/**
 * A row that opens something or shows a value: a tinted icon tile, a title, an optional
 * second line, an optional value on the right and a chevron.
 *
 * @param external true = the row leaves the app (shows an arrow pointing out instead of a chevron)
 */
@Composable
fun SettingRow(
    index: Int,
    count: Int,
    title: String,
    modifier: Modifier = Modifier,
    icon: Ic? = null,
    iconColor: Color = MaterialTheme.colorScheme.primary,
    subtitle: String? = null,
    value: String? = null,
    external: Boolean = false,
    background: Color = Color.Unspecified,
    onClick: (() -> Unit)? = null,
) {
    val leading: (@Composable () -> Unit)? = if (icon != null) { { IconTile(icon, iconColor) } } else null
    GroupItem(index, count, modifier, onClick = onClick, background = background) {
        RowContent(
            title = title,
            subtitle = subtitle,
            leading = leading,
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (value != null) {
                        Text(
                            value,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (onClick != null) {
                        if (external) KIcon(Ic.arrow_outward, size = 20.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant) else Chevron()
                    }
                }
            },
        )
    }
}

/** A row with a switch at the end. Tapping anywhere on the row flips it. */
@Composable
fun SwitchRow(
    index: Int,
    count: Int,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    icon: Ic? = null,
    iconColor: Color = MaterialTheme.colorScheme.primary,
    subtitle: String? = null,
    background: Color = Color.Unspecified,
) {
    val haptics = rememberHaptics()
    val leading: (@Composable () -> Unit)? = if (icon != null) { { IconTile(icon, iconColor) } } else null
    GroupItem(
        index, count, modifier,
        onClick = {
            haptics.toggle(!checked)
            onCheckedChange(!checked)
        },
        background = background,
    ) {
        RowContent(
            title = title,
            subtitle = subtitle,
            leading = leading,
            trailing = { KSwitch(checked = checked, onCheckedChange = onCheckedChange, label = title) },
        )
    }
}

/** One choice in a pick-one list: the chosen row shows a tick. */
@Composable
fun CheckRow(
    index: Int,
    count: Int,
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    background: Color = Color.Unspecified,
    leading: (@Composable () -> Unit)? = null,
) {
    val haptics = rememberHaptics()
    GroupItem(
        index, count, modifier,
        onClick = {
            haptics.tick()
            onClick()
        },
        background = background,
    ) {
        RowContent(
            title = title,
            subtitle = subtitle,
            leading = leading,
            trailing = {
                if (selected) KIcon(Ic.check, tint = MaterialTheme.colorScheme.primary, contentDescription = "Selected")
            },
        )
    }
}

/** The menu behind a "more" button: Material's DropdownMenu. Put [MenuItem]s inside. */
@Composable
fun KMenu(expanded: Boolean, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss, content = content)
}

@Composable
fun MenuItem(text: String, icon: Ic, onClick: () -> Unit, danger: Boolean = false) {
    val color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    DropdownMenuItem(
        text = { Text(text, color = color) },
        onClick = onClick,
        leadingIcon = { KIcon(icon, size = 22.dp, tint = if (danger) color else MaterialTheme.colorScheme.onSurfaceVariant) },
    )
}

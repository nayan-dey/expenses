package com.kharcha.app.ui.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kharcha.app.core.model.Category
import com.kharcha.app.core.model.Kind
import com.kharcha.app.core.util.Dates
import com.kharcha.app.core.util.newId
import com.kharcha.app.data.Seed
import com.kharcha.app.ui.components.AppBar
import com.kharcha.app.ui.components.Btn
import com.kharcha.app.ui.components.BtnStyle
import com.kharcha.app.ui.components.ButtonGroup
import com.kharcha.app.ui.components.CategoryDot
import com.kharcha.app.ui.components.FieldKind
import com.kharcha.app.ui.components.Foot
import com.kharcha.app.ui.components.Gap
import com.kharcha.app.ui.components.GroupItem
import com.kharcha.app.ui.components.GroupOption
import com.kharcha.app.ui.components.IconBtn
import com.kharcha.app.ui.components.IconBtnStyle
import com.kharcha.app.ui.components.KField
import com.kharcha.app.ui.components.KGroup
import com.kharcha.app.ui.components.KIcon
import com.kharcha.app.ui.components.KSheet
import com.kharcha.app.ui.components.ScreenColumn
import com.kharcha.app.ui.components.SearchField
import com.kharcha.app.ui.components.SectionHeader
import com.kharcha.app.ui.components.SettingRow
import com.kharcha.app.ui.components.jsonSaver
import com.kharcha.app.ui.components.onColor
import com.kharcha.app.ui.components.rememberHaptics
import com.kharcha.app.ui.icons.Ic
import com.kharcha.app.ui.icons.IconGroups
import com.kharcha.app.ui.nav.CategoryRoute
import com.kharcha.app.ui.nav.LocalContainer
import com.kharcha.app.ui.nav.LocalMessenger
import com.kharcha.app.ui.nav.LocalNav

/** New category, or edit one: its kind, name, icon and colour. */
@Composable
fun CategoryEditScreen(key: CategoryRoute) {
    val app = LocalContainer.current
    val nav = LocalNav.current
    val messenger = LocalMessenger.current
    val haptics = rememberHaptics()
    val existing = remember(key.categoryId) { app.ledger.current.category(key.categoryId) }
    val isNew = existing == null

    var draft by rememberSaveable(stateSaver = jsonSaver(Category.serializer())) {
        mutableStateOf(
            existing ?: Category(
                id = newId(),
                name = "",
                kind = if (key.kind == "INCOME") Kind.INCOME else Kind.EXPENSE,
                icon = "list",
                color = Seed.palette[13],
            ),
        )
    }
    var pickIcon by remember { mutableStateOf(false) }
    var pickColor by remember { mutableStateOf(false) }
    val color = Color(draft.color)

    fun save() {
        val name = draft.name.trim()
        if (name.isEmpty()) {
            messenger.show("Give the category a name")
            return
        }
        app.ledger.saveCategory(draft.copy(name = name))
        haptics.confirm()
        nav.back()
    }

    ScreenColumn {
        AppBar(
            onBack = { nav.back() },
            title = if (isNew) "New category" else "Edit category",
            actions = { IconBtn(Ic.check, "Save", { save() }, style = IconBtnStyle.FILLED) },
        )
        ButtonGroup(
            options = listOf(GroupOption("Expense"), GroupOption("Income")),
            selectedIndex = if (draft.kind == Kind.EXPENSE) 0 else 1,
            onSelect = { draft = draft.copy(kind = if (it == 0) Kind.EXPENSE else Kind.INCOME) },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        Gap(10.dp)

        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            CategoryDot(Ic.of(draft.icon), color, size = 92.dp)
        }
        Gap(12.dp)
        KField(draft.name, { draft = draft.copy(name = it) }, "Name your category", Modifier.padding(horizontal = 16.dp), kind = FieldKind.NAME, last = true)
        Gap(12.dp)
        KGroup {
            SettingRow(0, 1, "Edit icon", icon = Ic.of(draft.icon), iconColor = color, onClick = { pickIcon = true })
        }

        SectionHeader("Colour")
        KGroup {
            GroupItem(0, 2) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Seed.palette.chunked(6).forEach { rowColors ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            rowColors.forEach { value ->
                                val swatch = Color(value)
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clip(CircleShape)
                                        .background(swatch)
                                        .clickable(onClickLabel = "Use this colour") {
                                            haptics.tick()
                                            draft = draft.copy(color = value)
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (value == draft.color) KIcon(Ic.check, tint = onColor(swatch), contentDescription = "Selected")
                                }
                            }
                        }
                    }
                }
            }
            SettingRow(1, 2, "Custom colour", icon = Ic.colorize, iconColor = color, onClick = { pickColor = true })
        }

        if (existing != null) {
            Gap(16.dp)
            Btn(
                "Delete category",
                {
                    app.ledger.deleteCategory(existing.id)
                    haptics.reject()
                    nav.back()
                    messenger.show("Category moved to trash", "Undo") { app.ledger.restoreCategory(existing.id) }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                icon = Ic.delete,
                style = BtnStyle.DANGER,
            )
            if (existing.createdAt > 0L) Foot("Created: ${Dates.full(existing.createdAt)}")
        }
    }

    if (pickIcon) IconSheet(selected = draft.icon, tint = color, onPick = { draft = draft.copy(icon = it.name) }, onDismiss = { pickIcon = false })
    if (pickColor) {
        ColorDialog(
            initial = color,
            onPick = { picked -> draft = draft.copy(color = picked.toArgb().toLong() and 0xFFFFFFFFL) },
            onDismiss = { pickColor = false },
        )
    }
}

/** A grid of icons in named groups, with a search box. */
@Composable
private fun IconSheet(selected: String, tint: Color, onPick: (Ic) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val groups = remember(query) {
        val words = query.trim().lowercase().replace(' ', '_')
        IconGroups.all.map { (name, icons) -> name to icons.filter { words.isEmpty() || it.name.contains(words) } }.filter { it.second.isNotEmpty() }
    }
    KSheet(onDismiss, "Icon") {
        SearchField(query, { query = it }, "Search icons", Modifier.padding(horizontal = 16.dp))
        Gap(8.dp)
        LazyVerticalGrid(
            columns = GridCells.Adaptive(58.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            groups.forEach { (name, icons) ->
                item(key = "title-$name", span = { GridItemSpan(maxLineSpan) }) {
                    Text(name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp, start = 4.dp))
                }
                items(icons, key = { "$name-${it.name}" }) { icon ->
                    val chosen = icon.name == selected
                    val background = if (chosen) tint else MaterialTheme.colorScheme.surfaceContainerHighest
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(CircleShape)
                            .background(background)
                            .clickable {
                                onPick(icon)
                                onDismiss()
                            }
                            .semantics { contentDescription = icon.name.replace('_', ' ') },
                        contentAlignment = Alignment.Center,
                    ) {
                        KIcon(icon, fill = true, size = 26.dp, tint = if (chosen) onColor(tint) else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** Mix any colour with two sliders: which colour, and how light. */
@Composable
private fun ColorDialog(initial: Color, onPick: (Color) -> Unit, onDismiss: () -> Unit) {
    val start = remember(initial) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(initial.toArgb(), hsv)
        hsv
    }
    var hue by remember { mutableFloatStateOf(start[0]) }
    var light by remember { mutableFloatStateOf(start[2].coerceIn(0.35f, 1f)) }
    val mixed = Color.hsv(hue.coerceIn(0f, 359.9f), 0.68f, light)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom colour") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(mixed),
                )
                Gap(6.dp)
                Text("Colour", style = MaterialTheme.typography.labelLarge, modifier = Modifier.fillMaxWidth())
                Slider(value = hue, onValueChange = { hue = it }, valueRange = 0f..359f, modifier = Modifier.semantics { contentDescription = "Colour" })
                Text("Lightness", style = MaterialTheme.typography.labelLarge, modifier = Modifier.fillMaxWidth())
                Slider(value = light, onValueChange = { light = it }, valueRange = 0.35f..1f, modifier = Modifier.semantics { contentDescription = "Lightness" })
            }
        },
        confirmButton = { TextButton(onClick = { onDismiss(); onPick(mixed) }) { Text("Use") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

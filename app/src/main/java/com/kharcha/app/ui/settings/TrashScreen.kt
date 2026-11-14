package com.kharcha.app.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kharcha.app.core.util.Dates
import com.kharcha.app.ui.components.AppBar
import com.kharcha.app.ui.components.Btn
import com.kharcha.app.ui.components.BtnStyle
import com.kharcha.app.ui.components.CategoryDot
import com.kharcha.app.ui.components.ConfirmDialog
import com.kharcha.app.ui.components.EmptyState
import com.kharcha.app.ui.components.Foot
import com.kharcha.app.ui.components.Gap
import com.kharcha.app.ui.components.GroupItem
import com.kharcha.app.ui.components.KGroup
import com.kharcha.app.ui.components.RowContent
import com.kharcha.app.ui.components.ScreenColumn
import com.kharcha.app.ui.components.SectionHeader
import com.kharcha.app.ui.components.ShapeAvatar
import com.kharcha.app.ui.components.rememberHaptics
import com.kharcha.app.ui.components.rememberNow
import com.kharcha.app.ui.icons.Ic
import com.kharcha.app.ui.nav.LocalContainer
import com.kharcha.app.ui.nav.LocalMessenger
import com.kharcha.app.ui.nav.LocalNav
import com.kharcha.app.ui.sheet.ItemRowContent

/** Deleted sheets, categories and items. They stay here for 30 days, then go for good. */
@Composable
fun TrashScreen() {
    val app = LocalContainer.current
    val nav = LocalNav.current
    val messenger = LocalMessenger.current
    val haptics = rememberHaptics()
    val data by app.ledger.data.collectAsStateWithLifecycle()
    val now = rememberNow()
    var confirm by remember { mutableStateOf(false) }

    val sheets = remember(data.sheets) { data.sheets.filter { it.deletedAt != null }.sortedByDescending { it.deletedAt } }
    val categories = remember(data.categories) { data.categories.filter { it.deletedAt != null }.sortedByDescending { it.deletedAt } }
    // Items deleted by themselves. Items of a deleted sheet come back with their sheet.
    val items = remember(data.items, data.sheets) {
        val gone = sheets.map { it.id }.toSet()
        data.items.filter { it.deletedAt != null && it.sheetId !in gone }.sortedByDescending { it.deletedAt }
    }
    val empty = sheets.isEmpty() && categories.isEmpty() && items.isEmpty()

    ScreenColumn {
        AppBar(onBack = { nav.back() }, title = "Trash")
        if (empty) {
            EmptyState("The trash is empty", "Deleted sheets, categories and items wait here for 30 days in case you change your mind.", Ic.delete)
        } else {
            if (sheets.isNotEmpty()) {
                SectionHeader("Sheets · ${sheets.size}")
                KGroup {
                    sheets.forEachIndexed { index, sheet ->
                        GroupItem(index, sheets.size) {
                            RowContent(
                                title = sheet.name,
                                subtitle = "${data.items.count { it.sheetId == sheet.id }} items · deleted ${Dates.ago(sheet.deletedAt ?: now, now)}",
                                leading = { ShapeAvatar(sheet.name, sheet.id, size = 40.dp) },
                                trailing = { TextButton(onClick = { app.ledger.restoreSheet(sheet.id); messenger.show("\"${sheet.name}\" is back") }) { Text("Restore") } },
                            )
                        }
                    }
                }
            }
            if (categories.isNotEmpty()) {
                SectionHeader("Categories · ${categories.size}")
                KGroup {
                    categories.forEachIndexed { index, category ->
                        GroupItem(index, categories.size) {
                            RowContent(
                                title = category.name,
                                subtitle = "Deleted ${Dates.ago(category.deletedAt ?: now, now)}",
                                leading = { CategoryDot(category, size = 40.dp) },
                                trailing = { TextButton(onClick = { app.ledger.restoreCategory(category.id); messenger.show("\"${category.name}\" is back") }) { Text("Restore") } },
                            )
                        }
                    }
                }
            }
            if (items.isNotEmpty()) {
                SectionHeader("Items · ${items.size}")
                KGroup {
                    items.forEachIndexed { index, item ->
                        val sheet = data.sheet(item.sheetId)
                        if (sheet != null) {
                            GroupItem(index, items.size, onClick = { app.ledger.restoreItems(setOf(item.id)); messenger.show("Item restored to ${sheet.name}") }) {
                                ItemRowContent(item, sheet, data, extra = "${sheet.name} · tap to restore")
                            }
                        }
                    }
                }
            }
            Gap(18.dp)
            Btn(
                "Empty trash",
                { confirm = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                icon = Ic.delete_forever,
                style = BtnStyle.DANGER,
            )
            Foot("Things in the trash are removed by themselves 30 days after they were deleted.")
        }
    }

    if (confirm) {
        ConfirmDialog(
            title = "Empty the trash?",
            text = "Everything in the trash, with its pictures, is deleted for good. This cannot be undone.",
            confirm = "Empty",
            danger = true,
            onConfirm = {
                app.ledger.emptyTrash()
                haptics.reject()
                messenger.show("Trash emptied")
            },
            onDismiss = { confirm = false },
        )
    }
}

package com.kharcha.app.ui.categories

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kharcha.app.core.model.Kind
import com.kharcha.app.ui.components.AppBar
import com.kharcha.app.ui.components.ButtonGroup
import com.kharcha.app.ui.components.CategoryDot
import com.kharcha.app.ui.components.Chevron
import com.kharcha.app.ui.components.EmptyState
import com.kharcha.app.ui.components.Foot
import com.kharcha.app.ui.components.Gap
import com.kharcha.app.ui.components.GroupItem
import com.kharcha.app.ui.components.GroupOption
import com.kharcha.app.ui.components.IconBtn
import com.kharcha.app.ui.components.IconBtnStyle
import com.kharcha.app.ui.components.KGroup
import com.kharcha.app.ui.components.RowContent
import com.kharcha.app.ui.components.ScreenColumn
import com.kharcha.app.ui.components.SearchField
import com.kharcha.app.ui.icons.Ic
import com.kharcha.app.ui.nav.CategoryRoute
import com.kharcha.app.ui.nav.LocalContainer
import com.kharcha.app.ui.nav.LocalNav

/** Every category, split into expense and income, with how many items use each. */
@Composable
fun CategoriesScreen() {
    val app = LocalContainer.current
    val nav = LocalNav.current
    val scheme = MaterialTheme.colorScheme
    val data by app.ledger.data.collectAsStateWithLifecycle()
    var kindIndex by rememberSaveable { mutableStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    val kind = if (kindIndex == 0) Kind.EXPENSE else Kind.INCOME

    val live = remember(data.items) { data.items.filter { it.deletedAt == null && !it.isTransfer } }
    val counts = remember(live) { live.groupingBy { it.categoryId }.eachCount() }
    val none = remember(live, kind) { live.count { it.categoryId == null && it.kind == kind } }
    val shown = remember(data.categories, kind, query) {
        data.liveCategories.filter { it.kind == kind && it.name.contains(query.trim(), ignoreCase = true) }.sortedBy { it.order }
    }

    ScreenColumn {
        AppBar(
            onBack = { nav.back() },
            title = "Categories",
            actions = { IconBtn(Ic.add, "New category", { nav.go(CategoryRoute(kind = kind.name)) }, style = IconBtnStyle.TONAL) },
        )
        SearchField(query, { query = it }, "Search categories", Modifier.padding(horizontal = 16.dp))
        Gap(10.dp)
        ButtonGroup(
            options = listOf(GroupOption("Expense"), GroupOption("Income")),
            selectedIndex = kindIndex,
            onSelect = { kindIndex = it },
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Gap(12.dp)

        if (shown.isEmpty() && query.isNotBlank()) {
            EmptyState("No such category", "Nothing matches \"${query.trim()}\".", Ic.search_off)
        } else {
            val rows = shown.size + if (query.isBlank()) 1 else 0
            KGroup {
                var position = 0
                if (query.isBlank()) {
                    GroupItem(position++, rows) {
                        RowContent(
                            title = "No category",
                            leading = { CategoryDot(null, size = 40.dp) },
                            trailing = { Text(none.toString(), style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant) },
                        )
                    }
                }
                shown.forEach { category ->
                    GroupItem(position++, rows, onClick = { nav.go(CategoryRoute(category.id, category.kind.name)) }) {
                        RowContent(
                            title = category.name,
                            leading = { CategoryDot(category, size = 40.dp) },
                            trailing = {
                                Text((counts[category.id] ?: 0).toString(), style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
                                Chevron()
                            },
                        )
                    }
                }
            }
            Foot("The number is how many items use the category. Deleted categories wait in the trash for 30 days.")
        }
    }
}

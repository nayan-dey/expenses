@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.kharcha.app.ui.sheet

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kharcha.app.core.model.Kind
import com.kharcha.app.core.util.Dates
import com.kharcha.app.domain.Filter
import com.kharcha.app.domain.budget
import com.kharcha.app.domain.groupByDay
import com.kharcha.app.domain.headline
import com.kharcha.app.domain.matchesSearch
import com.kharcha.app.domain.pending
import com.kharcha.app.domain.settled
import com.kharcha.app.domain.totals
import com.kharcha.app.platform.Printer
import com.kharcha.app.platform.Share
import com.kharcha.app.platform.findActivity
import com.kharcha.app.ui.components.ActionDock
import com.kharcha.app.ui.components.AppBar
import com.kharcha.app.ui.components.Btn
import com.kharcha.app.ui.components.BtnSize
import com.kharcha.app.ui.components.BtnStyle
import com.kharcha.app.ui.components.ButtonGroup
import com.kharcha.app.ui.components.CategorySheet
import com.kharcha.app.ui.components.EmptyState
import com.kharcha.app.ui.components.Eyebrow
import com.kharcha.app.ui.components.Gap
import com.kharcha.app.ui.components.GroupItem
import com.kharcha.app.ui.components.GroupOption
import com.kharcha.app.ui.components.HeroAmount
import com.kharcha.app.ui.components.HeroCard
import com.kharcha.app.ui.components.IconBtn
import com.kharcha.app.ui.components.KMenu
import com.kharcha.app.ui.components.MenuItem
import com.kharcha.app.ui.components.PeriodSheet
import com.kharcha.app.ui.components.SearchField
import com.kharcha.app.ui.components.SheetPickSheet
import com.kharcha.app.ui.components.jsonSaver
import com.kharcha.app.ui.components.money
import com.kharcha.app.ui.components.rememberHaptics
import com.kharcha.app.ui.components.rememberNow
import com.kharcha.app.ui.icons.Ic
import com.kharcha.app.ui.nav.CategoryRoute
import com.kharcha.app.ui.nav.ExportRoute
import com.kharcha.app.ui.nav.ImportRoute
import com.kharcha.app.ui.nav.ItemRoute
import com.kharcha.app.ui.nav.LocalContainer
import com.kharcha.app.ui.nav.LocalMessenger
import com.kharcha.app.ui.nav.LocalNav
import com.kharcha.app.ui.nav.RatesRoute
import com.kharcha.app.ui.nav.SheetEditRoute
import com.kharcha.app.ui.nav.SheetRoute
import com.kharcha.app.ui.nav.StatsRoute
import com.kharcha.app.ui.nav.TrendsRoute
import com.kharcha.app.ui.theme.KharchaTheme
import com.kharcha.app.ui.theme.KharchaType
import kotlinx.coroutines.launch

/**
 * One sheet: the totals for the chosen period on top, then every item grouped by day.
 * Press and hold an item to select several at once.
 */
@Composable
fun SheetScreen(key: SheetRoute) {
    val app = LocalContainer.current
    val nav = LocalNav.current
    val messenger = LocalMessenger.current
    val haptics = rememberHaptics()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme
    val data by app.ledger.data.collectAsStateWithLifecycle()
    val settings by app.settings.settings.collectAsStateWithLifecycle()
    val now = rememberNow()

    val sheet = data.sheet(key.sheetId)
    if (sheet == null || sheet.deletedAt != null) {
        // The sheet was deleted while this screen was open.
        LaunchedEffect(Unit) { nav.leaveSheet(key.sheetId) }
        return
    }

    var tab by rememberSaveable { mutableStateOf(0) }
    var newestFirst by rememberSaveable { mutableStateOf(true) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable(stateSaver = jsonSaver(Filter.serializer())) { mutableStateOf(Filter()) }
    var selection by remember { mutableStateOf<Set<String>>(emptySet()) }
    var menuOpen by remember { mutableStateOf(false) }
    var filtersOpen by remember { mutableStateOf(false) }
    var periodOpen by remember { mutableStateOf(false) }
    var moveOpen by remember { mutableStateOf(false) }
    var categoryFor by remember { mutableStateOf<Kind?>(null) }
    val selecting = selection.isNotEmpty()

    val range = remember(sheet.period, sheet.customFrom, sheet.customTo, now) { Dates.range(sheet.period, now, sheet.customFrom, sheet.customTo) }
    val settled = remember(data, sheet.id, range) { data.settled(sheet, range) }
    val waiting = remember(data, sheet.id) { data.pending(sheet) }
    val sums = remember(settled, sheet.currency) { totals(settled, sheet) }
    val budget = remember(data, sheet, now) { data.budget(sheet, now) }
    val periodLabel = Dates.label(sheet.period, sheet.customFrom, sheet.customTo)

    fun narrowed(source: List<com.kharcha.app.core.model.Item>, by: Filter): List<com.kharcha.app.core.model.Item> =
        source.filter { by.matches(it, sheet) && it.matchesSearch(query, data.category(it.categoryId)) }

    val visible = remember(settled, waiting, tab, filter, query, data.categories) { narrowed(if (tab == 0) settled else waiting, filter) }
    val groups = remember(visible, newestFirst, sheet.currency) { groupByDay(visible, sheet, newestFirst) }

    // In select mode, Back leaves select mode instead of the screen.
    BackHandler(enabled = selecting) { selection = emptySet() }

    fun toggle(id: String) {
        selection = if (id in selection) selection - id else selection + id
    }

    fun deleteSelected() {
        val ids = selection
        app.ledger.deleteItems(ids)
        selection = emptySet()
        haptics.reject()
        messenger.show(if (ids.size == 1) "Item moved to trash" else "${ids.size} items moved to trash", "Undo") { app.ledger.restoreItems(ids) }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(scheme.surface)
            .statusBarsPadding(),
    ) {
        Column(Modifier.fillMaxSize()) {
            if (selecting) {
                AppBar(
                    onBack = { selection = emptySet() },
                    title = if (selection.size == 1) "1 selected" else "${selection.size} selected",
                    actions = {
                        IconBtn(Ic.delete, "Delete the selected items", { deleteSelected() })
                        Box {
                            IconBtn(Ic.more_vert, "More", { menuOpen = true })
                            KMenu(menuOpen, { menuOpen = false }) {
                                MenuItem("Select all", Ic.select_all, { menuOpen = false; selection = visible.map { it.id }.toSet() })
                                MenuItem("Move to another sheet", Ic.drive_file_move, { menuOpen = false; moveOpen = true })
                                MenuItem("Change category", Ic.category, {
                                    menuOpen = false
                                    val kinds = visible.filter { it.id in selection && !it.isTransfer }.map { it.kind }.toSet()
                                    if (kinds.size == 1) categoryFor = kinds.first() else messenger.show("Select only expenses, or only income, to change their category.")
                                })
                                MenuItem(if (tab == 0) "Mark as pending" else "Mark as settled", Ic.hourglass_top, {
                                    menuOpen = false
                                    app.ledger.setPending(selection, tab == 0)
                                    selection = emptySet()
                                })
                            }
                        }
                    },
                )
            } else {
                AppBar(
                    onBack = { nav.back() },
                    title = sheet.name,
                    actions = {
                        IconBtn(Ic.ios_share, "Share this sheet as a file", {
                            scope.launch {
                                val (file, uri) = Share.newFile(context, sheet.name.replace(Regex("[^A-Za-z0-9 _-]"), "").trim().ifBlank { "sheet" } + ".kharcha.zip")
                                val ok = runCatching { file.outputStream().use { app.exchange.writeSheet(sheet.id, it) } }.isSuccess
                                if (ok) Share.send(context, uri, "application/zip", "Share ${sheet.name}") else messenger.show("Could not make the file")
                            }
                        })
                        Box {
                            IconBtn(Ic.more_vert, "More", { menuOpen = true })
                            KMenu(menuOpen, { menuOpen = false }) {
                                MenuItem("Stats", Ic.pie_chart, { menuOpen = false; nav.go(StatsRoute(sheet.id)) })
                                MenuItem("Trends", Ic.show_chart, { menuOpen = false; nav.go(TrendsRoute(sheet.id)) })
                                MenuItem("Exchange rates", Ic.currency_exchange, { menuOpen = false; nav.go(RatesRoute(sheet.currency)) })
                                MenuItem("Select", Ic.check_circle, {
                                    menuOpen = false
                                    val first = visible.firstOrNull()
                                    if (first == null) messenger.show("Nothing to select") else selection = setOf(first.id)
                                })
                                MenuItem("Print", Ic.print, {
                                    menuOpen = false
                                    val activity = context.findActivity()
                                    val html = Printer.sheetHtml(data, sheet, visible, if (tab == 0) periodLabel else "Pending", settings.print)
                                    if (activity == null || !Printer.print(activity, sheet.name, html)) messenger.show("This phone cannot print")
                                })
                                MenuItem("Export", Ic.upload, { menuOpen = false; nav.go(ExportRoute(sheet.id)) })
                                MenuItem("Import", Ic.download, { menuOpen = false; nav.go(ImportRoute(sheet.id)) })
                                MenuItem("Edit sheet", Ic.edit, { menuOpen = false; nav.go(SheetEditRoute(sheet.id)) })
                            }
                        }
                    },
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 132.dp),
            ) {
                item(key = "summary") {
                    HeroCard(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        decoration = MaterialShapes.Cookie9Sided,
                    ) {
                        val soft = KharchaTheme.extras.onHero.copy(alpha = 0.75f)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Eyebrow(if (sheet.showTotalBalance) "Balance · ${sheet.currency}" else "Spent · ${sheet.currency}", color = soft)
                            Spacer(Modifier.weight(1f))
                            Btn(periodLabel, { periodOpen = true }, trailing = Ic.expand_more, style = BtnStyle.HERO, size = BtnSize.SM, contentDescription = "Period: $periodLabel")
                        }
                        Gap(4.dp)
                        HeroAmount(money(sheet.headline(sums), sheet.currency))
                        Gap(14.dp)
                        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            Column {
                                Eyebrow("Expense", color = soft)
                                Text(money(-sums.expense, sheet.currency), style = KharchaType.amount)
                            }
                            Column {
                                Eyebrow("Income", color = soft)
                                Text(money(sums.income, sheet.currency), style = KharchaType.amount)
                            }
                        }
                        if (budget != null) {
                            Gap(14.dp)
                            // A calm bar while there is room; it turns wavy once most of the budget is used.
                            LinearWavyProgressIndicator(
                                progress = { budget.fraction },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clearAndSetSemantics { },
                                color = KharchaTheme.extras.onHero,
                                trackColor = KharchaTheme.extras.onHero.copy(alpha = 0.22f),
                                amplitude = { if (budget.fraction >= 0.8f) 1f else 0f },
                            )
                            Gap(6.dp)
                            Text(
                                if (budget.over) {
                                    "${money(-budget.left, sheet.currency)} over this month's budget of ${money(budget.budget, sheet.currency)}"
                                } else {
                                    "${money(budget.left, sheet.currency)} left of ${money(budget.budget, sheet.currency)} this month"
                                },
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }

                item(key = "tabs") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ButtonGroup(
                            options = listOf(
                                GroupOption("$periodLabel  ${settled.size}", Ic.receipt_long),
                                GroupOption("Pending  ${waiting.size}", Ic.hourglass_top),
                            ),
                            selectedIndex = tab,
                            onSelect = {
                                tab = it
                                selection = emptySet()
                            },
                            modifier = Modifier.weight(1f),
                        )
                        IconBtn(
                            if (newestFirst) Ic.arrow_downward else Ic.arrow_upward,
                            if (newestFirst) "Newest first. Tap for oldest first." else "Oldest first. Tap for newest first.",
                            { newestFirst = !newestFirst },
                        )
                    }
                }

                if (searching) {
                    item(key = "search") {
                        SearchField(query, { query = it }, "Search this sheet", Modifier.padding(horizontal = 16.dp, vertical = 6.dp), autoFocus = true)
                    }
                }
                if (filter.active) {
                    item(key = "filter-note") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 22.dp, end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                if (filter.count == 1) "1 filter on · ${visible.size} shown" else "${filter.count} filters on · ${visible.size} shown",
                                style = MaterialTheme.typography.labelMedium,
                                color = scheme.primary,
                                modifier = Modifier.weight(1f),
                            )
                            Btn("Clear", { filter = Filter() }, style = BtnStyle.TEXT, size = BtnSize.SM)
                        }
                    }
                }

                if (groups.isEmpty()) {
                    item(key = "empty") {
                        when {
                            query.isNotBlank() || filter.active -> EmptyState("Nothing matches", "No item in this list fits the search or the filters.", Ic.search_off)
                            tab == 1 -> EmptyState("Nothing pending", "Items you mark as pending wait here, outside the totals, until they are settled.", Ic.hourglass_top)
                            settled.isEmpty() && waiting.isEmpty() && data.itemsOf(sheet.id).isEmpty() -> EmptyState(
                                "A fresh sheet",
                                "Add your first expense or income with the + button.",
                                Ic.receipt_long,
                                action = { Btn("Add an item", { nav.go(ItemRoute(sheet.id)) }, icon = Ic.add) },
                            )
                            else -> EmptyState("Nothing in this period", "No items fall in \"$periodLabel\". Pick another period at the top.", Ic.event)
                        }
                    }
                }

                groups.forEach { group ->
                    item(key = "day-" + group.day) {
                        val (date, weekday) = dayTitle(group.day, now)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 22.dp, end = 22.dp, top = 16.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(date, style = MaterialTheme.typography.titleLarge, color = scheme.primary)
                            Text(weekday, style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 2.dp))
                            Spacer(Modifier.weight(1f))
                            Text(money(group.net, sheet.currency), style = KharchaType.amount, color = scheme.primary)
                        }
                    }
                    itemsIndexed(group.items, key = { _, item -> item.id }) { index, item ->
                        GroupItem(
                            index, group.items.size,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = ListItemDefaults.SegmentedGap / 2),
                            onClick = { if (selecting) toggle(item.id) else nav.go(ItemRoute(sheet.id, item.id)) },
                            onLongClick = {
                                haptics.longPress()
                                toggle(item.id)
                            },
                            selected = if (selecting) item.id in selection else null,
                        ) {
                            ItemRowContent(item, sheet, data, checked = if (selecting) item.id in selection else null)
                        }
                    }
                }
            }
        }

        if (!selecting) {
            ActionDock(
                onAdd = { nav.go(ItemRoute(sheet.id)) },
                addLabel = "New item",
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 14.dp),
            ) {
                IconBtn(Ic.filter_list, if (filter.active) "Filters, ${filter.count} on" else "Filters", { filtersOpen = true }, dot = filter.active)
                IconBtn(
                    if (searching) Ic.close else Ic.search,
                    if (searching) "Close search" else "Search",
                    {
                        searching = !searching
                        if (!searching) query = ""
                    },
                )
                IconBtn(Ic.pie_chart, "Stats", { nav.go(StatsRoute(sheet.id)) })
            }
        }
    }

    if (filtersOpen) {
        FiltersSheet(
            filter = filter,
            sheet = sheet,
            data = data,
            now = now,
            count = { candidate -> narrowed(if (tab == 0) settled else waiting, candidate).size },
            onChange = { filter = it },
            onDismiss = { filtersOpen = false },
        )
    }
    if (periodOpen) {
        PeriodSheet(
            kind = sheet.period,
            from = sheet.customFrom,
            to = sheet.customTo,
            now = now,
            onChange = { kind, from, to -> app.ledger.setPeriod(sheet.id, kind, from, to) },
            onDismiss = { periodOpen = false },
        )
    }
    if (moveOpen) {
        SheetPickSheet(
            title = "Move to",
            sheets = data.liveSheets.filter { it.id != sheet.id },
            selectedId = null,
            onPick = { target ->
                val ids = selection
                app.ledger.moveItems(ids, target.id) { item ->
                    // Re-price for the new sheet's currency; if no rate is known, carry the old price across.
                    app.rates.rate(item.currency, target.currency) ?: (item.rate * (app.rates.rate(sheet.currency, target.currency) ?: 1.0))
                }
                selection = emptySet()
                messenger.show(if (ids.size == 1) "Moved to ${target.name}" else "${ids.size} items moved to ${target.name}")
            },
            onDismiss = { moveOpen = false },
        )
    }
    val kindToSet = categoryFor
    if (kindToSet != null) {
        CategorySheet(
            kind = kindToSet,
            categories = data.categories,
            selectedId = null,
            onPick = { categoryId ->
                app.ledger.setCategory(selection, categoryId, kindToSet)
                selection = emptySet()
                messenger.show("Category changed")
            },
            onDismiss = { categoryFor = null },
            onNew = { nav.go(CategoryRoute(kind = kindToSet.name)) },
        )
    }
}

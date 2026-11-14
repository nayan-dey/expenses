@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.kharcha.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kharcha.app.core.model.Sheet
import com.kharcha.app.core.model.SheetSort
import com.kharcha.app.core.util.Currencies
import com.kharcha.app.core.util.Dates
import com.kharcha.app.data.between
import com.kharcha.app.domain.quickLook
import com.kharcha.app.ui.components.IconTile
import com.kharcha.app.ui.components.KCard
import com.kharcha.app.ui.habit.StreakTree
import com.kharcha.app.ui.nav.HabitRoute
import com.kharcha.app.ui.nav.InsightsRoute
import com.kharcha.app.ui.nav.PeopleRoute
import com.kharcha.app.domain.headline
import com.kharcha.app.domain.matchesSearch
import com.kharcha.app.domain.overview
import com.kharcha.app.domain.sheetTotals
import com.kharcha.app.domain.sortedSheets
import com.kharcha.app.ui.components.ActionDock
import com.kharcha.app.ui.components.Banner
import com.kharcha.app.ui.components.Btn
import com.kharcha.app.ui.components.BtnSize
import com.kharcha.app.ui.components.BtnStyle
import com.kharcha.app.ui.components.CardKind
import com.kharcha.app.ui.components.ChoiceSheet
import com.kharcha.app.ui.components.ConfirmDialog
import com.kharcha.app.ui.components.EmptyState
import com.kharcha.app.ui.components.Eyebrow
import com.kharcha.app.ui.components.Gap
import com.kharcha.app.ui.components.GroupItem
import com.kharcha.app.ui.components.HeroAmount
import com.kharcha.app.ui.components.HeroCard
import com.kharcha.app.ui.components.IconBtn
import com.kharcha.app.ui.components.KMenu
import com.kharcha.app.ui.components.KharchaLogo
import com.kharcha.app.ui.components.MenuItem
import com.kharcha.app.ui.components.SearchField
import com.kharcha.app.ui.components.ShapeAvatar
import com.kharcha.app.ui.components.money
import com.kharcha.app.ui.components.rememberHaptics
import com.kharcha.app.ui.components.rememberNow
import com.kharcha.app.ui.icons.Ic
import com.kharcha.app.ui.nav.BackupRoute
import com.kharcha.app.ui.nav.ItemRoute
import com.kharcha.app.ui.nav.LocalContainer
import com.kharcha.app.ui.nav.LocalMessenger
import com.kharcha.app.ui.nav.LocalNav
import com.kharcha.app.ui.nav.SettingsRoute
import com.kharcha.app.ui.nav.SheetEditRoute
import com.kharcha.app.ui.nav.SheetRoute
import com.kharcha.app.ui.nav.StatsRoute
import com.kharcha.app.ui.sheet.ItemRowContent
import com.kharcha.app.ui.theme.KharchaTheme
import com.kharcha.app.ui.theme.KharchaType

private const val BACKUP_REMINDER_MS = 14L * 24 * 60 * 60 * 1000

/**
 * The first screen: every sheet with its total, and all of them added up at the top.
 * The bar at the bottom searches across all sheets and adds a new item.
 */
@Composable
fun HomeScreen() {
    val app = LocalContainer.current
    val nav = LocalNav.current
    val messenger = LocalMessenger.current
    val haptics = rememberHaptics()
    val scheme = MaterialTheme.colorScheme
    val data by app.ledger.data.collectAsStateWithLifecycle()
    val settings by app.settings.settings.collectAsStateWithLifecycle()
    val rates by app.rates.rates.collectAsStateWithLifecycle()
    val now = rememberNow()

    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var sortOpen by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<Sheet?>(null) }

    val sheets = remember(data.sheets, settings.sheetSort) { data.sortedSheets(settings.sheetSort) }
    val sums = remember(data, now) { data.liveSheets.associate { it.id to data.sheetTotals(it, now) } }
    val counts = remember(data.items) { data.items.filter { it.deletedAt == null }.groupingBy { it.sheetId }.eachCount() }
    val preferred = settings.preferredCurrency.ifBlank { sheets.firstOrNull()?.currency ?: Currencies.ofPhone() }
    val overview = remember(data, rates, preferred, now) {
        data.overview(data.liveSheets, preferred, now) { from -> app.rates.rate(from, preferred, rates) }
    }
    val look = remember(data, rates, preferred, now) { data.quickLook(now, preferred) { from -> rates.between(from, preferred) } }
    val liveItems = remember(data.items) { data.items.count { it.deletedAt == null } }
    val remind = liveItems >= 10 && now - settings.lastBackupAt > BACKUP_REMINDER_MS
    val newest = remember(data.sheets) { data.liveSheets.maxByOrNull { it.editedAt } }

    // What the search box finds, across every sheet.
    val found = remember(data, query) {
        if (query.isBlank()) emptyList() else {
            val live = data.liveSheets.associateBy { it.id }
            data.items
                .filter { it.deletedAt == null && it.sheetId in live && it.matchesSearch(query, data.category(it.categoryId)) }
                .sortedByDescending { it.at }
                .take(120)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(scheme.surface)
            .statusBarsPadding(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 132.dp),
        ) {
            item(key = "header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    KharchaLogo(44.dp)
                    Column(
                        Modifier
                            .weight(1f)
                            .padding(start = 12.dp),
                    ) {
                        Eyebrow(Dates.longDay(now))
                        Text("Kharcha", style = MaterialTheme.typography.headlineMedium, maxLines = 1)
                    }
                    IconBtn(Ic.backup, "Backup", { nav.go(BackupRoute) }, dot = remind)
                    IconBtn(Ic.settings, "Settings", { nav.go(SettingsRoute) })
                }
            }

            if (searching) {
                item(key = "search") {
                    SearchField(query, { query = it }, "Search every sheet", Modifier.padding(horizontal = 16.dp, vertical = 6.dp), autoFocus = true)
                }
            }

            when {
                sheets.isEmpty() -> item(key = "empty") {
                    EmptyState(
                        title = "No sheets yet",
                        text = "A sheet is one ledger: your own spending, the household, a trip with friends. Make your first one.",
                        icon = Ic.description,
                        action = { Btn("New sheet", { nav.go(SheetEditRoute()) }, icon = Ic.add) },
                    )
                }

                query.isNotBlank() -> {
                    if (found.isEmpty()) {
                        item(key = "nothing") {
                            EmptyState(title = "Nothing found", text = "No item matches \"${query.trim()}\". Search looks at notes, merchants, categories and amounts.", icon = Ic.search_off)
                        }
                    } else {
                        item(key = "found-title") {
                            Text(
                                if (found.size == 1) "1 item" else "${found.size} items",
                                style = MaterialTheme.typography.titleSmall,
                                color = scheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp),
                            )
                        }
                        itemsIndexed(found, key = { _, item -> "found-" + item.id }) { index, item ->
                            val sheet = data.sheet(item.sheetId)
                            if (sheet != null) {
                                GroupItem(
                                    index, found.size,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = ListItemDefaults.SegmentedGap / 2),
                                    onClick = { nav.go(ItemRoute(sheet.id, item.id)) },
                                ) {
                                    ItemRowContent(item, sheet, data, extra = "${sheet.name} · ${Dates.dayMonth(item.at)}")
                                }
                            }
                        }
                    }
                }

                else -> {
                    item(key = "overview") {
                        HeroCard(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            decoration = MaterialShapes.Sunny,
                        ) {
                            Eyebrow(if (sheets.size == 1) "Balance · $preferred" else "All ${sheets.size} sheets · $preferred", color = KharchaTheme.extras.onHero.copy(alpha = 0.75f))
                            Gap(6.dp)
                            HeroAmount(money(overview.balance, preferred))
                            Gap(14.dp)
                            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                                Column {
                                    Eyebrow("Spent this month", color = KharchaTheme.extras.onHero.copy(alpha = 0.75f))
                                    Text(money(-overview.spent, preferred), style = KharchaType.amount)
                                }
                                Column {
                                    Eyebrow("Received", color = KharchaTheme.extras.onHero.copy(alpha = 0.75f))
                                    Text(money(overview.received, preferred), style = KharchaType.amount)
                                }
                            }
                            if (overview.leftOut > 0) {
                                Gap(10.dp)
                                Text(
                                    if (overview.leftOut == 1) "1 sheet is not counted: no exchange rate to $preferred yet." else "${overview.leftOut} sheets are not counted: no exchange rate to $preferred yet.",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }

                    // The daily check: the streak and its tree, friends, and where to save.
                    item(key = "today") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            KCard(Modifier.weight(1f), onClick = { nav.go(HabitRoute) }, padding = 14.dp) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    StreakTree(look.streak.current, size = 54.dp)
                                    Column {
                                        Eyebrow("Streak")
                                        Text(
                                            if (look.streak.current == 1) "1 day" else "${look.streak.current} days",
                                            style = MaterialTheme.typography.titleLarge,
                                            maxLines = 1,
                                        )
                                        Text(
                                            if (look.streak.today) "Done today" else "Log today",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (look.streak.today) KharchaTheme.extras.income else scheme.tertiary,
                                            maxLines = 1,
                                        )
                                    }
                                }
                            }
                            KCard(Modifier.weight(1f), onClick = { nav.go(PeopleRoute) }, padding = 14.dp) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    IconTile(Ic.group, scheme.primary, size = 44.dp)
                                    Column {
                                        Eyebrow("Friends")
                                        Text(
                                            when {
                                                look.owedToYou > 0L -> money(look.owedToYou, preferred)
                                                look.youOwe > 0L -> money(look.youOwe, preferred)
                                                else -> "All square"
                                            },
                                            style = MaterialTheme.typography.titleMedium,
                                            maxLines = 1,
                                        )
                                        Text(
                                            when {
                                                look.owedToYou > 0L -> "owed to you"
                                                look.youOwe > 0L -> "you owe"
                                                data.livePeople.isEmpty() -> "Split a bill"
                                                else -> "Nothing owed"
                                            },
                                            style = MaterialTheme.typography.labelMedium,
                                            color = scheme.onSurfaceVariant,
                                            maxLines = 1,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    item(key = "insights") {
                        Banner(
                            title = "Where could you save?",
                            subtitle = if (look.month > 0L) "This month so far: ${money(look.month, preferred)} spent. See what stands out." else "Log a few days and suggestions appear here.",
                            icon = Ic.insights,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            kind = CardKind.SECONDARY,
                            onClick = { nav.go(InsightsRoute) },
                        )
                    }

                    if (remind) {
                        item(key = "remind") {
                            Banner(
                                title = "Save a backup",
                                subtitle = if (settings.lastBackupAt == 0L) "Everything is only on this phone. You have not saved a backup yet." else "Your last backup was ${Dates.ago(settings.lastBackupAt, now)}.",
                                icon = Ic.backup,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                kind = CardKind.WARN,
                                onClick = { nav.go(BackupRoute) },
                            )
                        }
                    }

                    item(key = "sheets-title") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 22.dp, end = 8.dp, top = 14.dp, bottom = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Sheets", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                            IconBtn(Ic.sort, "Sort sheets", { sortOpen = true })
                            IconBtn(
                                if (settings.sheetsCollapsed) Ic.expand_more else Ic.expand_less,
                                if (settings.sheetsCollapsed) "Show the sheets" else "Hide the sheets",
                                { app.settings.update { it.copy(sheetsCollapsed = !it.sheetsCollapsed) } },
                            )
                        }
                    }

                    if (!settings.sheetsCollapsed) {
                        itemsIndexed(sheets, key = { _, sheet -> sheet.id }) { index, sheet ->
                            val totals = sums[sheet.id]
                            GroupItem(
                                index, sheets.size,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = ListItemDefaults.SegmentedGap / 2),
                                onClick = { nav.go(SheetRoute(sheet.id)) },
                                onLongClick = {
                                    haptics.longPress()
                                    menuFor = sheet.id
                                },
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 14.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    ShapeAvatar(sheet.name, sheet.id)
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Text(sheet.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text(
                                                if (totals == null) "" else money(sheet.headline(totals), sheet.currency),
                                                style = KharchaType.amount,
                                                color = scheme.primary,
                                                maxLines = 1,
                                            )
                                            Text(
                                                "${counts[sheet.id] ?: 0} · ${Dates.short(sheet.editedAt)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = scheme.onSurfaceVariant,
                                                maxLines = 1,
                                            )
                                        }
                                    }
                                    Box {
                                        IconBtn(Ic.more_vert, "More for ${sheet.name}", { menuFor = sheet.id })
                                        KMenu(expanded = menuFor == sheet.id, onDismiss = { menuFor = null }) {
                                            MenuItem("Stats", Ic.pie_chart, { menuFor = null; nav.go(StatsRoute(sheet.id)) })
                                            MenuItem("Edit sheet", Ic.edit, { menuFor = null; nav.go(SheetEditRoute(sheet.id)) })
                                            if (settings.sheetSort == SheetSort.MANUAL) {
                                                if (index > 0) MenuItem("Move up", Ic.arrow_upward, { menuFor = null; app.ledger.moveSheet(sheet.id, -1) })
                                                if (index < sheets.lastIndex) MenuItem("Move down", Ic.arrow_downward, { menuFor = null; app.ledger.moveSheet(sheet.id, 1) })
                                            }
                                            MenuItem("Delete", Ic.delete, { menuFor = null; deleting = sheet }, danger = true)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item(key = "new-sheet") {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Btn("New sheet", { nav.go(SheetEditRoute()) }, icon = Ic.add, style = BtnStyle.TONAL, size = BtnSize.SM)
                        }
                    }
                }
            }
        }

        ActionDock(
            onAdd = {
                val target = newest
                if (target == null) nav.go(SheetEditRoute()) else nav.go(ItemRoute(target.id))
            },
            addLabel = "New item",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 14.dp),
        ) {
            IconBtn(
                if (searching) Ic.close else Ic.search,
                if (searching) "Close search" else "Search",
                {
                    searching = !searching
                    if (!searching) query = ""
                },
            )
            IconBtn(
                if (settings.sensitive) Ic.visibility_off else Ic.visibility,
                if (settings.sensitive) "Show amounts" else "Hide amounts",
                {
                    app.settings.update { it.copy(sensitive = !it.sensitive) }
                    messenger.show(if (settings.sensitive) "Amounts are showing" else "Amounts are hidden")
                },
                toggled = settings.sensitive,
            )
            IconBtn(Ic.group, "Friends", { nav.go(PeopleRoute) })
        }
    }

    if (sortOpen) {
        ChoiceSheet(
            title = "Sort sheets by",
            options = SheetSort.entries.toList(),
            selected = settings.sheetSort,
            label = { it.label },
            onPick = { choice -> app.settings.update { it.copy(sheetSort = choice) } },
            onDismiss = { sortOpen = false },
        )
    }

    val doomed = deleting
    if (doomed != null) {
        ConfirmDialog(
            title = "Delete \"${doomed.name}\"?",
            text = "The sheet and its items move to the trash. You can bring them back from Settings, Trash, for 30 days.",
            confirm = "Delete",
            danger = true,
            onConfirm = {
                app.ledger.deleteSheet(doomed.id)
                haptics.reject()
                messenger.show("Moved to trash", "Undo") { app.ledger.restoreSheet(doomed.id) }
            },
            onDismiss = { deleting = null },
        )
    }
}

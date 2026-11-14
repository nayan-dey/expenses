@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.kharcha.app.ui.item

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kharcha.app.core.model.AppData
import com.kharcha.app.core.model.Item
import com.kharcha.app.core.model.Kind
import com.kharcha.app.core.model.Repeat
import com.kharcha.app.core.util.Calc
import com.kharcha.app.core.util.Currencies
import com.kharcha.app.core.util.Dates
import com.kharcha.app.core.util.Money
import com.kharcha.app.core.util.newId
import com.kharcha.app.platform.Share
import com.kharcha.app.ui.components.AppBar
import com.kharcha.app.ui.components.Btn
import com.kharcha.app.ui.components.BtnSize
import com.kharcha.app.ui.components.BtnStyle
import com.kharcha.app.ui.components.ButtonGroup
import com.kharcha.app.ui.components.CategoryDot
import com.kharcha.app.ui.components.CategorySheet
import com.kharcha.app.ui.components.Chip
import com.kharcha.app.ui.components.ChoiceSheet
import com.kharcha.app.ui.components.CurrencySheet
import com.kharcha.app.ui.components.DateDialog
import com.kharcha.app.ui.components.FieldKind
import com.kharcha.app.ui.components.Foot
import com.kharcha.app.ui.components.Gap
import com.kharcha.app.ui.components.GroupItem
import com.kharcha.app.ui.components.GroupOption
import com.kharcha.app.ui.components.HIDDEN_AMOUNT
import com.kharcha.app.ui.components.IconBtn
import com.kharcha.app.ui.components.IconBtnStyle
import com.kharcha.app.ui.components.IconTile
import com.kharcha.app.ui.components.KField
import com.kharcha.app.ui.components.KGroup
import com.kharcha.app.ui.components.Keypad
import com.kharcha.app.ui.components.MerchantSheet
import com.kharcha.app.ui.components.PictureViewer
import com.kharcha.app.ui.components.PromptDialog
import com.kharcha.app.ui.components.RowContent
import com.kharcha.app.ui.components.SettingRow
import com.kharcha.app.ui.components.SheetPickSheet
import com.kharcha.app.ui.components.SwitchRow
import com.kharcha.app.ui.components.Thumb
import com.kharcha.app.ui.components.TimeDialog
import com.kharcha.app.ui.components.jsonSaver
import com.kharcha.app.ui.components.rememberHaptics
import com.kharcha.app.ui.icons.Ic
import com.kharcha.app.ui.nav.CategoryRoute
import com.kharcha.app.ui.nav.ItemRoute
import com.kharcha.app.ui.nav.LocalContainer
import com.kharcha.app.ui.nav.LocalMessenger
import com.kharcha.app.ui.nav.LocalNav
import com.kharcha.app.ui.theme.LocalSensitive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.math.BigDecimal

private const val TRANSFER = "TRANSFER"

/** The form as it is being filled in. Kept across a screen rotation as JSON. */
@Serializable
private data class Draft(
    /** "EXPENSE", "INCOME" or "TRANSFER". */
    val mode: String,
    /** The amount exactly as typed on the keypad, e.g. "120+35×2". */
    val expr: String = "",
    val currency: String,
    /** A rate typed by hand for this one item. Empty = use the known rate. */
    val rateText: String = "",
    val note: String = "",
    val merchant: String = "",
    val categoryId: String? = null,
    val sheetId: String,
    /** For a transfer: the sheet the money arrives in. */
    val toSheetId: String? = null,
    val at: Long,
    val pending: Boolean = false,
    val repeat: Repeat = Repeat.NEVER,
    val images: List<String> = emptyList(),
) {
    val kind: Kind get() = if (mode == "INCOME") Kind.INCOME else Kind.EXPENSE
    val isTransfer: Boolean get() = mode == TRANSFER
}

/** The category most recently used for this kind of item in a sheet, to start a new item with. */
private fun AppData.lastCategory(sheetId: String, kind: Kind): String? =
    items.filter { it.sheetId == sheetId && it.kind == kind && it.deletedAt == null && !it.isTransfer && it.categoryId != null }
        .maxByOrNull { it.at }?.categoryId
        ?.takeIf { id -> categories.any { it.id == id && it.deletedAt == null } }

/** Up to eight categories to offer as one-tap chips: a match for the note first, then the most used. */
private fun AppData.suggestions(sheetId: String, kind: Kind, note: String): List<String> {
    val live = categories.filter { it.kind == kind && it.deletedAt == null }.map { it.id }.toSet()
    val past = items.filter { it.kind == kind && it.deletedAt == null && it.categoryId in live }
    val typed = note.trim()
    val byNote = if (typed.length < 2) null else past.filter { it.note.startsWith(typed, ignoreCase = true) }.maxByOrNull { it.at }?.categoryId
    val mostUsed = past.filter { it.sheetId == sheetId }.groupingBy { it.categoryId }.eachCount().entries.sortedByDescending { it.value }.mapNotNull { it.key }
    val rest = categories.filter { it.id in live }.sortedBy { it.order }.map { it.id }
    return (listOfNotNull(byNote) + mostUsed + rest).distinct().take(8)
}

/**
 * New item, or the details of an existing one. The amount is typed on the app's own keypad,
 * which can also add, subtract, multiply and divide.
 */
@Composable
fun ItemEditScreen(key: ItemRoute) {
    val app = LocalContainer.current
    val nav = LocalNav.current
    val messenger = LocalMessenger.current
    val haptics = rememberHaptics()
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme
    val hidden = LocalSensitive.current
    val data by app.ledger.data.collectAsStateWithLifecycle()
    val rates by app.rates.rates.collectAsStateWithLifecycle()

    // What is being edited. Read once: the form then owns its own copy.
    val existing: Item? = remember(key.itemId) { app.ledger.current.items.firstOrNull { it.id == key.itemId } }
    val peer: Item? = remember(existing) {
        existing?.transferId?.let { id -> app.ledger.current.items.firstOrNull { it.transferId == id && it.id != existing?.id } }
    }
    val isNew = existing == null

    var draft by rememberSaveable(stateSaver = jsonSaver(Draft.serializer())) {
        val now = app.now()
        val start = app.ledger.current
        val template = existing ?: start.items.firstOrNull { it.id == key.copyOf }
        mutableStateOf(
            when {
                template != null && template.isTransfer -> {
                    val other = start.items.firstOrNull { it.transferId == template.transferId && it.id != template.id }
                    val out = if (template.kind == Kind.EXPENSE) template else other ?: template
                    val into = if (template.kind == Kind.INCOME) template else other
                    Draft(
                        mode = TRANSFER,
                        expr = Money.plain(template.amountMinor, template.currency),
                        currency = template.currency,
                        note = template.note,
                        sheetId = out.sheetId,
                        toSheetId = into?.sheetId,
                        at = if (existing != null) template.at else now,
                        pending = template.pending,
                        repeat = template.repeat,
                        images = if (existing != null) out.images else emptyList(),
                    )
                }
                template != null -> Draft(
                    mode = template.kind.name,
                    expr = Money.plain(template.amountMinor, template.currency),
                    currency = template.currency,
                    note = template.note,
                    merchant = template.merchant,
                    categoryId = template.categoryId,
                    sheetId = if (existing != null) template.sheetId else key.sheetId,
                    at = if (existing != null) template.at else now,
                    pending = template.pending,
                    repeat = if (existing != null) template.repeat else Repeat.NEVER,
                    images = if (existing != null) template.images else emptyList(),
                )
                else -> {
                    val mode = if (key.kind in setOf("EXPENSE", "INCOME", TRANSFER)) key.kind else "EXPENSE"
                    Draft(
                        mode = mode,
                        currency = start.sheet(key.sheetId)?.currency ?: Currencies.ofPhone(),
                        categoryId = if (mode == TRANSFER) null else start.lastCategory(key.sheetId, if (mode == "INCOME") Kind.INCOME else Kind.EXPENSE),
                        sheetId = key.sheetId,
                        at = now,
                    )
                }
            },
        )
    }

    // The keypad is up straight away for a new item, because the amount comes first.
    var keypad by rememberSaveable { mutableStateOf(isNew) }
    var pickCurrency by remember { mutableStateOf(false) }
    var pickCategory by remember { mutableStateOf(false) }
    var pickMerchant by remember { mutableStateOf(false) }
    // Which sheet row is being changed: "from", "to" or null.
    var pickSheet by remember { mutableStateOf<String?>(null) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    var pickRepeat by remember { mutableStateOf(false) }
    var askRate by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<String?>(null) }
    var cameraName by rememberSaveable { mutableStateOf<String?>(null) }

    val sheet = data.sheet(draft.sheetId) ?: data.liveSheets.firstOrNull()
    val toSheet = data.sheet(draft.toSheetId)
    if (sheet == null) {
        // No sheet to put the item in (everything was deleted meanwhile).
        Column(Modifier.fillMaxSize().statusBarsPadding()) { AppBar(onBack = { nav.back() }, title = "New item") }
        return
    }

    val amount: BigDecimal? = remember(draft.expr) { Calc.eval(draft.expr) }
    val amountMinor = amount?.let { Money.toMinor(it, draft.currency) }
    val typedRate = draft.rateText.replace(",", "").toDoubleOrNull()?.takeIf { it > 0.0 }

    /** How many units of a sheet's currency one unit of the item's currency is worth. Null = unknown. */
    fun rateTo(target: String, saved: Item?): Double? = when {
        draft.currency == target -> 1.0
        typedRate != null -> typedRate
        // An item being edited keeps the rate it was saved with, unless its currency or sheet changed.
        saved != null && saved.currency == draft.currency && data.sheet(saved.sheetId)?.currency == target -> saved.rate
        else -> app.rates.rate(draft.currency, target, rates)
    }

    val savedOut = if (existing?.kind == Kind.EXPENSE) existing else peer
    val savedIn = if (existing?.kind == Kind.INCOME) existing else peer
    val rate: Double? = rateTo(sheet.currency, if (draft.isTransfer) savedOut else existing)
    val rateToTarget: Double? = if (toSheet == null) null else rateTo(toSheet.currency, savedIn)
    val foreign = draft.currency != sheet.currency

    val problem: String? = when {
        amountMinor == null || amountMinor <= 0L -> "Enter an amount"
        rate == null -> "Set an exchange rate"
        draft.isTransfer && toSheet == null -> "Choose where the money goes"
        draft.isTransfer && toSheet?.id == sheet.id -> "Choose two different sheets"
        draft.isTransfer && rateToTarget == null -> "Set an exchange rate"
        else -> null
    }

    fun save() {
        val minor = amountMinor
        val known = rate
        if (problem != null || minor == null || known == null) {
            messenger.show(problem ?: "Enter an amount")
            return
        }
        if (draft.isTransfer) {
            val target = toSheet ?: return
            app.ledger.saveTransfer(
                transferId = existing?.transferId,
                fromSheetId = sheet.id,
                toSheetId = target.id,
                amountMinor = minor,
                currency = draft.currency,
                rateToFrom = known,
                rateToTo = rateToTarget ?: 1.0,
                note = draft.note.trim(),
                at = draft.at,
                pending = draft.pending,
                repeat = draft.repeat,
                pictures = draft.images,
            )
        } else {
            app.ledger.saveItem(
                Item(
                    id = existing?.id ?: newId(),
                    sheetId = sheet.id,
                    kind = draft.kind,
                    amountMinor = minor,
                    currency = draft.currency,
                    rate = known,
                    note = draft.note.trim(),
                    merchant = draft.merchant.trim(),
                    categoryId = draft.categoryId,
                    at = draft.at,
                    pending = draft.pending,
                    repeat = draft.repeat,
                    seriesId = existing?.seriesId,
                    images = draft.images,
                    createdAt = existing?.createdAt ?: 0L,
                ),
            )
        }
        haptics.confirm()
        nav.back()
    }

    // The phone's photo picker and camera. Pictures are copied into the app's own folder.
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val name = app.images.importFrom(uri)
                if (name == null) messenger.show("Could not read that picture") else draft = draft.copy(images = draft.images + name)
            }
        }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val name = cameraName
        cameraName = null
        if (name != null) {
            scope.launch {
                if (taken && app.images.finishCamera(name)) draft = draft.copy(images = draft.images + name) else app.images.delete(listOf(name))
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(scheme.surface)
            .statusBarsPadding(),
    ) {
        AppBar(
            onBack = { nav.back() },
            title = when {
                isNew -> "New item"
                draft.isTransfer -> "Transfer"
                else -> "Details"
            },
            actions = { IconBtn(Ic.check, "Save", { save() }, style = IconBtnStyle.FILLED) },
        )

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 20.dp),
        ) {
            // Expense, income or transfer. A saved transfer stays a transfer.
            val modes = when {
                isNew -> listOf("EXPENSE" to "Expense", "INCOME" to "Income", TRANSFER to "Transfer")
                draft.isTransfer -> emptyList()
                else -> listOf("EXPENSE" to "Expense", "INCOME" to "Income")
            }
            if (modes.isNotEmpty()) {
                ButtonGroup(
                    options = modes.map { GroupOption(it.second) },
                    selectedIndex = modes.indexOfFirst { it.first == draft.mode }.coerceAtLeast(0),
                    onSelect = { index ->
                        val mode = modes[index].first
                        if (mode != draft.mode) {
                            draft = draft.copy(
                                mode = mode,
                                categoryId = if (mode == TRANSFER) null else data.lastCategory(sheet.id, if (mode == "INCOME") Kind.INCOME else Kind.EXPENSE),
                            )
                        }
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                Gap(8.dp)
            }

            // The amount. Tapping it brings up the keypad.
            Surface(
                onClick = {
                    focus.clearFocus()
                    keypad = !keypad
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .semantics { contentDescription = "Amount" },
                shape = MaterialTheme.shapes.medium,
                color = scheme.surfaceContainer,
                border = if (keypad) BorderStroke(2.dp, scheme.primary) else null,
            ) {
                Column(Modifier.padding(start = 18.dp, end = 8.dp, top = 12.dp, bottom = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            draft.expr.ifEmpty { "0" },
                            style = MaterialTheme.typography.displaySmall.copy(fontFeatureSettings = "tnum"),
                            color = if (draft.expr.isEmpty()) scheme.onSurfaceVariant else scheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.StartEllipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (sheet.showCurrency || foreign) {
                            Btn(draft.currency, { pickCurrency = true }, trailing = Ic.expand_more, style = BtnStyle.TONAL, size = BtnSize.SM, contentDescription = "Currency: ${Currencies.name(draft.currency)}")
                        }
                    }
                    val line = when {
                        amountMinor == null && draft.expr.isNotEmpty() -> "This sum has no answer"
                        amountMinor == null -> Money.format(0, draft.currency)
                        else -> (if (Calc.hasOperator(draft.expr)) "= " else "") + Money.signed(amountMinor, draft.currency, if (draft.isTransfer) Kind.INCOME else draft.kind)
                    }
                    Text(line, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                }
            }

            // Paid in another currency: what it comes to in the sheet's own, and the rate used.
            if (foreign) {
                Gap(8.dp)
                KGroup {
                    SettingRow(
                        0, 1,
                        title = when {
                            rate == null -> "No exchange rate known"
                            amountMinor == null -> "1 ${draft.currency} = ${Money.rateText(rate)} ${sheet.currency}"
                            hidden -> "$HIDDEN_AMOUNT in ${sheet.currency}"
                            else -> Money.format(Money.convert(amountMinor, draft.currency, sheet.currency, rate), sheet.currency)
                        },
                        icon = Ic.currency_exchange,
                        iconColor = if (rate == null) scheme.error else scheme.primary,
                        subtitle = when {
                            rate == null -> "Tap to type how much 1 ${draft.currency} is worth in ${sheet.currency}"
                            typedRate != null -> "Your rate: 1 ${draft.currency} = ${Money.rateText(rate)} ${sheet.currency}"
                            else -> "At 1 ${draft.currency} = ${Money.rateText(rate)} ${sheet.currency}. Tap to change."
                        },
                        onClick = { askRate = true },
                    )
                }
            }

            Gap(10.dp)
            KField(
                draft.note,
                { draft = draft.copy(note = it) },
                "Notes",
                Modifier
                    .padding(horizontal = 16.dp)
                    .onFocusChanged { if (it.isFocused) keypad = false },
                last = true,
            )
            Gap(12.dp)

            if (draft.isTransfer) {
                KGroup {
                    SettingRow(0, 2, "From", icon = Ic.arrow_upward, iconColor = scheme.error, value = sheet.name, onClick = { pickSheet = "from" })
                    SettingRow(1, 2, "To", icon = Ic.arrow_downward, iconColor = Color(0xFF2FA866), value = toSheet?.name ?: "Choose", onClick = { pickSheet = "to" })
                }
                if (toSheet != null && toSheet.currency != draft.currency && rateToTarget != null && amountMinor != null && !hidden) {
                    Foot("Arrives as ${Money.format(Money.convert(amountMinor, draft.currency, toSheet.currency, rateToTarget), toSheet.currency)} in ${toSheet.name}.")
                }
            } else {
                val category = data.category(draft.categoryId)
                val rows = if (sheet.showMerchant) 3 else 2
                KGroup {
                    var position = 0
                    if (sheet.showMerchant) {
                        SettingRow(position++, rows, "Merchant", icon = Ic.storefront, iconColor = Color(0xFFF59B42), value = draft.merchant.ifBlank { "No merchant" }, onClick = { pickMerchant = true })
                    }
                    GroupItem(position++, rows, onClick = { pickCategory = true }) {
                        Column {
                            RowContent(
                                title = "Category",
                                leading = { CategoryDot(category, size = 40.dp) },
                                trailing = {
                                    Text(category?.name ?: "No category", style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                },
                            )
                            if (sheet.showSuggestions) {
                                val ids = remember(data.items, data.categories, sheet.id, draft.kind, draft.note) { data.suggestions(sheet.id, draft.kind, draft.note) }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState())
                                        .padding(start = 14.dp, end = 14.dp, bottom = 10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    ids.forEach { id ->
                                        val option = data.category(id)
                                        if (option != null) {
                                            Chip(
                                                option.name,
                                                { draft = draft.copy(categoryId = id) },
                                                selected = id == draft.categoryId,
                                                icon = Ic.of(option.icon),
                                                fillIcon = true,
                                                iconTint = Color(option.color),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    SettingRow(position, rows, "Sheet", icon = Ic.description, iconColor = Color(0xFF6C7CF5), value = sheet.name, onClick = { pickSheet = "from" })
                }
            }

            Gap(12.dp)
            KGroup {
                val rows = if (sheet.showTime) 2 else 1
                SettingRow(0, rows, "Date", icon = Ic.calendar_month, iconColor = Color(0xFFEF5350), value = Dates.medium(draft.at), onClick = { pickDate = true })
                if (sheet.showTime) {
                    SettingRow(1, rows, "Time", icon = Ic.schedule, iconColor = Color(0xFF3F8EF5), value = Dates.time(draft.at), onClick = { pickTime = true })
                }
            }

            Gap(12.dp)
            KGroup {
                SwitchRow(
                    0, 2, "Pending", draft.pending, { draft = draft.copy(pending = it) },
                    icon = Ic.hourglass_top,
                    iconColor = Color(0xFFF7B928),
                    subtitle = "Not settled yet: kept out of the totals",
                )
                SettingRow(
                    1, 2, "Repeat",
                    icon = Ic.repeat,
                    iconColor = Color(0xFF62C968),
                    value = draft.repeat.label,
                    subtitle = if (draft.repeat == Repeat.NEVER) null else "Next: ${Dates.medium(Dates.next(draft.repeat, draft.at))}",
                    onClick = { pickRepeat = true },
                )
            }

            Gap(12.dp)
            KGroup {
                GroupItem(0, 1) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            IconTile(Ic.image, Color(0xFFC63FE8))
                            Text(if (draft.images.isEmpty()) "Pictures" else "Pictures · ${draft.images.size}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        }
                        if (draft.images.isNotEmpty()) {
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                draft.images.forEach { name -> Thumb(name, { viewing = name }) }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Btn(
                                "Take a photo",
                                {
                                    val (name, uri) = app.images.newCameraTarget()
                                    cameraName = name
                                    runCatching { camera.launch(uri) }.onFailure {
                                        cameraName = null
                                        messenger.show("No camera app found")
                                    }
                                },
                                icon = Ic.photo_camera,
                                style = BtnStyle.TONAL,
                                size = BtnSize.SM,
                            )
                            Btn(
                                "Choose",
                                { runCatching { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) } },
                                icon = Ic.photo_library,
                                style = BtnStyle.TONAL,
                                size = BtnSize.SM,
                            )
                        }
                    }
                }
            }

            if (existing != null) {
                Gap(16.dp)
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Btn(
                        "Delete",
                        {
                            val ids = setOf(existing.id)
                            app.ledger.deleteItems(ids)
                            haptics.reject()
                            nav.back()
                            messenger.show("Item moved to trash", "Undo") { app.ledger.restoreItems(ids) }
                        },
                        modifier = Modifier.weight(1f),
                        icon = Ic.delete,
                        style = BtnStyle.DANGER,
                    )
                    Btn(
                        "Duplicate",
                        { nav.replaceTop(ItemRoute(sheet.id, copyOf = existing.id)) },
                        modifier = Modifier.weight(1f),
                        icon = Ic.content_copy,
                        style = BtnStyle.TONAL,
                    )
                }
                if (existing.createdAt > 0L) Foot("Created: ${Dates.full(existing.createdAt)}")
            }
        }

        AnimatedVisibility(visible = keypad) {
            Keypad(
                onKey = { key -> draft = draft.copy(expr = Calc.press(draft.expr, key, Money.digits(draft.currency))) },
                onBackspace = { draft = draft.copy(expr = Calc.backspace(draft.expr)) },
                onDone = { keypad = false },
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                decimal = Money.digits(draft.currency) > 0,
            )
        }
    }

    // ---- pickers ----
    if (pickCurrency) {
        val used = remember(data.items) { data.items.filter { it.deletedAt == null }.map { it.currency }.distinct() }
        CurrencySheet(
            selected = draft.currency,
            pinned = listOf(sheet.currency) + used,
            onPick = { draft = draft.copy(currency = it, rateText = "") },
            onDismiss = { pickCurrency = false },
        )
    }
    if (pickCategory) {
        CategorySheet(
            kind = draft.kind,
            categories = data.categories,
            selectedId = draft.categoryId,
            onPick = { draft = draft.copy(categoryId = it) },
            onDismiss = { pickCategory = false },
            onNew = { nav.go(CategoryRoute(kind = draft.kind.name)) },
        )
    }
    if (pickMerchant) {
        val known = remember(data.items) {
            data.items.filter { it.merchant.isNotBlank() }.groupingBy { it.merchant }.eachCount().entries.sortedByDescending { it.value }.map { it.key }
        }
        MerchantSheet(draft.merchant, known, { draft = draft.copy(merchant = it) }, { pickMerchant = false })
    }
    val whichSheet = pickSheet
    if (whichSheet != null) {
        SheetPickSheet(
            title = if (!draft.isTransfer) "Sheet" else if (whichSheet == "from") "From" else "To",
            sheets = data.liveSheets,
            selectedId = if (whichSheet == "from") sheet.id else toSheet?.id,
            onPick = { picked -> draft = if (whichSheet == "from") draft.copy(sheetId = picked.id) else draft.copy(toSheetId = picked.id) },
            onDismiss = { pickSheet = null },
        )
    }
    if (pickDate) {
        DateDialog(
            initial = Dates.dateOf(draft.at),
            onPick = { date -> draft = draft.copy(at = Dates.millis(date, Dates.dateTimeOf(draft.at).toLocalTime())) },
            onDismiss = { pickDate = false },
        )
    }
    if (pickTime) {
        TimeDialog(
            initial = Dates.dateTimeOf(draft.at).toLocalTime(),
            onPick = { time -> draft = draft.copy(at = Dates.millis(Dates.dateOf(draft.at), time)) },
            onDismiss = { pickTime = false },
        )
    }
    if (pickRepeat) {
        ChoiceSheet(
            title = "Repeat",
            options = Repeat.entries.toList(),
            selected = draft.repeat,
            label = { it.label },
            onPick = { draft = draft.copy(repeat = it) },
            onDismiss = { pickRepeat = false },
        )
    }
    if (askRate) {
        PromptDialog(
            title = "Exchange rate",
            label = "1 ${draft.currency} in ${sheet.currency}",
            initial = draft.rateText.ifBlank { rate?.let { Money.rateText(it) }.orEmpty() },
            text = "How much one ${Currencies.name(draft.currency)} is worth in ${Currencies.name(sheet.currency)}, for this item only. Leave it empty to use the usual rate.",
            kind = FieldKind.MONEY,
            onConfirm = { typed -> draft = draft.copy(rateText = typed) },
            onDismiss = { askRate = false },
        )
    }
    val shown = viewing
    if (shown != null) {
        PictureViewer(
            name = shown,
            onDismiss = { viewing = null },
            onShare = { Share.send(context, app.images.shareUri(shown), "image/jpeg", "Share picture") },
            onRemove = { draft = draft.copy(images = draft.images - shown) },
        )
    }
}

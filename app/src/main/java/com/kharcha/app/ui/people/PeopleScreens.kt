package com.kharcha.app.ui.people

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kharcha.app.core.model.Item
import com.kharcha.app.core.model.Kind
import com.kharcha.app.core.model.PayMethod
import com.kharcha.app.core.model.Person
import com.kharcha.app.core.model.Sheet
import com.kharcha.app.core.util.Dates
import com.kharcha.app.core.util.Money
import com.kharcha.app.core.util.newId
import com.kharcha.app.domain.dues
import com.kharcha.app.domain.effectOn
import com.kharcha.app.domain.sharedWith
import com.kharcha.app.domain.titleOf
import com.kharcha.app.ui.components.AppBar
import com.kharcha.app.ui.components.Btn
import com.kharcha.app.ui.components.BtnSize
import com.kharcha.app.ui.components.BtnStyle
import com.kharcha.app.ui.components.ButtonGroup
import com.kharcha.app.ui.components.Chevron
import com.kharcha.app.ui.components.ConfirmDialog
import com.kharcha.app.ui.components.EmptyState
import com.kharcha.app.ui.components.Eyebrow
import com.kharcha.app.ui.components.FieldKind
import com.kharcha.app.ui.components.Foot
import com.kharcha.app.ui.components.Gap
import com.kharcha.app.ui.components.GroupItem
import com.kharcha.app.ui.components.GroupOption
import com.kharcha.app.ui.components.HIDDEN_AMOUNT
import com.kharcha.app.ui.components.HeroAmount
import com.kharcha.app.ui.components.HeroCard
import com.kharcha.app.ui.components.IconBtn
import com.kharcha.app.ui.components.IconBtnStyle
import com.kharcha.app.ui.components.KField
import com.kharcha.app.ui.components.KGroup
import com.kharcha.app.ui.components.RowContent
import com.kharcha.app.ui.components.ScreenColumn
import com.kharcha.app.ui.components.SectionHeader
import com.kharcha.app.ui.components.ShapeAvatar
import com.kharcha.app.ui.components.money
import com.kharcha.app.ui.components.rememberHaptics
import com.kharcha.app.ui.icons.Ic
import com.kharcha.app.ui.nav.ItemRoute
import com.kharcha.app.ui.nav.LocalContainer
import com.kharcha.app.ui.nav.LocalMessenger
import com.kharcha.app.ui.nav.LocalNav
import com.kharcha.app.ui.nav.PersonRoute
import com.kharcha.app.ui.theme.KharchaTheme
import com.kharcha.app.ui.theme.KharchaType
import com.kharcha.app.ui.theme.LocalSensitive

/** An amount as text, or dots in Sensitive mode. A plain function, so it can be used inside list operations. */
private fun shown(amount: Long, code: String, hidden: Boolean): String = if (hidden) HIDDEN_AMOUNT else Money.format(amount, code)

/** "owes you ₹500" or "you owe ₹200", per currency, as one line. */
private fun dueText(perCurrency: Map<String, Long>, hidden: Boolean): String {
    if (perCurrency.isEmpty()) return "All square"
    return perCurrency.entries.sortedByDescending { kotlin.math.abs(it.value) }.joinToString(", ") { (code, amount) ->
        if (amount > 0) "owes you ${shown(amount, code, hidden)}" else "you owe ${shown(-amount, code, hidden)}"
    }
}

/** A dialog to add a friend or change their name and UPI address. */
@Composable
private fun PersonDialog(person: Person?, onSave: (Person) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(person?.name.orEmpty()) }
    var upi by remember { mutableStateOf(person?.upi.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (person == null) "Add a friend" else "Edit friend") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                KField(name, { name = it }, "Name", kind = FieldKind.NAME)
                KField(upi, { upi = it }, "UPI address (optional)", supporting = "Like name@bank. Lets you pay them back in one tap.", last = true)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) {
                    onDismiss()
                    onSave((person ?: Person(id = newId(), name = "")).copy(name = name.trim(), upi = upi.trim()))
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * Friends: everyone you share bills with, and what stands between you. Bills are split on
 * the item form ("Who paid" and "Split with"); this screen adds them up.
 */
@Composable
fun PeopleScreen() {
    val app = LocalContainer.current
    val nav = LocalNav.current
    val scheme = MaterialTheme.colorScheme
    val data by app.ledger.data.collectAsStateWithLifecycle()
    val hidden = LocalSensitive.current
    var adding by remember { mutableStateOf(false) }

    val dues = remember(data) { data.dues() }
    val people = remember(data.people, dues) {
        data.livePeople.sortedWith(compareByDescending<Person> { person -> dues[person.id].orEmpty().values.sumOf { kotlin.math.abs(it) } }.thenBy { it.name.lowercase() })
    }
    // Totals per currency: what is owed to you, and what you owe.
    val owed = remember(dues) { HashMap<String, Long>().also { map -> dues.values.forEach { it.forEach { (code, amount) -> if (amount > 0) map[code] = (map[code] ?: 0L) + amount } } } }
    val owing = remember(dues) { HashMap<String, Long>().also { map -> dues.values.forEach { it.forEach { (code, amount) -> if (amount < 0) map[code] = (map[code] ?: 0L) - amount } } } }

    ScreenColumn {
        AppBar(
            onBack = { nav.back() },
            title = "Friends",
            actions = { IconBtn(Ic.person_add, "Add a friend", { adding = true }, style = IconBtnStyle.TONAL) },
        )
        if (people.isEmpty()) {
            EmptyState(
                title = "Who do you go out with?",
                text = "Add your friends. When you pay a bill for the group, split it on the item, and Kharcha keeps track of who owes whom.",
                icon = Ic.diversity_3,
                action = { Btn("Add a friend", { adding = true }, icon = Ic.person_add) },
            )
        } else {
            HeroCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                val soft = KharchaTheme.extras.onHero.copy(alpha = 0.75f)
                Eyebrow("Friends owe you", color = soft)
                Gap(4.dp)
                HeroAmount(if (owed.isEmpty()) "Nothing" else owed.entries.joinToString(" + ") { shown(it.value, it.key, hidden) })
                Gap(12.dp)
                Eyebrow("You owe", color = soft)
                Text(if (owing.isEmpty()) "Nothing" else owing.entries.joinToString(" + ") { shown(it.value, it.key, hidden) }, style = KharchaType.amount)
            }
            Gap(10.dp)
            KGroup {
                people.forEachIndexed { index, person ->
                    val due = dues[person.id].orEmpty()
                    val net = due.values.sum()
                    GroupItem(index, people.size, onClick = { nav.go(PersonRoute(person.id)) }) {
                        RowContent(
                            title = person.name,
                            subtitle = dueText(due, hidden),
                            leading = { ShapeAvatar(person.name, person.id) },
                            trailing = {
                                if (due.isNotEmpty()) {
                                    Text(
                                        if (net > 0) "+" else "−",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = if (net > 0) KharchaTheme.extras.income else KharchaTheme.extras.expense,
                                    )
                                }
                                Chevron()
                            },
                        )
                    }
                }
            }
            Foot("To split a bill: add the expense, then set \"Who paid\" and \"Split with\" on it. Tap a friend here to settle up.")
        }
    }

    if (adding) PersonDialog(null, onSave = { app.ledger.savePerson(it) }, onDismiss = { adding = false })
}

/** One friend: the balance, settling up, and every bill shared with them. */
@Composable
fun PersonScreen(key: PersonRoute) {
    val app = LocalContainer.current
    val nav = LocalNav.current
    val messenger = LocalMessenger.current
    val haptics = rememberHaptics()
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val hidden = LocalSensitive.current
    val data by app.ledger.data.collectAsStateWithLifecycle()
    val person = data.person(key.personId)
    if (person == null || person.deletedAt != null) {
        LaunchedEffect(Unit) { nav.back() }
        return
    }

    var editing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    /** The currency being settled, or null when the settle dialog is closed. */
    var settling by remember { mutableStateOf<String?>(null) }

    val due = remember(data) { data.dues()[person.id].orEmpty() }
    val shared = remember(data) { data.sharedWith(person.id) }
    val main = due.entries.maxByOrNull { kotlin.math.abs(it.value) }

    /** Records a repayment as an item, in the most recently used sheet with that currency. */
    fun settle(currency: String, amountMinor: Long, theyPaid: Boolean, method: PayMethod) {
        val sheet: Sheet = data.liveSheets.filter { it.currency == currency }.maxByOrNull { it.editedAt } ?: data.liveSheets.maxByOrNull { it.editedAt } ?: return
        val rate = if (sheet.currency == currency) 1.0 else app.rates.rate(currency, sheet.currency) ?: 1.0
        app.ledger.saveItem(
            Item(
                id = newId(),
                sheetId = sheet.id,
                kind = if (theyPaid) Kind.INCOME else Kind.EXPENSE,
                amountMinor = amountMinor,
                currency = currency,
                rate = rate,
                note = "Settled up",
                at = app.now(),
                method = method,
                settles = person.id,
            ),
        )
        haptics.confirm()
        messenger.show(if (theyPaid) "Recorded: ${person.name} paid you back" else "Recorded: you paid ${person.name} back")
    }

    ScreenColumn {
        AppBar(
            onBack = { nav.back() },
            title = person.name,
            subtitle = person.upi,
            actions = {
                IconBtn(Ic.edit, "Edit this friend", { editing = true })
                IconBtn(Ic.delete, "Remove this friend", { confirmDelete = true })
            },
        )
        HeroCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            val soft = KharchaTheme.extras.onHero.copy(alpha = 0.75f)
            when {
                main == null -> {
                    Eyebrow("Between you and ${person.name}", color = soft)
                    Gap(4.dp)
                    HeroAmount("All square")
                }
                main.value > 0 -> {
                    Eyebrow("${person.name} owes you", color = soft)
                    Gap(4.dp)
                    HeroAmount(money(main.value, main.key))
                }
                else -> {
                    Eyebrow("You owe ${person.name}", color = soft)
                    Gap(4.dp)
                    HeroAmount(money(-main.value, main.key))
                }
            }
            if (due.size > 1) {
                Gap(6.dp)
                Text(dueText(due, hidden), style = MaterialTheme.typography.bodySmall)
            }
            if (main != null) {
                Gap(14.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Btn("Settle up", { settling = main.key }, icon = Ic.handshake, style = BtnStyle.HERO, size = BtnSize.SM)
                    if (main.value < 0 && person.upi.isNotBlank() && main.key == "INR") {
                        Btn(
                            "Pay by UPI",
                            {
                                // Opens the phone's UPI app with the friend and the amount already filled in.
                                val uri = Uri.Builder().scheme("upi").authority("pay")
                                    .appendQueryParameter("pa", person.upi)
                                    .appendQueryParameter("pn", person.name)
                                    .appendQueryParameter("am", Money.plain(-main.value, "INR"))
                                    .appendQueryParameter("cu", "INR")
                                    .appendQueryParameter("tn", "Settling up")
                                    .build()
                                val ok = runCatching { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW, uri), "Pay with")) }.isSuccess
                                if (!ok) messenger.show("No UPI app found on this phone")
                            },
                            icon = Ic.payments,
                            style = BtnStyle.HERO,
                            size = BtnSize.SM,
                        )
                    }
                    if (main.value > 0) {
                        Btn(
                            "Remind",
                            {
                                val text = "Hi ${person.name}, a reminder that you owe me ${if (hidden) "some money" else Money.format(main.value, main.key)} for the bills we split. Thanks!"
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, text)
                                }
                                runCatching { context.startActivity(Intent.createChooser(send, "Remind ${person.name}")) }
                            },
                            icon = Ic.share,
                            style = BtnStyle.HERO,
                            size = BtnSize.SM,
                        )
                    }
                }
            }
        }
        if (main != null && main.value < 0 && person.upi.isBlank()) {
            Foot("Add ${person.name}'s UPI address (the pencil at the top) to pay them back in one tap.")
        } else if (main != null && main.value < 0) {
            Foot("After paying in your UPI app, come back and tap \"Settle up\" to record it. Kharcha cannot see whether the payment went through.")
        }

        SectionHeader("Shared · ${shared.size}")
        if (shared.isEmpty()) {
            Foot("Nothing shared yet. Add an expense and set \"Who paid\" or \"Split with\" to ${person.name}.")
        } else {
            KGroup {
                shared.forEachIndexed { index, item ->
                    val effect = item.effectOn(person.id)
                    GroupItem(index, shared.size, onClick = { nav.go(ItemRoute(item.sheetId, item.id)) }) {
                        RowContent(
                            title = data.titleOf(item),
                            subtitle = listOfNotNull(Dates.medium(item.at), item.note.takeIf { it.isNotBlank() && item.settles == null }).joinToString(" · "),
                            trailing = {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        if (hidden) HIDDEN_AMOUNT else (if (effect >= 0) "+" else "−") + Money.format(kotlin.math.abs(effect), item.currency),
                                        style = KharchaType.amount,
                                        color = if (effect >= 0) KharchaTheme.extras.income else KharchaTheme.extras.expense,
                                        maxLines = 1,
                                    )
                                    Text(
                                        if (effect >= 0) "to you" else "to them",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = scheme.onSurfaceVariant,
                                    )
                                }
                            },
                        )
                    }
                }
            }
            Foot("A plus means the balance moved in your favour (they owe you more, or you paid them back). A minus is the other way.")
        }
    }

    if (editing) PersonDialog(person, onSave = { app.ledger.savePerson(it) }, onDismiss = { editing = false })
    if (confirmDelete) {
        ConfirmDialog(
            title = "Remove ${person.name}?",
            text = "They leave your list of friends. Bills already shared with them keep their name.",
            confirm = "Remove",
            danger = true,
            onConfirm = {
                app.ledger.deletePerson(person.id)
                nav.back()
                messenger.show("${person.name} removed", "Undo") { app.ledger.restorePerson(person.id) }
            },
            onDismiss = { confirmDelete = false },
        )
    }
    val currency = settling
    if (currency != null) {
        val net = due[currency] ?: 0L
        SettleDialog(
            person = person,
            currency = currency,
            net = net,
            onSettle = { amount, theyPaid, method -> settle(currency, amount, theyPaid, method) },
            onDismiss = { settling = null },
        )
    }
}

/** Record a repayment: how much, which way, and how it was paid. */
@Composable
private fun SettleDialog(
    person: Person,
    currency: String,
    net: Long,
    onSettle: (amountMinor: Long, theyPaid: Boolean, method: PayMethod) -> Unit,
    onDismiss: () -> Unit,
) {
    var amount by remember { mutableStateOf(Money.plain(kotlin.math.abs(net), currency)) }
    // Which way the money went: 0 = they paid you, 1 = you paid them.
    var direction by remember { mutableStateOf(if (net >= 0) 0 else 1) }
    var method by remember { mutableStateOf(PayMethod.UPI) }
    val methods = listOf(PayMethod.UPI, PayMethod.CASH, PayMethod.BANK)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settle up with ${person.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ButtonGroup(
                    options = listOf(GroupOption("They paid me"), GroupOption("I paid them")),
                    selectedIndex = direction,
                    onSelect = { direction = it },
                )
                KField(amount, { amount = it }, "Amount", kind = FieldKind.MONEY, prefix = Money.symbol(currency), last = true)
                ButtonGroup(
                    options = methods.map { GroupOption(it.label) },
                    selectedIndex = methods.indexOf(method).coerceAtLeast(0),
                    onSelect = { method = methods[it] },
                )
                Text("It is saved in your sheet as a repayment, so the balance there stays right. It is not counted as spending or income.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val value = amount.replace(",", "").toBigDecimalOrNull()
                if (value != null && value.signum() > 0) {
                    onDismiss()
                    onSettle(Money.toMinor(value, currency), direction == 0, method)
                }
            }) { Text("Record") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.kharcha.app.ui.insights

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kharcha.app.core.util.Currencies
import com.kharcha.app.core.util.Money
import com.kharcha.app.data.between
import com.kharcha.app.domain.Tone
import com.kharcha.app.domain.asPrompt
import com.kharcha.app.domain.report
import com.kharcha.app.ui.components.AmountRow
import com.kharcha.app.ui.components.AppBar
import com.kharcha.app.ui.components.Btn
import com.kharcha.app.ui.components.BtnStyle
import com.kharcha.app.ui.components.ButtonGroup
import com.kharcha.app.ui.components.Chevron
import com.kharcha.app.ui.components.Eyebrow
import com.kharcha.app.ui.components.Foot
import com.kharcha.app.ui.components.Gap
import com.kharcha.app.ui.components.GroupItem
import com.kharcha.app.ui.components.GroupOption
import com.kharcha.app.ui.components.HIDDEN_AMOUNT
import com.kharcha.app.ui.components.HeroAmount
import com.kharcha.app.ui.components.HeroCard
import com.kharcha.app.ui.components.IconTile
import com.kharcha.app.ui.components.KCard
import com.kharcha.app.ui.components.KGroup
import com.kharcha.app.ui.components.KSheet
import com.kharcha.app.ui.components.RowContent
import com.kharcha.app.ui.components.ScreenColumn
import com.kharcha.app.ui.components.SectionHeader
import com.kharcha.app.ui.components.StatTile
import com.kharcha.app.ui.components.rememberNow
import com.kharcha.app.ui.icons.Ic
import com.kharcha.app.ui.nav.HabitRoute
import com.kharcha.app.ui.nav.LocalContainer
import com.kharcha.app.ui.nav.LocalMessenger
import com.kharcha.app.ui.nav.LocalNav
import com.kharcha.app.ui.theme.KharchaTheme
import com.kharcha.app.ui.theme.LocalSensitive

private const val GEMINI_PACKAGE = "com.google.android.apps.bard"

/**
 * "Where could I save?" The last week or month, compared with the one before, with plain
 * findings and what each is worth. Worked out on the phone; nothing is sent anywhere unless
 * you tap "Ask Gemini" and send the summary yourself.
 */
@Composable
fun InsightsScreen() {
    val app = LocalContainer.current
    val nav = LocalNav.current
    val scheme = MaterialTheme.colorScheme
    val hidden = LocalSensitive.current
    val data by app.ledger.data.collectAsStateWithLifecycle()
    val settings by app.settings.settings.collectAsStateWithLifecycle()
    val rates by app.rates.rates.collectAsStateWithLifecycle()
    val now = rememberNow()
    var span by rememberSaveable { mutableStateOf(0) }
    var asking by remember { mutableStateOf(false) }

    val days = if (span == 0) 7 else 30
    val currency = settings.preferredCurrency.ifBlank { data.liveSheets.firstOrNull()?.currency ?: Currencies.ofPhone() }
    val show: (Long) -> String = { amount -> if (hidden) HIDDEN_AMOUNT else Money.format(amount, currency) }
    val report = remember(data, rates, days, currency, now, hidden) {
        data.report(days, now, currency, { from -> rates.between(from, currency) }, show)
    }

    ScreenColumn {
        AppBar(onBack = { nav.back() }, title = "Insights", subtitle = "Where could you save?")
        ButtonGroup(
            options = listOf(GroupOption("Last 7 days"), GroupOption("Last 30 days")),
            selectedIndex = span,
            onSelect = { span = it },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        Gap(6.dp)
        HeroCard(modifier = Modifier.padding(horizontal = 16.dp), decoration = MaterialShapes.Flower) {
            val soft = KharchaTheme.extras.onHero.copy(alpha = 0.75f)
            Eyebrow(if (report.potential > 0L) "You could keep about" else "Spent in $days days", color = soft)
            Gap(4.dp)
            HeroAmount(show(if (report.potential > 0L) report.potential else report.spent))
            Gap(8.dp)
            Text(
                if (report.potential > 0L) {
                    "more every ${if (days == 7) "week" else "month"} with the two small changes below. Over a year that is ${show(report.potential * (if (days == 7) 52 else 12))}."
                } else {
                    "Log a few more days and suggestions will appear here."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Spent", show(report.spent), Modifier.weight(1f), icon = Ic.arrow_upward)
            StatTile("Before", show(report.spentBefore), Modifier.weight(1f), icon = Ic.history)
        }

        SectionHeader("What stands out")
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            report.insights.forEach { insight ->
                val color = when (insight.tone) {
                    Tone.GOOD -> KharchaTheme.extras.income
                    Tone.WARN -> scheme.tertiary
                    Tone.NEUTRAL -> scheme.primary
                }
                KCard(Modifier.fillMaxWidth(), padding = 14.dp) {
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IconTile(Ic.of(insight.icon, Ic.insights), color)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(insight.title, style = MaterialTheme.typography.titleMedium)
                            Text(insight.text, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        if (report.top.isNotEmpty()) {
            SectionHeader("Where it went")
            KCard(Modifier.padding(horizontal = 16.dp)) {
                report.top.forEach { (name, amount) -> AmountRow(name, show(amount)) }
                if (report.byMethod.isNotEmpty()) {
                    Gap(8.dp)
                    Eyebrow("How you paid")
                    report.byMethod.forEach { (name, amount) -> AmountRow(name, show(amount)) }
                }
            }
        }

        SectionHeader("Next step")
        KGroup {
            GroupItem(0, 2, onClick = { nav.go(HabitRoute) }) {
                RowContent(
                    title = "Put the saving to work",
                    subtitle = "Set a goal and a monthly amount, and keep your streak",
                    leading = { IconTile(Ic.savings, KharchaTheme.extras.income) },
                    trailing = { Chevron() },
                )
            }
            GroupItem(1, 2, onClick = { asking = true }) {
                RowContent(
                    title = "Ask Gemini",
                    subtitle = "Share this summary with an AI assistant for ideas",
                    leading = { IconTile(Ic.auto_awesome, scheme.primary) },
                    trailing = { Chevron() },
                )
            }
        }
        Foot("These are simple sums made on your phone from what you logged. They are a nudge, not financial advice.")
    }

    if (asking) {
        val goals = data.liveGoals.map { goal ->
            "${goal.name} (${goal.kind.label.lowercase()}" + (if (goal.monthlyMinor > 0L) ", ${show(goal.monthlyMinor)} a month planned" else "") + ")"
        }
        val prompt = remember(report, hidden) { report.asPrompt(show, goals) }
        AskSheet(prompt = prompt, hidden = hidden, onDismiss = { asking = false })
    }
}

/** Shows exactly what will be shared, then hands it to Gemini (or any other app). */
@Composable
private fun AskSheet(prompt: String, hidden: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val messenger = LocalMessenger.current
    val scheme = MaterialTheme.colorScheme

    fun send(toGemini: Boolean) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, prompt)
            if (toGemini) setPackage(GEMINI_PACKAGE)
        }
        val direct = toGemini && runCatching { context.startActivity(intent) }.isSuccess
        if (!direct) {
            // Gemini is not installed (or refused): let the person pick any app.
            intent.setPackage(null)
            runCatching { context.startActivity(Intent.createChooser(intent, "Share the summary")) }
        }
        onDismiss()
    }

    KSheet(onDismiss, "Ask Gemini") {
        Text(
            if (hidden) {
                "Sensitive mode is on, so the amounts below are hidden and the answer will be vague. Turn it off first for real advice."
            } else {
                "This is everything that will be shared. It leaves your phone only when you send it."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (hidden) scheme.error else scheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Gap(10.dp)
        Column(
            Modifier
                .weight(1f, fill = false)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            KCard(Modifier.fillMaxWidth(), padding = 14.dp) {
                Text(prompt, style = MaterialTheme.typography.bodySmall)
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Btn("Open Gemini", { send(true) }, modifier = Modifier.weight(1f), icon = Ic.auto_awesome)
            Btn("Other app", { send(false) }, icon = Ic.share, style = BtnStyle.TONAL)
            Btn(
                "Copy",
                {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    clipboard?.setPrimaryClip(ClipData.newPlainText("Kharcha summary", prompt))
                    messenger.show("Copied")
                    onDismiss()
                },
                icon = Ic.content_copy,
                style = BtnStyle.TONAL,
            )
        }
    }
}

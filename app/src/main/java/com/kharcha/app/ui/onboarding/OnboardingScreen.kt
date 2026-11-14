@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.kharcha.app.ui.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kharcha.app.core.model.AppData
import com.kharcha.app.core.util.Currencies
import com.kharcha.app.data.Seed
import com.kharcha.app.platform.Biometrics
import com.kharcha.app.platform.findActivity
import com.kharcha.app.ui.components.Btn
import com.kharcha.app.ui.components.BtnSize
import com.kharcha.app.ui.components.BtnStyle
import com.kharcha.app.ui.components.CurrencySheet
import com.kharcha.app.ui.components.FieldKind
import com.kharcha.app.ui.components.Gap
import com.kharcha.app.ui.components.KField
import com.kharcha.app.ui.components.KGroup
import com.kharcha.app.ui.components.KharchaLogo
import com.kharcha.app.ui.components.SettingRow
import com.kharcha.app.ui.components.ShapeIcon
import com.kharcha.app.ui.components.rememberHaptics
import com.kharcha.app.ui.icons.Ic
import com.kharcha.app.ui.nav.LocalContainer
import kotlinx.coroutines.launch

/**
 * The first screen a new install shows. It asks for two things only: what to call the
 * first sheet and which currency to use. No sign-up, no account.
 */
@Composable
fun OnboardingScreen() {
    val app = LocalContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val scheme = MaterialTheme.colorScheme
    var name by rememberSaveable { mutableStateOf("Personal") }
    var currency by rememberSaveable { mutableStateOf(Currencies.ofPhone()) }
    var pickCurrency by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }

    fun finish(data: AppData, preferred: String) {
        app.ledger.replaceAll(data)
        app.settings.update { it.copy(onboarded = true, preferredCurrency = preferred) }
        app.lock.unlock()
        haptics.confirm()
    }

    // Someone moving from another phone can start from their backup instead.
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val file = runCatching { context.contentResolver.openInputStream(uri)?.use { app.exchange.readFile(it) } }.getOrNull()
                if (file == null || file.data.sheets.isEmpty()) {
                    problem = "That file is not a Kharcha backup."
                } else {
                    finish(file.data, file.data.sheets.first().currency)
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        KharchaLogo(112.dp)
        Gap(18.dp)
        Text("Kharcha", style = MaterialTheme.typography.displayMedium)
        Gap(6.dp)
        Text(
            "Know where your money goes.",
            style = MaterialTheme.typography.titleMedium,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Gap(28.dp)

        Promise(Ic.phone_android, MaterialShapes.Cookie9Sided, "Stays on this phone", "No account, no sign-up, no server. Your money is your business.")
        Promise(Ic.workspace_premium, MaterialShapes.Sunny, "Everything is unlocked", "Unlimited sheets, repeats, pictures, stats, export. Nothing to buy.")
        Promise(Ic.currency_exchange, MaterialShapes.Clover4Leaf, "Any currency", "Each sheet has its own, and items in other currencies are converted.")
        Gap(22.dp)

        KField(name, { name = it }, "Name of your first sheet", kind = FieldKind.NAME, supporting = "A sheet is one ledger: Personal, Home, a trip.", last = true)
        Gap(10.dp)
        KGroup(horizontal = 0.dp) {
            SettingRow(
                0, 1, "Currency",
                icon = Ic.payments,
                value = currency,
                subtitle = Currencies.name(currency),
                onClick = { pickCurrency = true },
            )
        }
        Gap(24.dp)

        Btn(
            "Start",
            { finish(AppData(sheets = listOf(Seed.firstSheet(name.trim(), currency)), categories = Seed.defaultCategories).stamped(app.now()), currency) },
            modifier = Modifier.fillMaxWidth(),
            trailing = Ic.arrow_forward,
            size = BtnSize.LG,
        )
        Gap(10.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Btn("Try with sample data", { finish(Seed.sample(currency, app.now()), currency) }, style = BtnStyle.TONAL, size = BtnSize.SM)
            Btn("Restore a backup", { openBackup.launch(arrayOf("application/zip", "application/json", "application/octet-stream", "*/*")) }, style = BtnStyle.TEXT, size = BtnSize.SM)
        }
        val message = problem
        if (message != null) {
            Gap(10.dp)
            Text(message, style = MaterialTheme.typography.bodyMedium, color = scheme.error, textAlign = TextAlign.Center)
        }
    }

    if (pickCurrency) {
        CurrencySheet(
            selected = currency,
            pinned = listOf(currency, Currencies.ofPhone()),
            onPick = { currency = it },
            onDismiss = { pickCurrency = false },
        )
    }
}

/** Marks a fresh sheet with its creation time (the ledger normally does this, but here the data is replaced whole). */
private fun AppData.stamped(time: Long): AppData = copy(sheets = sheets.map { it.copy(createdAt = time, editedAt = time) })

@Composable
private fun Promise(icon: Ic, shape: androidx.graphics.shapes.RoundedPolygon, title: String, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ShapeIcon(icon, size = 52.dp, shape = shape)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Shown instead of the app while "App lock" is on and the app has not been unlocked yet.
 * It asks Android to check a fingerprint, a face, or the phone's PIN.
 */
@Composable
fun LockScreen() {
    val app = LocalContainer.current
    val activity = LocalContext.current.findActivity()
    val scheme = MaterialTheme.colorScheme

    fun ask() {
        // If the phone no longer has a screen lock there is nothing to check against: let the owner in.
        if (activity == null || !Biometrics.available(activity)) {
            app.lock.unlock()
            return
        }
        Biometrics.ask(activity, "Unlock Kharcha") { ok -> if (ok) app.lock.unlock() }
    }
    LaunchedEffect(Unit) { ask() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        KharchaLogo(96.dp)
        Gap(20.dp)
        Text("Kharcha is locked", style = MaterialTheme.typography.headlineSmall)
        Gap(6.dp)
        Text(
            "Use your fingerprint, face or the phone's PIN to open it.",
            style = MaterialTheme.typography.bodyLarge,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Gap(24.dp)
        Btn("Unlock", { ask() }, icon = Ic.lock_open)
    }
}

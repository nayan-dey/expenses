package com.kharcha.app.ui.components

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.kharcha.app.AppContainer
import com.kharcha.app.platform.Biometrics
import com.kharcha.app.platform.findActivity
import com.kharcha.app.ui.icons.Ic
import com.kharcha.app.ui.nav.LocalContainer
import com.kharcha.app.ui.nav.LocalMessenger
import com.kharcha.app.ui.nav.Messenger
import com.kharcha.app.ui.theme.LocalSensitive

/**
 * Hiding and showing amounts ("Sensitive mode").
 *
 * Hiding happens at once: it only ever protects you. Showing them again asks Android to
 * check it is you (fingerprint, face or the phone's PIN), so someone holding your unlocked
 * phone cannot simply tap the eye. On a phone with no screen lock there is nothing to check
 * against, and the eye works as a plain switch.
 */
fun setSensitive(app: AppContainer, context: Context, messenger: Messenger, hide: Boolean) {
    if (hide) {
        app.settings.update { it.copy(sensitive = true) }
        messenger.show("Amounts are hidden")
        return
    }
    val reveal = {
        app.settings.update { it.copy(sensitive = false) }
        messenger.show("Amounts are showing")
    }
    val activity = context.findActivity()
    if (activity == null || !Biometrics.available(activity)) {
        reveal()
        return
    }
    Biometrics.ask(activity, "Show amounts", onBroken = reveal) { ok ->
        if (ok) reveal() else messenger.show("Amounts stay hidden")
    }
}

/** The eye in every top bar: one tap hides every amount in the app, another shows them again. */
@Composable
fun SensitiveToggle() {
    val app = LocalContainer.current
    val messenger = LocalMessenger.current
    val context = LocalContext.current
    val hidden = LocalSensitive.current
    IconBtn(
        if (hidden) Ic.visibility_off else Ic.visibility,
        if (hidden) "Show amounts" else "Hide amounts",
        { setSensitive(app, context, messenger, hide = !hidden) },
        toggled = hidden,
    )
}

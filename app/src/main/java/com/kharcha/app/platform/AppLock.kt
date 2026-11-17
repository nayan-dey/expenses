package com.kharcha.app.platform

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.ContextWrapper
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The lock in front of the app. It starts locked; the root screen only shows the lock when
 * "App lock" is switched on in Settings. After the app has been out of sight for a short
 * while it locks again.
 */
class AppLock {
    val locked = MutableStateFlow(true)
    private var leftAt = 0L

    fun unlock() {
        locked.value = false
    }

    fun onBackground(now: Long) {
        leftAt = now
    }

    fun onForeground(now: Long) {
        if (leftAt != 0L && now - leftAt > RELOCK_AFTER_MS) locked.value = true
    }

    private companion object {
        const val RELOCK_AFTER_MS = 20_000L
    }
}

/** Android's own "prove it is you" sheet: fingerprint, face, or the phone's PIN or pattern. */
object Biometrics {

    /** The lock needs Android 11 or newer, and a screen lock set up on the phone. */
    fun available(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true

    /**
     * Shows the sheet. `onResult(true)` = it was them, `onResult(false)` = it was not, or they
     * backed out. `onBroken` runs when the phone could not show the sheet at all (an error
     * inside Android, not a wrong finger): callers decide whether that should let the person
     * in, so a phone fault can never lock someone out of their own records for good.
     *
     * Needs the USE_BIOMETRIC permission in the manifest. Without it Android throws instead of
     * showing the sheet, which is why everything here is wrapped: a failure must never crash.
     */
    fun ask(activity: Activity, title: String, onBroken: () -> Unit = {}, onResult: (Boolean) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            onBroken()
            return
        }
        // Android can answer more than once (a wrong finger, then an error); only the first answer counts.
        var answered = false
        fun answer(block: () -> Unit) {
            if (!answered) {
                answered = true
                block()
            }
        }
        try {
            val prompt = BiometricPrompt.Builder(activity)
                .setTitle(title)
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build()
            prompt.authenticate(
                CancellationSignal(),
                activity.mainExecutor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                        answer { onResult(true) }
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                        when (errorCode) {
                            // The phone has no lock, or its fingerprint hardware is not working: nothing to ask with.
                            BiometricPrompt.BIOMETRIC_ERROR_HW_UNAVAILABLE,
                            BiometricPrompt.BIOMETRIC_ERROR_HW_NOT_PRESENT,
                            BiometricPrompt.BIOMETRIC_ERROR_NO_DEVICE_CREDENTIAL -> answer { onBroken() }
                            else -> answer { onResult(false) }
                        }
                    }
                },
            )
        } catch (failure: Throwable) {
            answer { onBroken() }
        }
    }
}

/** Compose hands out a Context; this digs the Activity out of it. */
fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

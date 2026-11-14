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

    fun ask(activity: Activity, title: String, onResult: (Boolean) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            onResult(false)
            return
        }
        val prompt = BiometricPrompt.Builder(activity)
            .setTitle(title)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()
        prompt.authenticate(
            CancellationSignal(),
            activity.mainExecutor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                    onResult(true)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                    onResult(false)
                }
            },
        )
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

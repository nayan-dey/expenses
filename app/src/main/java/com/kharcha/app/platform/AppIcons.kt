package com.kharcha.app.platform

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.kharcha.app.core.model.AppIcon

/**
 * Changes the icon on the home screen. Android has no "set icon" call, so the manifest holds
 * one launcher entry (an activity-alias) per icon and this switches on the chosen one and
 * switches the others off. The launcher may take a few seconds to show the change.
 */
object AppIcons {
    private const val PACKAGE = "com.kharcha.app"

    fun apply(context: Context, chosen: AppIcon) {
        val manager = context.packageManager
        // Enable the chosen one first, so there is never a moment with no launcher entry at all.
        val ordered = listOf(chosen) + AppIcon.entries.filter { it != chosen }
        for (icon in ordered) {
            val state = if (icon == chosen) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            runCatching {
                manager.setComponentEnabledSetting(
                    ComponentName(context.packageName, "$PACKAGE.${icon.alias}"),
                    state,
                    PackageManager.DONT_KILL_APP,
                )
            }
        }
    }
}

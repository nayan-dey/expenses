package com.kharcha.app.platform

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.kharcha.app.KharchaApplication
import com.kharcha.app.MainActivity
import com.kharcha.app.R
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The daily nudge. No server and no Firebase are involved: the phone's own alarm clock wakes
 * the app once a day, and the app posts a notification by itself.
 *
 * The same daily wake-up also writes the automatic backup, when that is switched on.
 */
object Reminders {
    const val CHANNEL = "daily"
    private const val REQUEST = 4101
    private const val NOTIFICATION = 1

    private fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST,
        Intent(context, DailyReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Sets (or moves) the daily wake-up to the next time the clock shows `hour:minute`. */
    fun schedule(context: Context, hour: Int, minute: Int) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        var next = LocalDateTime.now().withHour(hour.coerceIn(0, 23)).withMinute(minute.coerceIn(0, 59)).withSecond(0).withNano(0)
        if (!next.isAfter(LocalDateTime.now())) next = next.plusDays(1)
        val at = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        // "Inexact": Android may deliver it a few minutes late to save battery. That needs no special permission.
        runCatching { manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, alarmIntent(context)) }
    }

    fun cancel(context: Context) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        runCatching { manager.cancel(alarmIntent(context)) }
    }

    /** True when the app is allowed to show notifications. From Android 13 the person has to say yes once. */
    fun allowed(context: Context): Boolean {
        val granted = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val channel = NotificationChannel(CHANNEL, "Daily reminder", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "One nudge a day to log your spending and keep your streak"
        }
        manager.createNotificationChannel(channel)
    }

    /** Shows the reminder. Tapping it opens the app; `action` says where (see MainActivity). */
    fun show(context: Context, title: String, text: String, action: String) {
        if (!allowed(context)) return
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context,
            REQUEST + 1,
            Intent(context, MainActivity::class.java).setAction(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION, notification) }
    }
}

/** Woken by the daily alarm. Hands over to the app, which decides what to say and sets tomorrow's alarm. */
class DailyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val app = context.applicationContext as? KharchaApplication ?: return
        // The work reads files, so it must not block this call: ask Android for a little more time.
        val pending = goAsync()
        app.container.dailyTick { pending.finish() }
    }
}

/** After the phone restarts, alarms are forgotten. This sets the daily one again. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val app = context.applicationContext as? KharchaApplication ?: return
        val pending = goAsync()
        app.container.rescheduleDaily { pending.finish() }
    }
}

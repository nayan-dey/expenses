package com.kharcha.app.platform

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.kharcha.app.KharchaApplication
import com.kharcha.app.MainActivity
import com.kharcha.app.R
import com.kharcha.app.core.model.AppData
import com.kharcha.app.core.model.Rates
import com.kharcha.app.core.model.Settings
import com.kharcha.app.core.util.Money
import com.kharcha.app.data.KharchaJson
import com.kharcha.app.data.between
import com.kharcha.app.domain.QuickLook
import com.kharcha.app.domain.quickLook
import java.io.File

/**
 * The home-screen widgets. A widget is drawn by the launcher, not by the app, so it cannot
 * use Compose: it is a small XML layout (res/layout/widget_*.xml) that the app fills in.
 *
 *   TodayWidget: what you spent today, this month, your streak, and a + button.
 *   MonthWidget: this month's spending against your budget, and what friends owe you.
 */
object Widgets {

    /** Redraws every widget on the home screen. Called after the data changes. */
    fun refresh(context: Context) {
        runCatching {
            val manager = AppWidgetManager.getInstance(context)
            val today = manager.getAppWidgetIds(ComponentName(context, TodayWidget::class.java))
            val month = manager.getAppWidgetIds(ComponentName(context, MonthWidget::class.java))
            if (today.isEmpty() && month.isEmpty()) return
            val state = read(context)
            today.forEach { manager.updateAppWidget(it, todayViews(context, state)) }
            month.forEach { manager.updateAppWidget(it, monthViews(context, state)) }
        }
    }

    private class State(val look: QuickLook, val hidden: Boolean)

    /** The numbers to show. Uses the app's live data when it is loaded, otherwise reads the files. */
    private fun read(context: Context): State {
        val container = (context.applicationContext as? KharchaApplication)?.container
        val data: AppData
        val settings: Settings
        val rates: Rates
        if (container != null && container.loaded.value) {
            data = container.ledger.current
            settings = container.settings.current
            rates = container.rates.rates.value
        } else {
            val dir = context.filesDir
            data = runCatching { KharchaJson.decodeFromString(AppData.serializer(), File(dir, "kharcha.json").readText()) }.getOrDefault(AppData())
            settings = runCatching { KharchaJson.decodeFromString(Settings.serializer(), File(dir, "settings.json").readText()) }.getOrDefault(Settings())
            rates = runCatching { KharchaJson.decodeFromString(Rates.serializer(), File(dir, "rates.json").readText()) }.getOrDefault(Rates())
        }
        val currency = preferredCurrency(data, settings)
        val look = data.quickLook(System.currentTimeMillis(), currency) { from -> rates.between(from, currency) }
        return State(look, settings.sensitive)
    }

    private fun open(context: Context, action: String, request: Int): PendingIntent = PendingIntent.getActivity(
        context,
        request,
        Intent(context, MainActivity::class.java).setAction(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun amount(state: State, minor: Long): String = if (state.hidden) "••••" else Money.format(minor, state.look.currency)

    private fun todayViews(context: Context, state: State): RemoteViews {
        val look = state.look
        return RemoteViews(context.packageName, R.layout.widget_today).apply {
            setTextViewText(R.id.widget_amount, amount(state, look.today))
            setTextViewText(R.id.widget_sub, "This month ${amount(state, look.month)}")
            setTextViewText(
                R.id.widget_streak,
                when {
                    look.streak.current == 0 -> "Start a streak"
                    look.streak.today -> "${look.streak.current}-day streak"
                    else -> "${look.streak.current}-day streak · log today"
                },
            )
            setOnClickPendingIntent(android.R.id.background, open(context, Launch.OPEN, 11))
            setOnClickPendingIntent(R.id.widget_add, open(context, Launch.NEW_ITEM, 12))
        }
    }

    private fun monthViews(context: Context, state: State): RemoteViews {
        val look = state.look
        return RemoteViews(context.packageName, R.layout.widget_month).apply {
            setTextViewText(R.id.widget_amount, amount(state, look.month))
            if (look.budget > 0L) {
                val left = look.budget - look.month
                setTextViewText(R.id.widget_sub, if (left >= 0L) "${amount(state, left)} left of ${amount(state, look.budget)}" else "${amount(state, -left)} over budget")
                setProgressBar(R.id.widget_progress, 100, ((look.month * 100) / look.budget).toInt().coerceIn(0, 100), false)
                setViewVisibility(R.id.widget_progress, View.VISIBLE)
            } else {
                setTextViewText(R.id.widget_sub, "No monthly budget set")
                setViewVisibility(R.id.widget_progress, View.GONE)
            }
            setTextViewText(
                R.id.widget_owed,
                when {
                    look.owedToYou > 0L -> "Friends owe you ${amount(state, look.owedToYou)}"
                    look.youOwe > 0L -> "You owe friends ${amount(state, look.youOwe)}"
                    else -> "All square with friends"
                },
            )
            setOnClickPendingIntent(android.R.id.background, open(context, Launch.INSIGHTS, 13))
            setOnClickPendingIntent(R.id.widget_add, open(context, Launch.NEW_ITEM, 14))
        }
    }

    internal fun update(context: Context, manager: AppWidgetManager, ids: IntArray, today: Boolean) {
        runCatching {
            val state = read(context)
            ids.forEach { manager.updateAppWidget(it, if (today) todayViews(context, state) else monthViews(context, state)) }
        }
    }
}

/** "Today" widget: spent today, this month, the streak, and a + button. */
class TodayWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        Widgets.update(context, appWidgetManager, appWidgetIds, today = true)
    }
}

/** "This month" widget: spending against the budget, and what friends owe. */
class MonthWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        Widgets.update(context, appWidgetManager, appWidgetIds, today = false)
    }
}

package com.kharcha.app.platform

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.RemoteViews
import com.kharcha.app.KharchaApplication
import com.kharcha.app.MainActivity
import com.kharcha.app.R
import com.kharcha.app.core.model.AppData
import com.kharcha.app.core.model.Kind
import com.kharcha.app.core.model.Rates
import com.kharcha.app.core.model.Settings
import com.kharcha.app.core.util.Dates
import com.kharcha.app.core.util.Money
import com.kharcha.app.data.KharchaJson
import com.kharcha.app.data.between
import com.kharcha.app.data.rescueAppData
import com.kharcha.app.domain.Mood
import com.kharcha.app.domain.QuickLook
import com.kharcha.app.domain.gems
import com.kharcha.app.domain.moodOf
import com.kharcha.app.domain.quickLook
import com.kharcha.app.domain.repairOffer
import com.kharcha.app.domain.titleOf
import com.kharcha.app.domain.treeStage
import java.io.File

/** The four widgets. Each has a layout in res/layout and a class at the bottom of this file. */
enum class WidgetKind { TODAY, MONTH, WIDE, LARGE }

/**
 * The home-screen widgets. A widget is drawn by the launcher, not by the app, so it cannot
 * use Compose: it is a small XML layout (res/layout/widget_*.xml) that the app fills in.
 * Pictures (Gullu, the tree) are handed over as bitmaps drawn by platform/HabitArt.kt.
 *
 *   TodayWidget (2 by 2): what you spent today, this month, your streak, and a + button.
 *   MonthWidget (2 by 2): this month's spending against your budget, and what friends owe you.
 *   WideWidget  (4 by 2): Gullu in today's mood, today's spending, the streak tree, a + button.
 *   LargeWidget (4 by 4): all of that, plus the budget bar, the week, gems and the latest items.
 *
 * Until today is logged, the tree is drawn with dry leaves and Gullu waits (then worries, in
 * the evening). At midnight an unlogged day ends the streak and the tree goes back to a seed.
 */
object Widgets {

    private val providers: Map<WidgetKind, Class<out AppWidgetProvider>> = mapOf(
        WidgetKind.TODAY to TodayWidget::class.java,
        WidgetKind.MONTH to MonthWidget::class.java,
        WidgetKind.WIDE to WideWidget::class.java,
        WidgetKind.LARGE to LargeWidget::class.java,
    )

    /** True when at least one widget is on the home screen. */
    fun placed(context: Context): Boolean = runCatching {
        val manager = AppWidgetManager.getInstance(context)
        providers.values.any { manager.getAppWidgetIds(ComponentName(context, it)).isNotEmpty() }
    }.getOrDefault(false)

    /** Redraws every widget on the home screen. Called after the data changes. */
    fun refresh(context: Context) {
        runCatching {
            val manager = AppWidgetManager.getInstance(context)
            val ids = providers.mapValues { (_, provider) -> manager.getAppWidgetIds(ComponentName(context, provider)) }
            if (ids.values.all { it.isEmpty() }) return
            val state = read(context)
            ids.forEach { (kind, list) -> list.forEach { id -> manager.updateAppWidget(id, views(context, state, kind)) } }
        }
    }

    internal fun update(context: Context, manager: AppWidgetManager, ids: IntArray, kind: WidgetKind) {
        runCatching {
            val state = read(context)
            ids.forEach { manager.updateAppWidget(it, views(context, state, kind)) }
        }
    }

    /** One of the latest items, as two short texts. */
    private class Line(val title: String, val amount: String)

    private class State(
        val look: QuickLook,
        val hidden: Boolean,
        val mood: Mood,
        val gems: Int,
        val latest: List<Line>,
    )

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
            val text = runCatching { File(dir, "kharcha.json").readText() }.getOrDefault("")
            data = runCatching { KharchaJson.decodeFromString(AppData.serializer(), text) }.getOrNull() ?: rescueAppData(text) ?: AppData()
            settings = runCatching { KharchaJson.decodeFromString(Settings.serializer(), File(dir, "settings.json").readText()) }.getOrDefault(Settings())
            rates = runCatching { KharchaJson.decodeFromString(Rates.serializer(), File(dir, "rates.json").readText()) }.getOrDefault(Rates())
        }
        val now = System.currentTimeMillis()
        val currency = preferredCurrency(data, settings)
        val look = data.quickLook(now, currency) { from -> rates.between(from, currency) }
        val live = data.liveSheets.map { it.id }.toSet()
        val latest = data.items
            .filter { it.deletedAt == null && it.sheetId in live }
            .sortedByDescending { it.at }
            .take(3)
            .map { item ->
                Line(
                    title = listOfNotNull(data.titleOf(item), item.note.takeIf { it.isNotBlank() && item.settles == null }).joinToString(" · "),
                    amount = if (settings.sensitive) "••••" else (if (item.kind == Kind.EXPENSE) "−" else "+") + Money.format(item.amountMinor, item.currency),
                )
            }
        return State(
            look = look,
            hidden = settings.sensitive,
            mood = moodOf(look.streak, Dates.dateTimeOf(now).hour, data.repairOffer(now)),
            gems = data.gems().balance,
            latest = latest,
        )
    }

    private fun open(context: Context, action: String, request: Int): PendingIntent = PendingIntent.getActivity(
        context,
        request,
        Intent(context, MainActivity::class.java).setAction(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun amount(state: State, minor: Long): String = if (state.hidden) "••••" else Money.format(minor, state.look.currency)

    private fun px(context: Context, dp: Int): Int = (dp * context.resources.displayMetrics.density).toInt().coerceIn(48, 320)

    private fun streakText(look: QuickLook): String = when {
        look.streak.current == 0 -> "Start a streak today"
        look.streak.today -> "${look.streak.current}-day streak · done today"
        else -> "${look.streak.current}-day streak · log before midnight"
    }

    private fun budgetText(state: State): String {
        val look = state.look
        if (look.budget <= 0L) return "This month ${amount(state, look.month)}"
        val left = look.budget - look.month
        return if (left >= 0L) "${amount(state, left)} left of ${amount(state, look.budget)} this month" else "${amount(state, -left)} over this month's budget"
    }

    /** The last seven days as dots, oldest first: a bright dot for a day that counted. */
    private fun weekDots(week: List<Boolean>): CharSequence {
        val text = SpannableStringBuilder()
        week.forEach { done ->
            val start = text.length
            text.append("●")
            text.setSpan(ForegroundColorSpan(if (done) 0xFF45C486.toInt() else 0x44FFFFFF), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return text
    }

    private fun views(context: Context, state: State, kind: WidgetKind): RemoteViews = when (kind) {
        WidgetKind.TODAY -> todayViews(context, state)
        WidgetKind.MONTH -> monthViews(context, state)
        WidgetKind.WIDE -> wideViews(context, state)
        WidgetKind.LARGE -> largeViews(context, state)
    }

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

    /** Gullu and the tree, shared by the two bigger widgets. */
    private fun RemoteViews.pictures(context: Context, state: State, mascotDp: Int, treeDp: Int) {
        val streak = state.look.streak
        setImageViewBitmap(R.id.widget_art, HabitArt.mascot(state.mood, px(context, mascotDp)))
        // Dry leaves while today is still open; watered by logging something.
        setImageViewBitmap(R.id.widget_tree, HabitArt.tree(treeStage(streak.current), thirsty = !streak.today && streak.current > 0, px = px(context, treeDp)))
    }

    private fun wideViews(context: Context, state: State): RemoteViews {
        val look = state.look
        return RemoteViews(context.packageName, R.layout.widget_wide).apply {
            pictures(context, state, mascotDp = 68, treeDp = 46)
            setTextViewText(R.id.widget_amount, amount(state, look.today))
            setTextViewText(R.id.widget_sub, budgetText(state))
            setTextViewText(R.id.widget_streak, streakText(look))
            setOnClickPendingIntent(android.R.id.background, open(context, Launch.OPEN, 15))
            setOnClickPendingIntent(R.id.widget_tree, open(context, Launch.HABIT, 16))
            setOnClickPendingIntent(R.id.widget_add, open(context, Launch.NEW_ITEM, 17))
        }
    }

    private fun largeViews(context: Context, state: State): RemoteViews {
        val look = state.look
        return RemoteViews(context.packageName, R.layout.widget_large).apply {
            pictures(context, state, mascotDp = 64, treeDp = 64)
            setTextViewText(R.id.widget_amount, amount(state, look.today))
            setTextViewText(
                R.id.widget_sub,
                when {
                    look.owedToYou > 0L -> "Friends owe you ${amount(state, look.owedToYou)}"
                    look.youOwe > 0L -> "You owe friends ${amount(state, look.youOwe)}"
                    else -> "All square with friends"
                },
            )
            if (look.budget > 0L) {
                setProgressBar(R.id.widget_progress, 100, ((look.month * 100) / look.budget).toInt().coerceIn(0, 100), false)
                setViewVisibility(R.id.widget_progress, View.VISIBLE)
            } else {
                setViewVisibility(R.id.widget_progress, View.GONE)
            }
            setTextViewText(R.id.widget_budget, budgetText(state))
            setTextViewText(R.id.widget_streak, streakText(look))
            setTextViewText(R.id.widget_week, weekDots(look.streak.week))
            setTextViewText(R.id.widget_gems, if (state.gems == 1) "1 gem" else "${state.gems} gems")

            val rows = listOf(
                Triple(R.id.widget_row1, R.id.widget_row1_title, R.id.widget_row1_amount),
                Triple(R.id.widget_row2, R.id.widget_row2_title, R.id.widget_row2_amount),
                Triple(R.id.widget_row3, R.id.widget_row3_title, R.id.widget_row3_amount),
            )
            rows.forEachIndexed { index, (row, title, amount) ->
                val line = state.latest.getOrNull(index)
                if (line == null) {
                    setViewVisibility(row, View.GONE)
                } else {
                    setViewVisibility(row, View.VISIBLE)
                    setTextViewText(title, line.title)
                    setTextViewText(amount, line.amount)
                }
            }
            setOnClickPendingIntent(android.R.id.background, open(context, Launch.OPEN, 18))
            setOnClickPendingIntent(R.id.widget_tree, open(context, Launch.HABIT, 19))
            setOnClickPendingIntent(R.id.widget_add, open(context, Launch.NEW_ITEM, 20))
        }
    }
}

/** What every widget class does: draw itself when asked, and make sure the midnight wake-up is set once a first widget is placed. */
abstract class KharchaWidget(private val kind: WidgetKind) : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        Widgets.update(context, appWidgetManager, appWidgetIds, kind)
    }

    override fun onEnabled(context: Context) {
        (context.applicationContext as? KharchaApplication)?.container?.rescheduleDaily { }
    }
}

/** "Today" widget: spent today, this month, the streak, and a + button. */
class TodayWidget : KharchaWidget(WidgetKind.TODAY)

/** "This month" widget: spending against the budget, and what friends owe. */
class MonthWidget : KharchaWidget(WidgetKind.MONTH)

/** Wide widget: Gullu, today, the streak tree. */
class WideWidget : KharchaWidget(WidgetKind.WIDE)

/** Large widget: the whole day at a glance. */
class LargeWidget : KharchaWidget(WidgetKind.LARGE)

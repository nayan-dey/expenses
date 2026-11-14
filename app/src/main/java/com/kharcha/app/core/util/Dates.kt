package com.kharcha.app.core.util

import com.kharcha.app.core.model.PeriodKind
import com.kharcha.app.core.model.Repeat
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

/** A stretch of time: from `from` up to, but not including, `to`. */
data class Range(val from: Long, val to: Long) {
    operator fun contains(time: Long): Boolean = time >= from && time < to
}

/**
 * Time helpers. Moments are stored as UTC milliseconds; "today" and "this month" are worked
 * out in the phone's own timezone.
 */
object Dates {
    private fun zone(): ZoneId = ZoneId.systemDefault()
    private fun locale(): Locale = Locale.getDefault()

    fun dateOf(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(zone()).toLocalDate()
    fun dateTimeOf(millis: Long): LocalDateTime = Instant.ofEpochMilli(millis).atZone(zone()).toLocalDateTime()
    fun millis(dateTime: LocalDateTime): Long = dateTime.atZone(zone()).toInstant().toEpochMilli()
    fun millis(date: LocalDate, time: LocalTime): Long = millis(LocalDateTime.of(date, time))
    fun startOfDay(date: LocalDate): Long = date.atStartOfDay(zone()).toInstant().toEpochMilli()
    fun startOfDay(millis: Long): Long = startOfDay(dateOf(millis))

    /** Material's date picker speaks "midnight UTC". These two translate to and from a local date. */
    fun pickerMillis(date: LocalDate): Long = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    fun fromPicker(utcMillis: Long): LocalDate = Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate()

    fun firstDayOfWeek(): DayOfWeek = WeekFields.of(locale()).firstDayOfWeek
    fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(firstDayOfWeek()))

    // ---- text ----
    private fun format(pattern: String, millis: Long): String =
        DateTimeFormatter.ofPattern(pattern, locale()).format(dateTimeOf(millis))

    /** "4 Sep" */
    fun dayMonth(millis: Long): String = format("d MMM", millis)

    /** "Fri" */
    fun weekday(millis: Long): String = format("EEE", millis)

    /** "14 Nov 2026" */
    fun medium(millis: Long): String = format("d MMM yyyy", millis)

    /** "Saturday, 14 November" */
    fun longDay(millis: Long): String = format("EEEE, d MMMM", millis)

    /** "Nov 2026" */
    fun monthYear(millis: Long): String = format("MMM yyyy", millis)

    /** The phone's own short date, e.g. "4/11/2026". */
    fun short(millis: Long): String =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(locale()).format(dateOf(millis))

    /** "1:31 PM" or "13:31", following the phone. */
    fun time(millis: Long): String =
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale()).format(dateTimeOf(millis))

    /** "4 September 2026 at 2:51 PM" */
    fun full(millis: Long): String = format("d MMMM yyyy", millis) + " at " + time(millis)

    /** "2026-11-14" and "13:31": fixed forms for CSV files and file names. */
    fun iso(millis: Long): String = dateOf(millis).toString()
    fun isoTime(millis: Long): String = format("HH:mm", millis)

    /** "just now", "5 min ago", "3 h ago", "2 days ago", then a date. */
    fun ago(millis: Long, now: Long): String {
        val minutes = (now - millis) / 60_000
        return when {
            millis <= 0L -> "never"
            minutes < 1 -> "just now"
            minutes < 60 -> "$minutes min ago"
            minutes < 60 * 24 -> "${minutes / 60} h ago"
            minutes < 60 * 24 * 30 -> {
                val days = minutes / (60 * 24)
                if (days == 1L) "yesterday" else "$days days ago"
            }
            else -> medium(millis)
        }
    }

    // ---- periods ----

    /** The stretch of time a period covers, seen from `now`. */
    fun range(kind: PeriodKind, now: Long, customFrom: Long? = null, customTo: Long? = null): Range {
        val today = dateOf(now)
        return when (kind) {
            // Everything up to the end of today. Items dated in the future are left out.
            PeriodKind.AS_OF_TODAY -> Range(Long.MIN_VALUE, startOfDay(today.plusDays(1)))
            PeriodKind.TODAY -> Range(startOfDay(today), startOfDay(today.plusDays(1)))
            PeriodKind.THIS_WEEK -> {
                val start = weekStart(today)
                Range(startOfDay(start), startOfDay(start.plusWeeks(1)))
            }
            PeriodKind.THIS_MONTH -> {
                val start = today.withDayOfMonth(1)
                Range(startOfDay(start), startOfDay(start.plusMonths(1)))
            }
            PeriodKind.LAST_MONTH -> {
                val start = today.withDayOfMonth(1).minusMonths(1)
                Range(startOfDay(start), startOfDay(start.plusMonths(1)))
            }
            PeriodKind.THIS_YEAR -> {
                val start = today.withDayOfYear(1)
                Range(startOfDay(start), startOfDay(start.plusYears(1)))
            }
            PeriodKind.CUSTOM -> Range(
                from = customFrom ?: Long.MIN_VALUE,
                to = customTo?.let { startOfDay(dateOf(it).plusDays(1)) } ?: Long.MAX_VALUE,
            )
        }
    }

    /** The label shown for a period: its name, or the two dates of a custom one. */
    fun label(kind: PeriodKind, customFrom: Long?, customTo: Long?): String = when {
        kind != PeriodKind.CUSTOM -> kind.label
        customFrom != null && customTo != null -> "${dayMonth(customFrom)} to ${dayMonth(customTo)}"
        customFrom != null -> "From ${dayMonth(customFrom)}"
        customTo != null -> "Until ${dayMonth(customTo)}"
        else -> "Custom"
    }

    /** When a repeating item comes round next. */
    fun next(repeat: Repeat, millis: Long): Long {
        val at = dateTimeOf(millis)
        val next = when (repeat) {
            Repeat.NEVER -> return Long.MAX_VALUE
            Repeat.DAILY -> at.plusDays(1)
            Repeat.WEEKLY -> at.plusWeeks(1)
            Repeat.BIWEEKLY -> at.plusWeeks(2)
            Repeat.MONTHLY -> at.plusMonths(1)
            Repeat.QUARTERLY -> at.plusMonths(3)
            Repeat.YEARLY -> at.plusYears(1)
        }
        return millis(next)
    }
}

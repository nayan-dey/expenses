package com.kharcha.app.core.util

import com.kharcha.app.core.model.Kind
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Money helpers. Amounts are whole numbers of a currency's smallest unit ("minor units"):
 * ₹80,588.00 is 8058800, ¥500 is 500 (yen has no smaller unit). Never floating point.
 */
object Money {
    private val formatters = ConcurrentHashMap<String, NumberFormat>()

    fun currency(code: String): Currency? = runCatching { Currency.getInstance(code) }.getOrNull()

    /** How many digits come after the decimal point for this currency (2 for INR, 0 for JPY, 3 for KWD). */
    fun digits(code: String): Int = currency(code)?.defaultFractionDigits?.takeIf { it >= 0 } ?: 2

    fun toMinor(value: BigDecimal, code: String): Long =
        value.movePointRight(digits(code)).setScale(0, RoundingMode.HALF_UP).toLong()

    fun toMajor(minor: Long, code: String): BigDecimal = BigDecimal.valueOf(minor, digits(code))

    private fun formatter(code: String): NumberFormat = formatters.getOrPut(code) {
        NumberFormat.getCurrencyInstance(Locale.getDefault()).apply {
            val currency = currency(code)
            if (currency != null) this.currency = currency
            val digits = digits(code)
            minimumFractionDigits = digits
            maximumFractionDigits = digits
        }
    }

    /** "₹80,588.00". A negative number comes out as "-₹50.00". */
    fun format(minor: Long, code: String): String {
        val formatter = formatter(code)
        val text = synchronized(formatter) { formatter.format(toMajor(minor, code)) }
        // Unknown codes have no symbol of their own; make sure the code is still visible.
        return if (currency(code) == null) "$code $text" else text
    }

    /** An item's amount the way lists show it: expenses carry a minus sign. */
    fun signed(minor: Long, code: String, kind: Kind): String =
        format(if (kind == Kind.EXPENSE) -minor else minor, code)

    /** "50" or "50.5": no symbol and no thousands separators, for the amount box and CSV files. */
    fun plain(minor: Long, code: String): String {
        val value = toMajor(minor, code).stripTrailingZeros()
        return if (value.scale() < 0) value.setScale(0).toPlainString() else value.toPlainString()
    }

    /** "₹80.6K": a short form for chart labels. */
    fun compact(minor: Long, code: String): String {
        val value = toMajor(minor, code).toDouble()
        val abs = kotlin.math.abs(value)
        val (scaled, suffix) = when {
            abs >= 1_000_000_000 -> value / 1_000_000_000 to "B"
            abs >= 1_000_000 -> value / 1_000_000 to "M"
            abs >= 1_000 -> value / 1_000 to "K"
            else -> value to ""
        }
        val number = if (suffix.isEmpty() || kotlin.math.abs(scaled) >= 100) {
            String.format(Locale.getDefault(), "%.0f", scaled)
        } else {
            String.format(Locale.getDefault(), "%.1f", scaled)
        }
        return symbol(code) + number + suffix
    }

    fun symbol(code: String): String = currency(code)?.getSymbol(Locale.getDefault()) ?: code

    /** Turns an amount in one currency into another: 10.00 USD at 88.5 becomes 885.00 INR. */
    fun convert(minor: Long, from: String, to: String, rate: Double): Long {
        if (from == to) return minor
        return toMinor(toMajor(minor, from).multiply(BigDecimal.valueOf(rate)), to)
    }

    /** A rate as short text: "88.4521". */
    fun rateText(rate: Double): String {
        val digits = if (rate >= 100) 2 else if (rate >= 1) 4 else 6
        return BigDecimal.valueOf(rate).setScale(digits, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
    }
}

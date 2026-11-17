package com.kharcha.app.core.util

import java.math.BigDecimal
import java.math.MathContext

/**
 * The little calculator behind the amount box. The amount is kept as the text the person
 * typed ("120+35×2") and worked out only when needed, so they can still correct it.
 */
object Calc {
    const val PLUS = '+'
    const val MINUS = '−'
    const val TIMES = '×'
    const val DIVIDE = '÷'
    private val operators = charArrayOf(PLUS, MINUS, TIMES, DIVIDE)

    fun isOperator(c: Char): Boolean = c in operators

    /** True when the text is a sum to work out, not just a number. */
    fun hasOperator(expr: String): Boolean = expr.any { isOperator(it) }

    /** The text after pressing a key. Keys that make no sense at this point are ignored. */
    fun press(expr: String, key: Char, maxDecimals: Int): String {
        val current = expr.takeLastWhile { !isOperator(it) }
        return when {
            key.isDigit() -> {
                val decimals = current.substringAfter('.', "").length
                when {
                    expr.length >= 24 -> expr
                    '.' in current && decimals >= maxDecimals -> expr
                    // "0" followed by a digit: replace the lone zero instead of writing "05".
                    current == "0" -> expr.dropLast(1) + key
                    else -> expr + key
                }
            }
            key == '.' -> when {
                maxDecimals == 0 || '.' in current -> expr
                current.isEmpty() -> expr + "0."
                else -> expr + "."
            }
            isOperator(key) -> when {
                expr.isEmpty() -> expr
                isOperator(expr.last()) -> expr.dropLast(1) + key
                expr.last() == '.' -> expr.dropLast(1) + key
                else -> expr + key
            }
            else -> expr
        }
    }

    fun backspace(expr: String): String = expr.dropLast(1)

    /**
     * Works the text out. × and ÷ go before + and −, as on paper. Returns null when there is
     * nothing to work out, when it divides by zero, or when the answer is below zero.
     */
    fun eval(expr: String): BigDecimal? = compute(expr)?.takeIf { it.signum() >= 0 }

    /** True when the sum can be worked out but comes to less than zero ("50−58"). */
    fun belowZero(expr: String): Boolean = (compute(expr)?.signum() ?: 0) < 0

    /** The sum without its dangling last key ("20×5÷" becomes "20×5"), as it is kept on an item. */
    fun tidy(expr: String): String {
        var text = expr.trim()
        while (text.isNotEmpty() && (isOperator(text.last()) || text.last() == '.')) text = text.dropLast(1)
        return text
    }

    private fun compute(expr: String): BigDecimal? {
        var text = expr.trim()
        while (text.isNotEmpty() && (isOperator(text.last()) || text.last() == '.')) text = text.dropLast(1)
        if (text.isEmpty()) return null

        val numbers = ArrayList<BigDecimal>()
        val ops = ArrayList<Char>()
        val number = StringBuilder()
        for (c in text) {
            if (isOperator(c)) {
                numbers += number.toString().toBigDecimalOrNull() ?: return null
                number.setLength(0)
                ops += c
            } else {
                number.append(c)
            }
        }
        numbers += number.toString().toBigDecimalOrNull() ?: return null

        // First pass: multiply and divide, left to right.
        val terms = ArrayList<BigDecimal>()
        val signs = ArrayList<Char>()
        var running = numbers[0]
        for (i in ops.indices) {
            val next = numbers[i + 1]
            when (ops[i]) {
                TIMES -> running = running.multiply(next)
                DIVIDE -> {
                    if (next.signum() == 0) return null
                    running = running.divide(next, MathContext.DECIMAL64)
                }
                else -> {
                    terms += running
                    signs += ops[i]
                    running = next
                }
            }
        }
        terms += running

        // Second pass: add and subtract.
        var total = terms[0]
        for (i in signs.indices) {
            total = if (signs[i] == PLUS) total.add(terms[i + 1]) else total.subtract(terms[i + 1])
        }
        return total
    }
}

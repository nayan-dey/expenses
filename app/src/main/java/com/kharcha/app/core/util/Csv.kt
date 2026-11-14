package com.kharcha.app.core.util

/** Reads and writes CSV: the plain "comma separated" table format every spreadsheet understands. */
object Csv {

    /** One line of a CSV file. Cells with a comma, a quote or a line break are wrapped in quotes. */
    fun line(cells: List<String>): String = cells.joinToString(",") { cell ->
        if (cell.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + cell.replace("\"", "\"\"") + "\""
        } else {
            cell
        }
    }

    /** Splits a CSV text into rows of cells. The separator (comma, semicolon or tab) is worked out from the first line. */
    fun parse(text: String): List<List<String>> {
        val clean = text.removePrefix("﻿")
        val firstLine = clean.lineSequence().firstOrNull().orEmpty()
        val separator = listOf(',', ';', '\t').maxByOrNull { candidate -> firstLine.count { it == candidate } } ?: ','

        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < clean.length) {
            val c = clean[i]
            when {
                quoted && c == '"' && i + 1 < clean.length && clean[i + 1] == '"' -> {
                    cell.append('"')
                    i++
                }
                c == '"' -> quoted = !quoted
                !quoted && c == separator -> {
                    row.add(cell.toString())
                    cell.setLength(0)
                }
                !quoted && (c == '\n' || c == '\r') -> {
                    if (c == '\r' && i + 1 < clean.length && clean[i + 1] == '\n') i++
                    row.add(cell.toString())
                    cell.setLength(0)
                    rows.add(row)
                    row = ArrayList()
                }
                else -> cell.append(c)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) {
            row.add(cell.toString())
            rows.add(row)
        }
        return rows.filter { cells -> cells.any { it.isNotBlank() } }
    }
}

package com.kharcha.app.data

import com.kharcha.app.core.model.AppData
import com.kharcha.app.core.model.Category
import com.kharcha.app.core.model.Item
import com.kharcha.app.core.model.Kind
import com.kharcha.app.core.model.PayMethod
import com.kharcha.app.core.model.Sheet
import com.kharcha.app.core.util.Csv
import com.kharcha.app.core.util.Dates
import com.kharcha.app.core.util.Money
import com.kharcha.app.core.util.Range
import com.kharcha.app.core.util.newId
import com.kharcha.app.domain.inSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.BufferedInputStream
import java.io.InputStream
import java.io.OutputStream
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * A Kharcha file: a full backup, or one shared sheet. On disk it is a ZIP holding
 * `kharcha.json` (this class) and an `images/` folder with the receipt pictures.
 */
@Serializable
data class KharchaFile(
    val app: String = "kharcha",
    /** "backup" = everything, "sheet" = one sheet with the categories it uses. */
    val kind: String = "backup",
    val format: Int = 1,
    val exportedAt: Long = 0,
    val data: AppData = AppData(),
)

/** One line read from somebody's CSV file, before it becomes an item. */
data class CsvRow(
    val kind: Kind,
    val amountMinor: Long,
    val currency: String,
    val category: String,
    val note: String,
    val merchant: String,
    val at: Long,
    val pending: Boolean,
    val method: PayMethod = PayMethod.NONE,
)

data class CsvPreview(
    val rows: List<CsvRow>,
    /** Lines that could not be understood (no date or no amount). */
    val skipped: Int,
    /** Category names in the file that the app does not have yet. */
    val newCategories: List<Pair<String, Kind>>,
    val problem: String? = null,
) {
    val expenses: Int get() = rows.count { it.kind == Kind.EXPENSE }
    val incomes: Int get() = rows.count { it.kind == Kind.INCOME }
}

/**
 * Getting data out of the app and back in: backups, shared sheets and CSV files.
 * Everything here is a file the person saves or opens themselves. Nothing is sent anywhere.
 */
class Exchange(private val ledger: Ledger, private val images: ImageStore, private val now: () -> Long) {

    // ---------------------------------------------------------------------------
    // Kharcha files (backup, shared sheet)
    // ---------------------------------------------------------------------------

    /** Everything, in one file. */
    suspend fun writeBackup(out: OutputStream) = writeFile(out, KharchaFile(kind = "backup", exportedAt = now(), data = ledger.current))

    /** One sheet with its items and the categories they use, to hand to another person. */
    suspend fun writeSheet(sheetId: String, out: OutputStream) {
        val data = ledger.current
        val sheet = data.sheet(sheetId) ?: return
        val items = data.itemsOf(sheetId)
        val used = items.mapNotNull { it.categoryId }.toSet()
        val part = AppData(sheets = listOf(sheet), categories = data.categories.filter { it.id in used }, items = items)
        writeFile(out, KharchaFile(kind = "sheet", exportedAt = now(), data = part))
    }

    private suspend fun writeFile(out: OutputStream, file: KharchaFile) = withContext(Dispatchers.IO) {
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("kharcha.json"))
            zip.write(KharchaJson.encodeToString(KharchaFile.serializer(), file).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            file.data.items.flatMap { it.images }.distinct().forEach { name ->
                val picture = images.file(name)
                if (picture.exists()) {
                    zip.putNextEntry(ZipEntry("images/$name"))
                    picture.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
    }

    /**
     * Reads a Kharcha file. Its pictures are copied into the app's folder straight away; the
     * data is only handed back, and the caller decides whether to merge or replace.
     * Returns null when the file is not a Kharcha file.
     */
    suspend fun readFile(input: InputStream): KharchaFile? = withContext(Dispatchers.IO) {
        runCatching {
            val stream = BufferedInputStream(input)
            stream.mark(4)
            val isZip = stream.read() == 'P'.code && stream.read() == 'K'.code
            stream.reset()
            if (!isZip) {
                // An unzipped kharcha.json is accepted too.
                return@runCatching parse(stream.readBytes().toString(Charsets.UTF_8))
            }
            var result: KharchaFile? = null
            ZipInputStream(stream).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    // Only the bare file name is used, so a crafted path cannot write outside our folder.
                    val name = entry.name.substringAfterLast('/')
                    when {
                        entry.isDirectory -> Unit
                        entry.name == "kharcha.json" -> result = parse(zip.readBytes().toString(Charsets.UTF_8))
                        entry.name.startsWith("images/") && name.isNotBlank() -> images.file(name).outputStream().use { zip.copyTo(it) }
                    }
                }
            }
            result
        }.getOrNull()
    }

    private fun parse(text: String): KharchaFile? = readKharchaJson(text)

    // ---------------------------------------------------------------------------
    // CSV out
    // ---------------------------------------------------------------------------

    private val header = listOf(
        "Date", "Time", "Type", "Amount", "Currency", "Category", "Notes", "Merchant", "Sheet",
        "Pending", "Repeat", "Amount in sheet currency", "Sheet currency", "Pictures",
        "Paid with", "Who paid", "Friends owe",
    )

    /** The items of the given sheets (all sheets when null) inside a stretch of time, oldest first. */
    fun itemsFor(sheetIds: Set<String>?, range: Range): List<Item> {
        val data = ledger.current
        val live = data.liveSheets.map { it.id }.toSet()
        return data.items
            .filter { it.deletedAt == null && it.sheetId in live && (sheetIds == null || it.sheetId in sheetIds) && it.at in range }
            .sortedBy { it.at }
    }

    fun csv(items: List<Item>): String {
        val data = ledger.current
        val lines = ArrayList<String>()
        lines += Csv.line(header)
        for (item in items) {
            val sheet = data.sheet(item.sheetId) ?: continue
            val type = when {
                item.settles != null && item.kind == Kind.INCOME -> "Repaid to me"
                item.settles != null -> "Repaid by me"
                item.isTransfer && item.kind == Kind.EXPENSE -> "Transfer out"
                item.isTransfer -> "Transfer in"
                item.kind == Kind.EXPENSE -> "Expense"
                else -> "Income"
            }
            lines += Csv.line(
                listOf(
                    Dates.iso(item.at),
                    Dates.isoTime(item.at),
                    type,
                    Money.plain(item.amountMinor, item.currency),
                    item.currency,
                    data.category(item.categoryId)?.name.orEmpty(),
                    item.note,
                    item.merchant,
                    sheet.name,
                    if (item.pending) "yes" else "no",
                    if (item.repeat == com.kharcha.app.core.model.Repeat.NEVER) "" else item.repeat.label,
                    Money.plain(item.inSheet(sheet), sheet.currency),
                    sheet.currency,
                    item.images.joinToString(" "),
                    if (item.method == PayMethod.NONE) "" else item.method.label,
                    // Who paid the bill: a friend's name, or the friend a repayment was with.
                    data.person(item.payer ?: item.settles)?.name.orEmpty(),
                    if (item.lent > 0L) Money.plain(item.lent, item.currency) else "",
                ),
            )
        }
        return lines.joinToString("\r\n") + "\r\n"
    }

    /** A ZIP with the CSV and the pictures the items point at. */
    suspend fun writeCsvZip(out: OutputStream, items: List<Item>) = withContext(Dispatchers.IO) {
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("kharcha.csv"))
            zip.write(csv(items).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            items.flatMap { it.images }.distinct().forEach { name ->
                val picture = images.file(name)
                if (picture.exists()) {
                    zip.putNextEntry(ZipEntry("images/$name"))
                    picture.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
    }

    // ---------------------------------------------------------------------------
    // CSV in
    // ---------------------------------------------------------------------------

    private fun column(headers: List<String>, vararg names: String): Int =
        headers.indexOfFirst { header -> names.any { header == it } }.takeIf { it >= 0 }
            ?: headers.indexOfFirst { header -> names.any { header.startsWith(it) } }

    /**
     * Reads a CSV from another app or a spreadsheet. The first row must name the columns;
     * the names are matched loosely ("Notes", "Description" and "Memo" all count as the note).
     */
    fun previewCsv(text: String, sheet: Sheet): CsvPreview {
        val rows = Csv.parse(text)
        if (rows.size < 2) return CsvPreview(emptyList(), 0, emptyList(), "The file has no rows to import.")
        val headers = rows.first().map { it.trim().lowercase() }
        val dateAt = column(headers, "date", "day", "transaction date", "created")
        val timeAt = column(headers, "time")
        val typeAt = column(headers, "type", "kind", "transaction type")
        val amountAt = column(headers, "amount", "value", "sum", "total")
        val outAt = column(headers, "expense", "debit", "withdrawal", "paid out", "money out")
        val inAt = column(headers, "income", "credit", "deposit", "paid in", "money in")
        val currencyAt = column(headers, "currency")
        val categoryAt = column(headers, "category")
        val noteAt = column(headers, "notes", "note", "description", "memo", "details", "title", "name")
        val merchantAt = column(headers, "merchant", "payee", "vendor", "store")
        val pendingAt = column(headers, "pending", "status")
        val methodAt = column(headers, "paid with", "payment method", "method", "payment")
        if (dateAt < 0 || (amountAt < 0 && outAt < 0 && inAt < 0)) {
            return CsvPreview(emptyList(), 0, emptyList(), "The first row must name the columns, with at least a Date and an Amount column.")
        }

        val body = rows.drop(1)
        val dayFirst = guessDayFirst(body.mapNotNull { it.getOrNull(dateAt) })
        val parsed = ArrayList<CsvRow>()
        var skipped = 0
        for (cells in body) {
            fun cell(index: Int): String = if (index < 0) "" else cells.getOrNull(index).orEmpty().trim()
            val date = parseDate(cell(dateAt), dayFirst)
            val rawAmount = parseAmount(cell(amountAt))
            val out = parseAmount(cell(outAt))
            val into = parseAmount(cell(inAt))
            val type = cell(typeAt).lowercase()
            val (kind, value) = when {
                into != null && into.signum() != 0 -> Kind.INCOME to into.abs()
                out != null && out.signum() != 0 -> Kind.EXPENSE to out.abs()
                rawAmount == null -> null to null
                type.contains("inc") || type.contains("credit") || type.contains("deposit") || type.endsWith(" in") -> Kind.INCOME to rawAmount.abs()
                type.contains("exp") || type.contains("debit") || type.contains("out") -> Kind.EXPENSE to rawAmount.abs()
                // No type column: a minus sign means money went out. Plain numbers are taken as expenses.
                rawAmount.signum() < 0 -> Kind.EXPENSE to rawAmount.abs()
                typeAt < 0 && cell(amountAt).trim().startsWith("+") -> Kind.INCOME to rawAmount
                else -> Kind.EXPENSE to rawAmount
            }
            if (date == null || kind == null || value == null) {
                skipped++
                continue
            }
            val currency = cell(currencyAt).uppercase().takeIf { it.length == 3 && Money.currency(it) != null } ?: sheet.currency
            val time = parseTime(cell(timeAt)) ?: parseTime(timePart(cell(dateAt))) ?: LocalTime.NOON
            val pendingText = cell(pendingAt).lowercase()
            parsed += CsvRow(
                kind = kind,
                amountMinor = Money.toMinor(value, currency),
                currency = currency,
                category = cell(categoryAt),
                note = cell(noteAt),
                merchant = cell(merchantAt),
                at = Dates.millis(date, time),
                pending = pendingText == "yes" || pendingText == "true" || pendingText == "pending" || pendingText == "1",
                method = cell(methodAt).lowercase().let { text ->
                    when {
                        text.contains("upi") -> PayMethod.UPI
                        text.contains("cash") -> PayMethod.CASH
                        text.contains("card") -> PayMethod.CARD
                        text.contains("bank") || text.contains("transfer") -> PayMethod.BANK
                        else -> PayMethod.NONE
                    }
                },
            )
        }
        val known = ledger.current.liveCategories
        val fresh = parsed.filter { it.category.isNotBlank() }
            .map { it.category to it.kind }
            .distinctBy { (name, kind) -> name.lowercase() to kind }
            .filter { (name, kind) -> known.none { it.kind == kind && it.name.equals(name, ignoreCase = true) } }
        return CsvPreview(parsed, skipped, fresh)
    }

    /**
     * Writes previewed rows into a sheet. Categories named in `create` are added; rows whose
     * category is neither known nor created end up with no category.
     * `rateFor` gives the exchange rate for a row in another currency.
     */
    fun importCsv(preview: CsvPreview, sheet: Sheet, create: Set<Pair<String, Kind>>, rateFor: (String) -> Double): Int {
        val time = now()
        val existing = ledger.current.liveCategories
        val created = preview.newCategories.filter { it in create }.mapIndexed { index, (name, kind) ->
            Category(id = newId(), name = name, kind = kind, icon = "label", color = Seed.palette[index % Seed.palette.size])
        }
        created.forEach { ledger.saveCategory(it) }
        val all = existing + created
        val items = preview.rows.map { row ->
            Item(
                id = newId(),
                sheetId = sheet.id,
                kind = row.kind,
                amountMinor = row.amountMinor,
                currency = row.currency,
                rate = if (row.currency == sheet.currency) 1.0 else rateFor(row.currency),
                note = row.note,
                merchant = row.merchant,
                categoryId = all.firstOrNull { it.kind == row.kind && it.name.equals(row.category, ignoreCase = true) }?.id,
                at = row.at,
                pending = row.pending,
                method = row.method,
                createdAt = time,
                editedAt = time,
            )
        }
        ledger.merge(AppData(items = items))
        ledger.saveSheet(ledger.current.sheet(sheet.id) ?: sheet)
        return items.size
    }

    private fun parseAmount(raw: String): BigDecimal? {
        if (raw.isBlank()) return null
        var text = raw.trim()
        val negative = text.startsWith("(") && text.endsWith(")") || text.contains('-') || text.contains('−')
        text = text.filter { it.isDigit() || it == '.' || it == ',' }
        if (text.isEmpty()) return null
        val lastDot = text.lastIndexOf('.')
        val lastComma = text.lastIndexOf(',')
        val normal = when {
            // Both present: whichever comes last is the decimal mark ("1,234.56" or "1.234,56").
            lastDot >= 0 && lastComma >= 0 ->
                if (lastDot > lastComma) text.replace(",", "") else text.replace(".", "").replace(',', '.')
            // Only commas: a comma followed by one or two digits is a decimal mark, otherwise thousands.
            lastComma >= 0 -> if (text.length - lastComma - 1 in 1..2) text.replace(',', '.') else text.replace(",", "")
            else -> text
        }
        val value = normal.toBigDecimalOrNull() ?: return null
        return if (negative) value.negate() else value
    }

    /** "03/04/2026" is April in some countries and March in others. Look for a number above 12 to tell. */
    private fun guessDayFirst(dates: List<String>): Boolean {
        for (text in dates) {
            val parts = text.trim().split('/', '-', '.').mapNotNull { it.trim().take(4).toIntOrNull() }
            if (parts.size < 3 || parts[0] > 31) continue
            if (parts[0] > 12) return true
            if (parts[1] > 12) return false
        }
        return Locale.getDefault().country !in setOf("US", "PH", "CA")
    }

    private fun parseDate(raw: String, dayFirst: Boolean): LocalDate? {
        var text = raw.trim()
        if (text.isEmpty()) return null
        // Drop a time written after the date: "2026-11-14T13:31:00" or "14/11/2026 13:31".
        text = if (isoDateTime.containsMatchIn(text)) {
            text.substringBefore('T')
        } else {
            text.replace(trailingTime, "").trim().trimEnd(',').trim()
        }
        val numeric = if (dayFirst) listOf("d/M/yyyy", "d-M-yyyy", "d.M.yyyy", "d/M/yy", "d-M-yy") else listOf("M/d/yyyy", "M-d-yyyy", "M.d.yyyy", "M/d/yy", "M-d-yy")
        val patterns = listOf("yyyy-M-d", "yyyy/M/d", "yyyy.M.d") + numeric + listOf("d MMM yyyy", "d MMMM yyyy", "MMM d, yyyy", "MMMM d, yyyy", "d-MMM-yyyy", "d MMM yy")
        for (pattern in patterns) {
            val date = runCatching { LocalDate.parse(text, DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH)) }.getOrNull()
            if (date != null) return date
        }
        return null
    }

    private val isoDateTime = Regex("^\\d{4}-\\d{1,2}-\\d{1,2}T(\\d{1,2}:\\d{2}(:\\d{2})?)")
    private val trailingTime = Regex("(\\d{1,2}:\\d{2}(:\\d{2})?\\s*([AaPp][Mm])?)\\s*$")

    /** The time written inside a date cell, if there is one. */
    private fun timePart(raw: String): String {
        val text = raw.trim()
        isoDateTime.find(text)?.let { return it.groupValues[1] }
        return trailingTime.find(text)?.groupValues?.get(1).orEmpty()
    }

    private fun parseTime(raw: String): LocalTime? {
        val text = raw.trim().uppercase()
        if (text.isEmpty()) return null
        for (pattern in listOf("H:mm", "H:mm:ss", "h:mm a", "h:mm:ss a", "h:mma")) {
            val time = runCatching { LocalTime.parse(text, DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH)) }.getOrNull()
            if (time != null) return time
        }
        return null
    }
}

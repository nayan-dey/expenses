package com.kharcha.app.platform

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.FileProvider
import com.kharcha.app.core.model.AppData
import com.kharcha.app.core.model.Item
import com.kharcha.app.core.model.Kind
import com.kharcha.app.core.model.PrintOptions
import com.kharcha.app.core.model.Sheet
import com.kharcha.app.core.util.Dates
import com.kharcha.app.core.util.Money
import com.kharcha.app.domain.inSheet
import com.kharcha.app.domain.slices
import com.kharcha.app.domain.totals
import java.io.File

/**
 * Printing and sharing. A sheet is laid out as a small web page and handed to Android's own
 * print screen, which works with any printer the phone knows and with "Save as PDF".
 */
object Printer {
    // The page being printed must stay alive until Android has finished reading it.
    private var printing: WebView? = null

    private fun esc(text: String): String =
        text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    /** A sheet as a printable page: a summary, an optional split by category, then every item by day. */
    fun sheetHtml(data: AppData, sheet: Sheet, items: List<Item>, periodLabel: String, options: PrintOptions): String {
        val sums = totals(items, sheet)
        val code = sheet.currency
        val html = StringBuilder()
        html.append(
            """
            <html><head><meta charset="utf-8"><style>
            body { font-family: sans-serif; color: #111; margin: 28px; font-size: 12px; }
            h1 { font-size: 24px; margin: 0 0 2px 0; }
            .sub { color: #666; margin-bottom: 18px; }
            .cards { display: flex; gap: 12px; margin-bottom: 18px; }
            .card { flex: 1; border: 1px solid #ccc; border-radius: 10px; padding: 10px 12px; }
            .card b { display: block; font-size: 17px; margin-top: 2px; }
            .label { color: #666; font-size: 10px; text-transform: uppercase; letter-spacing: .06em; }
            table { width: 100%; border-collapse: collapse; margin-bottom: 16px; }
            th { text-align: left; color: #666; font-size: 10px; text-transform: uppercase; letter-spacing: .06em; border-bottom: 1px solid #999; padding: 6px 4px; }
            td { padding: 6px 4px; border-bottom: 1px solid #e4e4e4; vertical-align: top; }
            .num { text-align: right; white-space: nowrap; }
            .day td { background: #f1f1f6; font-weight: bold; }
            .muted { color: #666; }
            h2 { font-size: 14px; margin: 18px 0 6px 0; }
            </style></head><body>
            """.trimIndent(),
        )
        html.append("<h1>${esc(sheet.name)}</h1>")
        html.append("<div class=\"sub\">${esc(periodLabel)} · ${items.size} items · printed ${esc(Dates.medium(System.currentTimeMillis()))}</div>")

        if (options.summary) {
            html.append("<div class=\"cards\">")
            html.append("<div class=\"card\"><span class=\"label\">Balance</span><b>${esc(Money.format(sums.balance, code))}</b></div>")
            html.append("<div class=\"card\"><span class=\"label\">Expense</span><b>${esc(Money.format(-sums.expense, code))}</b></div>")
            html.append("<div class=\"card\"><span class=\"label\">Income</span><b>${esc(Money.format(sums.income, code))}</b></div>")
            html.append("</div>")
        }

        if (options.byCategory) {
            val split = slices(items, sheet, Kind.EXPENSE, data)
            if (split.isNotEmpty()) {
                html.append("<h2>Spending by category</h2><table><tr><th>Category</th><th class=\"num\">Items</th><th class=\"num\">Share</th><th class=\"num\">Amount</th></tr>")
                for (slice in split) {
                    html.append("<tr><td>${esc(slice.category?.name ?: "No category")}</td><td class=\"num\">${slice.count}</td>")
                    html.append("<td class=\"num\">${Math.round(slice.share * 100)}%</td><td class=\"num\">${esc(Money.format(slice.amount, code))}</td></tr>")
                }
                html.append("</table>")
            }
        }

        html.append("<h2>Items</h2><table><tr><th>Time</th><th>Category</th>")
        if (options.notes) html.append("<th>Notes</th>")
        if (options.merchant) html.append("<th>Merchant</th>")
        html.append("<th class=\"num\">Amount</th></tr>")
        val columns = 3 + (if (options.notes) 1 else 0) + (if (options.merchant) 1 else 0)
        items.sortedByDescending { it.at }.groupBy { Dates.startOfDay(it.at) }.forEach { (day, dayItems) ->
            val net = totals(dayItems, sheet).balance
            html.append("<tr class=\"day\"><td colspan=\"${columns - 1}\">${esc(Dates.medium(day))} · ${esc(Dates.weekday(day))}</td><td class=\"num\">${esc(Money.format(net, code))}</td></tr>")
            for (item in dayItems) {
                val title = when {
                    item.isTransfer && item.kind == Kind.EXPENSE -> "Transfer to ${data.sheet(item.transferPeerSheetId)?.name ?: "another sheet"}"
                    item.isTransfer -> "Transfer from ${data.sheet(item.transferPeerSheetId)?.name ?: "another sheet"}"
                    else -> data.category(item.categoryId)?.name ?: "No category"
                }
                html.append("<tr><td class=\"muted\">${esc(Dates.time(item.at))}</td><td>${esc(title)}${if (item.pending) " <span class=\"muted\">(pending)</span>" else ""}</td>")
                if (options.notes) html.append("<td>${esc(item.note)}</td>")
                if (options.merchant) html.append("<td>${esc(item.merchant)}</td>")
                val amount = Money.signed(item.inSheet(sheet), code, item.kind)
                val original = if (item.currency != code) "<br><span class=\"muted\">${esc(Money.format(item.amountMinor, item.currency))}</span>" else ""
                html.append("<td class=\"num\">${esc(amount)}$original</td></tr>")
            }
        }
        html.append("</table></body></html>")
        return html.toString()
    }

    /** Opens Android's print screen for a page. Returns false if the phone cannot print. */
    fun print(activity: Activity, jobName: String, html: String): Boolean {
        val manager = activity.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return false
        return runCatching {
            val view = WebView(activity)
            view.webViewClient = object : WebViewClient() {
                override fun onPageFinished(page: WebView, url: String?) {
                    manager.print(jobName, page.createPrintDocumentAdapter(jobName), PrintAttributes.Builder().build())
                }
            }
            view.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
            printing = view
            true
        }.getOrDefault(false)
    }
}

/** Handing things to other apps through Android's share sheet. */
object Share {

    /** A fresh file in the "share" cache folder, and the address other apps can read it from. */
    fun newFile(context: Context, name: String): Pair<File, Uri> {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        // Old exports are not needed once shared.
        dir.listFiles()?.forEach { runCatching { it.delete() } }
        val file = File(dir, name)
        return file to FileProvider.getUriForFile(context, context.packageName + ".files", file)
    }

    fun send(context: Context, uri: Uri, mime: String, title: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { context.startActivity(Intent.createChooser(intent, title)) }
    }

    fun view(context: Context, uri: Uri, mime: String) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { context.startActivity(intent) }
    }
}

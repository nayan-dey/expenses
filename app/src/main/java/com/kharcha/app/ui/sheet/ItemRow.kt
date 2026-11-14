package com.kharcha.app.ui.sheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kharcha.app.core.model.AppData
import com.kharcha.app.core.model.Item
import com.kharcha.app.core.model.Kind
import com.kharcha.app.core.model.Repeat
import com.kharcha.app.core.model.Sheet
import com.kharcha.app.core.util.Dates
import com.kharcha.app.domain.inSheet
import com.kharcha.app.domain.titleOf
import com.kharcha.app.ui.components.CategoryDot
import com.kharcha.app.ui.components.KIcon
import com.kharcha.app.ui.components.money
import com.kharcha.app.ui.components.moneySigned
import com.kharcha.app.ui.icons.Ic
import com.kharcha.app.ui.theme.KharchaTheme
import com.kharcha.app.ui.theme.KharchaType

/**
 * One item in a list: its category badge, what it was, a second line (note, merchant) and
 * the amount. Used on a sheet, in search results and in the trash.
 *
 * @param extra a word or two added to the second line (the sheet's name in search results, a date in the trash)
 * @param checked null normally; in select mode, whether this row is ticked
 */
@Composable
fun ItemRowContent(item: Item, sheet: Sheet, data: AppData, modifier: Modifier = Modifier, extra: String? = null, checked: Boolean? = null) {
    val scheme = MaterialTheme.colorScheme
    val second = listOfNotNull(
        item.note.takeIf { it.isNotBlank() },
        item.merchant.takeIf { it.isNotBlank() },
        extra,
    ).joinToString(" · ")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (checked != null) {
            KIcon(
                if (checked) Ic.check_circle else Ic.radio_button_unchecked,
                fill = checked,
                tint = if (checked) scheme.primary else scheme.outline,
                contentDescription = if (checked) "Selected" else "Not selected",
            )
        }
        CategoryDot(data.category(item.categoryId), transfer = item.isTransfer)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(data.titleOf(item), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (second.isNotEmpty()) {
                Text(second, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                moneySigned(item.inSheet(sheet), sheet.currency, item.kind),
                style = KharchaType.amount,
                color = if (item.kind == Kind.INCOME) KharchaTheme.extras.income else scheme.onSurface,
                maxLines = 1,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                // Paid in another currency: show what was actually paid.
                if (item.currency != sheet.currency) {
                    Text(money(item.amountMinor, item.currency), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant, maxLines = 1)
                }
                if (item.repeat != Repeat.NEVER) KIcon(Ic.repeat, size = 14.dp, tint = scheme.onSurfaceVariant, contentDescription = "Repeats")
                if (item.images.isNotEmpty()) KIcon(Ic.image, size = 14.dp, tint = scheme.onSurfaceVariant, contentDescription = "Has a picture")
                if (item.pending) KIcon(Ic.hourglass_top, size = 14.dp, tint = scheme.tertiary, contentDescription = "Pending")
            }
        }
    }
}

/** "4 Sep" plus the weekday, for day headers. */
fun dayTitle(day: Long, now: Long): Pair<String, String> {
    val today = Dates.startOfDay(now)
    val weekday = when (day) {
        today -> "Today"
        Dates.startOfDay(Dates.dateOf(now).minusDays(1)) -> "Yesterday"
        else -> Dates.weekday(day)
    }
    val sameYear = Dates.dateOf(day).year == Dates.dateOf(now).year
    return (if (sameYear) Dates.dayMonth(day) else Dates.medium(day)) to weekday
}

package com.kharcha.app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType

/** What kind of thing goes in a field. It picks the right keyboard. */
enum class FieldKind { TEXT, NAME, MONEY, QUANTITY }

/** A form field: Material's OutlinedTextField with Kharcha's rounded shape. */
@Composable
fun KField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    kind: FieldKind = FieldKind.TEXT,
    prefix: String? = null,
    suffix: String? = null,
    supporting: String? = null,
    error: String? = null,
    last: Boolean = false,
    /** false = the box grows with the text (notes). */
    singleLine: Boolean = true,
) {
    // Typed out so Kotlin knows these optional slots are composable.
    val prefixSlot: (@Composable () -> Unit)? = if (prefix != null) { { Text(prefix) } } else null
    val suffixSlot: (@Composable () -> Unit)? = if (suffix != null) { { Text(suffix) } } else null
    val help = error ?: supporting
    val supportingSlot: (@Composable () -> Unit)? = if (help != null) { { Text(help) } } else null
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        prefix = prefixSlot,
        suffix = suffixSlot,
        supportingText = supportingSlot,
        isError = error != null,
        singleLine = singleLine,
        maxLines = if (singleLine) 1 else 5,
        shape = MaterialTheme.shapes.small,
        keyboardOptions = KeyboardOptions(
            capitalization = when (kind) {
                FieldKind.NAME -> KeyboardCapitalization.Words
                FieldKind.TEXT -> KeyboardCapitalization.Sentences
                else -> KeyboardCapitalization.None
            },
            keyboardType = when (kind) {
                FieldKind.MONEY, FieldKind.QUANTITY -> KeyboardType.Decimal
                else -> KeyboardType.Text
            },
            imeAction = if (!singleLine) ImeAction.Default else if (last) ImeAction.Done else ImeAction.Next,
        ),
    )
}

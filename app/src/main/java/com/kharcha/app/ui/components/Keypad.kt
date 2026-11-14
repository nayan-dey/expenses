@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.kharcha.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kharcha.app.core.util.Calc
import com.kharcha.app.ui.icons.Ic
import com.kharcha.app.ui.theme.KharchaType

private enum class KeyStyle { DIGIT, OPERATOR, ACTION }

/**
 * The amount keypad: digits on the left, sums on the right. Every key is a Material Button,
 * so it gets Material's press shape and ripple for free. It reports the key pressed; the
 * screen owns the text.
 *
 * @param decimal false for currencies with no smaller unit (yen), which hides the point key
 */
@Composable
fun Keypad(
    onKey: (Char) -> Unit,
    onBackspace: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    decimal: Boolean = true,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp, top = 2.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Btn("Done", onDone, trailing = Ic.expand_more, style = BtnStyle.TEXT, size = BtnSize.SM, contentDescription = "Hide the keypad")
            }
            KeyRow {
                Key("1", onKey); Key("2", onKey); Key("3", onKey)
                Key(Calc.DIVIDE.toString(), onKey, KeyStyle.OPERATOR, "Divide")
            }
            KeyRow {
                Key("4", onKey); Key("5", onKey); Key("6", onKey)
                Key(Calc.TIMES.toString(), onKey, KeyStyle.OPERATOR, "Multiply")
            }
            KeyRow {
                Key("7", onKey); Key("8", onKey); Key("9", onKey)
                Key(Calc.MINUS.toString(), onKey, KeyStyle.OPERATOR, "Minus")
            }
            KeyRow {
                if (decimal) Key(".", onKey, description = "Point") else Spacer(Modifier.weight(1f))
                Key("0", onKey)
                IconKey(Ic.backspace, "Delete the last digit", onBackspace, KeyStyle.DIGIT)
                Key(Calc.PLUS.toString(), onKey, KeyStyle.OPERATOR, "Plus")
            }
        }
    }
}

@Composable
private fun KeyRow(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), content = content)
}

@Composable
private fun keyColors(style: KeyStyle) = when (style) {
    KeyStyle.DIGIT -> ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface,
    )
    KeyStyle.OPERATOR -> ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    )
    KeyStyle.ACTION -> ButtonDefaults.buttonColors()
}

@Composable
private fun RowScope.Key(label: String, onKey: (Char) -> Unit, style: KeyStyle = KeyStyle.DIGIT, description: String? = null) {
    val haptics = rememberHaptics()
    Button(
        onClick = {
            haptics.tick()
            onKey(label[0])
        },
        shapes = ButtonDefaults.shapesFor(KEY_HEIGHT),
        modifier = Modifier
            .weight(1f)
            .height(KEY_HEIGHT)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
        colors = keyColors(style),
        contentPadding = PaddingValues(0.dp),
    ) {
        Text(label, style = KharchaType.key)
    }
}

@Composable
private fun RowScope.IconKey(icon: Ic, description: String, onClick: () -> Unit, style: KeyStyle) {
    val haptics = rememberHaptics()
    Button(
        onClick = {
            haptics.tick()
            onClick()
        },
        shapes = ButtonDefaults.shapesFor(KEY_HEIGHT),
        modifier = Modifier
            .weight(1f)
            .height(KEY_HEIGHT),
        colors = keyColors(style),
        contentPadding = PaddingValues(0.dp),
    ) {
        KIcon(icon, size = 26.dp, contentDescription = description)
    }
}

private val KEY_HEIGHT = 54.dp

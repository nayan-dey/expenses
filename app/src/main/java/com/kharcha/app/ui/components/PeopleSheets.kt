package com.kharcha.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kharcha.app.core.model.Person
import com.kharcha.app.ui.icons.Ic

/** A box to type a new friend's name, with an Add button. Shown at the bottom of the friend pickers. */
@Composable
private fun AddFriend(onAdd: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        KField(name, { name = it }, "Add a friend", Modifier.weight(1f), kind = FieldKind.NAME, last = true)
        IconBtn(
            Ic.person_add,
            "Add this friend",
            {
                if (name.isNotBlank()) {
                    onAdd(name.trim())
                    name = ""
                }
            },
            style = IconBtnStyle.FILLED,
        )
    }
}

/** Who paid the bill: you, or one friend. */
@Composable
fun PayerSheet(
    people: List<Person>,
    selectedId: String?,
    onPick: (String?) -> Unit,
    onAdd: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val row = MaterialTheme.colorScheme.surfaceContainerHigh
    KSheet(onDismiss, "Who paid?") {
        Column(
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
        ) {
            KGroup {
                CheckRow(
                    0, 1, "You", selected = selectedId == null,
                    onClick = { onPick(null); onDismiss() },
                    background = row,
                    leading = { IconTile(Ic.person, MaterialTheme.colorScheme.primary) },
                )
            }
            if (people.isNotEmpty()) {
                Gap(10.dp)
                KGroup {
                    people.forEachIndexed { index, person ->
                        CheckRow(
                            index, people.size, person.name, selected = person.id == selectedId,
                            onClick = { onPick(person.id); onDismiss() },
                            subtitle = "You owe them your share",
                            background = row,
                            leading = { ShapeAvatar(person.name, person.id, size = 40.dp) },
                        )
                    }
                }
            }
        }
        AddFriend(onAdd)
    }
}

/** Pick one friend, or "none of them". Used for money lent or borrowed. */
@Composable
fun FriendSheet(
    title: String,
    /** The first row, which picks nobody ("Not a loan"). */
    none: String,
    noneNote: String?,
    /** The line under each friend's name. */
    friendNote: String?,
    people: List<Person>,
    selectedId: String?,
    onPick: (String?) -> Unit,
    onAdd: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val row = MaterialTheme.colorScheme.surfaceContainerHigh
    KSheet(onDismiss, title) {
        Column(
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
        ) {
            KGroup {
                CheckRow(
                    0, 1, none, selected = selectedId == null,
                    onClick = { onPick(null); onDismiss() },
                    subtitle = noneNote,
                    background = row,
                    leading = { IconTile(Ic.close, MaterialTheme.colorScheme.outline) },
                )
            }
            if (people.isNotEmpty()) {
                Gap(10.dp)
                KGroup {
                    people.forEachIndexed { index, person ->
                        CheckRow(
                            index, people.size, person.name, selected = person.id == selectedId,
                            onClick = { onPick(person.id); onDismiss() },
                            subtitle = friendNote,
                            background = row,
                            leading = { ShapeAvatar(person.name, person.id, size = 40.dp) },
                        )
                    }
                }
            }
        }
        AddFriend(onAdd)
    }
}

/** Which friends share a bill you paid. Tap to tick or untick; the sheet stays open. */
@Composable
fun SplitWithSheet(
    people: List<Person>,
    selected: List<String>,
    onChange: (List<String>) -> Unit,
    onAdd: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val row = MaterialTheme.colorScheme.surfaceContainerHigh
    KSheet(onDismiss, "Split with") {
        Column(
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
        ) {
            if (people.isEmpty()) {
                Foot("Add the friends you go out with. Each one is just a name; their UPI address can be added later under Friends.")
            } else {
                KGroup {
                    people.forEachIndexed { index, person ->
                        val on = person.id in selected
                        CheckRow(
                            index, people.size, person.name, selected = on,
                            onClick = { onChange(if (on) selected - person.id else selected + person.id) },
                            background = row,
                            leading = { ShapeAvatar(person.name, person.id, size = 40.dp) },
                        )
                    }
                }
            }
        }
        AddFriend(onAdd)
        Btn(
            if (selected.isEmpty()) "Done" else "Split with ${selected.size}",
            onDismiss,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            icon = Ic.check,
            size = BtnSize.SM,
        )
    }
}

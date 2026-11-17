package com.kharcha.app.data

import com.kharcha.app.core.model.AppData
import com.kharcha.app.core.model.Category
import com.kharcha.app.core.model.Goal
import com.kharcha.app.core.model.Item
import com.kharcha.app.core.model.Person
import com.kharcha.app.core.model.Sheet
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

/*
 * Reading data written by ANY version of the app.
 *
 * Kharcha has no account and no server, so a backup file is the only way data moves from an
 * old install to a new one. Two things keep every old file readable:
 *
 *   1. The rules in core/model/Models.kt (new fields have defaults, nothing is ever renamed
 *      or removed), with KharchaJson skipping fields it does not know.
 *   2. This file: when a file still cannot be read as a whole, it is read one entry at a
 *      time, so a single entry that makes no sense costs only itself, never the rest.
 *
 * app/src/test/.../BackupCompatTest.kt opens saved copies of old backups on every build.
 * Nothing here touches Android, so that test runs on a plain computer.
 */

/** The lists a data file can hold. Seeing one of these is how a bare data file is recognised. */
private val DATA_KEYS = listOf("sheets", "categories", "items", "people", "goals", "noSpendDays", "repairedDays")

private fun JsonObject.text(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull
private fun JsonObject.whole(name: String): Long? = (this[name] as? JsonPrimitive)?.longOrNull

/** One list, entry by entry: an entry that cannot be understood is left out. */
private fun <T> JsonObject.readList(name: String, serializer: KSerializer<T>): List<T> {
    val list = this[name] as? JsonArray ?: return emptyList()
    return list.mapNotNull { entry -> runCatching { KharchaJson.decodeFromJsonElement(serializer, entry) }.getOrNull() }
}

/** The patient way to read the data: whatever can be understood is kept. */
private fun JsonObject.toAppData(): AppData = AppData(
    format = whole("format")?.toInt() ?: 1,
    sheets = readList("sheets", Sheet.serializer()),
    categories = readList("categories", Category.serializer()),
    items = readList("items", Item.serializer()),
    people = readList("people", Person.serializer()),
    goals = readList("goals", Goal.serializer()),
    noSpendDays = readList("noSpendDays", String.serializer()),
    repairedDays = readList("repairedDays", String.serializer()),
    gemsSpent = whole("gemsSpent")?.toInt()?.coerceAtLeast(0) ?: 0,
)

/** For the app's own data file on the phone, when the normal read fails. Null = not JSON at all. */
fun rescueAppData(text: String): AppData? =
    runCatching { (KharchaJson.parseToJsonElement(text) as? JsonObject)?.toAppData() }.getOrNull()

/**
 * Reads the `kharcha.json` inside a backup or a shared sheet, from any version of the app.
 * Returns null when the text is not a Kharcha file at all (so a stray JSON file is never
 * mistaken for an empty backup and offered as a "replace everything").
 */
fun readKharchaJson(text: String): KharchaFile? {
    val root = runCatching { KharchaJson.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return null
    val app = root.text("app")
    if (app != null && app != "kharcha") return null

    val inner = root["data"]
    val dataPart: JsonObject = when {
        // A backup or shared sheet: the data sits inside a wrapper.
        inner is JsonObject -> inner
        // A backup of an empty app has a date and nothing else.
        "exportedAt" in root -> JsonObject(emptyMap())
        // The app's own data file, copied out by hand: the lists sit at the top.
        DATA_KEYS.any { it in root } -> root
        else -> return null
    }
    val wrapped = dataPart !== root
    val data = runCatching { KharchaJson.decodeFromJsonElement(AppData.serializer(), dataPart) }.getOrNull()
        ?: dataPart.toAppData()
    return KharchaFile(
        kind = if (wrapped) root.text("kind") ?: "backup" else "backup",
        format = if (wrapped) root.whole("format")?.toInt() ?: 1 else 1,
        exportedAt = if (wrapped) root.whole("exportedAt") ?: 0L else 0L,
        data = data,
    )
}

package com.kharcha.app.ui.nav

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Every screen has a "key": a small object that says which screen it is and carries the
 * few values that screen needs (a sheet id, an item id). The back stack is simply a
 * list of these keys; the screen on top of the list is the one on display.
 */
@Serializable
sealed interface Route : NavKey

/** The list of sheets. The app opens here. */
@Serializable data object HomeRoute : Route

/** One sheet: its totals and its items. */
@Serializable data class SheetRoute(val sheetId: String) : Route

/** New sheet (no id) or edit a sheet. */
@Serializable data class SheetEditRoute(val sheetId: String? = null) : Route

/**
 * New item or edit an item. `kind` is "EXPENSE", "INCOME" or "TRANSFER" for a new one.
 * `copyOf` starts a new item filled in from an existing one.
 */
@Serializable data class ItemRoute(
    val sheetId: String,
    val itemId: String? = null,
    val kind: String = "EXPENSE",
    val copyOf: String? = null,
) : Route

@Serializable data class StatsRoute(val sheetId: String) : Route
@Serializable data class TrendsRoute(val sheetId: String) : Route

/** Exchange rates, shown against this currency. */
@Serializable data class RatesRoute(val base: String) : Route

@Serializable data object SettingsRoute : Route
@Serializable data object CategoriesRoute : Route
@Serializable data class CategoryRoute(val categoryId: String? = null, val kind: String = "EXPENSE") : Route
@Serializable data object BackupRoute : Route
@Serializable data class ExportRoute(val sheetId: String? = null) : Route
@Serializable data class ImportRoute(val sheetId: String? = null) : Route
@Serializable data object TrashRoute : Route
@Serializable data object AppearanceRoute : Route
@Serializable data object AppIconRoute : Route
@Serializable data object PrivacyRoute : Route
@Serializable data object PrintRoute : Route
@Serializable data object AboutRoute : Route

/** Friends and who owes whom. */
@Serializable data object PeopleRoute : Route
@Serializable data class PersonRoute(val personId: String) : Route

/** "Where could I save": insights for the week or the month. */
@Serializable data object InsightsRoute : Route

/** The daily streak, the tree, and savings goals. */
@Serializable data object HabitRoute : Route
@Serializable data object RemindersRoute : Route

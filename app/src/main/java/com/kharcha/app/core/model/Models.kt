package com.kharcha.app.core.model

import kotlinx.serialization.Serializable

/*
 * Everything Kharcha stores. These classes ARE the data file: the whole AppData object is
 * written to one JSON file on the phone (see data/JsonFile.kt). There is no database.
 *
 * Rules for changing them (so old data files and old backups keep opening):
 *   - every new field needs a default value;
 *   - never rename or remove a field, and never rename an enum entry;
 *   - money is always a whole number of the currency's smallest unit (paise, cents).
 */

/** Money going out or coming in. A transfer is one EXPENSE and one INCOME that point at each other. */
@Serializable
enum class Kind { EXPENSE, INCOME }

/** How often an item comes back by itself. */
@Serializable
enum class Repeat(val label: String) {
    NEVER("Never"),
    DAILY("Every day"),
    WEEKLY("Every week"),
    BIWEEKLY("Every 2 weeks"),
    MONTHLY("Every month"),
    QUARTERLY("Every 3 months"),
    YEARLY("Every year"),
}

/** Which stretch of time a total covers. */
@Serializable
enum class PeriodKind(val label: String) {
    AS_OF_TODAY("As of today"),
    THIS_YEAR("This year"),
    THIS_MONTH("This month"),
    LAST_MONTH("Last month"),
    THIS_WEEK("This week"),
    TODAY("Today"),
    CUSTOM("Custom"),
}

/** How something was paid. */
@Serializable
enum class PayMethod(val label: String) {
    NONE("Not set"),
    UPI("UPI"),
    CASH("Cash"),
    CARD("Card"),
    BANK("Bank"),
}

/** A friend you share bills with. */
@Serializable
data class Person(
    val id: String,
    val name: String,
    /** Their UPI address ("name@bank"), so settling up can open a UPI app already filled in. */
    val upi: String = "",
    val createdAt: Long = 0,
    val editedAt: Long = 0,
    val deletedAt: Long? = null,
)

/** One friend's part of a bill you paid: they owe you this much, in the item's currency. */
@Serializable
data class Split(val personId: String, val amountMinor: Long)

@Serializable
enum class GoalKind(val label: String) { SAVE("Saving"), INVEST("Investing") }

/** Money put towards a goal on a day. */
@Serializable
data class Deposit(val at: Long, val amountMinor: Long)

/** Something to save or invest for: a target, and a plan of so much a month. */
@Serializable
data class Goal(
    val id: String,
    val name: String,
    val kind: GoalKind = GoalKind.INVEST,
    val currency: String,
    /** The amount to reach. 0 = no end, just keep going. */
    val targetMinor: Long = 0,
    /** The plan: this much every month (a SIP is exactly this). 0 = no monthly plan. */
    val monthlyMinor: Long = 0,
    /** The day of the month the monthly amount is due (1 to 28). */
    val dayOfMonth: Int = 1,
    val deposits: List<Deposit> = emptyList(),
    val createdAt: Long = 0,
    val editedAt: Long = 0,
    val deletedAt: Long? = null,
) {
    val saved: Long get() = deposits.sumOf { it.amountMinor }
}

/** A sheet is one ledger: "Personal", "Goa trip", "Flat with Rahul". */
@Serializable
data class Sheet(
    val id: String,
    val name: String,
    /** ISO code of the sheet's own currency, e.g. "INR". Totals are shown in it. */
    val currency: String,
    /** true = the big number is income minus expense; false = the big number is what was spent. */
    val showTotalBalance: Boolean = true,
    val period: PeriodKind = PeriodKind.AS_OF_TODAY,
    /** Only for [PeriodKind.CUSTOM]: start of the first day and start of the last day, in milliseconds. */
    val customFrom: Long? = null,
    val customTo: Long? = null,
    // What the "new item" form shows for this sheet.
    val showCurrency: Boolean = true,
    val showMerchant: Boolean = true,
    val showTime: Boolean = true,
    val showSuggestions: Boolean = true,
    /** Spending limit per calendar month in the sheet's currency. 0 = no budget. */
    val monthlyBudgetMinor: Long = 0,
    val createdAt: Long = 0,
    val editedAt: Long = 0,
    /** Position when sheets are sorted by hand. */
    val order: Int = 0,
    /** Set when the sheet is in the trash. */
    val deletedAt: Long? = null,
)

@Serializable
data class Category(
    val id: String,
    val name: String,
    val kind: Kind,
    /** Name of an icon in ui/icons/Ic.kt. */
    val icon: String = "list",
    /** Colour as 0xAARRGGBB. */
    val color: Long = 0xFF8E8E93,
    val order: Int = 0,
    val createdAt: Long = 0,
    val editedAt: Long = 0,
    val deletedAt: Long? = null,
)

/** One line in a sheet: an expense or an income. */
@Serializable
data class Item(
    val id: String,
    val sheetId: String,
    val kind: Kind,
    /** The amount as typed, in the smallest unit of [currency]. Never negative. */
    val amountMinor: Long,
    val currency: String,
    /**
     * How many units of the SHEET's currency one unit of [currency] was worth when the item
     * was saved. 1.0 when both are the same. Frozen here so old totals do not move when
     * exchange rates change.
     */
    val rate: Double = 1.0,
    val note: String = "",
    val merchant: String = "",
    val categoryId: String? = null,
    /** When it happened, in milliseconds. */
    val at: Long,
    /** Not settled yet: listed under "Pending" and left out of totals. */
    val pending: Boolean = false,
    val repeat: Repeat = Repeat.NEVER,
    /** Items made by the same repeating item share this. */
    val seriesId: String? = null,
    /** File names of attached pictures in the app's images folder. */
    val images: List<String> = emptyList(),
    /** Both halves of a transfer share this id. */
    val transferId: String? = null,
    /** For a transfer half: the sheet the other half lives in. */
    val transferPeerSheetId: String? = null,
    /** How it was paid: UPI, cash, card. */
    val method: PayMethod = PayMethod.NONE,
    /**
     * The friend who paid this bill, when it was not you. The amount is then YOUR share, which
     * you owe them. It counts as your spending but not as money that left your pocket.
     */
    val payer: String? = null,
    /** When you paid for others too: what each friend owes you back. Your own share is the rest. */
    val splits: List<Split> = emptyList(),
    /**
     * People who shared this bill but are not in Friends (family, someone you are treating).
     * An equal split counts them as heads, and their parts stay inside your own share: nobody
     * owes you for them. Only kept so the form opens the same way it was saved.
     */
    val covered: Int = 0,
    /** Set on a settling-up payment: the friend it squares things with. Not spending, not income. */
    val settles: String? = null,
    val createdAt: Long = 0,
    val editedAt: Long = 0,
    val deletedAt: Long? = null,
) {
    val isTransfer: Boolean get() = transferId != null

    /** True for money that only moved around: a transfer between sheets, or settling up with a friend. */
    val isMovement: Boolean get() = transferId != null || settles != null

    /** What friends owe back on this bill, in the item's currency. */
    val lent: Long get() = if (payer == null) splits.sumOf { it.amountMinor } else 0L
}

/** The whole data file. */
@Serializable
data class AppData(
    /** Raised when the layout changes in a way old versions cannot read. */
    val format: Int = 1,
    val sheets: List<Sheet> = emptyList(),
    val categories: List<Category> = emptyList(),
    val items: List<Item> = emptyList(),
    val people: List<Person> = emptyList(),
    val goals: List<Goal> = emptyList(),
    /** Days ticked as "spent nothing today" (as "2026-11-14"), which keep the daily streak alive. */
    val noSpendDays: List<String> = emptyList(),
) {
    val liveSheets: List<Sheet> get() = sheets.filter { it.deletedAt == null }
    val livePeople: List<Person> get() = people.filter { it.deletedAt == null }
    val liveGoals: List<Goal> get() = goals.filter { it.deletedAt == null }

    fun person(id: String?): Person? = if (id == null) null else people.firstOrNull { it.id == id }
    val liveCategories: List<Category> get() = categories.filter { it.deletedAt == null }

    fun sheet(id: String?): Sheet? = sheets.firstOrNull { it.id == id }
    fun category(id: String?): Category? = if (id == null) null else categories.firstOrNull { it.id == id }

    /** Items of a sheet that are not in the trash. */
    fun itemsOf(sheetId: String): List<Item> = items.filter { it.sheetId == sheetId && it.deletedAt == null }
}

// ---------------------------------------------------------------------------
// Settings (their own small file)
// ---------------------------------------------------------------------------

@Serializable
enum class ThemeChoice { SYSTEM, LIGHT, DARK }

@Serializable
enum class SheetSort(val label: String) {
    MANUAL("Manual"),
    EDITED("Date edited"),
    CREATED("Date created"),
    NAME_ASC("Name (A to Z)"),
    NAME_DESC("Name (Z to A)"),
}

/** The launcher icons to choose from. `alias` is the activity-alias name in the manifest. */
@Serializable
enum class AppIcon(val label: String, val alias: String) {
    IRIS("Iris", "IconIris"),
    INK("Ink", "IconInk"),
    CORAL("Coral", "IconCoral"),
    MINT("Mint", "IconMint"),
    MARIGOLD("Marigold", "IconMarigold"),
    BLUEPRINT("Blueprint", "IconBlueprint"),
}

/** The typefaces to choose from. All but "System" are bundled, and all of them draw the rupee sign. */
@Serializable
enum class FontChoice(val label: String) {
    JAKARTA("Plus Jakarta Sans"),
    MANROPE("Manrope"),
    FIGTREE("Figtree"),
    SPACE("Space Grotesk"),
    SYSTEM("Phone's own font"),
}

/** How large text is drawn, on top of the phone's own font size setting. */
@Serializable
enum class TextSize(val label: String, val scale: Float) {
    SMALL("Small", 0.9f),
    NORMAL("Normal", 1f),
    LARGE("Large", 1.12f),
    XLARGE("Largest", 1.25f),
}

/** The brand colour. `hue` is where it sits on the colour wheel (0 to 360); null keeps Kharcha's own iris. */
@Serializable
enum class Accent(val label: String, val hue: Float?) {
    IRIS("Iris", null),
    BLUE("Blue", 212f),
    TEAL("Teal", 178f),
    GREEN("Green", 146f),
    ROSE("Rose", 338f),
    ORANGE("Orange", 22f),
}

/** How round cards and rows are. */
@Serializable
enum class Corners(val label: String, val factor: Float) {
    ROUND("Round", 1f),
    SOFT("Soft", 0.6f),
    SQUARE("Square", 0.28f),
}

@Serializable
data class PrintOptions(
    val summary: Boolean = true,
    val byCategory: Boolean = true,
    val notes: Boolean = true,
    val merchant: Boolean = true,
)

@Serializable
data class Settings(
    val onboarded: Boolean = false,
    val theme: ThemeChoice = ThemeChoice.SYSTEM,
    /** Take the colours from the phone's wallpaper (Android 12 and newer). */
    val dynamicColor: Boolean = false,
    val sheetSort: SheetSort = SheetSort.EDITED,
    val sheetsCollapsed: Boolean = false,
    /** The currency new sheets start with, and the one the "all sheets" total is shown in. */
    val preferredCurrency: String = "",
    /** Hide every amount behind dots. */
    val sensitive: Boolean = false,
    /** Ask for fingerprint, face or the phone's PIN when the app is opened. */
    val appLock: Boolean = false,
    /** Keep the app out of screenshots and the recent-apps preview. */
    val blockScreenshots: Boolean = false,
    val appIcon: AppIcon = AppIcon.IRIS,
    /** Download exchange rates by itself. The only thing the app ever goes online for. */
    val autoRates: Boolean = true,
    val lastBackupAt: Long = 0,
    val exportImages: Boolean = true,
    val print: PrintOptions = PrintOptions(),
    /** A notification once a day: log your spending, keep the streak. */
    val reminder: Boolean = false,
    val reminderHour: Int = 21,
    val reminderMinute: Int = 0,
    /** Save a backup into the phone's Downloads folder once a day (Android 10 and newer). */
    val autoExport: Boolean = false,
    /** The day ("2026-11-14") the last automatic backup was written. */
    val lastAutoExportDay: String = "",
    /** The way the last item was paid, offered first on the next one. */
    val lastMethod: PayMethod = PayMethod.NONE,
    val font: FontChoice = FontChoice.JAKARTA,
    val textSize: TextSize = TextSize.NORMAL,
    val accent: Accent = Accent.IRIS,
    /** In the dark theme, make the background true black (saves battery on OLED screens). */
    val pureBlack: Boolean = false,
    val corners: Corners = Corners.ROUND,
)

/** Exchange rates (their own small file). */
@Serializable
data class Rates(
    val base: String = "USD",
    val fetchedAt: Long = 0,
    /** How many units of each currency one unit of [base] buys. */
    val perBase: Map<String, Double> = emptyMap(),
    /** Rates typed in by hand. Key "EUR>INR" = how many INR one EUR is worth. They win over [perBase]. */
    val custom: Map<String, Double> = emptyMap(),
    val source: String = "",
)

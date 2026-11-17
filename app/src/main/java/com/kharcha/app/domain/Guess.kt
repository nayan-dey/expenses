package com.kharcha.app.domain

import com.kharcha.app.core.model.AppData
import com.kharcha.app.core.model.Kind
import com.kharcha.app.core.model.PayMethod

/*
 * Guessing the category and the way of paying from what is typed in "Notes".
 *
 * Nothing clever and nothing online: it is word matching, in this order.
 *   1. Your own history. A word you have used before ("chai") points at the category you
 *      filed it under most often. This is what makes it get better the more you log.
 *   2. The names of your categories. Typing "rent" picks a category called Rent.
 *   3. A built-in list of everyday words for the categories the app starts with.
 *
 * A guess is only ever a starting point: once a category or a way of paying has been
 * picked by hand on the form, the form stops guessing for that item.
 */

/** Words that say nothing about what was bought. */
private val FILLER = setOf(
    "the", "and", "for", "with", "from", "at", "to", "of", "in", "on", "a", "an", "my", "me", "by",
    "paid", "pay", "payment", "bought", "buy", "got", "new", "some", "ka", "ki", "ke", "ko", "se", "aur",
)

/** The words of a note, lower case, without filler and numbers. */
fun words(text: String): List<String> =
    text.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 2 && it !in FILLER && it.any { c -> c.isLetter() } }

/** Everyday words for the categories a new install starts with (their ids are fixed, see data/Seed.kt). */
private val STARTER_WORDS: Map<String, List<String>> = mapOf(
    "cat-food" to listOf(
        "water", "tea", "chai", "coffee", "lunch", "dinner", "breakfast", "snack", "snacks", "biryani", "pizza", "burger",
        "restaurant", "cafe", "swiggy", "zomato", "juice", "momo", "momos", "maggi", "samosa", "dosa", "idli", "roll", "cake",
        "icecream", "beer", "drinks", "drink", "bar", "food", "meal", "thali", "paratha", "sweets", "mithai", "chocolate",
        "biscuit", "coke", "pepsi", "lassi", "tiffin", "canteen", "mess", "dhaba", "eat", "ate", "kfc", "dominos", "mcdonalds",
        "starbucks", "pani", "khana", "nashta", "chaat", "sandwich", "noodles", "soup", "shake", "bottle",
    ),
    "cat-groceries" to listOf(
        "grocery", "groceries", "vegetables", "vegetable", "sabzi", "sabji", "milk", "bread", "rice", "atta", "dal", "oil",
        "fruit", "fruits", "bigbasket", "blinkit", "zepto", "instamart", "dmart", "kirana", "eggs", "egg", "onion", "potato",
        "tomato", "sugar", "salt", "masala", "ration", "supermarket", "curd", "paneer", "butter", "ghee", "flour",
    ),
    "cat-transport" to listOf(
        "uber", "ola", "rapido", "auto", "cab", "taxi", "bus", "metro", "train", "petrol", "diesel", "fuel", "toll",
        "parking", "rickshaw", "fare", "bike", "scooty", "ride", "local", "ticket", "fastag", "puncture", "service",
    ),
    "cat-bills" to listOf(
        "electricity", "bill", "bills", "recharge", "mobile", "gas", "dth", "cylinder", "maintenance", "postpaid",
        "prepaid", "emi", "insurance", "premium", "tax", "fees", "fee", "subscription",
    ),
    "cat-internet" to listOf("wifi", "broadband", "internet", "fiber", "fibre", "jiofiber", "airtel", "jio", "data"),
    "cat-rent" to listOf("rent", "pg", "hostel", "deposit", "landlord", "flat", "room"),
    "cat-entertainment" to listOf(
        "movie", "movies", "netflix", "spotify", "prime", "hotstar", "game", "games", "concert", "pvr", "inox", "party",
        "club", "show", "youtube", "bowling", "cinema", "outing", "nightout", "pub",
    ),
    "cat-shopping" to listOf(
        "amazon", "flipkart", "myntra", "meesho", "ajio", "clothes", "shirt", "tshirt", "shoes", "jeans", "shopping",
        "phone", "watch", "bag", "charger", "earphones", "headphones", "cover", "mall", "dress", "jacket", "kurta",
    ),
    "cat-health" to listOf(
        "doctor", "medicine", "medicines", "pharmacy", "hospital", "gym", "medical", "tablet", "tablets", "clinic",
        "dentist", "apollo", "chemist", "checkup", "test", "protein", "physio", "haircut", "salon",
    ),
    "cat-travel" to listOf(
        "flight", "hotel", "trip", "irctc", "makemytrip", "oyo", "airbnb", "visa", "luggage", "tour", "resort",
        "booking", "goibibo", "airport", "vacation", "holiday",
    ),
    "cat-salary" to listOf("salary", "stipend", "payroll", "wage", "wages", "paycheck", "bonus", "increment"),
    "cat-freelance" to listOf("freelance", "client", "project", "invoice", "gig", "upwork", "fiverr", "contract"),
    "cat-gift" to listOf("gift", "birthday", "shagun", "present", "diwali", "eidi", "pocket"),
    "cat-interest" to listOf("interest", "dividend", "cashback", "fd", "returns"),
)

/** Words in a note that name the way of paying. */
private val METHOD_WORDS: Map<PayMethod, List<String>> = mapOf(
    PayMethod.CASH to listOf("cash", "coins", "nakad"),
    PayMethod.UPI to listOf("upi", "gpay", "googlepay", "phonepe", "paytm", "bhim", "qr", "scan", "scanned"),
    PayMethod.CARD to listOf("card", "credit", "debit", "swipe", "swiped", "visa", "mastercard", "rupay"),
    PayMethod.BANK to listOf("neft", "imps", "rtgs", "netbanking", "cheque", "transfer"),
)

/** Words in a SHEET's name that say how things in it are usually paid. A "Bank" sheet is paid from by UPI. */
private val SHEET_WORDS: Map<PayMethod, List<String>> = mapOf(
    PayMethod.CASH to listOf("cash", "wallet", "pocket", "nakad"),
    PayMethod.CARD to listOf("card", "credit"),
    PayMethod.UPI to listOf(
        "upi", "gpay", "phonepe", "paytm", "bank", "account", "savings", "salary",
        "hdfc", "sbi", "icici", "axis", "kotak", "pnb", "idfc",
    ),
)

/**
 * What past items say: for every word, how often it went with each category and each way of
 * paying. Built once from the items, then asked many times while a note is being typed.
 */
class Hints(
    private val categoryByWord: Map<Kind, Map<String, Map<String, Int>>>,
    private val methodByWord: Map<String, Map<PayMethod, Int>>,
    private val categoryNames: Map<Kind, Map<String, String>>,
    /** The categories that still exist, and whether each is for expenses or income. */
    private val kindById: Map<String, Kind>,
) {
    /** The category this note most likely belongs to, or null when nothing in it rings a bell. */
    fun category(kind: Kind, note: String, merchant: String = ""): String? {
        val typed = words("$note $merchant")
        if (typed.isEmpty()) return null

        // 1. Your own history.
        val votes = HashMap<String, Int>()
        val byWord = categoryByWord[kind].orEmpty()
        for (word in typed) byWord[word]?.forEach { (id, count) -> votes[id] = (votes[id] ?: 0) + count }
        votes.maxByOrNull { it.value }?.let { return it.key }

        // 2. A word that is the name of one of your categories.
        val names = categoryNames[kind].orEmpty()
        typed.asReversed().firstNotNullOfOrNull { names[it] }?.let { return it }

        // 3. Everyday words for the starting categories. The later word wins a tie ("water bill" is a bill).
        val starter = HashMap<String, Int>()
        typed.forEachIndexed { index, word ->
            for ((id, list) in STARTER_WORDS) {
                if (kindById[id] == kind && word in list) starter[id] = (starter[id] ?: 0) + 100 + index
            }
        }
        return starter.maxByOrNull { it.value }?.key
    }

    /** How this was most likely paid, or null when there is nothing to go on. */
    fun method(note: String, merchant: String, sheetName: String): PayMethod? {
        val typed = words("$note $merchant")
        // 1. The note says it outright.
        for ((method, list) in METHOD_WORDS) if (typed.any { it in list }) return method
        // 2. The same words were paid a certain way before.
        val votes = HashMap<PayMethod, Int>()
        for (word in typed) methodByWord[word]?.forEach { (method, count) -> votes[method] = (votes[method] ?: 0) + count }
        votes.maxByOrNull { it.value }?.let { return it.key }
        // 3. The sheet's name: a "Cash" sheet is paid in cash, a "Bank" sheet by UPI.
        val sheetWords = words(sheetName)
        for ((method, list) in SHEET_WORDS) if (sheetWords.any { it in list }) return method
        return null
    }
}

/** Reads the hints out of everything logged so far. */
fun AppData.hints(): Hints {
    val live = categories.filter { it.deletedAt == null }
    val liveIds = live.map { it.id }.toSet()
    val categoryByWord = HashMap<Kind, HashMap<String, HashMap<String, Int>>>()
    val methodByWord = HashMap<String, HashMap<PayMethod, Int>>()
    for (item in items) {
        if (item.deletedAt != null || item.isMovement) continue
        val typed = words("${item.note} ${item.merchant}").distinct()
        if (typed.isEmpty()) continue
        val category = item.categoryId
        if (category != null && category in liveIds) {
            val byWord = categoryByWord.getOrPut(item.kind) { HashMap() }
            for (word in typed) {
                val counts = byWord.getOrPut(word) { HashMap() }
                counts[category] = (counts[category] ?: 0) + 1
            }
        }
        if (item.method != PayMethod.NONE) {
            for (word in typed) {
                val counts = methodByWord.getOrPut(word) { HashMap() }
                counts[item.method] = (counts[item.method] ?: 0) + 1
            }
        }
    }
    // A category's own name counts as a word for it: each word of "Food & Drink" points at it.
    val names = HashMap<Kind, HashMap<String, String>>()
    for (category in live.sortedByDescending { it.order }) {
        val byName = names.getOrPut(category.kind) { HashMap() }
        for (word in words(category.name)) byName[word] = category.id
    }
    return Hints(categoryByWord, methodByWord, names, live.associate { it.id to it.kind })
}

package com.kharcha.app.data

import com.kharcha.app.core.model.AppData
import com.kharcha.app.core.model.Category
import com.kharcha.app.core.model.Deposit
import com.kharcha.app.core.model.Goal
import com.kharcha.app.core.model.Person
import com.kharcha.app.core.model.Item
import com.kharcha.app.core.model.Kind
import com.kharcha.app.core.model.Repeat
import com.kharcha.app.core.model.Sheet
import com.kharcha.app.core.util.Dates
import com.kharcha.app.core.util.newId
import kotlinx.coroutines.flow.StateFlow

/** What a merge did, for the message shown afterwards. */
data class MergeSummary(val sheets: Int, val categories: Int, val items: Int)

/**
 * The book of record. Every change to sheets, categories and items goes through here, so the
 * rules live in one place:
 *
 *   - Deleting moves a thing to the trash (`deletedAt`). Only "Empty trash" removes it for good.
 *   - Any change to an item also marks its sheet as edited.
 *   - A transfer is two items, one in each sheet, that are always changed and deleted together.
 *   - A repeating item hands its "repeat" on to the copy it makes, so there is always exactly
 *     one live end of the chain.
 *
 * Changes are instant (the data is in memory) and saved to disk a moment later by [JsonFile].
 */
class Ledger(
    private val file: JsonFile<AppData>,
    private val images: ImageStore,
    private val now: () -> Long,
) {
    val data: StateFlow<AppData> get() = file.state
    val current: AppData get() = file.value

    private fun change(block: (AppData) -> AppData) = file.update(block)

    private fun AppData.touch(sheetIds: Collection<String>, time: Long): AppData =
        if (sheetIds.isEmpty()) this else copy(sheets = sheets.map { if (it.id in sheetIds) it.copy(editedAt = time) else it })

    // ---------------------------------------------------------------------------
    // Sheets
    // ---------------------------------------------------------------------------

    /** Adds a sheet, or replaces the one with the same id. */
    fun saveSheet(sheet: Sheet) {
        val time = now()
        change { data ->
            val exists = data.sheets.any { it.id == sheet.id }
            if (exists) {
                data.copy(sheets = data.sheets.map { if (it.id == sheet.id) sheet.copy(editedAt = time) else it })
            } else {
                val order = (data.sheets.maxOfOrNull { it.order } ?: -1) + 1
                data.copy(sheets = data.sheets + sheet.copy(createdAt = time, editedAt = time, order = order))
            }
        }
    }

    /** Changes which stretch of time a sheet's totals cover. A viewing choice, so the sheet does not count as edited. */
    fun setPeriod(id: String, period: com.kharcha.app.core.model.PeriodKind, from: Long?, to: Long?) {
        change { data -> data.copy(sheets = data.sheets.map { if (it.id == id) it.copy(period = period, customFrom = from, customTo = to) else it }) }
    }

    fun deleteSheet(id: String) {
        val time = now()
        change { data -> data.copy(sheets = data.sheets.map { if (it.id == id) it.copy(deletedAt = time) else it }) }
    }

    fun restoreSheet(id: String) {
        change { data -> data.copy(sheets = data.sheets.map { if (it.id == id) it.copy(deletedAt = null) else it }) }
    }

    /**
     * Gives a sheet a new currency and re-prices its items for it. `rate` says how many units
     * of the new currency one unit of an item's currency is worth. Returns false, changing
     * nothing, when a needed rate is not known.
     */
    fun changeCurrency(sheetId: String, currency: String, rate: (from: String) -> Double?): Boolean {
        val needed = current.items.filter { it.sheetId == sheetId }.map { it.currency }.toSet()
        val rates = HashMap<String, Double>()
        for (code in needed) {
            rates[code] = if (code == currency) 1.0 else rate(code) ?: return false
        }
        val time = now()
        change { data ->
            data.copy(
                sheets = data.sheets.map { if (it.id == sheetId) it.copy(currency = currency, editedAt = time) else it },
                items = data.items.map { if (it.sheetId == sheetId) it.copy(rate = rates[it.currency] ?: it.rate) else it },
            )
        }
        return true
    }

    /** Moves a sheet one place up (-1) or down (+1) in the hand-sorted order. */
    fun moveSheet(id: String, delta: Int) {
        change { data ->
            val ordered = data.liveSheets.sortedBy { it.order }.toMutableList()
            val index = ordered.indexOfFirst { it.id == id }
            val target = index + delta
            if (index < 0 || target < 0 || target > ordered.lastIndex) return@change data
            val moved = ordered.removeAt(index)
            ordered.add(target, moved)
            val orders = ordered.mapIndexed { position, sheet -> sheet.id to position }.toMap()
            data.copy(sheets = data.sheets.map { sheet -> orders[sheet.id]?.let { sheet.copy(order = it) } ?: sheet })
        }
    }

    // ---------------------------------------------------------------------------
    // Categories
    // ---------------------------------------------------------------------------

    fun saveCategory(category: Category) {
        val time = now()
        change { data ->
            val exists = data.categories.any { it.id == category.id }
            if (exists) {
                data.copy(categories = data.categories.map { if (it.id == category.id) category.copy(editedAt = time) else it })
            } else {
                val order = (data.categories.maxOfOrNull { it.order } ?: -1) + 1
                data.copy(categories = data.categories + category.copy(createdAt = time, editedAt = time, order = order))
            }
        }
    }

    fun deleteCategory(id: String) {
        val time = now()
        change { data -> data.copy(categories = data.categories.map { if (it.id == id) it.copy(deletedAt = time) else it }) }
    }

    fun restoreCategory(id: String) {
        change { data -> data.copy(categories = data.categories.map { if (it.id == id) it.copy(deletedAt = null) else it }) }
    }

    // ---------------------------------------------------------------------------
    // Items
    // ---------------------------------------------------------------------------

    /** Adds an item, or replaces the one with the same id. */
    fun saveItem(item: Item) {
        val time = now()
        change { data ->
            val old = data.items.firstOrNull { it.id == item.id }
            val items = if (old != null) {
                data.items.map { if (it.id == item.id) item.copy(editedAt = time) else it }
            } else {
                data.items + item.copy(createdAt = time, editedAt = time)
            }
            data.copy(items = items).touch(setOfNotNull(item.sheetId, old?.sheetId), time)
        }
    }

    /**
     * Saves a transfer: money leaving one sheet and arriving in another. Pass the existing
     * `transferId` to change a transfer that is already saved.
     */
    fun saveTransfer(
        transferId: String?,
        fromSheetId: String,
        toSheetId: String,
        amountMinor: Long,
        currency: String,
        rateToFrom: Double,
        rateToTo: Double,
        note: String,
        at: Long,
        pending: Boolean,
        repeat: Repeat,
        pictures: List<String>,
    ) {
        val time = now()
        val id = transferId ?: newId()
        change { data ->
            val old = data.items.filter { it.transferId == id }
            val oldOut = old.firstOrNull { it.kind == Kind.EXPENSE }
            val oldIn = old.firstOrNull { it.kind == Kind.INCOME }
            fun half(previous: Item?, kind: Kind, sheetId: String, peer: String, rate: Double) = Item(
                id = previous?.id ?: newId(),
                sheetId = sheetId,
                kind = kind,
                amountMinor = amountMinor,
                currency = currency,
                rate = rate,
                note = note,
                at = at,
                pending = pending,
                repeat = repeat,
                seriesId = previous?.seriesId,
                // The pictures are kept on the half that leaves, so they exist once.
                images = if (kind == Kind.EXPENSE) pictures else emptyList(),
                transferId = id,
                transferPeerSheetId = peer,
                createdAt = previous?.createdAt ?: time,
                editedAt = time,
            )
            val out = half(oldOut, Kind.EXPENSE, fromSheetId, toSheetId, rateToFrom)
            val into = half(oldIn, Kind.INCOME, toSheetId, fromSheetId, rateToTo)
            val kept = data.items.filter { it.transferId != id }
            data.copy(items = kept + out + into).touch(old.map { it.sheetId } + fromSheetId + toSheetId, time)
        }
    }

    /** Ids of the given items plus the other halves of any transfers among them. */
    private fun AppData.withTransferPeers(ids: Set<String>): Set<String> {
        val transfers = items.filter { it.id in ids }.mapNotNull { it.transferId }.toSet()
        return if (transfers.isEmpty()) ids else ids + items.filter { it.transferId in transfers }.map { it.id }
    }

    /** Moves items to the trash. */
    fun deleteItems(ids: Set<String>) {
        val time = now()
        change { data ->
            val all = data.withTransferPeers(ids)
            val sheets = data.items.filter { it.id in all }.map { it.sheetId }
            data.copy(items = data.items.map { if (it.id in all) it.copy(deletedAt = time, repeat = Repeat.NEVER) else it }).touch(sheets, time)
        }
    }

    fun restoreItems(ids: Set<String>) {
        change { data ->
            val all = data.withTransferPeers(ids)
            data.copy(items = data.items.map { if (it.id in all) it.copy(deletedAt = null) else it })
        }
    }

    /** Moves items to another sheet. Transfers stay where they are: each half belongs to its sheet. */
    fun moveItems(ids: Set<String>, toSheetId: String, rateFor: (Item) -> Double) {
        val time = now()
        change { data ->
            val from = data.items.filter { it.id in ids && !it.isTransfer }.map { it.sheetId }
            data.copy(
                items = data.items.map { item ->
                    if (item.id in ids && !item.isTransfer) item.copy(sheetId = toSheetId, rate = rateFor(item), editedAt = time) else item
                },
            ).touch(from + toSheetId, time)
        }
    }

    fun setCategory(ids: Set<String>, categoryId: String?, kind: Kind) {
        val time = now()
        change { data ->
            data.copy(
                items = data.items.map { item ->
                    if (item.id in ids && !item.isTransfer && item.kind == kind) item.copy(categoryId = categoryId, editedAt = time) else item
                },
            )
        }
    }

    fun setPending(ids: Set<String>, pending: Boolean) {
        val time = now()
        change { data ->
            val all = data.withTransferPeers(ids)
            data.copy(items = data.items.map { if (it.id in all) it.copy(pending = pending, editedAt = time) else it })
        }
    }

    // ---------------------------------------------------------------------------
    // Friends
    // ---------------------------------------------------------------------------

    fun savePerson(person: Person) {
        val time = now()
        change { data ->
            if (data.people.any { it.id == person.id }) {
                data.copy(people = data.people.map { if (it.id == person.id) person.copy(editedAt = time) else it })
            } else {
                data.copy(people = data.people + person.copy(createdAt = time, editedAt = time))
            }
        }
    }

    /** Hides a friend. Their name stays on past bills. */
    fun deletePerson(id: String) {
        val time = now()
        change { data -> data.copy(people = data.people.map { if (it.id == id) it.copy(deletedAt = time, editedAt = time) else it }) }
    }

    fun restorePerson(id: String) {
        val time = now()
        change { data -> data.copy(people = data.people.map { if (it.id == id) it.copy(deletedAt = null, editedAt = time) else it }) }
    }

    // ---------------------------------------------------------------------------
    // Goals and the daily habit
    // ---------------------------------------------------------------------------

    fun saveGoal(goal: Goal) {
        val time = now()
        change { data ->
            if (data.goals.any { it.id == goal.id }) {
                data.copy(goals = data.goals.map { if (it.id == goal.id) goal.copy(editedAt = time) else it })
            } else {
                data.copy(goals = data.goals + goal.copy(createdAt = time, editedAt = time))
            }
        }
    }

    fun deleteGoal(id: String) {
        val time = now()
        change { data -> data.copy(goals = data.goals.map { if (it.id == id) it.copy(deletedAt = time, editedAt = time) else it }) }
    }

    fun restoreGoal(id: String) {
        val time = now()
        change { data -> data.copy(goals = data.goals.map { if (it.id == id) it.copy(deletedAt = null, editedAt = time) else it }) }
    }

    /** Adds money to a goal (or takes the last addition back with a negative amount). */
    fun deposit(goalId: String, amountMinor: Long) {
        val time = now()
        change { data ->
            data.copy(goals = data.goals.map { if (it.id == goalId) it.copy(deposits = it.deposits + Deposit(time, amountMinor), editedAt = time) else it })
        }
    }

    fun removeDeposit(goalId: String, at: Long) {
        val time = now()
        change { data ->
            data.copy(goals = data.goals.map { if (it.id == goalId) it.copy(deposits = it.deposits.filter { deposit -> deposit.at != at }, editedAt = time) else it })
        }
    }

    /** Ticks (or unticks) a day as "spent nothing", which keeps the streak going. */
    fun setNoSpend(day: String, on: Boolean) {
        change { data -> data.copy(noSpendDays = if (on) (data.noSpendDays + day).distinct().sorted() else data.noSpendDays - day) }
    }

    // ---------------------------------------------------------------------------
    // Trash
    // ---------------------------------------------------------------------------

    /** Removes everything in the trash for good. With `olderThan`, only what was deleted before that moment. */
    fun emptyTrash(olderThan: Long = Long.MAX_VALUE) {
        val gone = ArrayList<String>()
        change { data ->
            gone.clear()
            fun expired(deletedAt: Long?) = deletedAt != null && deletedAt < olderThan
            val deadSheets = data.sheets.filter { expired(it.deletedAt) }.map { it.id }.toSet()
            val deadCategories = data.categories.filter { expired(it.deletedAt) }.map { it.id }.toSet()
            val (deadItems, liveItems) = data.items.partition { expired(it.deletedAt) || it.sheetId in deadSheets }
            deadItems.forEach { gone.addAll(it.images) }
            data.copy(
                sheets = data.sheets.filter { it.id !in deadSheets },
                categories = data.categories.filter { it.id !in deadCategories },
                goals = data.goals.filter { !expired(it.deletedAt) },
                // An item whose category is gone for good becomes "no category".
                items = liveItems.map { if (it.categoryId in deadCategories) it.copy(categoryId = null) else it },
            )
        }
        images.delete(gone)
    }

    // ---------------------------------------------------------------------------
    // Repeating items
    // ---------------------------------------------------------------------------

    /**
     * Writes the copies that repeating items owe up to now. A monthly rent entered in January
     * and opened again in April gets its February, March and April lines here.
     * Returns how many lines were added.
     */
    fun materializeRepeats(): Int {
        val time = now()
        var added = 0
        change { data ->
            added = 0
            val additions = ArrayList<Item>()
            val handedOn = HashSet<String>()
            for (head in data.items) {
                if (head.repeat == Repeat.NEVER || head.deletedAt != null) continue
                if (data.sheet(head.sheetId)?.deletedAt != null) continue
                var last = head
                var guard = 0
                while (guard++ < 500) {
                    val nextAt = Dates.next(head.repeat, last.at)
                    if (nextAt > time) break
                    last = last.copy(
                        id = newId(),
                        at = nextAt,
                        repeat = Repeat.NEVER,
                        seriesId = head.seriesId ?: head.id,
                        images = emptyList(),
                        // Both halves of a repeating transfer must land on the same new id.
                        transferId = head.transferId?.let { "$it@$nextAt" },
                        createdAt = time,
                        editedAt = time,
                    )
                    additions += last
                }
                if (last !== head) {
                    handedOn += head.id
                    // The newest copy carries the repeat on.
                    additions[additions.lastIndex] = last.copy(repeat = head.repeat)
                }
            }
            if (additions.isEmpty()) return@change data
            added = additions.size
            val items = data.items.map { if (it.id in handedOn) it.copy(repeat = Repeat.NEVER, seriesId = it.seriesId ?: it.id) else it }
            data.copy(items = items + additions).touch(additions.map { it.sheetId }, time)
        }
        return added
    }

    // ---------------------------------------------------------------------------
    // Whole-file operations (backup, restore, shared sheets)
    // ---------------------------------------------------------------------------

    fun replaceAll(data: AppData) = change { data }

    /**
     * Folds another copy of the data into this one. For anything both copies have, the one
     * edited most recently wins. This is how two phones are brought back in step by hand.
     */
    fun merge(incoming: AppData): MergeSummary {
        var summary = MergeSummary(0, 0, 0)
        change { data ->
            var sheets = 0
            var categories = 0
            var items = 0
            fun <T> fold(mine: List<T>, theirs: List<T>, id: (T) -> String, edited: (T) -> Long, count: () -> Unit): List<T> {
                val result = LinkedHashMap<String, T>()
                mine.forEach { result[id(it)] = it }
                theirs.forEach { other ->
                    val own = result[id(other)]
                    if (own == null || edited(other) > edited(own)) {
                        result[id(other)] = other
                        count()
                    }
                }
                return result.values.toList()
            }
            val merged = data.copy(
                sheets = fold(data.sheets, incoming.sheets, { it.id }, { it.editedAt }) { sheets++ },
                categories = fold(data.categories, incoming.categories, { it.id }, { it.editedAt }) { categories++ },
                items = fold(data.items, incoming.items, { it.id }, { it.editedAt }) { items++ },
                people = fold(data.people, incoming.people, { it.id }, { it.editedAt }) { },
                goals = fold(data.goals, incoming.goals, { it.id }, { it.editedAt }) { },
                noSpendDays = (data.noSpendDays + incoming.noSpendDays).distinct().sorted(),
            )
            summary = MergeSummary(sheets, categories, items)
            merged
        }
        return summary
    }

    /** Deletes every sheet, item and category, and all pictures. */
    fun eraseAll() {
        change { AppData() }
        images.deleteUnused(emptySet())
    }

    /** File names of every picture some item still uses. */
    fun picturesInUse(): Set<String> = current.items.flatMap { it.images }.toSet()
}

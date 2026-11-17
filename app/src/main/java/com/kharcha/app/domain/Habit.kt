package com.kharcha.app.domain

import com.kharcha.app.core.model.AppData
import com.kharcha.app.core.util.Dates
import java.time.LocalDate

/*
 * The daily habit, beyond the streak itself (which is in Summaries.kt): the tree's stages,
 * gems, bringing a broken streak back, and the mood of the mascot.
 *
 * Nothing here is stored except two things in AppData: the days bought back (`repairedDays`)
 * and the gems used up (`gemsSpent`). Gems EARNED are always counted fresh from the days
 * logged, so they cannot drift, and two phones that merge agree on them.
 */

// ---------------------------------------------------------------------------
// The tree
// ---------------------------------------------------------------------------

/** How grown the tree is for a streak of this many days: 0 (a seed) to 5 (a full tree with fruit). */
fun treeStage(streak: Int): Int = when {
    streak <= 0 -> 0
    streak < 3 -> 1
    streak < 7 -> 2
    streak < 14 -> 3
    streak < 30 -> 4
    else -> 5
}

/** How many more days until the tree grows again, or null when it is fully grown. */
fun daysToNextStage(streak: Int): Int? = listOf(1, 3, 7, 14, 30).firstOrNull { it > streak }?.let { it - streak }

fun treeName(stage: Int): String = listOf("a seed", "a sprout", "a sapling", "a young tree", "a tree", "a full tree with fruit")[stage.coerceIn(0, 5)]

// ---------------------------------------------------------------------------
// Gems
// ---------------------------------------------------------------------------

/** Earned for every day something is logged (or ticked as "nothing spent"). */
const val GEMS_PER_DAY = 5

/** A bonus for every seven days logged, whether or not they were in a row. */
const val GEMS_PER_WEEK = 15

/** What it costs to bring back one missed day. */
const val GEMS_PER_REPAIR = 20

/** A gap longer than this cannot be bought back: the streak starts again. */
const val MAX_REPAIR_DAYS = 3

data class Gems(val earned: Int, val spent: Int) {
    val balance: Int get() = (earned - spent).coerceAtLeast(0)
}

private fun List<String>.asDays(): List<LocalDate> = mapNotNull { text -> runCatching { LocalDate.parse(text) }.getOrNull() }

/** The days that were really logged: an item was added, or "nothing spent" was ticked. Bought-back days are not among them. */
private fun AppData.loggedDays(): Set<LocalDate> {
    val days = HashSet<LocalDate>()
    items.forEach { if (it.createdAt > 0L) days.add(Dates.dateOf(it.createdAt)) }
    days.addAll(noSpendDays.asDays())
    return days
}

fun AppData.gems(): Gems {
    val logged = loggedDays().size
    return Gems(earned = logged * GEMS_PER_DAY + (logged / 7) * GEMS_PER_WEEK, spent = gemsSpent.coerceAtLeast(0))
}

// ---------------------------------------------------------------------------
// Bringing a broken streak back
// ---------------------------------------------------------------------------

/** An offer to buy back the days that broke a streak. */
data class Repair(
    /** The missed days, as "2026-11-16", oldest first. */
    val days: List<String>,
    val cost: Int,
    /** How long the streak was when it broke. */
    val streakBefore: Int,
    /** How long it will be once the missed days are bought back. */
    val streakAfter: Int,
)

/**
 * Null when there is nothing to bring back: the streak is alive, the gap is longer than
 * [MAX_REPAIR_DAYS], or what broke was only a day or two long.
 */
fun AppData.repairOffer(now: Long): Repair? {
    val days = loggedDays() + repairedDays.asDays()
    val today = Dates.dateOf(now)
    // The run that is alive now: today (if it counts) and the days straight before it.
    var cursor = today
    var alive = 0
    if (cursor in days) {
        while (cursor in days) {
            alive++
            cursor = cursor.minusDays(1)
        }
    } else {
        // Today can still be saved before midnight, so it is not a missed day yet.
        cursor = today.minusDays(1)
    }
    val missed = ArrayList<LocalDate>()
    while (cursor !in days && missed.size <= MAX_REPAIR_DAYS) {
        missed += cursor
        cursor = cursor.minusDays(1)
    }
    if (missed.isEmpty() || missed.size > MAX_REPAIR_DAYS) return null
    var before = 0
    while (cursor in days) {
        before++
        cursor = cursor.minusDays(1)
    }
    if (before < 2) return null
    return Repair(
        days = missed.map { it.toString() }.sorted(),
        cost = missed.size * GEMS_PER_REPAIR,
        streakBefore = before,
        streakAfter = before + missed.size + alive,
    )
}

// ---------------------------------------------------------------------------
// The mascot
// ---------------------------------------------------------------------------

/** The little money pot that keeps you company. "Gullak" is the Hindi word for a clay piggy bank. */
const val MASCOT_NAME = "Gullu"

enum class Mood {
    /** Today is logged. */
    HAPPY,

    /** Today is logged and the streak just reached a round number. */
    PARTY,

    /** Nothing logged yet, and there is still plenty of day left. */
    WAITING,

    /** Nothing logged yet, and it is evening. */
    WORRIED,

    /** The streak broke. */
    SAD,
}

private val MILESTONES = setOf(3, 7, 14, 21, 30, 50, 75, 100, 150, 200, 300, 365)

/** @param hour the hour of the day, 0 to 23 */
fun moodOf(streak: Streak, hour: Int, repair: Repair?): Mood = when {
    streak.today && streak.current in MILESTONES -> Mood.PARTY
    streak.today -> Mood.HAPPY
    repair != null && streak.current == 0 -> Mood.SAD
    hour >= 19 -> Mood.WORRIED
    else -> Mood.WAITING
}

/** What the mascot says, in one or two short sentences. */
fun mascotLine(mood: Mood, streak: Streak, repair: Repair?, gems: Int): String = when (mood) {
    Mood.PARTY -> "${streak.current} days in a row! Look at us go."
    Mood.HAPPY -> if (streak.current <= 1) "Day one is in. Come back tomorrow and we have a streak." else "Logged. ${streak.current} days and counting. See you tomorrow."
    Mood.WAITING -> if (streak.current > 0) "Spent anything yet today? Tell me, and your ${streak.current}-day streak lives on." else "Tell me one thing you spent today and we start a streak."
    Mood.WORRIED -> if (streak.current > 0) "It is getting late. One entry before midnight saves your ${streak.current}-day streak." else "Nothing yet today? Even \"nothing spent\" counts."
    Mood.SAD -> when {
        repair == null -> "The streak broke. We start again today."
        gems >= repair.cost -> "Your ${repair.streakBefore}-day streak broke. Bring it back for ${repair.cost} gems."
        else -> "Your ${repair.streakBefore}-day streak broke. ${repair.cost - gems} more gems would bring it back. Or we start again today."
    }
}

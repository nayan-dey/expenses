package com.kharcha.app.domain

/*
 * Splitting a bill you paid.
 *
 * Three kinds of people can be at the table:
 *   - you;
 *   - friends from the Friends list, who owe you their part back;
 *   - people you are paying for (your father, a guest). They are not in Friends and owe
 *     nothing: their parts stay inside your own share.
 *
 * Example: a bill of 1,200 for you, one friend and your father is three heads, 400 each.
 * The friend owes you 400. Your share is 800 (yours and your father's).
 */

/** The most people you can pay for on one bill. Only there to keep the counter sensible. */
const val MAX_COVERED = 20

/** How many parts an equal split has: you, the friends, and the people you pay for. */
fun splitHeads(friends: Int, covered: Int): Int = 1 + friends.coerceAtLeast(0) + covered.coerceIn(0, MAX_COVERED)

/**
 * What each friend owes on an equal split, in minor units. Rounded down, so any leftover
 * paisa lands on you and the friends' parts can never add up to more than the bill.
 */
fun equalShare(amountMinor: Long, friends: Int, covered: Int): Long =
    if (friends <= 0 || amountMinor <= 0L) 0L else amountMinor / splitHeads(friends, covered)

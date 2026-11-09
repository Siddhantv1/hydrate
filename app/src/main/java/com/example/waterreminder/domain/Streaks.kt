package com.example.waterreminder.domain

import java.time.LocalDate
import java.time.YearMonth

// ponytail: history is capped at ~13 months so the stored set stays small; raise if a longer calendar is wanted.
private const val KEEP_DAYS = 400L

internal fun keepRecent(days: Set<String>, today: LocalDate): Set<String> {
    val cutoff = today.minusDays(KEEP_DAYS).toString()
    return days.filterTo(HashSet()) { it >= cutoff } // ISO dates sort as strings
}

/**
 * Met days weren't recorded before the calendar existed. If none are stored yet but there is a streak,
 * the streak itself says which days they were: [streak] consecutive days ending on [HydrateState.goalMetDate]
 * (or yesterday, if today's goal isn't met yet).
 */
fun withBackfilledMetDays(s: HydrateState, today: LocalDate): HydrateState {
    if (s.metDays.isNotEmpty() || s.streak <= 0) return s
    val last = if (s.goalMetDate.isNotEmpty()) LocalDate.parse(s.goalMetDate) else today.minusDays(1)
    return s.copy(metDays = (0 until s.streak).mapTo(HashSet()) { last.minusDays(it.toLong()).toString() })
}

/** Longest run of consecutive days in [days]. */
fun bestStreak(days: Set<String>): Int {
    var best = 0
    for (d in days) {
        val day = LocalDate.parse(d)
        if (day.minusDays(1).toString() in days) continue // not the start of a run
        var len = 1
        while (day.plusDays(len.toLong()).toString() in days) len++
        best = maxOf(best, len)
    }
    return best
}

enum class DayStatus { Met, Missed, Today, Future, BeforeStart }

fun dayStatus(day: LocalDate, s: HydrateState, today: LocalDate): DayStatus = when {
    day.toString() in s.metDays -> DayStatus.Met
    day == today -> DayStatus.Today
    day.isAfter(today) -> DayStatus.Future
    s.firstDate.isEmpty() || day.isBefore(LocalDate.parse(s.firstDate)) -> DayStatus.BeforeStart
    else -> DayStatus.Missed
}

/** Month grid starting on Monday: leading nulls pad the first week, trailing nulls fill the last row. */
fun monthCells(month: YearMonth): List<LocalDate?> {
    val lead = month.atDay(1).dayOfWeek.value - 1 // Monday = 0
    val days = (1..month.lengthOfMonth()).map { month.atDay(it) }
    val cells = List<LocalDate?>(lead) { null } + days
    return cells + List((7 - cells.size % 7) % 7) { null }
}

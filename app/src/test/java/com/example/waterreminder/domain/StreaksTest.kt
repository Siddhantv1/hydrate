package com.example.waterreminder.domain

import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreaksTest {
    private val d3 = LocalDate.of(2026, 11, 3)
    private val at15 = ZonedDateTime.of(2026, 11, 3, 15, 0, 0, 0, ZoneId.of("Asia/Kolkata"))
    private val base = HydrateState(onboarded = true, goalGlasses = 8, startMinute = 480, endMinute = 1320, date = "2026-11-03", firstDate = "2026-10-20")

    @Test fun meetingAndUndoingTheGoalRecordsTheDay() {
        val met = addGlasses(base.copy(glassesToday = 7), 1, at15)
        assertEquals(setOf("2026-11-03"), met.metDays)
        assertEquals(emptySet<String>(), undoGlass(met, at15).metDays)
    }

    @Test fun backfillFromExistingStreak() {
        val s = base.copy(streak = 3, goalMetDate = "2026-11-03")
        assertEquals(setOf("2026-11-01", "2026-11-02", "2026-11-03"), withBackfilledMetDays(s, d3).metDays)
        // Today not met yet: the streak ends yesterday.
        assertEquals(setOf("2026-11-01", "2026-11-02"), withBackfilledMetDays(base.copy(streak = 2, goalMetDate = "2026-11-02"), d3).metDays)
        // Already recorded, or no streak: untouched.
        assertEquals(setOf("2026-10-01"), withBackfilledMetDays(s.copy(metDays = setOf("2026-10-01")), d3).metDays)
        assertEquals(emptySet<String>(), withBackfilledMetDays(base, d3).metDays)
    }

    @Test fun oldHistoryIsTrimmed() {
        val s = base.copy(glassesToday = 7, metDays = setOf("2025-01-01", "2026-11-02"))
        assertEquals(setOf("2026-11-02", "2026-11-03"), addGlasses(s, 1, at15).metDays)
    }

    @Test fun longestRun() {
        assertEquals(0, bestStreak(emptySet()))
        assertEquals(3, bestStreak(setOf("2026-10-30", "2026-10-31", "2026-11-01", "2026-11-03")))
    }

    @Test fun statusOfEachDay() {
        val s = base.copy(metDays = setOf("2026-11-01"))
        assertEquals(DayStatus.Met, dayStatus(LocalDate.of(2026, 11, 1), s, d3))
        assertEquals(DayStatus.Missed, dayStatus(LocalDate.of(2026, 11, 2), s, d3))
        assertEquals(DayStatus.Today, dayStatus(d3, s, d3))
        assertEquals(DayStatus.Future, dayStatus(LocalDate.of(2026, 11, 4), s, d3))
        assertEquals(DayStatus.BeforeStart, dayStatus(LocalDate.of(2026, 10, 19), s, d3))
    }

    @Test fun monthGridStartsOnMonday() {
        val cells = monthCells(YearMonth.of(2026, 11)) // 1 Nov 2026 is a Sunday
        assertEquals(6, cells.takeWhile { it == null }.size)
        assertEquals(LocalDate.of(2026, 11, 1), cells[6])
        assertTrue(cells.size % 7 == 0)
        assertEquals(LocalDate.of(2026, 11, 30), cells.last { it != null })
    }
}

package com.example.waterreminder.domain

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RulesTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private fun at(h: Int, m: Int, day: Int = 3) = ZonedDateTime.of(2026, 11, day, h, m, 0, 0, zone)
    private fun ms(h: Int, m: Int, day: Int = 3) = at(h, m, day).toInstant().toEpochMilli()
    private val d3 = LocalDate.of(2026, 11, 3)
    // 8:00–22:00 = 840 min / 8 glasses = 105 min interval
    private val base = HydrateState(onboarded = true, goalGlasses = 8, startMinute = 480, endMinute = 1320, date = "2026-11-03")

    @Test fun suggestedGoal() {
        assertEquals(10, suggestedGoalGlasses(25, Gender.MALE, VolumeUnit.ML))   // 2500 ml
        assertEquals(8, suggestedGoalGlasses(25, Gender.FEMALE, VolumeUnit.ML))  // 2000 ml
        assertEquals(9, suggestedGoalGlasses(11, Gender.MALE, VolumeUnit.ML))    // 2100 ml
        assertEquals(8, suggestedGoalGlasses(11, Gender.FEMALE, VolumeUnit.ML))  // 1900 ml
        assertEquals(7, suggestedGoalGlasses(6, Gender.FEMALE, VolumeUnit.ML))   // 1600 ml
        assertEquals(11, suggestedGoalGlasses(25, Gender.MALE, VolumeUnit.OZ))   // 2500 / 237
    }

    @Test fun formatsGoal() {
        assertEquals("2000 ml", formatGlasses(8, VolumeUnit.ML))
        assertEquals("2.0 L", formatGlasses(8, VolumeUnit.L))
        assertEquals("2.25 L", formatGlasses(9, VolumeUnit.L))
        assertEquals("64 oz", formatGlasses(8, VolumeUnit.OZ))
    }

    @Test fun interval() {
        assertEquals(105L, intervalMinutes(base))
        assertEquals(15L, intervalMinutes(base.copy(goalGlasses = 100)))
    }

    @Test fun planNextFromInteraction() =
        assertEquals(ms(10, 45), planNext(base, at(9, 0), at(9, 0)).nextReminderAt)

    @Test fun planNextKeepsValidFuture() {
        val s = base.copy(nextReminderAt = ms(11, 0))
        assertEquals(ms(11, 0), planNext(s, at(9, 0), null).nextReminderAt)
    }

    @Test fun planNextReplacesPastReminder() {
        val s = base.copy(nextReminderAt = ms(7, 0))
        assertEquals(ms(10, 45), planNext(s, at(9, 0), null).nextReminderAt)
    }

    @Test fun planNextClampsIntoWindow() {
        assertEquals(ms(8, 0, day = 4), planNext(base, at(21, 30), at(21, 30)).nextReminderAt)
        assertEquals(ms(8, 0), planNext(base, at(6, 0), null).nextReminderAt)
    }

    @Test fun goalMetMeansTomorrow() =
        assertEquals(ms(8, 0, day = 4), planNext(base.copy(glassesToday = 8), at(12, 0), at(12, 0)).nextReminderAt)

    @Test fun ignoreToday() {
        val s = ignoreToday(base.copy(outstanding = true), at(12, 0))
        assertEquals("2026-11-03", s.ignoredDate)
        assertFalse(s.outstanding)
        assertEquals(ms(8, 0, day = 4), s.nextReminderAt)
        assertFalse(canRemind(s, at(13, 0)))
    }

    @Test fun drankLogsOneAndPushesReminder() {
        val s = drank(base.copy(glassesToday = 2, outstanding = true), at(10, 0))
        assertEquals(3, s.glassesToday)
        assertFalse(s.outstanding)
        assertEquals(ms(11, 45), s.nextReminderAt)
    }

    @Test fun skipThenDrankCountsTwo() {
        val skipped = skip(base.copy(glassesToday = 2, outstanding = true), at(10, 0))
        assertTrue(skipped.doubleNext)
        assertFalse(skipped.outstanding)
        val s = drank(skipped, at(11, 0))
        assertEquals(4, s.glassesToday)
        assertFalse(s.doubleNext)
    }

    @Test fun skipTwiceStillTwo() {
        val s = drank(skip(skip(base, at(10, 0)), at(11, 0)), at(12, 0))
        assertEquals(2, s.glassesToday)
    }

    @Test fun missedReminderCountsAsSkip() {
        val before = base.copy(glassesToday = 2, outstanding = true)
        val after = reminderFired(before, at(12, 0))
        assertTrue(after.outstanding)
        assertTrue(after.doubleNext)
        assertEquals("Missed one. Make it two glasses.", reminderBody(before, after))
    }

    @Test fun reminderBodies() {
        val fresh = base.copy(glassesToday = 2)
        assertEquals("Glass 3 of 8", reminderBody(fresh, reminderFired(fresh, at(12, 0))))
        val skipped = base.copy(doubleNext = true)
        assertEquals("Make it two glasses.", reminderBody(skipped, reminderFired(skipped, at(12, 0))))
    }

    @Test fun staleReminderDoesNotNotify() {
        val met = reminderFired(base.copy(glassesToday = 8), at(12, 0))
        assertFalse(met.outstanding)
        assertEquals(ms(8, 0, day = 4), met.nextReminderAt)
        assertFalse(reminderFired(base, at(22, 30)).outstanding)
        assertFalse(reminderFired(base.copy(ignoredDate = "2026-11-03"), at(12, 0)).outstanding)
    }

    @Test fun reachingGoalCreditsStreakOnce() {
        val met = addGlasses(base.copy(glassesToday = 7, streak = 4), 1, at(15, 0))
        assertEquals(5, met.streak)
        assertEquals("2026-11-03", met.goalMetDate)
        assertEquals(5, addGlasses(met, 1, at(16, 0)).streak)
    }

    @Test fun undoAfterGoalMetRevertsStreak() {
        val s = undoGlass(base.copy(glassesToday = 8, streak = 5, goalMetDate = "2026-11-03"), at(15, 0))
        assertEquals(7, s.glassesToday)
        assertEquals(4, s.streak)
        assertEquals("", s.goalMetDate)
        assertEquals(ms(16, 45), s.nextReminderAt)
    }

    @Test fun saveGoalBelowTodayCountMeetsGoal() {
        val s = saveGoal(base.copy(glassesToday = 6, streak = 2), VolumeUnit.ML, false, 6, at(15, 0))
        assertEquals(3, s.streak)
        assertEquals(3, saveGoal(s, VolumeUnit.ML, false, 6, at(15, 5)).streak)
    }

    @Test fun rolloverAfterMetDayKeepsStreak() {
        val s = rollover(base.copy(glassesToday = 8, streak = 5, goalMetDate = "2026-11-03"), d3.plusDays(1))
        assertEquals(5, s.streak); assertEquals(0, s.glassesToday)
        assertEquals("2026-11-04", s.date); assertFalse(s.showMissedGoal)
    }

    @Test fun rolloverAfterMissedDayResetsStreak() {
        val s = rollover(base.copy(glassesToday = 3, streak = 5, goalMetDate = "2026-11-02", outstanding = true, doubleNext = true), d3.plusDays(1))
        assertEquals(0, s.streak); assertTrue(s.showMissedGoal)
        assertFalse(s.outstanding); assertFalse(s.doubleNext)
    }

    @Test fun onboardingDayIsNotMissed() {
        val on = completeOnboarding(HydrateState(), 25, Gender.MALE, VolumeUnit.ML, true, 8, 480, 1320, at(21, 0, 3))
        val next = rollover(on, d3.plusDays(1))
        assertFalse(next.showMissedGoal); assertEquals(0, next.streak)
        // a real missed day after the first day still resets
        val missed = rollover(rollover(on, d3.plusDays(1)).copy(streak = 2), d3.plusDays(2))
        assertEquals(0, missed.streak); assertTrue(missed.showMissedGoal)
    }

    @Test fun rolloverAfterGapDayResetsStreak() {
        val s = rollover(base.copy(glassesToday = 8, streak = 5, goalMetDate = "2026-11-03"), d3.plusDays(2))
        assertEquals(0, s.streak); assertTrue(s.showMissedGoal)
    }

    @Test fun rolloverNeverMissesBeforeFirstDay() {
        assertFalse(rollover(HydrateState(), d3).showMissedGoal)
        assertEquals(base, rollover(base, d3))
    }

    @Test fun onboardingStartsToday() {
        val s = completeOnboarding(HydrateState(), 25, Gender.FEMALE, VolumeUnit.ML, true, 8, 480, 1320, at(9, 0))
        assertTrue(s.onboarded); assertEquals("2026-11-03", s.date)
        assertEquals(ms(10, 45), s.nextReminderAt)
    }
}

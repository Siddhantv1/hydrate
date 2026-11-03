package com.example.waterreminder.ui

import com.example.waterreminder.domain.HydrateState
import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenTest {
    private val on = HydrateState(onboarded = true)
    @Test fun start() {
        assertEquals(Screen.Age, startScreen(HydrateState()))
        assertEquals(Screen.StreakLost, startScreen(on.copy(showMissedGoal = true, outstanding = true)))
        assertEquals(Screen.DrinkUp, startScreen(on.copy(outstanding = true)))
        assertEquals(Screen.Home, startScreen(on))
    }
    @Test fun back() {
        assertEquals(Screen.Home, backOf(Screen.DrinkUp))
        assertEquals(Screen.EditGoal, backOf(Screen.EditHours))
        assertEquals(Screen.Age, backOf(Screen.Gender))
        assertEquals(null, backOf(Screen.Home))
    }
    @Test fun resumeKeepsPlaceOnlyInInput() {
        listOf(Screen.Age, Screen.Gender, Screen.Goal, Screen.Hours, Screen.AllSet, Screen.EditGoal, Screen.EditHours)
            .forEach { assertEquals(it.name, true, keepsPlaceOnResume(it)) }
        listOf(Screen.Home, Screen.Menu, Screen.DrinkUp, Screen.HellYeah, Screen.SkipNote, Screen.AllDone, Screen.StreakUp, Screen.StreakLost)
            .forEach { assertEquals(it.name, false, keepsPlaceOnResume(it)) }
    }
}
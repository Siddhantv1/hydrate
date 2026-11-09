package com.example.waterreminder.ui

import com.example.waterreminder.domain.Gender
import com.example.waterreminder.domain.HydrateState
import com.example.waterreminder.domain.VolumeUnit

enum class Screen {
    Age, Gender, Goal, Hours, AllSet, Home, Menu, EditGoal, EditHours,
    ResetHours, // "Reset timer" in the menu: pick the reminder hours again; saving restarts the schedule from now
    Notice,     // short confirmation (e.g. the new next-reminder time), then Home
    Calendar,   // streak calendar, opened by tapping the streak on Home
    DrinkUp, HellYeah, SkipNote, AllDone, StreakUp, StreakLost,
}

fun startScreen(s: HydrateState): Screen = when {
    !s.onboarded -> Screen.Age
    s.showMissedGoal -> Screen.StreakLost
    s.outstanding -> Screen.DrinkUp
    else -> Screen.Home
}

/** Screens that keep their place when the app resumes: in-progress input must not be lost. */
fun keepsPlaceOnResume(screen: Screen): Boolean = when (screen) {
    Screen.Age, Screen.Gender, Screen.Goal, Screen.Hours, Screen.AllSet, Screen.EditGoal, Screen.EditHours, Screen.ResetHours -> true
    else -> false
}

/** null = leave the app. */
fun backOf(screen: Screen): Screen? = when (screen) {
    Screen.Gender -> Screen.Age
    Screen.Goal -> Screen.Gender
    Screen.Hours -> Screen.Goal
    Screen.Menu -> Screen.Home
    Screen.EditGoal -> Screen.Menu
    Screen.EditHours -> Screen.EditGoal
    Screen.ResetHours -> Screen.Menu
    Screen.Calendar -> Screen.Home
    Screen.DrinkUp -> Screen.Home
    else -> null
}

data class Draft(
    val age: Int,
    val gender: Gender,
    val unit: VolumeUnit,
    val autoGoal: Boolean,
    val goalGlasses: Int,
    val startMinute: Int,
    val endMinute: Int,
)

data class Logged(val from: Int, val to: Int, val total: Int)
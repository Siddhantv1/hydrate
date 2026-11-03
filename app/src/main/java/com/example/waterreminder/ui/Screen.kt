package com.example.waterreminder.ui

import com.example.waterreminder.domain.Gender
import com.example.waterreminder.domain.HydrateState
import com.example.waterreminder.domain.VolumeUnit

enum class Screen { Age, Gender, Goal, Hours, AllSet, Home, Menu, EditGoal, EditHours, DrinkUp, HellYeah, SkipNote, AllDone, StreakUp, StreakLost }

fun startScreen(s: HydrateState): Screen = when {
    !s.onboarded -> Screen.Age
    s.showMissedGoal -> Screen.StreakLost
    s.outstanding -> Screen.DrinkUp
    else -> Screen.Home
}

/** null = leave the app. */
fun backOf(screen: Screen): Screen? = when (screen) {
    Screen.Gender -> Screen.Age
    Screen.Goal -> Screen.Gender
    Screen.Hours -> Screen.Goal
    Screen.Menu -> Screen.Home
    Screen.EditGoal -> Screen.Menu
    Screen.EditHours -> Screen.EditGoal
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
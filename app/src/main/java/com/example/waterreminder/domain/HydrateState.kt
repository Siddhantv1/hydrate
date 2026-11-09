package com.example.waterreminder.domain

enum class Gender { MALE, FEMALE }

enum class VolumeUnit { ML, L, OZ }

data class HydrateState(
    val onboarded: Boolean = false,
    val age: Int = 25,
    val gender: Gender = Gender.MALE,
    val unit: VolumeUnit = VolumeUnit.ML,
    val autoGoal: Boolean = true,
    val goalGlasses: Int = 8,
    val startMinute: Int = 8 * 60,
    val endMinute: Int = 22 * 60,
    val glassesToday: Int = 0,
    val date: String = "",          // ISO LocalDate that glassesToday belongs to
    val streak: Int = 0,
    val goalMetDate: String = "",   // date the streak was last credited
    val ignoredDate: String = "",
    val nextReminderAt: Long = 0L,  // epoch millis, 0 = none
    val outstanding: Boolean = false,
    val doubleNext: Boolean = false,
    val showMissedGoal: Boolean = false,
    val firstDate: String = "",      // onboarding day; never counts as missed
    val lastReminderAt: Long = 0L,   // epoch millis of the last reminder actually shown, 0 = none yet
    val metDays: Set<String> = emptySet(), // ISO dates on which the goal was met (for the streak calendar)
)

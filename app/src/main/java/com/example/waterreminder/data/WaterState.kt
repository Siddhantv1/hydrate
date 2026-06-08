package com.example.waterreminder.data

import java.time.LocalDate
import kotlin.math.roundToInt

data class WaterState(
    val dailyGoalMl: Int = WaterDefaults.DEFAULT_DAILY_GOAL_ML,
    val currentDayIntakeMl: Int = 0,
    val defaultDrinkAmountMl: Int = WaterDefaults.DEFAULT_DRINK_AMOUNT_ML,
    val reminderStartMinute: Int = WaterDefaults.DEFAULT_REMINDER_START_MINUTE,
    val reminderEndMinute: Int = WaterDefaults.DEFAULT_REMINDER_END_MINUTE,
    val lastTrackedDate: String = LocalDate.now().toString()
) {
    val remainingIntakeMl: Int
        get() = (dailyGoalMl - currentDayIntakeMl).coerceAtLeast(0)

    val progressFraction: Float
        get() = if (dailyGoalMl <= 0) {
            0f
        } else {
            (currentDayIntakeMl.toFloat() / dailyGoalMl.toFloat()).coerceIn(0f, 1f)
        }

    val percentComplete: Int
        get() = (progressFraction * 100f).roundToInt()

    val isGoalReached: Boolean
        get() = currentDayIntakeMl >= dailyGoalMl

    val hasValidReminderWindow: Boolean
        get() = reminderEndMinute > reminderStartMinute
}

package com.example.waterreminder.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

class WaterRepository(context: Context) {
    private val preferences = WaterPreferences(context.applicationContext)

    val waterState: Flow<WaterState> = preferences.state

    suspend fun currentState(): WaterState = preferences.currentState()

    suspend fun ensureToday() {
        preferences.ensureToday()
    }

    suspend fun addDrink() {
        preferences.addDrink()
    }

    suspend fun setGoalAndDrinkAmount(goalMl: Int, drinkAmountMl: Int) {
        preferences.setGoalAndDrinkAmount(goalMl, drinkAmountMl)
    }

    suspend fun setReminderWindow(startMinute: Int, endMinute: Int) {
        preferences.setReminderWindow(startMinute, endMinute)
    }

    suspend fun resetToday() {
        preferences.resetToday()
    }

    suspend fun applyNotificationAction(actionId: String, drank: Boolean): Boolean {
        return preferences.applyNotificationAction(actionId, drank)
    }
}

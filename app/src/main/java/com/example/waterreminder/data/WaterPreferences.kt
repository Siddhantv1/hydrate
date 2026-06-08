package com.example.waterreminder.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.waterDataStore by preferencesDataStore(name = "water_preferences")

class WaterPreferences(private val context: Context) {
    val state: Flow<WaterState> = context.waterDataStore.data.map { preferences ->
        preferences.toWaterState()
    }

    suspend fun currentState(): WaterState = state.first()

    suspend fun ensureToday(date: LocalDate = LocalDate.now()) {
        val today = date.toString()
        context.waterDataStore.edit { preferences ->
            resetIfNewDay(preferences, today)
        }
    }

    suspend fun addDrink(amountMl: Int? = null) {
        context.waterDataStore.edit { preferences ->
            resetIfNewDay(preferences, LocalDate.now().toString())
            val amount = amountMl ?: preferences[DEFAULT_DRINK_AMOUNT_ML]
                ?: WaterDefaults.DEFAULT_DRINK_AMOUNT_ML
            val current = preferences[CURRENT_DAY_INTAKE_ML] ?: 0
            preferences[CURRENT_DAY_INTAKE_ML] = (current + amount).coerceAtLeast(0)
        }
    }

    suspend fun setGoalAndDrinkAmount(goalMl: Int, drinkAmountMl: Int) {
        context.waterDataStore.edit { preferences ->
            resetIfNewDay(preferences, LocalDate.now().toString())
            preferences[DAILY_GOAL_ML] = goalMl.coerceIn(
                WaterDefaults.MIN_GOAL_ML,
                WaterDefaults.MAX_GOAL_ML
            )
            preferences[DEFAULT_DRINK_AMOUNT_ML] = drinkAmountMl.coerceIn(
                WaterDefaults.MIN_DRINK_AMOUNT_ML,
                WaterDefaults.MAX_DRINK_AMOUNT_ML
            )
        }
    }

    suspend fun setReminderWindow(startMinute: Int, endMinute: Int) {
        if (endMinute <= startMinute) return

        context.waterDataStore.edit { preferences ->
            preferences[REMINDER_START_MINUTE] = startMinute.coerceIn(0, LAST_MINUTE_OF_DAY)
            preferences[REMINDER_END_MINUTE] = endMinute.coerceIn(0, LAST_MINUTE_OF_DAY)
        }
    }

    suspend fun resetToday() {
        context.waterDataStore.edit { preferences ->
            preferences[CURRENT_DAY_INTAKE_ML] = 0
            preferences[LAST_TRACKED_DATE] = LocalDate.now().toString()
            preferences[HANDLED_ACTION_IDS] = emptySet()
        }
    }

    suspend fun applyNotificationAction(actionId: String, drank: Boolean): Boolean {
        if (actionId.isBlank()) return false

        var applied = false
        context.waterDataStore.edit { preferences ->
            resetIfNewDay(preferences, LocalDate.now().toString())

            val handledIds = preferences[HANDLED_ACTION_IDS].orEmpty()
            if (actionId in handledIds) return@edit

            preferences[HANDLED_ACTION_IDS] = trimHandledIds(handledIds + actionId)
            if (drank) {
                val amount = preferences[DEFAULT_DRINK_AMOUNT_ML]
                    ?: WaterDefaults.DEFAULT_DRINK_AMOUNT_ML
                val current = preferences[CURRENT_DAY_INTAKE_ML] ?: 0
                preferences[CURRENT_DAY_INTAKE_ML] = (current + amount).coerceAtLeast(0)
            }
            applied = true
        }
        return applied
    }

    private fun Preferences.toWaterState(): WaterState {
        val today = LocalDate.now().toString()
        val storedDate = this[LAST_TRACKED_DATE] ?: today
        val intake = if (storedDate == today) this[CURRENT_DAY_INTAKE_ML] ?: 0 else 0

        return WaterState(
            dailyGoalMl = (this[DAILY_GOAL_ML] ?: WaterDefaults.DEFAULT_DAILY_GOAL_ML)
                .coerceIn(WaterDefaults.MIN_GOAL_ML, WaterDefaults.MAX_GOAL_ML),
            currentDayIntakeMl = intake,
            defaultDrinkAmountMl = (this[DEFAULT_DRINK_AMOUNT_ML]
                ?: WaterDefaults.DEFAULT_DRINK_AMOUNT_ML)
                .coerceIn(
                    WaterDefaults.MIN_DRINK_AMOUNT_ML,
                    WaterDefaults.MAX_DRINK_AMOUNT_ML
                ),
            reminderStartMinute = (this[REMINDER_START_MINUTE]
                ?: WaterDefaults.DEFAULT_REMINDER_START_MINUTE)
                .coerceIn(0, LAST_MINUTE_OF_DAY),
            reminderEndMinute = (this[REMINDER_END_MINUTE]
                ?: WaterDefaults.DEFAULT_REMINDER_END_MINUTE)
                .coerceIn(0, LAST_MINUTE_OF_DAY),
            lastTrackedDate = if (storedDate == today) storedDate else today
        )
    }

    private fun resetIfNewDay(preferences: MutablePreferences, today: String) {
        if (preferences[LAST_TRACKED_DATE] != today) {
            preferences[CURRENT_DAY_INTAKE_ML] = 0
            preferences[LAST_TRACKED_DATE] = today
            preferences[HANDLED_ACTION_IDS] = emptySet()
        }
    }

    private fun trimHandledIds(ids: Set<String>): Set<String> {
        return if (ids.size <= MAX_HANDLED_ACTION_IDS) {
            ids
        } else {
            ids.takeLast(MAX_HANDLED_ACTION_IDS).toSet()
        }
    }

    private companion object {
        const val LAST_MINUTE_OF_DAY = 23 * 60 + 59
        const val MAX_HANDLED_ACTION_IDS = 80

        val DAILY_GOAL_ML = intPreferencesKey("daily_goal_ml")
        val CURRENT_DAY_INTAKE_ML = intPreferencesKey("current_day_intake_ml")
        val DEFAULT_DRINK_AMOUNT_ML = intPreferencesKey("default_drink_amount_ml")
        val REMINDER_START_MINUTE = intPreferencesKey("reminder_start_minute")
        val REMINDER_END_MINUTE = intPreferencesKey("reminder_end_minute")
        val LAST_TRACKED_DATE = stringPreferencesKey("last_tracked_date")
        val HANDLED_ACTION_IDS = stringSetPreferencesKey("handled_action_ids")
    }
}

package com.example.waterreminder.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.waterreminder.data.WaterRepository
import com.example.waterreminder.data.WaterState
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlin.math.ceil
import kotlin.math.max

object ReminderScheduler {
    const val ACTION_SHOW_REMINDER = "com.example.waterreminder.action.SHOW_REMINDER"

    private const val REMINDER_REQUEST_CODE = 4100
    private const val MIN_REMINDER_INTERVAL_MINUTES = 15L

    suspend fun scheduleNextReminder(context: Context) {
        val appContext = context.applicationContext
        val repository = WaterRepository(appContext)
        repository.ensureToday()
        val state = repository.currentState()
        val alarmManager = appContext.getSystemService(AlarmManager::class.java)
        val pendingIntent = reminderPendingIntent(appContext)

        alarmManager.cancel(pendingIntent)

        if (!state.hasValidReminderWindow) return

        val triggerAtMillis = calculateNextTriggerMillis(
            state = state,
            now = ZonedDateTime.now(),
            forceTomorrow = state.isGoalReached
        ) ?: return

        // Inexact idle-aware alarms are more battery-friendly and avoid exact-alarm permission.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    fun cancel(context: Context) {
        val alarmManager = context.applicationContext.getSystemService(AlarmManager::class.java)
        alarmManager.cancel(reminderPendingIntent(context.applicationContext))
    }

    internal fun isWithinReminderWindow(
        state: WaterState,
        now: LocalTime = LocalTime.now()
    ): Boolean {
        if (!state.hasValidReminderWindow) return false
        val minute = now.hour * 60 + now.minute
        return minute >= state.reminderStartMinute && minute < state.reminderEndMinute
    }

    internal fun calculateNextTriggerMillis(
        state: WaterState,
        now: ZonedDateTime = ZonedDateTime.now(),
        forceTomorrow: Boolean = false
    ): Long? {
        if (!state.hasValidReminderWindow || state.dailyGoalMl <= 0 || state.defaultDrinkAmountMl <= 0) {
            return null
        }

        val startTime = minuteOfDayToLocalTime(state.reminderStartMinute)
        val endTime = minuteOfDayToLocalTime(state.reminderEndMinute)
        val todayStart = ZonedDateTime.of(now.toLocalDate(), startTime, now.zone)
        val todayEnd = ZonedDateTime.of(now.toLocalDate(), endTime, now.zone)
        val windowMinutes = Duration.between(todayStart, todayEnd).toMinutes()

        if (windowMinutes <= 0) return null

        val intervalMinutes = calculateReminderIntervalMinutes(state, windowMinutes)
        val next = when {
            forceTomorrow -> todayStart.plusDays(1)
            now.isBefore(todayStart) -> todayStart
            !now.isBefore(todayEnd) -> todayStart.plusDays(1)
            else -> {
                val minutesSinceStart = Duration.between(todayStart, now)
                    .toMinutes()
                    .coerceAtLeast(0)
                val nextSlotIndex = (minutesSinceStart / intervalMinutes) + 1
                val candidate = todayStart.plusMinutes(nextSlotIndex * intervalMinutes)
                if (candidate.isBefore(todayEnd)) candidate else todayStart.plusDays(1)
            }
        }

        return next.toInstant().toEpochMilli()
    }

    internal fun calculateReminderIntervalMinutes(
        state: WaterState,
        windowMinutes: Long
    ): Long {
        val plannedReminderCount = ceil(
            state.dailyGoalMl.toDouble() / state.defaultDrinkAmountMl.toDouble()
        ).toLong().coerceAtLeast(1)
        return max(MIN_REMINDER_INTERVAL_MINUTES, windowMinutes / plannedReminderCount)
    }

    private fun minuteOfDayToLocalTime(minute: Int): LocalTime {
        val safeMinute = minute.coerceIn(0, 23 * 60 + 59)
        return LocalTime.of(safeMinute / 60, safeMinute % 60)
    }

    private fun reminderPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, ReminderNotificationReceiver::class.java)
            .setAction(ACTION_SHOW_REMINDER)
        return PendingIntent.getBroadcast(
            context,
            REMINDER_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

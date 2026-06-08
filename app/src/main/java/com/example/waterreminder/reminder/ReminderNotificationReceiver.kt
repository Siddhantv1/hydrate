package com.example.waterreminder.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.waterreminder.data.WaterRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ReminderNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ReminderScheduler.ACTION_SHOW_REMINDER) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val repository = WaterRepository(context)
                repository.ensureToday()
                val state = repository.currentState()

                if (
                    !state.isGoalReached &&
                    ReminderScheduler.isWithinReminderWindow(state)
                ) {
                    WaterNotification.showReminder(context, state)
                }

                ReminderScheduler.scheduleNextReminder(context)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

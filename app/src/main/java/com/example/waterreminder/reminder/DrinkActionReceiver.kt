package com.example.waterreminder.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.waterreminder.data.WaterRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class DrinkActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val shouldAddDrink = when (intent.action) {
            ACTION_DRANK -> true
            ACTION_SKIP -> false
            else -> return
        }

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val actionId = intent.getStringExtra(EXTRA_ACTION_ID).orEmpty()
                val notificationId = intent.getIntExtra(
                    EXTRA_NOTIFICATION_ID,
                    WaterNotification.REMINDER_NOTIFICATION_ID
                )
                val repository = WaterRepository(context)

                repository.ensureToday()
                repository.applyNotificationAction(actionId, shouldAddDrink)
                WaterNotification.dismissReminder(context, notificationId)
                ReminderScheduler.scheduleNextReminder(context)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_DRANK = "com.example.waterreminder.action.DRANK"
        const val ACTION_SKIP = "com.example.waterreminder.action.SKIP"
        const val EXTRA_ACTION_ID = "extra_action_id"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    }
}

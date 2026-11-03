package com.example.waterreminder.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.waterreminder.data.HydrateStore
import com.example.waterreminder.domain.canRemind
import com.example.waterreminder.domain.reminderBody
import com.example.waterreminder.domain.reminderFired
import java.time.ZonedDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ReminderScheduler.ACTION_REMIND) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val now = ZonedDateTime.now()
                val (before, after) = HydrateStore.get(context).update { reminderFired(it, now) }
                if (after.outstanding && canRemind(before, now)) {
                    ReminderNotification.show(context, reminderBody(before, after))
                }
                ReminderScheduler.arm(context, after)
            } finally {
                pending.finish()
            }
        }
    }
}
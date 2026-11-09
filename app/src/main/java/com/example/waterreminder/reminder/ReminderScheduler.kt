package com.example.waterreminder.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.waterreminder.domain.HydrateState

object ReminderScheduler {
    const val ACTION_REMIND = "com.example.waterreminder.action.REMIND"
    private const val REQUEST_CODE = 4100

    fun arm(context: Context, s: HydrateState) {
        val app = context.applicationContext
        val am = app.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(
            app, REQUEST_CODE,
            Intent(app, ReminderReceiver::class.java).setAction(ACTION_REMIND),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        am.cancel(pi)
        if (s.nextReminderAt <= 0) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, s.nextReminderAt, pi)
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, s.nextReminderAt, pi)
        }
    }
}
package com.example.waterreminder.reminder

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.waterreminder.MainActivity
import com.example.waterreminder.R
import com.example.waterreminder.data.WaterState
import java.time.LocalDate

object WaterNotification {
    const val CHANNEL_ID = "water_reminders"
    const val REMINDER_NOTIFICATION_ID = 2001

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notification_channel_description)
            enableVibration(true)
        }

        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    fun canPostNotifications(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    fun showReminder(context: Context, state: WaterState) {
        if (!canPostNotifications(context)) return

        ensureChannel(context)

        val actionId = "${LocalDate.now()}-${SystemClock.elapsedRealtime()}"
        val drankAction = NotificationCompat.Action.Builder(
            R.drawable.ic_stat_water,
            "Drank",
            actionPendingIntent(context, DrinkActionReceiver.ACTION_DRANK, actionId)
        ).build()
        val skipAction = NotificationCompat.Action.Builder(
            R.drawable.ic_stat_water,
            "Skip",
            actionPendingIntent(context, DrinkActionReceiver.ACTION_SKIP, actionId)
        ).build()

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_water)
            .setContentTitle("Time to drink water")
            .setContentText("Did you drink ${state.defaultDrinkAmountMl} ml?")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Did you drink ${state.defaultDrinkAmountMl} ml?")
            )
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setLocalOnly(true)
            .setContentIntent(openAppPendingIntent(context))
            .extend(
                NotificationCompat.WearableExtender()
                    .addAction(drankAction)
                    .addAction(skipAction)
                    .setDismissalId(actionId)
            )
            .build()

        NotificationManagerCompat.from(context)
            .notify(REMINDER_NOTIFICATION_ID, notification)
    }

    fun dismissReminder(context: Context, notificationId: Int = REMINDER_NOTIFICATION_ID) {
        NotificationManagerCompat.from(context).cancel(notificationId)
    }

    private fun actionPendingIntent(
        context: Context,
        action: String,
        actionId: String
    ): PendingIntent {
        val intent = Intent(context, DrinkActionReceiver::class.java)
            .setAction(action)
            .putExtra(DrinkActionReceiver.EXTRA_ACTION_ID, actionId)
            .putExtra(DrinkActionReceiver.EXTRA_NOTIFICATION_ID, REMINDER_NOTIFICATION_ID)

        return PendingIntent.getBroadcast(
            context,
            "$action-$actionId".hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun openAppPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

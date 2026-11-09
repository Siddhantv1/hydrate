package com.example.waterreminder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.example.waterreminder.reminder.ReminderNotification
import com.example.waterreminder.ui.HydrateApp
import com.example.waterreminder.ui.HydrateViewModel
import com.example.waterreminder.ui.theme.HydrateTheme

class MainActivity : ComponentActivity() {
    private val vm: HydrateViewModel by viewModels()

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ReminderNotification.ensureChannel(this)
        requestNotificationPermissionIfNeeded()

        setContent {
            HydrateTheme {
                HydrateApp(vm)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.onForeground(false)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        vm.onForeground(true)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
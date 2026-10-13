package com.limi.tvdesktop

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            if (DesktopPreferences.AutoStart.isEnabled(context)) {
                val launchIntent = Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                }
                runCatching { context.startActivity(launchIntent) }.onFailure {
                    android.util.Log.w("BootReceiver", "System did not allow launcher startup", it)
                }
            }
        }
    }
}

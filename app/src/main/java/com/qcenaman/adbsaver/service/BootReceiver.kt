package com.qcenaman.adbsaver.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.qcenaman.adbsaver.Prefs

/** Starts the engine on boot and after the app is updated. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_LOCKED_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {}
            else -> return
        }
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) Thread { com.qcenaman.adbsaver.engine.HelperClient.restart() }.start()
        if (!Prefs.killed) EngineService.start(context.applicationContext)
    }
}

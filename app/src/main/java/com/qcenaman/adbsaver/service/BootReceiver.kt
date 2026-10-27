package com.qcenaman.adbsaver.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.qcenaman.adbsaver.Prefs

/** Starts the engine on boot and after the app is updated. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (!Prefs.killed) EngineService.start(context.applicationContext)
    }
}

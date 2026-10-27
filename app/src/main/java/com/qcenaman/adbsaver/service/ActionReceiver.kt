package com.qcenaman.adbsaver.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.qcenaman.adbsaver.CmdLog
import com.qcenaman.adbsaver.Prefs

/** Handles the Pause / Kill buttons on the notification. */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            PAUSE -> { Prefs.paused = !Prefs.paused; CmdLog.add(if (Prefs.paused) "paused" else "resumed"); EngineService.start(context.applicationContext) }
            KILL -> {
                Prefs.killed = true; CmdLog.add("killed from notification")
                context.stopService(Intent(context, EngineService::class.java))
            }
        }
    }
    companion object { const val PAUSE = "com.qcenaman.adbsaver.PAUSE"; const val KILL = "com.qcenaman.adbsaver.KILL" }
}

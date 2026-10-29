package com.qcenaman.adbsaver.service

import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.qcenaman.adbsaver.CmdLog
import com.qcenaman.adbsaver.Prefs
import com.qcenaman.adbsaver.adb.Pairing

/** Handles the Pause / Kill buttons and the inline pairing-code reply on notifications. */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val app = context.applicationContext
        when (intent?.action) {
            PAUSE -> { Prefs.paused = !Prefs.paused; CmdLog.add(if (Prefs.paused) "paused" else "resumed"); EngineService.start(app) }
            KILL -> {
                Prefs.killed = true; CmdLog.add("killed from notification")
                context.stopService(Intent(context, EngineService::class.java))
            }
            AUTOPAIR_NOW -> com.qcenaman.adbsaver.engine.AutoPair.decision = "now"
            AUTOPAIR_SKIP -> com.qcenaman.adbsaver.engine.AutoPair.decision = "skip"
            PAIR_CODE -> {
                val code = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(Pairing.KEY_CODE)?.toString().orEmpty()
                val port = intent.getIntExtra("port", -1)
                if (code.isNotBlank()) Pairing.submitCode(app, port, code)
            }
        }
    }
    companion object {
        const val PAUSE = "com.qcenaman.adbsaver.PAUSE"
        const val KILL = "com.qcenaman.adbsaver.KILL"
        const val PAIR_CODE = "com.qcenaman.adbsaver.PAIR_CODE"
        const val AUTOPAIR_NOW = "com.qcenaman.adbsaver.AUTOPAIR_NOW"
        const val AUTOPAIR_SKIP = "com.qcenaman.adbsaver.AUTOPAIR_SKIP"
    }
}

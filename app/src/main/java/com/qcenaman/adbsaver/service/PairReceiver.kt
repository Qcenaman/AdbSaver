package com.qcenaman.adbsaver.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.qcenaman.adbsaver.engine.AutoPair

/**
 * `adb shell am broadcast -n com.qcenaman.adbsaver/.service.PairReceiver [--es code 123456 --ei port N]`
 * Pairs the app's own ADB client: with the given code, or fully automatically via accessibility.
 */
class PairReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val code = intent?.getStringExtra("code")
        val port = intent?.getIntExtra("port", 0) ?: 0
        val app = context.applicationContext
        Thread {
            if (!code.isNullOrBlank()) AutoPair.pairWithCode(app, code, intent?.getStringExtra("host"), port)
            else AutoPair.auto(app)
        }.start()
    }
}

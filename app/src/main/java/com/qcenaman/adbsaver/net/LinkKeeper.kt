package com.qcenaman.adbsaver.net

import android.content.Context
import com.qcenaman.adbsaver.adb.AdbManager
import com.qcenaman.adbsaver.engine.AutoPair
import com.qcenaman.adbsaver.engine.Shell

/**
 * Keeps the privileged channel alive: reconnects the shell channel, and when the phone says
 * pairing is required, pairs itself through the accessibility service (at most every 10 min).
 * PC route / Wake-on-LAN / Tailscale land in the link-keeper step.
 */
object LinkKeeper {
    @Volatile private var running = false

    fun start(ctx: Context) {
        if (running) return
        running = true
        Thread {
            while (running) {
                try {
                    if (!Shell.ready(ctx) && AdbManager.get(ctx).needsPairing &&
                        System.currentTimeMillis() - AutoPair.lastAttempt > 10 * 60_000L) {
                        AutoPair.auto(ctx)
                    }
                } catch (_: Exception) {}
                Thread.sleep(30_000)
            }
        }.apply { isDaemon = true; name = "link-keeper" }.start()
    }

    fun stop() { running = false }
}

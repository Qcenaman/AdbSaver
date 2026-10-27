package com.qcenaman.adbsaver.net

import android.content.Context

/**
 * Keeps the route to the PC alive and repairs the privileged channel.
 * Full reconnect / Wake-on-LAN / Tailscale logic lands in the link-keeper step;
 * for now it re-establishes the shell channel so tools work on LAN.
 */
object LinkKeeper {
    @Volatile private var running = false

    fun start(ctx: Context) {
        if (running) return
        running = true
        Thread {
            while (running) {
                try { com.qcenaman.adbsaver.engine.Shell.ready(ctx) } catch (_: Exception) {}
                Thread.sleep(30_000)
            }
        }.apply { isDaemon = true; name = "link-keeper" }.start()
    }

    fun stop() { running = false }
}

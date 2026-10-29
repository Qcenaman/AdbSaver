package com.qcenaman.adbsaver.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.provider.Settings
import com.qcenaman.adbsaver.CmdLog
import com.qcenaman.adbsaver.adb.AdbManager
import com.qcenaman.adbsaver.engine.AutoPair
import com.qcenaman.adbsaver.engine.HelperClient
import com.qcenaman.adbsaver.engine.Shell

/**
 * Keeps the privileged channel alive on its own:
 *  - keeps the shell helper running (it survives Wi-Fi loss once started);
 *  - when Wi-Fi comes back, turns Wireless debugging on again (WRITE_SECURE_SETTINGS) and reconnects;
 *  - when the phone says pairing is required, pairs itself through accessibility (≤ once per 10 min).
 * The PC route (report/WoL) lands in the PC-link step.
 */
object LinkKeeper {
    @Volatile private var running = false
    @Volatile private var wifiUp = false

    fun start(ctx: Context) {
        if (running) return
        running = true
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        cm.registerNetworkCallback(
            NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(),
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(n: Network) { wifiUp = true; CmdLog.add("wifi up"); Thread { repair(ctx); PcLink.report(ctx) }.start() }
                override fun onLost(n: Network) { wifiUp = false; CmdLog.add("wifi lost — shell stays on the helper if it is up"); Thread { Thread.sleep(5000); PcLink.report(ctx) }.start() }
            })
        Thread {
            var n = 0
            while (running) {
                try { repair(ctx); if (n++ % 20 == 0) PcLink.report(ctx) } catch (_: Exception) {}
                Thread.sleep(45_000)
            }
        }.apply { isDaemon = true; name = "link-keeper" }.start()
    }

    @Synchronized
    private fun repair(ctx: Context) {
        if (HelperClient.alive()) return
        if (!wifiUp) return // adbd only listens on Wi-Fi; nothing to do until it returns
        enableWirelessDebugging(ctx)
        if (Shell.ready(ctx)) { HelperClient.ensureStarted(ctx); return }
        if (AdbManager.get(ctx).needsPairing && System.currentTimeMillis() - AutoPair.lastAttempt > 10 * 60_000L) {
            AutoPair.auto(ctx)
        }
    }

    /** Needs WRITE_SECURE_SETTINGS (granted once over adb: pm grant … WRITE_SECURE_SETTINGS). */
    fun enableWirelessDebugging(ctx: Context): Boolean = try {
        val cr = ctx.contentResolver
        if (Settings.Global.getInt(cr, "adb_wifi_enabled", 0) != 1) {
            Settings.Global.putInt(cr, "adb_wifi_enabled", 1)
            CmdLog.add("wireless debugging switched on")
            Thread.sleep(1500)
        }
        true
    } catch (e: Exception) { CmdLog.add("cannot switch wireless debugging on: ${e.message}"); false }

    fun stop() { running = false }
}

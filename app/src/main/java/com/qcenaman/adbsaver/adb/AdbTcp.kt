package com.qcenaman.adbsaver.adb

import android.content.Context
import com.qcenaman.adbsaver.CmdLog
import com.qcenaman.adbsaver.engine.Shell

/**
 * Keeps adbd in classic TCP mode on a fixed port. Unlike Wireless debugging (random TLS port, mDNS on the LAN only,
 * switched off whenever Wi-Fi drops) it listens on every interface — Tailscale and mobile data included — and stays up
 * without Wi-Fi until the next reboot. Switching it on needs one live ADB connection (Wi-Fi once after a reboot).
 */
object AdbTcp {
    const val PORT = 5555

    /** True when `getprop service.adb.tcp.port` output shows adbd is already on [PORT]. */
    fun isOn(propOutput: String): Boolean = propOutput.trim().lines().lastOrNull()?.trim() == PORT.toString()

    /** Checks the mode and switches adbd to TCP when it is off. Returns true when TCP mode is on afterwards. */
    fun keep(ctx: Context): Boolean {
        val (code, out) = Shell.run(ctx, "getprop service.adb.tcp.port")
        if (code == 0 && isOn(out)) return true
        val adb = AdbManager.get(ctx)
        if (!adb.ensureConnected(ctx)) return false
        return try {
            adb.switchToTcp(PORT)
            CmdLog.add("adbd switched to TCP port $PORT (reachable over Tailscale)")
            true
        } catch (e: Exception) {
            CmdLog.add("could not switch adbd to TCP: ${e.message}"); false
        }
    }
}

package com.qcenaman.adbsaver.engine

import android.content.Context
import com.qcenaman.adbsaver.adb.AdbManager

/**
 * Shell access as the "shell" (uid 2000) user over our own ADB connection — the same power
 * Shizuku grants, without Shizuku. Lets us call input, pm, am, settings, dumpsys, screencap, etc.
 */
object Shell {
    @Volatile var lastError: String = ""

    fun ready(ctx: Context): Boolean = AdbManager.get(ctx).ensureConnected(ctx)

    /** Capture combined output and exit code. */
    fun run(ctx: Context, cmd: String): Pair<Int, String> {
        if (!ready(ctx)) return -1 to "adb shell unavailable: ${lastError.ifEmpty { "not connected" }}"
        return AdbManager.get(ctx).capture(cmd)
    }

    fun runBytes(ctx: Context, cmd: String): ByteArray {
        if (!ready(ctx)) return ByteArray(0)
        return AdbManager.get(ctx).execBytes(cmd)
    }

    /** True if the elevated (shell-user) channel is live. */
    fun elevated(ctx: Context): Boolean = AdbManager.get(ctx).isConnected
}

package com.qcenaman.adbsaver.engine

import android.content.Context
import com.qcenaman.adbsaver.adb.AdbManager

/**
 * Shell access as the "shell" (uid 2000) user — the power Shizuku grants, without Shizuku.
 * Prefers the persistent helper (survives Wi-Fi loss); falls back to the live ADB channel.
 */
object Shell {
    @Volatile var lastError: String = ""

    fun ready(ctx: Context): Boolean = HelperClient.alive() || HelperClient.ensureStarted(ctx) || AdbManager.get(ctx).ensureConnected(ctx)

    /** Capture combined output and exit code. */
    fun run(ctx: Context, cmd: String): Pair<Int, String> {
        val sb = StringBuilder()
        val code = exec(ctx, cmd) { b, n -> sb.append(String(b, 0, n, Charsets.UTF_8)) }
        return code to sb.toString()
    }

    /** Streamed execution; -1 when no channel is available. */
    fun exec(ctx: Context, cmd: String, onPid: (Int) -> Unit = {}, onData: (ByteArray, Int) -> Unit): Int {
        if (HelperClient.alive() || HelperClient.ensureStarted(ctx)) {
            val c = HelperClient.exec(cmd, onPid, onData)
            if (c != -1 || !AdbManager.get(ctx).ensureConnected(ctx)) return c
        }
        if (!AdbManager.get(ctx).ensureConnected(ctx)) {
            lastError = "no shell channel: helper down and Wireless debugging off (needs Wi-Fi once)"
            val m = lastError.toByteArray(); onData(m, m.size); return -1
        }
        return AdbManager.get(ctx).exec(cmd, onPid, onData)
    }

    fun runBytes(ctx: Context, cmd: String): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val code = exec(ctx, cmd) { b, n -> out.write(b, 0, n) }
        return if (code == -1 && out.size() < 200) ByteArray(0) else out.toByteArray()
    }

    fun status(ctx: Context): String = when {
        HelperClient.alive() -> "helper (persistent)"
        AdbManager.get(ctx).isConnected -> "adb (live)"
        else -> "down"
    }
}

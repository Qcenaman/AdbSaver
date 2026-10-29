package com.qcenaman.adbsaver.helper

import android.net.LocalServerSocket
import android.net.LocalSocket
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import kotlin.concurrent.thread
import kotlin.system.exitProcess

/**
 * Runs as the shell user (uid 2000) via app_process, started once over our ADB channel.
 * It outlives adbd, so the app keeps shell power after Wireless debugging turns off (Wi-Fi loss).
 *
 * Protocol over the abstract local socket "adbsaver_shell":
 *   client → "<token>\n" then [int32 len][utf8 command]
 *   server → frames: [byte type][int32 len][payload]; type 1 = output bytes, 2 = pid (int32), 3 = exit (int32)
 */
object Main {
    const val SOCKET = "adbsaver_shell"

    @JvmStatic
    fun main(args: Array<String>) {
        val token = args.getOrNull(0) ?: run { System.err.println("token required"); exitProcess(2) }
        val server = try { LocalServerSocket(SOCKET) } catch (e: Exception) { System.err.println("already running"); exitProcess(0) }
        println("adbsaver helper up uid=${android.os.Process.myUid()}")
        while (true) {
            val s = try { server.accept() } catch (e: Exception) { break }
            thread(isDaemon = true) { serve(s, token) }
        }
    }

    private fun serve(s: LocalSocket, token: String) {
        try {
            val inp = DataInputStream(s.inputStream)
            val out = DataOutputStream(s.outputStream)
            val line = StringBuilder()
            while (true) { val c = inp.read(); if (c < 0 || c == '\n'.code) break; line.append(c.toChar()) }
            if (line.toString() != token) { s.close(); return }
            val n = inp.readInt()
            val cmd = ByteArray(n).also { inp.readFully(it) }.toString(Charsets.UTF_8)
            if (cmd == "__ping__") { frame(out, 3, intBytes(0)); s.close(); return }
            if (cmd == "__exit__") { s.close(); exitProcess(0) }
            // Android's runtime has no Process.pid(): print the shell's pid, then exec the command in its place.
            val b64 = android.util.Base64.encodeToString(cmd.toByteArray(), android.util.Base64.NO_WRAP)
            val wrapped = "echo __ADBSAVER_PID=\$\$; exec /system/bin/sh -c \"\$(echo $b64 | base64 -d)\""
            val p = ProcessBuilder("/system/bin/sh", "-c", wrapped).redirectErrorStream(true).directory(File("/")).start()
            val buf = ByteArray(16384)
            p.inputStream.use { ps ->
                var first = true
                while (true) {
                    val r = ps.read(buf); if (r < 0) break
                    var off = 0; var len = r
                    if (first) {
                        first = false
                        val head = String(buf, 0, r, Charsets.UTF_8)
                        val nl = head.indexOf('\n')
                        if (head.startsWith("__ADBSAVER_PID=") && nl > 0) {
                            frame(out, 2, intBytes(head.substring(15, nl).trim().toIntOrNull() ?: -1))
                            val skip = head.substring(0, nl + 1).toByteArray(Charsets.UTF_8).size
                            off = skip; len = r - skip
                        }
                    }
                    if (len > 0) frame(out, 1, buf.copyOfRange(off, off + len))
                }
            }
            frame(out, 3, intBytes(p.waitFor()))
            out.flush()
        } catch (_: Exception) {
        } finally { try { s.close() } catch (_: Exception) {} }
    }

    private fun frame(out: DataOutputStream, type: Int, payload: ByteArray) {
        synchronized(out) { out.writeByte(type); out.writeInt(payload.size); out.write(payload); out.flush() }
    }

    private fun intBytes(v: Int) = byteArrayOf((v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte())
}

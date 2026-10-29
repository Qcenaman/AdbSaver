package com.qcenaman.adbsaver.net

import android.content.Context
import android.os.BatteryManager
import com.qcenaman.adbsaver.CmdLog
import com.qcenaman.adbsaver.Prefs
import com.qcenaman.adbsaver.mcp.Json
import com.qcenaman.adbsaver.mcp.ToolResult
import com.qcenaman.adbsaver.mcp.asMap
import com.qcenaman.adbsaver.mcp.int
import com.qcenaman.adbsaver.mcp.string
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL

/**
 * The PC's TokenSaver "link" front (tokensaver serve), reached over the LAN first, then Tailscale.
 * Details arrive from the PC's `phone pair` (POST /link) and live in Prefs.
 */
object PcLink {
    data class Pc(val token: String, val port: Int, val lanIp: String, val tsIp: String, val mac: String, val name: String)

    fun pc(): Pc? {
        val j = Prefs.pcLinkJson ?: return null
        val m = runCatching { Json.parse(j).asMap() }.getOrNull() ?: return null
        val tok = m.string("token"); if (tok.isEmpty()) return null
        return Pc(tok, m.int("port", 8766), m.string("lan_ip"), m.string("tailscale_ip"), m.string("mac"), m.string("name"))
    }

    fun store(json: String) { Prefs.pcLinkJson = json; cachedBase = null; CmdLog.add("PC link stored: ${pc()?.name} lan=${pc()?.lanIp} ts=${pc()?.tsIp}") }

    @Volatile private var cachedBase: String? = null
    @Volatile private var cachedAt = 0L

    private fun health(base: String, ms: Int): Boolean = runCatching {
        val c = URL("$base/health").openConnection() as HttpURLConnection
        c.connectTimeout = ms; c.readTimeout = ms
        c.responseCode == 200
    }.getOrDefault(false)

    /** First base URL that answers: LAN, then Tailscale. */
    fun reach(force: Boolean = false): Pair<String, String>? {
        val p = pc() ?: return null
        cachedBase?.let { if (!force && System.currentTimeMillis() - cachedAt < 60_000) return it to "cached" }
        for ((ip, via) in listOf(p.lanIp to "lan", p.tsIp to "tailscale")) {
            if (ip.isEmpty()) continue
            val base = "http://$ip:${p.port}"
            if (health(base, if (via == "lan") 1500 else 4000)) { cachedBase = base; cachedAt = System.currentTimeMillis(); return base to via }
        }
        return null
    }

    /** One MCP tools/call on the PC. */
    fun call(tool: String, argsJson: String): ToolResult {
        val p = pc() ?: return ToolResult.err("no PC link yet: run `phone pair` on the PC while the phone is on the LAN")
        val (base, _) = reach() ?: return ToolResult.err("PC unreachable (lan=${p.lanIp.ifEmpty { "-" }} tailscale=${p.tsIp.ifEmpty { "-" }}); try pc wake if it is asleep")
        val body = """{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":${Json.str(tool)},"arguments":$argsJson}}"""
        val reply = runCatching {
            val c = URL("$base/mcp").openConnection() as HttpURLConnection
            c.requestMethod = "POST"; c.doOutput = true; c.connectTimeout = 5000; c.readTimeout = 180_000
            c.setRequestProperty("Authorization", "Bearer ${p.token}"); c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.use { it.write(body.toByteArray()) }
            if (c.responseCode == 401) return ToolResult.err("PC rejected our token: pair again")
            (if (c.responseCode in 200..299) c.inputStream else c.errorStream).bufferedReader().readText()
        }.getOrElse { cachedBase = null; return ToolResult.err("PC call failed: ${it.message}") }
        val r = runCatching { Json.parse(reply).asMap() }.getOrNull() ?: return ToolResult.err("PC: bad reply: ${reply.take(200)}")
        r["error"]?.asMap()?.let { return ToolResult.err("PC: ${it.string("message")}") }
        val res = r["result"].asMap()
        val content = res["content"] as? List<*> ?: emptyList<Any>()
        val text = content.mapNotNull { it.asMap() }.filter { it.string("type") == "text" }.joinToString("\n") { it.string("text") }
        val img = content.mapNotNull { it.asMap() }.firstOrNull { it.string("type") == "image" }
        val isErr = res["isError"] == true
        return if (img != null) ToolResult(text, isErr, img.string("data"), img.string("mimeType", "image/png")) else ToolResult(text, isErr)
    }

    /** Tell the PC where we are (LAN / Tailscale / adb port / battery / network). Only works once paired. */
    fun report(ctx: Context, adbPort: Int = 0) {
        val p = pc() ?: return
        val (base, via) = reach() ?: return
        val a = Addresses.find()
        val bm = ctx.getSystemService(BatteryManager::class.java)
        val bat = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        val body = """{"LanIP":${Json.str(a.lan ?: "")},"TailscaleIP":${Json.str(a.tailscale ?: "")},"AdbSerial":${Json.str(if (adbPort > 0 && a.lan != null) "${a.lan}:$adbPort" else "")},"Battery":"$bat","Network":${Json.str(if (a.lan != null) "wifi" else "mobile")}}"""
        runCatching {
            val c = URL("$base/phone/report").openConnection() as HttpURLConnection
            c.requestMethod = "POST"; c.doOutput = true; c.connectTimeout = 4000; c.readTimeout = 8000
            c.setRequestProperty("Authorization", "Bearer ${p.token}")
            c.outputStream.use { it.write(body.toByteArray()) }
            if (c.responseCode == 200) CmdLog.add("reported to PC via $via: lan=${a.lan ?: "-"} ts=${a.tailscale ?: "-"}")
        }
    }

    /** Wake-on-LAN: magic packet to the PC's MAC over the local broadcast (works on the home Wi-Fi only). */
    fun wake(): String {
        val p = pc() ?: return "no PC link"
        val mac = p.mac.split(':', '-').mapNotNull { it.toIntOrNull(16)?.toByte() }
        if (mac.size != 6) return "PC MAC unknown (pair again at home)"
        val packet = ByteArray(6) { 0xFF.toByte() } + ByteArray(16 * 6) { mac[it % 6] }
        val targets = listOfNotNull(Addresses.find().lan?.let { broadcastOf(it) }, "255.255.255.255")
        return runCatching {
            DatagramSocket().use { s -> s.broadcast = true; for (t in targets) for (port in intArrayOf(9, 7)) s.send(DatagramPacket(packet, packet.size, InetAddress.getByName(t), port)) }
            "magic packet sent to ${p.mac} via ${targets.joinToString()} (only reaches the PC from the home network)"
        }.getOrElse { "wake failed: ${it.message}" }
    }

    private fun broadcastOf(ip: String): String = ip.substringBeforeLast('.') + ".255"
}

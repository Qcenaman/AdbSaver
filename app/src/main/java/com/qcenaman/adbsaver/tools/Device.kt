package com.qcenaman.adbsaver.tools

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.qcenaman.adbsaver.App
import com.qcenaman.adbsaver.engine.Shell
import com.qcenaman.adbsaver.mcp.*
import java.io.ByteArrayOutputStream

/** screen / apps / notifications / system / web — device-level tools. */
object Device {
    private val ctx: Context get() = App.ctx

    val screen = Tool("screen", "Screenshot as a downscaled JPEG (only when asked). Optional max_px.",
        """{"type":"object","properties":{"max_px":{"type":"integer"}}}""") { a ->
        val png = Shell.runBytes(ctx, "screencap -p")
        if (png.isEmpty()) return@Tool ToolResult.err("screencap failed (shell channel down)")
        val bmp = BitmapFactory.decodeByteArray(png, 0, png.size) ?: return@Tool ToolResult.err("decode failed")
        val max = a.int("max_px", 900)
        val scale = minOf(1f, max.toFloat() / maxOf(bmp.width, bmp.height))
        val out = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true) else bmp
        val bos = ByteArrayOutputStream(); out.compress(Bitmap.CompressFormat.JPEG, 70, bos)
        ToolResult.image(Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP), caption = "${out.width}x${out.height}")
    }

    val apps = Tool("apps", "List/launch/stop/install/uninstall apps. action + package.",
        """{"type":"object","properties":{"action":{"type":"string"},"package":{"type":"string"},"filter":{"type":"string"}}}""") { a ->
        val pkg = a.string("package")
        when (a.string("action", "list").lowercase()) {
            "list" -> { val (_, o) = Shell.run(ctx, "pm list packages -3"); val f = a.string("filter"); ToolResult.ok(o.lines().map { it.removePrefix("package:") }.filter { f.isEmpty() || it.contains(f, true) }.joinToString("\n")) }
            "launch" -> ToolResult.ok(Shell.run(ctx, "monkey -p $pkg -c android.intent.category.LAUNCHER 1").second.ifBlank { "launched $pkg" })
            "stop" -> ToolResult.ok(Shell.run(ctx, "am force-stop $pkg").let { "stopped $pkg" })
            "install" -> ToolResult.ok(Shell.run(ctx, "pm install -r ${a.string("path")}").second)
            "uninstall" -> ToolResult.ok(Shell.run(ctx, "pm uninstall $pkg").second)
            else -> ToolResult.err("unknown action")
        }
    }

    val notifications = Tool("notifications", "List active notifications (via NotificationListener). action list.",
        """{"type":"object","properties":{"action":{"type":"string"}}}""") { _ ->
        com.qcenaman.adbsaver.service.NotifService.instance?.let { ToolResult.ok(it.dump().ifEmpty { "(none)" }) }
            ?: ToolResult.err("notification access not granted")
    }

    val system = Tool("system", "Phone state: battery, network, storage.",
        """{"type":"object","properties":{}}""") { _ ->
        val bat = Shell.run(ctx, "dumpsys battery | grep -E 'level|status|temperature'").second
        val net = Shell.run(ctx, "settings get global wifi_on; ip route 2>/dev/null | head -1").second
        val disk = Shell.run(ctx, "df -h /sdcard | tail -1").second
        ToolResult.ok("-- battery --\n$bat\n-- network --\n$net\n-- storage --\n$disk")
    }

    val web = Tool("web", "Fetch a URL on the phone and return clean text.",
        """{"type":"object","properties":{"url":{"type":"string"},"max_chars":{"type":"integer"}},"required":["url"]}""") { a ->
        val url = a.string("url")
        val html = runCatching {
            val c = (java.net.URL(url).openConnection() as java.net.HttpURLConnection)
            c.setRequestProperty("User-Agent", "Mozilla/5.0 AdbSaver"); c.connectTimeout = 10000; c.readTimeout = 15000
            c.inputStream.bufferedReader().readText()
        }.getOrElse { return@Tool ToolResult.err("fetch failed: ${it.message}") }
        val text = html.replace(Regex("(?s)<script.*?</script>"), " ")
            .replace(Regex("(?s)<style.*?</style>"), " ")
            .replace(Regex("<[^>]+>"), " ").replace(Regex("[ \t]+"), " ")
            .replace(Regex("\n\\s*\n+"), "\n").trim()
        ToolResult.ok(text.take(a.int("max_chars", 6000)))
    }

    val all = listOf(screen, apps, notifications, system, web)
}

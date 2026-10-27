package com.qcenaman.adbsaver.tools

import com.qcenaman.adbsaver.App
import com.qcenaman.adbsaver.engine.Shell
import com.qcenaman.adbsaver.mcp.*
import java.io.File

/** local_ai and saved. local_ai runs an on-device first pass; saved stores named commands. */
object Extra {
    val localAi = Tool("local_ai", "On-device first pass (summarise/classify/filter) so only the result reaches Claude. action + text.",
        """{"type":"object","properties":{"action":{"type":"string"},"text":{"type":"string"},"instruction":{"type":"string"}},"required":["action"]}""") { a ->
        val res = LocalAi.run(a.string("action", "summarize"), a.string("text"), a.string("instruction"))
        if (res == null) ToolResult.err("local AI model not loaded yet") else ToolResult.ok(res)
    }

    private fun savedDir() = File(App.ctx.filesDir, "saved").also { it.mkdirs() }

    val saved = Tool("saved", "Named saved commands/macros: action list/show/save/remove/run.",
        """{"type":"object","properties":{"action":{"type":"string"},"name":{"type":"string"},"command":{"type":"string"}}}""") { a ->
        val name = a.string("name")
        when (a.string("action", "list").lowercase()) {
            "list" -> ToolResult.ok(savedDir().listFiles()?.joinToString("\n") { it.name } ?: "(none)")
            "show" -> { val f = File(savedDir(), name); if (f.exists()) ToolResult.ok(f.readText()) else ToolResult.err("not found") }
            "save" -> { File(savedDir(), name).writeText(a.string("command")); ToolResult.ok("saved $name") }
            "remove" -> { File(savedDir(), name).delete(); ToolResult.ok("removed $name") }
            "run" -> { val f = File(savedDir(), name); if (!f.exists()) return@Tool ToolResult.err("not found"); val (c, o) = Shell.run(App.ctx, f.readText()); ToolResult(o.take(4000), isError = c != 0) }
            else -> ToolResult.err("unknown action")
        }
    }

    val all = listOf(localAi, saved)
}

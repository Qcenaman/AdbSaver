package com.qcenaman.adbsaver.tools

import com.qcenaman.adbsaver.mcp.Tool

/** All MCP tools the phone engine exposes. */
object Registry {
    val tools: List<Tool> = buildList {
        addAll(Fs.all)
        addAll(ShellTools.all)
        addAll(Ui.all)
        addAll(Device.all)
        addAll(Extra.all)
    }
}

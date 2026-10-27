package com.qcenaman.adbsaver

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CmdLog {
    private lateinit var file: File
    private val recent = ArrayDeque<String>()
    private val fmt = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US)
    @Volatile var version = 0L
        private set

    fun init(ctx: Context) {
        file = File(ctx.filesDir, "logs/commands.log").apply { parentFile?.mkdirs() }
    }

    @Synchronized
    fun add(line: String) {
        val l = "${fmt.format(Date())} ${line.replace('\n', ' ').take(300)}"
        recent.addLast(l)
        while (recent.size > 300) recent.removeFirst()
        runCatching { file.appendText(l + "\n") }
        version++
    }

    @Synchronized
    fun snapshot(): List<String> = recent.toList().asReversed()
}

package com.qcenaman.adbsaver.tools

import com.qcenaman.adbsaver.App
import com.qcenaman.adbsaver.engine.Jobs
import com.qcenaman.adbsaver.engine.Shell
import com.qcenaman.adbsaver.mcp.*

/** run / run_job / job — shell commands as background jobs over our shell-user channel. */
object ShellTools {
    val run = Tool("run", "Run a shell command as a background job; returns exit code, error lines and a short tail. Full log stays on the phone.",
        """{"type":"object","properties":{"command":{"type":"string"},"name":{"type":"string"},"tail":{"type":"integer"},"wait_seconds":{"type":"integer"},"keep_running":{"type":"boolean"}},"required":["command"]}""") { a ->
        val job = Jobs.start(a.string("name", "run"), a.string("command"))
        if (a.bool("keep_running")) return@Tool ToolResult.ok("job ${job.id} started (keep_running)")
        Jobs.waitFor(job, a.int("wait_seconds", 60))
        report(job, a.int("tail", 20))
    }

    val runJob = Tool("run_job", "Run a sequence of shell commands as one background job.",
        """{"type":"object","properties":{"commands":{"type":"array","items":{"type":"string"}},"name":{"type":"string"},"wait_seconds":{"type":"integer"}},"required":["commands"]}""") { a ->
        val cmd = a.list("commands").joinToString(" && ") { "( ${it} )" }
        val job = Jobs.start(a.string("name", "run_job"), cmd)
        Jobs.waitFor(job, a.int("wait_seconds", 120))
        report(job, a.int("tail", 20))
    }

    val job = Tool("job", "Inspect jobs: action status/list/cancel.",
        """{"type":"object","properties":{"action":{"type":"string"},"id":{"type":"string"},"wait_seconds":{"type":"integer"},"tail":{"type":"integer"}}}""") { a ->
        when (a.string("action", "status").lowercase()) {
            "list" -> ToolResult.ok(Jobs.list().joinToString("\n") { "${it.id} ${it.name} ${if (it.done) "done exit=${it.exit}" else "running pid=${it.pid}"}" }.ifEmpty { "no jobs" })
            "cancel" -> ToolResult.ok(if (Jobs.cancel(App.ctx, a["id"]?.toString())) "cancelled" else "not found")
            else -> { val j = Jobs.get(a["id"]?.toString()) ?: return@Tool ToolResult.err("no job"); if (a.int("wait_seconds", 0) > 0) Jobs.waitFor(j, a.int("wait_seconds", 0)); report(j, a.int("tail", 30)) }
        }
    }

    private fun report(j: Jobs.Job, tail: Int): ToolResult {
        val status = if (j.done) "exit=${j.exit}" else "running pid=${j.pid}"
        val errs = Jobs.errorLines(j)
        val sb = StringBuilder("job ${j.id} $status")
        if (errs.isNotEmpty()) sb.append("\n-- errors --\n").append(errs)
        val t = Jobs.tail(j, tail)
        if (t.isNotEmpty()) sb.append("\n-- tail --\n").append(t)
        return ToolResult(sb.toString(), isError = (j.exit ?: 0) != 0)
    }

    val all = listOf(run, runJob, job)
}

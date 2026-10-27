package com.qcenaman.adbsaver.net

import android.content.Context
import com.qcenaman.adbsaver.Prefs
import com.qcenaman.adbsaver.mcp.McpServer
import com.qcenaman.adbsaver.tools.Registry
import fi.iki.elonen.NanoHTTPD

/**
 * Serves MCP over HTTP (JSON-RPC POST at /mcp), token-authenticated.
 * Bound to all interfaces so LAN and tunnel reach it; auth gates every call.
 */
class HttpServer(private val ctx: Context, port: Int) : NanoHTTPD(port) {
    private val mcp = McpServer(Registry.tools)

    override fun serve(session: IHTTPSession): Response {
        return when {
            session.uri == "/health" -> newFixedLengthResponse(Response.Status.OK, "text/plain", "ok")
            session.uri == "/mcp" && session.method == Method.POST -> handleMcp(session)
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "not found")
        }
    }

    private fun handleMcp(session: IHTTPSession): Response {
        val auth = session.headers["authorization"]?.removePrefix("Bearer ")?.trim()
            ?: session.headers["x-adbsaver-token"]
        if (auth != Prefs.token) return newFixedLengthResponse(Response.Status.UNAUTHORIZED, "text/plain", "unauthorized")
        val body = HashMap<String, String>()
        session.parseBody(body)
        val line = body["postData"] ?: ""
        val reply = mcp.handle(line) ?: return newFixedLengthResponse(Response.Status.NO_CONTENT, "application/json", "")
        return newFixedLengthResponse(Response.Status.OK, "application/json", reply)
    }
}

package com.qcenaman.adbsaver

import com.qcenaman.adbsaver.mcp.Json
import com.qcenaman.adbsaver.mcp.asMap
import com.qcenaman.adbsaver.mcp.int
import com.qcenaman.adbsaver.mcp.string
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonTest {
    @Test fun parsesNestedRpc() {
        val m = Json.parse("""{"jsonrpc":"2.0","id":7,"method":"tools/call","params":{"name":"run","arguments":{"command":"echo \"hi\"\n","tail":3,"ok":true}}}""").asMap()
        assertEquals("tools/call", m.string("method"))
        assertEquals(7, m.int("id", 0))
        val args = m["params"].asMap()["arguments"].asMap()
        assertEquals("echo \"hi\"\n", args.string("command"))
        assertEquals(3, args.int("tail", 0))
        assertEquals(true, args["ok"])
    }

    @Test fun escapesRoundTrip() {
        val s = "a\"b\\c\nd\te\u0001"
        assertEquals(s, Json.parse(Json.str(s)))
    }

    @Test fun unicodeEscape() {
        assertEquals("é", Json.parse("\"\\u00e9\""))
        assertTrue(Json.parse("[1,2.5,null]") is List<*>)
    }
}

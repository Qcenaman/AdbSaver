package com.qcenaman.adbsaver

import com.qcenaman.adbsaver.adb.AdbTcp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdbTcpTest {
    @Test fun detectsTcpModeFromGetprop() {
        assertTrue(AdbTcp.isOn("5555\n"))
        assertFalse(AdbTcp.isOn(""))
        assertFalse(AdbTcp.isOn("-1"))
        assertFalse(AdbTcp.isOn("no shell channel: helper down"))
    }
}

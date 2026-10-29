package com.qcenaman.adbsaver.engine

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.qcenaman.adbsaver.App
import com.qcenaman.adbsaver.R
import com.qcenaman.adbsaver.service.ActionReceiver
import com.qcenaman.adbsaver.CmdLog
import com.qcenaman.adbsaver.adb.AdbManager
import com.qcenaman.adbsaver.service.A11yService
import io.github.muntashirakon.adb.android.AdbMdns
import java.net.InetAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Pairs our own ADB client with this phone's Wireless debugging without the user:
 * opens the pairing dialog, reads the code (and host:port) from the screen through the
 * accessibility service, and runs the pairing handshake. Also accepts a code typed in the app.
 */
object AutoPair {
    private val busy = AtomicBoolean(false)
    @Volatile var lastAttempt = 0L
    @Volatile var lastResult = ""
    /** "now" / "skip" from the warning notification's buttons. */
    @Volatile var decision: String? = null
    @Volatile var attempts = 0
    const val MAX_ATTEMPTS = 3
    const val RETRY_MS = 30 * 60_000L
    const val WARN_MS = 20_000L
    private const val NOTIF_ID = 44

    /** May auto-pair run now? Not while the user said "not now" recently, and not after [MAX_ATTEMPTS] failures. */
    fun due(): Boolean = attempts < MAX_ATTEMPTS && System.currentTimeMillis() - lastAttempt > RETRY_MS

    private val codeRe = Regex("""^\d{6}$""")
    private val hostPortRe = Regex("""(\d{1,3}(?:\.\d{1,3}){3}):(\d{2,5})""")

    /** Pair with a known code; host/port discovered over mDNS when not given. */
    fun pairWithCode(ctx: Context, code: String, host: String? = null, port: Int = 0): Boolean {
        val (h, p) = if (host != null && port > 0) host to port else discoverPairingPort(ctx) ?: ("127.0.0.1" to port)
        if (p <= 0) { lastResult = "pairing port unknown"; CmdLog.add("pair: $lastResult"); return false }
        return try {
            val ok = AdbManager.get(ctx).pairWith(h, p, code.trim())
            lastResult = if (ok) "paired" else "pairing rejected"
            CmdLog.add("pair $h:$p -> $lastResult")
            if (ok) AdbManager.get(ctx).ensureConnected(ctx)
            ok
        } catch (e: Throwable) {
            lastResult = "pair failed: ${e.javaClass.simpleName}: ${e.message}"
            CmdLog.add(lastResult); false
        }
    }

    /** Fully automatic pairing via the accessibility service. Returns true when paired. */
    fun auto(ctx: Context): Boolean {
        if (!busy.compareAndSet(false, true)) return false
        lastAttempt = System.currentTimeMillis()
        val nm = ctx.getSystemService(NotificationManager::class.java)
        try {
            val a11y = A11yService.instance ?: run { lastResult = "accessibility service off"; CmdLog.add("autopair: $lastResult"); return false }
            // Warn first: it is about to take over the screen, and the user may be using the phone.
            if (!warnAndWait(ctx, nm)) {
                if (decision == "manual") {
                    // The user does it: open Developer options for them and ask for the code the moment the pairing dialog is up.
                    lastResult = "manual pairing chosen"; CmdLog.add("autopair: $lastResult"); attempts = MAX_ATTEMPTS
                    com.qcenaman.adbsaver.adb.Pairing.startWatching(ctx)
                    openWirelessDebugging(ctx)
                    nm.notify(NOTIF_ID, Notification.Builder(ctx, App.CH_PAIR).setSmallIcon(R.drawable.ic_engine)
                        .setContentTitle("Pair by hand").setContentText("Wireless debugging → Pair device with pairing code; a notification will ask for the code.").setAutoCancel(true).build())
                    return false
                }
                lastResult = "postponed by user"; CmdLog.add("autopair: $lastResult (retry in ${RETRY_MS / 60_000} min)"); return false
            }
            attempts++
            nm.notify(NOTIF_ID, Notification.Builder(ctx, App.CH_PAIR).setSmallIcon(R.drawable.ic_engine)
                .setContentTitle("AdbSaver is pairing — hands off the phone").setContentText("About 15 seconds; it goes back home when done.")
                .setOngoing(true).build())
            CmdLog.add("autopair: opening wireless debugging (attempt $attempts/$MAX_ATTEMPTS)")
            openWirelessDebugging(ctx)
            // Make sure the toggle is on, then open the pairing-code dialog.
            if (!waitFor(a11y, 4000) { labels -> labels.any { it.contains("pairing code", true) } }) {
                // Landed on Developer options: find the row (a tree search, not a visual one) and open its page.
                if (a11y.scrollToFind("Wireless debugging", maxPages = 12)) { Thread.sleep(400); a11y.clickFound("Wireless debugging") }
                else { lastResult = "Wireless debugging not found in Developer options"; CmdLog.add("autopair: $lastResult"); goHome(a11y); return false }
                waitFor(a11y, 5000) { labels -> labels.any { it.contains("pairing code", true) } }
            }
            val opened = listOf("Pair device with pairing code", "Pair with pairing code", "pairing code").any { a11y.clickFound(it) }
            if (!opened) {
                lastResult = "pairing option not found on screen"; CmdLog.add("autopair: $lastResult"); goHome(a11y); return false
            }
            var code = ""; var host: String? = null; var port = 0
            waitFor(a11y, 6000) { labels ->
                code = labels.firstOrNull { codeRe.matches(it.trim()) }?.trim().orEmpty()
                labels.firstNotNullOfOrNull { hostPortRe.find(it) }?.let { host = it.groupValues[1]; port = it.groupValues[2].toInt() }
                code.isNotEmpty()
            }
            if (code.isEmpty()) { lastResult = "no pairing code on screen"; CmdLog.add("autopair: $lastResult"); goHome(a11y); return false }
            CmdLog.add("autopair: code read, port ${port.takeIf { it > 0 } ?: "mdns"}")
            val ok = pairWithCode(ctx, code, host, port)
            goHome(a11y)
            if (ok) attempts = 0
            return ok
        } finally { nm.cancel(NOTIF_ID); busy.set(false) }
    }

    /** Heads-up with a countdown; "Pair now" goes at once, "Not now" postpones. True when it may proceed. */
    private fun warnAndWait(ctx: Context, nm: NotificationManager): Boolean {
        decision = null
        fun action(label: String, act: String) = Notification.Action.Builder(null, label,
            PendingIntent.getBroadcast(ctx, act.hashCode(), Intent(ctx, ActionReceiver::class.java).setAction(act), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)).build()
        val end = System.currentTimeMillis() + WARN_MS
        while (System.currentTimeMillis() < end && decision == null) {
            val left = ((end - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
            nm.notify(NOTIF_ID, Notification.Builder(ctx, App.CH_PAIR).setSmallIcon(R.drawable.ic_engine)
                .setContentTitle("AdbSaver will pair itself in ${left}s")
                .setContentText("It will open Settings and tap Wireless debugging. Put the phone down, or choose Manually / Not now.")
                .setOnlyAlertOnce(true).addAction(action("Pair now", ActionReceiver.AUTOPAIR_NOW)).addAction(action("Manually", ActionReceiver.AUTOPAIR_MANUAL)).addAction(action("Not now", ActionReceiver.AUTOPAIR_SKIP)).build())
            Thread.sleep(1000)
        }
        nm.cancel(NOTIF_ID)
        return decision == null || decision == "now"
    }

    /** Samsung routes the WirelessDebugging activity to Settings home, so always go via Developer options. */
    private fun openWirelessDebugging(ctx: Context) {
        ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        Thread.sleep(1500)
    }

    private fun waitFor(a11y: A11yService, ms: Long, cond: (List<String>) -> Boolean): Boolean {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) {
            if (cond(a11y.elements().map { it.label })) return true
            Thread.sleep(300)
        }
        return false
    }

    private fun goHome(a11y: A11yService) {
        a11y.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
        Thread.sleep(300)
        a11y.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
    }

    /** The pairing mDNS service is only advertised while the dialog is open. */
    private fun discoverPairingPort(ctx: Context, timeoutMs: Long = 5000): Pair<String, Int>? {
        val latch = CountDownLatch(1)
        var found: Pair<String, Int>? = null
        val mdns = AdbMdns(ctx, AdbMdns.SERVICE_TYPE_TLS_PAIRING) { addr: InetAddress?, port: Int ->
            if (port > 0) { found = (addr?.hostAddress ?: "127.0.0.1") to port; latch.countDown() }
        }
        mdns.start()
        latch.await(timeoutMs, TimeUnit.MILLISECONDS)
        mdns.stop()
        return found
    }
}

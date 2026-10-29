package com.qcenaman.adbsaver.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import com.qcenaman.adbsaver.CmdLog
import com.qcenaman.adbsaver.Prefs
import com.qcenaman.adbsaver.R
import com.qcenaman.adbsaver.net.HttpServer
import com.qcenaman.adbsaver.net.LinkKeeper

/** Foreground service that keeps the engine reachable: persistent notification, wake lock, HTTP server, link keeper. */
class EngineService : Service() {
    private var http: HttpServer? = null
    private var wake: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Prefs.killed) { stopSelf(); return START_NOT_STICKY }
        startForeground(NOTIF_ID, buildNotification())
        ensureAccessibilityEnabled()
        if (Prefs.stayAwake && wake == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AdbSaver:engine").apply {
                setReferenceCounted(false); acquire()
            }
        }
        if (http == null && !Prefs.paused) {
            http = HttpServer(this, Prefs.port).also { try { it.start(); CmdLog.add("http server on :${Prefs.port}") } catch (e: Exception) { CmdLog.add("http start failed: ${e.message}") } }
            LinkKeeper.start(this)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        http?.stop(); http = null
        LinkKeeper.stop()
        wake?.let { if (it.isHeld) it.release() }; wake = null
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Relaunch ourselves if the OS tries to drop us when the task is swiped away.
        if (!Prefs.killed) {
            val restart = Intent(applicationContext, EngineService::class.java)
            val pi = PendingIntent.getService(this, 1, restart, PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE)
            (getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager)
                .set(android.app.AlarmManager.RTC, System.currentTimeMillis() + 1000, pi)
        }
        super.onTaskRemoved(rootIntent)
    }

    /** Updates switch our accessibility service off; with WRITE_SECURE_SETTINGS we switch it back on ourselves. */
    private fun ensureAccessibilityEnabled() {
        try {
            val me = "$packageName/${A11yService::class.java.name}"
            val cr = contentResolver
            val cur = android.provider.Settings.Secure.getString(cr, "enabled_accessibility_services") ?: ""
            if (cur.split(':').none { it.equals(me, true) }) {
                android.provider.Settings.Secure.putString(cr, "enabled_accessibility_services", if (cur.isBlank()) me else "$cur:$me")
                android.provider.Settings.Secure.putInt(cr, "accessibility_enabled", 1)
                CmdLog.add("accessibility service re-enabled")
            }
        } catch (e: Exception) { CmdLog.add("cannot enable accessibility myself: ${e.message}") }
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CH, "AdbSaver engine", NotificationManager.IMPORTANCE_LOW).apply {
                    setShowBadge(false); description = "Keeps the phone engine reachable."
                })
        }
        fun action(label: String, what: String): Notification.Action {
            val pi = PendingIntent.getBroadcast(this, what.hashCode(),
                Intent(this, ActionReceiver::class.java).setAction(what),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            return Notification.Action.Builder(null, label, pi).build()
        }
        val state = when { Prefs.paused -> "Paused"; else -> "Running · :${Prefs.port}" }
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CH) else @Suppress("DEPRECATION") Notification.Builder(this)
        return b.setContentTitle("AdbSaver")
            .setContentText(state)
            .setSmallIcon(R.drawable.ic_engine)
            .setOngoing(true)
            .addAction(action(if (Prefs.paused) "Resume" else "Pause", ActionReceiver.PAUSE))
            .addAction(action("Kill", ActionReceiver.KILL))
            .build()
    }

    companion object {
        const val CH = "engine"; const val NOTIF_ID = 42
        fun start(c: Context) {
            if (Prefs.killed) return
            val i = Intent(c, EngineService::class.java)
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i) else c.startService(i)
        }
        fun refresh(c: Context) {
            val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            // Rebuild the notification to reflect pause state.
            start(c)
        }
    }
}

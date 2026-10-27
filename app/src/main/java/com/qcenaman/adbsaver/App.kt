package com.qcenaman.adbsaver

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import org.conscrypt.Conscrypt
import java.security.Security

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Security.insertProviderAt(Conscrypt.newProvider(), 1)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_ENGINE, "Engine", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_PAIR, "Pairing", NotificationManager.IMPORTANCE_HIGH))
        nm.createNotificationChannel(NotificationChannel(CH_ALERT, "Alerts", NotificationManager.IMPORTANCE_DEFAULT))
        Log.init(this)
    }

    companion object {
        const val CH_ENGINE = "engine"
        const val CH_PAIR = "pair"
        const val CH_ALERT = "alert"
    }
}

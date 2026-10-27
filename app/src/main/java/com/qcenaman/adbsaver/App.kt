package com.qcenaman.adbsaver

import android.app.Application
import org.conscrypt.Conscrypt
import java.security.Security

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Security.insertProviderAt(Conscrypt.newProvider(), 1)
        instance = this
        Prefs.init(this)
        CmdLog.init(this)
    }

    companion object {
        lateinit var instance: App
            private set
    }
}

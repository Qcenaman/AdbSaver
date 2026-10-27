package com.qcenaman.adbsaver

import android.app.Application
import android.content.Context
import org.conscrypt.Conscrypt
import java.security.Security

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        ctx = this
        Security.insertProviderAt(Conscrypt.newProvider(), 1)
        Prefs.init(this)
        CmdLog.init(this)
    }

    companion object {
        lateinit var ctx: Context
            private set
    }
}

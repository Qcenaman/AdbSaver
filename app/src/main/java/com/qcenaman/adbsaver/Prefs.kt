package com.qcenaman.adbsaver

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom

object Prefs {
    private lateinit var sp: SharedPreferences

    fun init(ctx: Context) {
        val key = MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        sp = EncryptedSharedPreferences.create(
            ctx, "adbsaver_secure", key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        if (sp.getString("token", null) == null) sp.edit().putString("token", newToken()).apply()
    }

    private fun newToken(): String {
        val b = ByteArray(24); SecureRandom().nextBytes(b)
        return b.joinToString("") { "%02x".format(it) }
    }

    val token: String get() = sp.getString("token", "")!!
    fun rotateToken() = sp.edit().putString("token", newToken()).apply()

    var port: Int
        get() = sp.getInt("port", 8765)
        set(v) = sp.edit().putInt("port", v).apply()
    var paused: Boolean
        get() = sp.getBoolean("paused", false)
        set(v) = sp.edit().putBoolean("paused", v).apply()
    var killed: Boolean
        get() = sp.getBoolean("killed", false)
        set(v) = sp.edit().putBoolean("killed", v).apply()
    var paired: Boolean
        get() = sp.getBoolean("paired", false)
        set(v) = sp.edit().putBoolean("paired", v).apply()
    var lastAdbPort: Int
        get() = sp.getInt("adb_port", 0)
        set(v) = sp.edit().putInt("adb_port", v).apply()

    fun toolEnabled(name: String) = sp.getBoolean("tool_$name", true)
    fun setToolEnabled(name: String, on: Boolean) = sp.edit().putBoolean("tool_$name", on).apply()
}

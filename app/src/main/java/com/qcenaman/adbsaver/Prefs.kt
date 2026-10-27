package com.qcenaman.adbsaver

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom

object Prefs {
    private lateinit var sp: SharedPreferences

    fun init(c: Context) {
        sp = try {
            val key = MasterKey.Builder(c).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
            EncryptedSharedPreferences.create(
                c, "secure", key,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            c.getSharedPreferences("secure_fallback", Context.MODE_PRIVATE)
        }
        if (token.isEmpty()) token = newToken()
    }

    fun newToken(): String {
        val b = ByteArray(24); SecureRandom().nextBytes(b)
        return b.joinToString("") { "%02x".format(it) }
    }

    var token: String
        get() = sp.getString("token", "") ?: ""
        set(v) = sp.edit().putString("token", v).apply()
    var port: Int
        get() = sp.getInt("port", 8765)
        set(v) = sp.edit().putInt("port", v).apply()
    var paused: Boolean
        get() = sp.getBoolean("paused", false)
        set(v) = sp.edit().putBoolean("paused", v).apply()
    /** Kill switch: when true nothing starts until the user re-enables it in the app. */
    var killed: Boolean
        get() = sp.getBoolean("killed", false)
        set(v) = sp.edit().putBoolean("killed", v).apply()
    var stayAwake: Boolean
        get() = sp.getBoolean("stay_awake", true)
        set(v) = sp.edit().putBoolean("stay_awake", v).apply()
    var hardened: Int
        get() = sp.getInt("hardened", 0)
        set(v) = sp.edit().putInt("hardened", v).apply()

    fun toolEnabled(name: String) = sp.getBoolean("tool_$name", true)
    fun setToolEnabled(name: String, on: Boolean) = sp.edit().putBoolean("tool_$name", on).apply()
}

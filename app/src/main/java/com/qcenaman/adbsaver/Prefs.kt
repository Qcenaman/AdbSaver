package com.qcenaman.adbsaver

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom

object Prefs {
    @Volatile private var sp: SharedPreferences? = null

    fun get(ctx: Context): SharedPreferences = sp ?: synchronized(this) {
        sp ?: EncryptedSharedPreferences.create(
            ctx.applicationContext, "secure",
            MasterKey.Builder(ctx.applicationContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        ).also { sp = it }
    }

    fun token(ctx: Context): String {
        val p = get(ctx)
        p.getString("token", null)?.let { return it }
        val b = ByteArray(24).also { SecureRandom().nextBytes(it) }
        val t = b.joinToString("") { "%02x".format(it) }
        p.edit().putString("token", t).apply()
        return t
    }

    fun paused(ctx: Context) = get(ctx).getBoolean("paused", false)
    fun setPaused(ctx: Context, v: Boolean) = get(ctx).edit().putBoolean("paused", v).apply()
    fun killed(ctx: Context) = get(ctx).getBoolean("killed", false)
    fun setKilled(ctx: Context, v: Boolean) = get(ctx).edit().putBoolean("killed", v).apply()
    fun port(ctx: Context) = get(ctx).getInt("port", 8765)
    fun toolEnabled(ctx: Context, name: String) = get(ctx).getBoolean("tool_$name", true)
    fun setToolEnabled(ctx: Context, name: String, v: Boolean) = get(ctx).edit().putBoolean("tool_$name", v).apply()
}

package com.asterplay.tv.store

import android.content.Context
import androidx.core.content.edit

/**
 * Credenciais Xtream Codes persistidas por SharedPreferences.
 * O painel devolve host+username+password já parseado.
 */
data class XtreamCreds(val host: String, val username: String, val password: String) {
    fun playerApi(action: String? = null, extra: String = ""): String {
        val base = "$host/player_api.php?username=$username&password=$password"
        val a = if (action != null) "&action=$action" else ""
        return "$base$a$extra"
    }
}

object XtreamStore {
    private const val PREFS = "asterplay_xtream"
    private const val KEY_HOST = "host"
    private const val KEY_USER = "user"
    private const val KEY_PASS = "pass"

    fun save(ctx: Context, creds: XtreamCreds) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(KEY_HOST, creds.host)
            putString(KEY_USER, creds.username)
            putString(KEY_PASS, creds.password)
        }
    }

    fun get(ctx: Context): XtreamCreds? {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val h = p.getString(KEY_HOST, null) ?: return null
        val u = p.getString(KEY_USER, null) ?: return null
        val pw = p.getString(KEY_PASS, null) ?: return null
        return XtreamCreds(h, u, pw)
    }

    fun clear(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { clear() }
    }
}

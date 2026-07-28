package com.asterplay.tv.store

import android.content.Context
import androidx.core.content.edit

/**
 * Guarda COMO o usuário entrou, pra revalidar sozinho no próximo boot:
 * - MAC  -> ativação por MAC/Chave no painel
 * - CODE -> código + usuário + senha no painel
 * - XTREAM -> credenciais Xtream (stream codes) já salvas em [XtreamStore]
 */
enum class LoginMethod { MAC, CODE, XTREAM }

data class CodeLogin(val code: String, val user: String, val pass: String)

object LoginStore {
    private const val PREFS = "asterplay_login"
    private const val KEY_METHOD = "method"
    private const val KEY_CODE = "code"
    private const val KEY_USER = "user"
    private const val KEY_PASS = "pass"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun saveMac(ctx: Context) = prefs(ctx).edit {
        putString(KEY_METHOD, LoginMethod.MAC.name)
        remove(KEY_CODE); remove(KEY_USER); remove(KEY_PASS)
    }

    fun saveCode(ctx: Context, code: String, user: String, pass: String) = prefs(ctx).edit {
        putString(KEY_METHOD, LoginMethod.CODE.name)
        putString(KEY_CODE, code)
        putString(KEY_USER, user)
        putString(KEY_PASS, pass)
    }

    fun saveXtream(ctx: Context) = prefs(ctx).edit {
        putString(KEY_METHOD, LoginMethod.XTREAM.name)
        remove(KEY_CODE); remove(KEY_USER); remove(KEY_PASS)
    }

    fun method(ctx: Context): LoginMethod? =
        prefs(ctx).getString(KEY_METHOD, null)?.let {
            runCatching { LoginMethod.valueOf(it) }.getOrNull()
        }

    fun codeLogin(ctx: Context): CodeLogin? {
        val p = prefs(ctx)
        val c = p.getString(KEY_CODE, null) ?: return null
        val u = p.getString(KEY_USER, null) ?: return null
        val pw = p.getString(KEY_PASS, null) ?: return null
        return CodeLogin(c, u, pw)
    }

    fun clear(ctx: Context) = prefs(ctx).edit { clear() }
}

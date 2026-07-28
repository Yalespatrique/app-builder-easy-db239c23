package com.asterplay.tv.store

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Cache leve (SharedPreferences) para o Top 10 do Home.
 * Guarda por conta (host|user) + tipo (movie/series), com TTL.
 */
object TopCacheStore {
    private const val PREF = "asterplay_top_cache"
    // Bump this whenever the Top 10 matching rule changes.
    // This prevents old wrong matches (ex: Homem-Aranha novo puxando 2002)
    // from staying on screen for 24h after an app update.
    private const val CACHE_VERSION = "v3"
    const val TTL_MS = 24L * 3600 * 1000

    data class Entry(
        val title: String,
        val poster: String?,
        val tmdbId: Long,
        val chName: String,
        val chUrl: String,
        val chLogo: String?,
        val chGroup: String?,
        val chTvg: String?,
    )

    private fun key(account: String, kind: String) = "$CACHE_VERSION|$kind|$account"

    fun read(ctx: Context, account: String, kind: String): List<Entry>? {
        val sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val raw = sp.getString(key(account, kind), null) ?: return null
        return try {
            val obj = JSONObject(raw)
            val exp = obj.optLong("exp", 0L)
            if (exp < System.currentTimeMillis()) return null
            val arr = obj.optJSONArray("items") ?: return null
            val out = ArrayList<Entry>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out += Entry(
                    title = o.optString("title"),
                    poster = o.optString("poster").takeIf { it.isNotBlank() },
                    tmdbId = o.optLong("tmdbId"),
                    chName = o.optString("chName"),
                    chUrl = o.optString("chUrl"),
                    chLogo = o.optString("chLogo").takeIf { it.isNotBlank() },
                    chGroup = o.optString("chGroup").takeIf { it.isNotBlank() },
                    chTvg = o.optString("chTvg").takeIf { it.isNotBlank() },
                )
            }
            out
        } catch (_: Throwable) { null }
    }

    fun write(ctx: Context, account: String, kind: String, items: List<Entry>) {
        val arr = JSONArray()
        for (e in items) {
            arr.put(JSONObject().apply {
                put("title", e.title)
                put("poster", e.poster ?: "")
                put("tmdbId", e.tmdbId)
                put("chName", e.chName)
                put("chUrl", e.chUrl)
                put("chLogo", e.chLogo ?: "")
                put("chGroup", e.chGroup ?: "")
                put("chTvg", e.chTvg ?: "")
            })
        }
        val obj = JSONObject().apply {
            put("exp", System.currentTimeMillis() + TTL_MS)
            put("items", arr)
        }
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString(key(account, kind), obj.toString()).apply()
    }

    fun clear(ctx: Context) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().clear().apply()
    }
}

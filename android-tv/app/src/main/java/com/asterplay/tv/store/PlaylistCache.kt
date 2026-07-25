package com.asterplay.tv.store

import android.content.Context
import com.asterplay.tv.net.Channel
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object PlaylistCache {
    private const val FILE = "playlist_cache.json"
    private const val META = "playlist_cache_meta"
    private const val KEY_URL = "url"
    private const val KEY_TS = "ts"

    private fun file(ctx: Context) = File(ctx.filesDir, FILE)

    fun save(ctx: Context, url: String, channels: List<Channel>) {
        val arr = JSONArray()
        channels.forEach { c ->
            arr.put(JSONObject().apply {
                put("name", c.name)
                put("url", c.url)
                put("logo", c.logo ?: "")
                put("group", c.group ?: "")
                put("tvgId", c.tvgId ?: "")
            })
        }
        file(ctx).writeText(arr.toString())
        ctx.getSharedPreferences(META, Context.MODE_PRIVATE).edit()
            .putString(KEY_URL, url)
            .putLong(KEY_TS, System.currentTimeMillis())
            .apply()
    }

    fun load(ctx: Context, url: String, maxAgeMs: Long = 6 * 60 * 60 * 1000L): List<Channel>? {
        val prefs = ctx.getSharedPreferences(META, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_URL, null) != url) return null
        if (System.currentTimeMillis() - prefs.getLong(KEY_TS, 0L) > maxAgeMs) return null
        val f = file(ctx); if (!f.exists()) return null
        return try {
            val arr = JSONArray(f.readText())
            val out = ArrayList<Channel>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out += Channel(
                    name = o.optString("name"),
                    url = o.optString("url"),
                    logo = o.optString("logo").ifEmpty { null },
                    group = o.optString("group").ifEmpty { null },
                    tvgId = o.optString("tvgId").ifEmpty { null }
                )
            }
            out
        } catch (_: Exception) { null }
    }

    fun clear(ctx: Context) {
        file(ctx).delete()
        ctx.getSharedPreferences(META, Context.MODE_PRIVATE).edit().clear().apply()
    }
}

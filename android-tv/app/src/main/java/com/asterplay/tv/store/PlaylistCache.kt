package com.asterplay.tv.store

import android.content.Context
import com.asterplay.tv.net.Channel
import org.json.JSONObject
import java.io.File

object PlaylistCache {
    private const val FILE = "playlist_cache.jsonl"
    private const val LEGACY_FILE = "playlist_cache.json"
    private const val META = "playlist_cache_meta"
    private const val KEY_URL = "url"
    private const val KEY_TS = "ts"
    private const val KEY_COUNT = "count"

    private fun file(ctx: Context) = File(ctx.filesDir, FILE)
    private fun legacyFile(ctx: Context) = File(ctx.filesDir, LEGACY_FILE)

    fun save(ctx: Context, url: String, channels: List<Channel>) {
        file(ctx).bufferedWriter().use { writer ->
            channels.forEach { c ->
                writer.write(
                    JSONObject().apply {
                        put("name", c.name)
                        put("url", c.url)
                        put("logo", c.logo ?: "")
                        put("group", c.group ?: "")
                        put("tvgId", c.tvgId ?: "")
                    }.toString()
                )
                writer.newLine()
            }
        }
        legacyFile(ctx).delete()
        ctx.getSharedPreferences(META, Context.MODE_PRIVATE).edit()
            .putString(KEY_URL, url)
            .putLong(KEY_TS, System.currentTimeMillis())
            .putInt(KEY_COUNT, channels.size)
            .apply()
    }

    fun has(ctx: Context, url: String): Boolean {
        val prefs = ctx.getSharedPreferences(META, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_URL, null) != url) return false
        val current = file(ctx)
        if (prefs.getInt(KEY_COUNT, 0) > 0 && current.exists()) return true
        return legacyFile(ctx).exists()
    }

    fun count(ctx: Context, url: String): Int {
        val prefs = ctx.getSharedPreferences(META, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_URL, null) != url) return 0
        val savedCount = prefs.getInt(KEY_COUNT, 0)
        if (savedCount > 0) return savedCount
        return if (legacyFile(ctx).exists()) 1 else 0
    }

    fun load(ctx: Context, url: String, maxAgeMs: Long = Long.MAX_VALUE): List<Channel>? {
        val prefs = ctx.getSharedPreferences(META, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_URL, null) != url) return null
        val age = System.currentTimeMillis() - prefs.getLong(KEY_TS, 0L)
        if (maxAgeMs != Long.MAX_VALUE && age > maxAgeMs) return null

        val current = file(ctx)
        if (current.exists()) {
            return try {
                val out = ArrayList<Channel>()
                current.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        if (line.isNotBlank()) out += channelFromJson(JSONObject(line))
                    }
                }
                out
            } catch (_: Exception) {
                null
            }
        }

        val legacy = legacyFile(ctx)
        if (!legacy.exists()) return null
        return try {
            val arr = org.json.JSONArray(legacy.readText())
            val out = ArrayList<Channel>(arr.length())
            for (i in 0 until arr.length()) out += channelFromJson(arr.getJSONObject(i))
            out
        } catch (_: Exception) {
            null
        }
    }

    private fun channelFromJson(o: JSONObject) = Channel(
        name = o.optString("name"),
        url = o.optString("url"),
        logo = o.optString("logo").ifEmpty { null },
        group = o.optString("group").ifEmpty { null },
        tvgId = o.optString("tvgId").ifEmpty { null }
    )

    fun clear(ctx: Context) {
        file(ctx).delete()
        legacyFile(ctx).delete()
        ctx.getSharedPreferences(META, Context.MODE_PRIVATE).edit().clear().apply()
    }
}

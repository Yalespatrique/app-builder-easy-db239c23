package com.asterplay.tv.store

import android.content.Context
import com.asterplay.tv.net.Channel
import com.asterplay.tv.net.M3UParser

/**
 * Cache backed by SQLite via [ChannelDb]. Keeps the previous API so the
 * rest of the app doesn't need to change.
 */
object PlaylistCache {

    fun saveFromM3uLines(
        ctx: Context,
        url: String,
        lines: Sequence<String>,
        onProgress: ((Int) -> Unit)? = null
    ): Int {
        val db = ChannelDb.get(ctx)
        return db.replaceAll(url, M3UParser.parseSequence(lines), onProgress)
    }

    fun has(ctx: Context, url: String): Boolean {
        val db = ChannelDb.get(ctx)
        return db.currentUrl() == url && db.currentCount() > 0
    }

    fun count(ctx: Context, url: String): Int {
        val db = ChannelDb.get(ctx)
        return if (db.currentUrl() == url) db.currentCount() else 0
    }

    /** Kept for compatibility; returns all channels for [url]. Prefer [byType]/[groups]. */
    fun load(ctx: Context, url: String, maxAgeMs: Long = Long.MAX_VALUE): List<Channel>? {
        val db = ChannelDb.get(ctx)
        if (db.currentUrl() != url) return null
        val ts = db.getMeta("ts")?.toLongOrNull() ?: 0L
        if (maxAgeMs != Long.MAX_VALUE && System.currentTimeMillis() - ts > maxAgeMs) return null
        val out = ArrayList<Channel>(db.currentCount())
        listOf("live", "vod", "series").forEach { t ->
            db.groupsByType(t).forEach { (g, _) ->
                out += db.channelsByGroup(t, g, limit = Int.MAX_VALUE)
            }
        }
        return out
    }

    fun groups(ctx: Context, type: String): List<Pair<String, Int>> =
        ChannelDb.get(ctx).groupsByType(type)

    fun byGroup(ctx: Context, type: String, group: String): List<Channel> =
        ChannelDb.get(ctx).channelsByGroup(type, group)

    fun search(ctx: Context, query: String): List<Channel> =
        ChannelDb.get(ctx).search(query)

    fun clear(ctx: Context) {
        ChannelDb.get(ctx).clearAll()
    }
}

package com.asterplay.tv.store

import android.content.Context
import com.asterplay.tv.net.Channel
import com.asterplay.tv.net.M3UParser
import java.io.File

/**
 * Cache backed by SQLite via [ChannelDb] + a raw M3U file on disk.
 *
 * Live channels are stored fully in SQLite for instant access. VOD/Series
 * categories are stored as counts only; the actual entries are streamed
 * on-demand from the cached raw playlist file when the user opens a category.
 */
object PlaylistCache {

    private const val FILE_NAME = "playlist.m3u"

    fun sourceFile(ctx: Context): File = File(ctx.filesDir, FILE_NAME)

    fun saveFromM3uLines(
        ctx: Context,
        url: String,
        lines: Sequence<String>,
        onProgress: ((Int) -> Unit)? = null
    ): Int {
        val db = ChannelDb.get(ctx)
        val file = sourceFile(ctx)
        return db.replaceAll(
            url,
            if (file.exists()) file.absolutePath else null,
            M3UParser.parseSequence(lines),
            onProgress
        )
    }

    fun has(ctx: Context, url: String): Boolean {
        val db = ChannelDb.get(ctx)
        return db.currentUrl() == url && db.currentCount() > 0
    }

    fun count(ctx: Context, url: String): Int {
        val db = ChannelDb.get(ctx)
        return if (db.currentUrl() == url) db.currentCount() else 0
    }

    fun groups(ctx: Context, type: String): List<Pair<String, Int>> =
        ChannelDb.get(ctx).groupsByType(type)

    fun byGroup(ctx: Context, type: String, group: String): List<Channel> {
        if (type == "live") return ChannelDb.get(ctx).channelsByGroup(type, group)
        // vod/series: stream cached file and filter matching group + type
        val file = sourceFile(ctx)
        if (!file.exists()) return emptyList()
        val out = ArrayList<Channel>()
        file.bufferedReader(Charsets.UTF_8).useLines { lines ->
            for (c in M3UParser.parseSequence(lines)) {
                if (ChannelDb.classify(c) != type) continue
                val g = c.group?.trim().takeUnless { it.isNullOrEmpty() } ?: "Outros"
                if (g.equals(group, ignoreCase = false)) out += c
                if (out.size >= 2000) break
            }
        }
        return out
    }

    fun search(ctx: Context, query: String): List<Channel> =
        ChannelDb.get(ctx).search(query)

    fun clear(ctx: Context) {
        ChannelDb.get(ctx).clearAll()
        sourceFile(ctx).delete()
    }
}

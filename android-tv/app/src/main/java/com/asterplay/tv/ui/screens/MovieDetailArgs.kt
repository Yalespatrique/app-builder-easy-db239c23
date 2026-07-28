package com.asterplay.tv.ui.screens

import com.asterplay.tv.net.Channel

/**
 * Holder in-memory pra passar o item entre HomeScreen/BrowseScreen e MovieDetailScreen
 * sem precisar serializar Channel em query params.
 */
object MovieDetailArgs {
    data class Pending(
        val channel: Channel,
        val tmdbId: Long? = null,
        val kind: String = "movie", // "movie" ou "tv"
    )

    @Volatile
    var pending: Pending? = null

    fun set(channel: Channel, tmdbId: Long? = null, kind: String = "movie") {
        pending = Pending(channel, tmdbId, kind)
    }

    fun consume(): Pending? {
        val p = pending
        pending = null
        return p
    }
}

/** Extrai o stream_id do URL do Xtream: {host}/movie/{u}/{p}/{stream_id}.{ext} */
fun Channel.xtreamStreamId(): String? {
    val last = url.substringAfterLast('/', "")
    if (last.isBlank()) return null
    return last.substringBeforeLast('.', last).ifBlank { null }
}

/** Extrai o series_id do pseudo-URL asterplay://series/{id}. */
fun Channel.xtreamSeriesId(): String? {
    if (!url.startsWith("asterplay://series/")) return null
    return url.removePrefix("asterplay://series/").ifBlank { null }
}

/**
 * Holder in-memory pra passar uma série entre HomeScreen/BrowseScreen e SeriesDetailScreen.
 */
object SeriesDetailArgs {
    data class Pending(val channel: Channel, val tmdbId: Long? = null)

    @Volatile
    var pending: Pending? = null

    fun set(channel: Channel, tmdbId: Long? = null) { pending = Pending(channel, tmdbId) }
    fun consume(): Pending? {
        val p = pending
        pending = null
        return p
    }
}

package com.asterplay.tv.net

import android.content.Context
import com.asterplay.tv.store.CacheDb
import com.asterplay.tv.store.TopCacheStore
import com.asterplay.tv.store.XtreamStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.text.Normalizer

/**
 * Pré-carrega o Top 10 do Home (TMDB × catálogo do servidor) e salva em cache.
 * Chamado no LoadingScreen para o menu já abrir populado.
 */
object TopHomePreload {

    suspend fun run(ctx: Context) {
        val creds = XtreamStore.get(ctx) ?: return
        val account = CacheDb.accountKey(creds.host, creds.username)
        val cachedM = TopCacheStore.read(ctx, account, "movie")
        val cachedS = TopCacheStore.read(ctx, account, "series")
        val cachedRecentM = TopCacheStore.read(ctx, account, "recent_movie")
        val cachedRecentS = TopCacheStore.read(ctx, account, "recent_series")
        if (!cachedM.isNullOrEmpty() && !cachedS.isNullOrEmpty() &&
            !cachedRecentM.isNullOrEmpty() && !cachedRecentS.isNullOrEmpty()
        ) return

        val (tmdbM, tmdbS, vodPair, seriesPair) = withContext(Dispatchers.IO) {
            coroutineScope {
                val a = async { TmdbApi.topMovies(25) }
                val b = async { TmdbApi.topSeries(25) }
                val c = async { XtreamApi.streamsAndRecent(creds, "vod", 20) }
                val d = async { XtreamApi.streamsAndRecent(creds, "series", 20) }
                HomePayload(a.await(), b.await(), c.await(), d.await())
            }
        }
        val srvM = vodPair.first
        val srvS = seriesPair.first
        val recentM = vodPair.second
        val recentS = seriesPair.second

        val movies = match(tmdbM, srvM, 10)
        val series = match(tmdbS, srvS, 10)
        if (movies.isNotEmpty()) TopCacheStore.write(ctx, account, "movie", movies)
        if (series.isNotEmpty()) TopCacheStore.write(ctx, account, "series", series)
        if (recentM.isNotEmpty()) TopCacheStore.write(ctx, account, "recent_movie", recentM.map { it.toCacheEntry() })
        if (recentS.isNotEmpty()) TopCacheStore.write(ctx, account, "recent_series", recentS.map { it.toCacheEntry() })
    }

    private data class HomePayload(
        val a: List<TmdbApi.Item>, val b: List<TmdbApi.Item>,
        val c: Pair<List<Channel>, List<Channel>>,
        val d: Pair<List<Channel>, List<Channel>>,
    )

    private fun Channel.toCacheEntry() = TopCacheStore.Entry(
        title = name,
        poster = logo,
        tmdbId = 0L,
        chName = name,
        chUrl = url,
        chLogo = logo,
        chGroup = group,
        chTvg = tvgId,
    )

    private val STRIP_TAGS = Regex("\\[[^\\]]*]|\\([^)]*\\)|\\bs\\d{1,2}e\\d{1,3}\\b|\\bs\\d{1,2}\\b|\\b\\d{4}\\b|\\b(4k|fhd|hd|sd|hevc|dub|dublado|leg|legendado|br|nac|multi|imax|remux)\\b")

    private fun norm(s: String): String {
        val stripped = s.lowercase().replace(STRIP_TAGS, " ")
        val n = Normalizer.normalize(stripped, Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        return n.replace(Regex("[^a-z0-9]+"), " ").trim()
    }

    private fun match(tmdb: List<TmdbApi.Item>, server: List<Channel>, limit: Int): List<TopCacheStore.Entry> {
        if (tmdb.isEmpty() || server.isEmpty()) return emptyList()
        data class IdxEntry(val key: String, val ch: Channel)
        val index = ArrayList<IdxEntry>(server.size)
        val seen = HashSet<String>()
        for (c in server) {
            val k = norm(c.name)
            if (k.isNotEmpty() && seen.add(k)) index += IdxEntry(k, c)
        }
        val out = ArrayList<TopCacheStore.Entry>(limit)
        val usedUrls = HashSet<String>()
        for (t in tmdb) {
            if (out.size >= limit) break
            val tk = norm(t.title); if (tk.isEmpty()) continue
            var hit: Channel? = index.firstOrNull { it.key == tk }?.ch
            if (hit == null) hit = index.firstOrNull { it.key.startsWith("$tk ") || it.key.endsWith(" $tk") }?.ch
            if (hit == null) hit = index.firstOrNull { it.key.contains(" $tk ") }?.ch
            if (hit == null && tk.length >= 4) hit = index.firstOrNull { it.key.contains(tk) || tk.contains(it.key) }?.ch
            if (hit != null && usedUrls.add(hit.url)) out += TopCacheStore.Entry(
                title = t.title, poster = t.poster, tmdbId = t.tmdbId,
                chName = hit.name, chUrl = hit.url, chLogo = hit.logo,
                chGroup = hit.group, chTvg = hit.tvgId,
            )
        }
        return out
    }
}

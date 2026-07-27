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
        if (!cachedM.isNullOrEmpty() && !cachedS.isNullOrEmpty()) return

        val (tmdbM, tmdbS, srvM, srvS) = withContext(Dispatchers.IO) {
            coroutineScope {
                val a = async { TmdbApi.topMovies(25) }
                val b = async { TmdbApi.topSeries(25) }
                val c = async { XtreamApi.allStreams(creds, "vod") }
                val d = async { XtreamApi.allStreams(creds, "series") }
                val r = listOf(a, b, c, d).awaitAll()
                @Suppress("UNCHECKED_CAST")
                Quad(
                    r[0] as List<TmdbApi.Item>,
                    r[1] as List<TmdbApi.Item>,
                    r[2] as List<Channel>,
                    r[3] as List<Channel>,
                )
            }
        }

        val movies = match(tmdbM, srvM, 10)
        val series = match(tmdbS, srvS, 10)
        if (movies.isNotEmpty()) TopCacheStore.write(ctx, account, "movie", movies)
        if (series.isNotEmpty()) TopCacheStore.write(ctx, account, "series", series)
    }

    private data class Quad(
        val a: List<TmdbApi.Item>, val b: List<TmdbApi.Item>,
        val c: List<Channel>, val d: List<Channel>,
    )

    private fun norm(s: String): String {
        val n = Normalizer.normalize(s, Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "").lowercase()
        return n.replace(Regex("[^a-z0-9]+"), " ").trim()
    }

    private fun match(tmdb: List<TmdbApi.Item>, server: List<Channel>, limit: Int): List<TopCacheStore.Entry> {
        if (tmdb.isEmpty() || server.isEmpty()) return emptyList()
        val index = HashMap<String, Channel>(server.size)
        for (c in server) {
            val k = norm(c.name)
            if (k.isNotEmpty() && !index.containsKey(k)) index[k] = c
        }
        val out = ArrayList<TopCacheStore.Entry>(limit)
        for (t in tmdb) {
            if (out.size >= limit) break
            val tk = norm(t.title); if (tk.isEmpty()) continue
            var hit = index[tk]
            if (hit == null) {
                for ((k, ch) in index) {
                    if (k.contains(tk) || tk.contains(k)) { hit = ch; break }
                }
            }
            if (hit != null) out += TopCacheStore.Entry(
                title = t.title, poster = t.poster, tmdbId = t.tmdbId,
                chName = hit.name, chUrl = hit.url, chLogo = hit.logo,
                chGroup = hit.group, chTvg = hit.tvgId,
            )
        }
        return out
    }
}

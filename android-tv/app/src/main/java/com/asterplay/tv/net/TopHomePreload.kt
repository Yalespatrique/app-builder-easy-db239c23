package com.asterplay.tv.net

import android.content.Context
import com.asterplay.tv.store.CacheDb
import com.asterplay.tv.store.TopCacheStore
import com.asterplay.tv.store.XtreamStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async

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

    private val BRACKETED = Regex("""\(([^)]*)\)|\[([^]]*)]""")
    private val EPISODE_TAGS = Regex("""\bs\d{1,2}e\d{1,3}\b|\bs\d{1,2}\b""", RegexOption.IGNORE_CASE)
    private val YEAR = Regex("""\b(19|20)\d{2}\b""")
    private val NOISE_WORDS = setOf(
        "4k", "uhd", "fhd", "hd", "sd", "hevc", "h265", "x265", "x264",
        "dub", "dublado", "dual", "audio", "leg", "legendado", "br", "nac",
        "multi", "imax", "remux", "bluray", "webdl", "web", "dl", "hdtv",
        "cam", "rip", "1080p", "720p", "2160p",
    )

    private fun deaccent(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFD)
        .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")

    private fun isNoiseBlock(s: String): Boolean {
        val tokens = deaccent(s.lowercase()).replace(Regex("[^a-z0-9]+"), " ")
            .trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
        return tokens.isNotEmpty() && tokens.all { it in NOISE_WORDS || YEAR.matches(it) }
    }

    private fun norm(s: String): String {
        // Normalização segura para Top 10:
        // - remove só ruído técnico/ano de release;
        // - mantém subtítulos dentro de parênteses/colchetes quando são parte do nome;
        // - evita falso positivo tipo "Homem-Aranha: Um Novo Dia" casar com "Homem-Aranha (2002)".
        val bracketHandled = BRACKETED.replace(s.lowercase()) { m ->
            val inside = (m.groups[1]?.value ?: m.groups[2]?.value ?: "").trim()
            if (isNoiseBlock(inside)) " " else " $inside "
        }
        val noTechTags = bracketHandled
            .replace(EPISODE_TAGS, " ")
            .replace(YEAR, " ")
        val n = deaccent(noTechTags)
        return n.replace(Regex("[^a-z0-9]+"), " ").trim()
    }

    private fun yearOf(s: String): String? = YEAR.find(s)?.value

    private fun match(tmdb: List<TmdbApi.Item>, server: List<Channel>, limit: Int): List<TopCacheStore.Entry> {
        if (tmdb.isEmpty() || server.isEmpty()) return emptyList()
        data class IdxEntry(val key: String, val year: String?, val ch: Channel)
        val index = ArrayList<IdxEntry>(server.size)
        for (c in server) {
            val k = norm(c.name)
            if (k.isNotEmpty()) index += IdxEntry(k, yearOf(c.name), c)
        }
        val out = ArrayList<TopCacheStore.Entry>(limit)
        val usedUrls = HashSet<String>()
        for (t in tmdb) {
            if (out.size >= limit) break
            val keys = listOfNotNull(norm(t.title), t.originalTitle?.let { norm(it) })
                .filter { it.isNotEmpty() }
                .distinct()
            if (keys.isEmpty()) continue
            val year = t.year
            // Match somente por título completo normalizado.
            // Se o item do servidor tiver ano no nome, ele precisa bater com o ano do TMDB.
            val hit = index.firstOrNull { e ->
                e.key in keys && (year == null || e.year == null || e.year == year)
            }?.ch
            if (hit != null && usedUrls.add(hit.url)) out += TopCacheStore.Entry(
                title = t.title, poster = t.poster, tmdbId = t.tmdbId,
                chName = hit.name, chUrl = hit.url, chLogo = hit.logo,
                chGroup = hit.group, chTvg = hit.tvgId,
            )
        }
        return out
    }
}

package com.asterplay.tv.net

import com.asterplay.tv.store.XtreamCreds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Item genérico retornado pela Xtream API.
 * Reaproveitamos [Channel] pra manter compatibilidade com PosterCard/Player.
 */
data class XtreamCategory(val id: String, val name: String)

object XtreamApi {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // ---------- Autenticação ----------

    /** Resultado da checagem de conta: distingue falha de rede de conta inválida. */
    enum class AuthResult { OK, INVALID, NETWORK }

    /** Retorna true se o painel Xtream aceitar as credenciais. */
    suspend fun authenticate(c: XtreamCreds): Boolean =
        authenticateDetailed(c) == AuthResult.OK

    /**
     * Faz até 3 tentativas. Só devolve INVALID quando o servidor respondeu
     * dizendo que a conta não vale; timeouts/erros viram NETWORK.
     */
    suspend fun authenticateDetailed(c: XtreamCreds): AuthResult = withContext(Dispatchers.IO) {
        var lastNetwork = true
        repeat(3) { attempt ->
            try {
                val body = get(c.playerApi())
                if (body != null) {
                    val json = JSONObject(body)
                    val userInfo = json.optJSONObject("user_info")
                    if (userInfo != null) {
                        val auth = userInfo.optInt("auth", 0)
                        val status = userInfo.optString("status")
                        val ok = auth == 1 &&
                            (status.isEmpty() || status.equals("Active", ignoreCase = true))
                        return@withContext if (ok) AuthResult.OK else AuthResult.INVALID
                    }
                }
            } catch (_: Exception) {
                lastNetwork = true
            }
            if (attempt < 2) kotlinx.coroutines.delay(1500)
        }
        if (lastNetwork) AuthResult.NETWORK else AuthResult.INVALID
    }

    /** Dados da conta no painel Xtream (validade da lista). */
    data class AccountInfo(
        val status: String,
        /** epoch millis; 0 = sem data (ilimitado). */
        val expiryMillis: Long,
        val isTrial: Boolean,
    )

    suspend fun accountInfo(c: XtreamCreds): AccountInfo? = withContext(Dispatchers.IO) {
        try {
            val body = get(c.playerApi()) ?: return@withContext null
            val info = JSONObject(body).optJSONObject("user_info") ?: return@withContext null
            val exp = info.optString("exp_date").trim()
            val millis = exp.toLongOrNull()?.let { it * 1000L } ?: 0L
            AccountInfo(
                status = info.optString("status"),
                expiryMillis = millis,
                isTrial = info.optString("is_trial") == "1",
            )
        } catch (_: Exception) { null }
    }




    // ---------- Categorias ----------

    suspend fun categories(c: XtreamCreds, kind: String): List<XtreamCategory> = withContext(Dispatchers.IO) {
        val action = when (kind) {
            "live" -> "get_live_categories"
            "vod" -> "get_vod_categories"
            "series" -> "get_series_categories"
            else -> return@withContext emptyList()
        }
        val body = get(c.playerApi(action)) ?: return@withContext emptyList()
        parseCategories(body)
    }

    private fun parseCategories(body: String): List<XtreamCategory> {
        val out = ArrayList<XtreamCategory>()
        try {
            val arr = JSONArray(body)
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optString("category_id")
                val name = o.optString("category_name")
                if (id.isNotEmpty() && name.isNotEmpty()) out += XtreamCategory(id, name)
            }
        } catch (_: Exception) {}
        return out
    }

    // ---------- Streams ----------

    suspend fun streams(c: XtreamCreds, kind: String, categoryId: String): List<Channel> = withContext(Dispatchers.IO) {
        val (action, path) = when (kind) {
            "live" -> "get_live_streams" to "live"
            "vod" -> "get_vod_streams" to "movie"
            "series" -> "get_series" to "series"
            else -> return@withContext emptyList()
        }
        val body = get(c.playerApi(action, "&category_id=$categoryId")) ?: return@withContext emptyList()
        val out = ArrayList<Channel>()
        try {
            val arr = JSONArray(body)
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val name = o.optString("name").ifEmpty { o.optString("title") }
                val logo = o.optString("stream_icon").ifEmpty { o.optString("cover") }
                val streamId = o.optString("stream_id").ifEmpty { o.optString("series_id") }
                if (name.isEmpty() || streamId.isEmpty()) continue
                val ext = o.optString("container_extension").ifEmpty { "ts" }
                val url = when (path) {
                    "live" -> "${c.host}/live/${c.username}/${c.password}/$streamId.ts"
                    "movie" -> "${c.host}/movie/${c.username}/${c.password}/$streamId.$ext"
                    "series" -> "asterplay://series/$streamId"  // resolve depois
                    else -> ""
                }
                out += Channel(
                    name = name,
                    url = url,
                    logo = logo.ifEmpty { null },
                    group = categoryId,
                    tvgId = o.optString("epg_channel_id").ifEmpty { null },
                )
            }
        } catch (_: Exception) {}
        return@withContext out
    }

    /** Retorna todos os itens de VOD ou séries (sem filtro de categoria). Usado pra montar índice de títulos. */
    suspend fun allStreams(c: XtreamCreds, kind: String): List<Channel> = withContext(Dispatchers.IO) {
        val (action, path) = when (kind) {
            "vod" -> "get_vod_streams" to "movie"
            "series" -> "get_series" to "series"
            "live" -> "get_live_streams" to "live"
            else -> return@withContext emptyList()
        }
        val body = get(c.playerApi(action)) ?: return@withContext emptyList()
        val out = ArrayList<Channel>()
        try {
            val arr = JSONArray(body)
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val name = o.optString("name").ifEmpty { o.optString("title") }
                val streamId = o.optString("stream_id").ifEmpty { o.optString("series_id") }
                if (name.isEmpty() || streamId.isEmpty()) continue
                val logo = o.optString("stream_icon").ifEmpty { o.optString("cover") }
                val ext = o.optString("container_extension").ifEmpty { "ts" }
                val url = when (path) {
                    "live" -> "${c.host}/live/${c.username}/${c.password}/$streamId.ts"
                    "movie" -> "${c.host}/movie/${c.username}/${c.password}/$streamId.$ext"
                    "series" -> "asterplay://series/$streamId"
                    else -> ""
                }
                out += Channel(
                    name = name, url = url,
                    logo = logo.ifEmpty { null },
                    group = o.optString("category_id").ifEmpty { null },
                    tvgId = o.optString("epg_channel_id").ifEmpty { null },
                )

            }
        } catch (_: Exception) {}
        return@withContext out
    }

    /** Retorna os itens mais recentes (VOD ou séries) ordenados por data adicionada desc. */
    suspend fun recentStreams(c: XtreamCreds, kind: String, limit: Int = 20): List<Channel> =
        streamsAndRecent(c, kind, limit).second

    /**
     * Faz UMA request e devolve (todos, recentes-ordenados-por-data).
     * Evita baixar o mesmo JSON gigante 2x quando o Home precisa de ambos.
     */
    suspend fun streamsAndRecent(
        c: XtreamCreds,
        kind: String,
        recentLimit: Int = 20,
    ): Pair<List<Channel>, List<Channel>> = withContext(Dispatchers.IO) {
        val (action, path) = when (kind) {
            "vod" -> "get_vod_streams" to "movie"
            "series" -> "get_series" to "series"
            else -> return@withContext emptyList<Channel>() to emptyList()
        }
        val body = get(c.playerApi(action)) ?: return@withContext emptyList<Channel>() to emptyList()
        val all = ArrayList<Channel>()
        data class Row(val ts: Long, val idx: Int)
        val timestamps = ArrayList<Row>()
        try {
            val arr = JSONArray(body)
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val name = o.optString("name").ifEmpty { o.optString("title") }
                val streamId = o.optString("stream_id").ifEmpty { o.optString("series_id") }
                if (name.isEmpty() || streamId.isEmpty()) continue
                val logo = o.optString("stream_icon").ifEmpty { o.optString("cover") }
                val ext = o.optString("container_extension").ifEmpty { "ts" }
                val url = when (path) {
                    "movie" -> "${c.host}/movie/${c.username}/${c.password}/$streamId.$ext"
                    "series" -> "asterplay://series/$streamId"
                    else -> ""
                }
                val ts = o.optString("added").toLongOrNull()
                    ?: o.optString("last_modified").toLongOrNull()
                    ?: 0L
                timestamps += Row(ts, all.size)
                all += Channel(
                    name = name, url = url,
                    logo = logo.ifEmpty { null },
                    group = o.optString("category_id").ifEmpty { null },
                    tvgId = null,
                )
            }
        } catch (_: Exception) {}
        timestamps.sortByDescending { it.ts }
        val recent = timestamps.take(recentLimit).map { all[it.idx] }
        all to recent
    }

    // ---------- Detalhes VOD (preview antes de reproduzir) ----------

    data class VodInfo(
        val plot: String? = null,
        val cast: String? = null,
        val director: String? = null,
        val genre: String? = null,
        val rating: String? = null,
        val duration: String? = null,
        val releaseDate: String? = null,
        val backdrop: String? = null,
        val cover: String? = null,
        val tmdbId: Long? = null,
        val youtubeTrailer: String? = null,
    )

    suspend fun vodInfo(c: XtreamCreds, streamId: String): VodInfo? = withContext(Dispatchers.IO) {
        val body = get(c.playerApi("get_vod_info", "&vod_id=$streamId")) ?: return@withContext null
        try {
            val root = JSONObject(body)
            val info = root.optJSONObject("info") ?: root.optJSONObject("movie_data") ?: return@withContext null
            val backdrops = info.optJSONArray("backdrop_path")
            val backdrop = when {
                backdrops != null && backdrops.length() > 0 -> backdrops.optString(0).ifBlank { null }
                else -> info.optString("backdrop_path").ifBlank { null }
            }
            VodInfo(
                plot = info.optString("plot").ifBlank { info.optString("description") }.ifBlank { null },
                cast = info.optString("cast").ifBlank { info.optString("actors") }.ifBlank { null },
                director = info.optString("director").ifBlank { null },
                genre = info.optString("genre").ifBlank { null },
                rating = info.optString("rating").ifBlank { info.optString("rating_5based") }.ifBlank { null },
                duration = info.optString("duration").ifBlank { info.optString("episode_run_time") }.ifBlank { null },
                releaseDate = info.optString("releasedate").ifBlank { info.optString("release_date") }.ifBlank { null },
                backdrop = backdrop,
                cover = info.optString("movie_image").ifBlank { info.optString("cover_big") }.ifBlank { null },
                tmdbId = info.optString("tmdb_id").toLongOrNull()?.takeIf { it > 0 },
                youtubeTrailer = info.optString("youtube_trailer").ifBlank { null },
            )
        } catch (_: Exception) { null }
    }

    // ---------- Séries (episódios) ----------

    data class Episode(
        val id: String,
        val title: String,
        val season: Int,
        val episodeNum: Int,
        val ext: String,
        val url: String,
        val plot: String? = null,
        val still: String? = null,
        val duration: String? = null,
    )
    data class SeriesInfo(val seasons: List<Int>, val episodes: Map<Int, List<Episode>>)

    data class SeriesMeta(
        val plot: String? = null,
        val cast: String? = null,
        val director: String? = null,
        val genre: String? = null,
        val rating: String? = null,
        val releaseDate: String? = null,
        val backdrop: String? = null,
        val cover: String? = null,
        val tmdbId: Long? = null,
    )

    data class SeriesFull(val meta: SeriesMeta, val info: SeriesInfo)

    suspend fun seriesFullInfo(c: XtreamCreds, seriesId: String): SeriesFull? = withContext(Dispatchers.IO) {
        val body = get(c.playerApi("get_series_info", "&series_id=$seriesId")) ?: return@withContext null
        try {
            val root = JSONObject(body)
            val info = root.optJSONObject("info") ?: JSONObject()
            val backdrops = info.optJSONArray("backdrop_path")
            val backdrop = when {
                backdrops != null && backdrops.length() > 0 -> backdrops.optString(0).ifBlank { null }
                else -> info.optString("backdrop_path").ifBlank { null }
            }
            val meta = SeriesMeta(
                plot = info.optString("plot").ifBlank { info.optString("description") }.ifBlank { null },
                cast = info.optString("cast").ifBlank { info.optString("actors") }.ifBlank { null },
                director = info.optString("director").ifBlank { null },
                genre = info.optString("genre").ifBlank { null },
                rating = info.optString("rating").ifBlank { info.optString("rating_5based") }.ifBlank { null },
                releaseDate = info.optString("releaseDate").ifBlank { info.optString("release_date") }.ifBlank { null },
                backdrop = backdrop,
                cover = info.optString("cover").ifBlank { info.optString("cover_big") }.ifBlank { null },
                tmdbId = info.optString("tmdb_id").toLongOrNull()?.takeIf { it > 0 },
            )
            val eps = root.optJSONObject("episodes")
            val bySeason = HashMap<Int, MutableList<Episode>>()
            if (eps != null) {
                val keys = eps.keys()
                while (keys.hasNext()) {
                    val seasonKey = keys.next()
                    val season = seasonKey.toIntOrNull() ?: continue
                    val arr = eps.optJSONArray(seasonKey) ?: continue
                    val list = ArrayList<Episode>()
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        val id = o.optString("id")
                        val epNum = o.optInt("episode_num")
                        val title = o.optString("title").ifEmpty { "Ep $epNum" }
                        val ext = o.optString("container_extension").ifEmpty { "mp4" }
                        if (id.isEmpty()) continue
                        val url = "${c.host}/series/${c.username}/${c.password}/$id.$ext"
                        val epInfo = o.optJSONObject("info")
                        list += Episode(
                            id = id,
                            title = title,
                            season = season,
                            episodeNum = epNum,
                            ext = ext,
                            url = url,
                            plot = epInfo?.optString("plot")?.ifBlank { null },
                            still = epInfo?.optString("movie_image")?.ifBlank { null },
                            duration = epInfo?.optString("duration")?.ifBlank { null },
                        )
                    }
                    list.sortBy { it.episodeNum }
                    bySeason[season] = list
                }
            }
            SeriesFull(meta, SeriesInfo(bySeason.keys.sorted(), bySeason))
        } catch (_: Exception) { null }
    }

    suspend fun seriesInfo(c: XtreamCreds, seriesId: String): SeriesInfo? = withContext(Dispatchers.IO) {
        val body = get(c.playerApi("get_series_info", "&series_id=$seriesId")) ?: return@withContext null
        try {
            val root = JSONObject(body)
            val eps = root.optJSONObject("episodes") ?: return@withContext null
            val bySeason = HashMap<Int, MutableList<Episode>>()
            val keys = eps.keys()
            while (keys.hasNext()) {
                val seasonKey = keys.next()
                val season = seasonKey.toIntOrNull() ?: continue
                val arr = eps.optJSONArray(seasonKey) ?: continue
                val list = ArrayList<Episode>()
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val id = o.optString("id")
                    val title = o.optString("title").ifEmpty { "Ep ${o.optInt("episode_num")}" }
                    val ext = o.optString("container_extension").ifEmpty { "mp4" }
                    if (id.isEmpty()) continue
                    val url = "${c.host}/series/${c.username}/${c.password}/$id.$ext"
                    list += Episode(id = id, title = title, season = season, episodeNum = o.optInt("episode_num"), ext = ext, url = url)
                }
                bySeason[season] = list
            }
            SeriesInfo(bySeason.keys.sorted(), bySeason)
        } catch (_: Exception) {
            null
        }
    }

    // ---------- EPG (canais ao vivo) ----------

    data class EpgItem(val start: Long, val end: Long, val title: String, val description: String?)

    /** Retorna a programação curta (agora + próximos) de um canal live. */
    suspend fun shortEpg(c: XtreamCreds, streamId: String, limit: Int = 8): List<EpgItem> = withContext(Dispatchers.IO) {
        val body = get(c.playerApi("get_short_epg", "&stream_id=$streamId&limit=$limit")) ?: return@withContext emptyList()
        val out = ArrayList<EpgItem>()
        try {
            val root = JSONObject(body)
            val arr = root.optJSONArray("epg_listings") ?: return@withContext emptyList()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val title = decodeB64(o.optString("title"))
                val desc = decodeB64(o.optString("description")).ifBlank { null }
                val start = o.optString("start_timestamp").toLongOrNull()?.times(1000) ?: 0L
                val end = o.optString("stop_timestamp").toLongOrNull()?.times(1000) ?: 0L
                if (title.isBlank() || start == 0L) continue
                out += EpgItem(start, end, title, desc)
            }
        } catch (_: Exception) {}
        out
    }

    private fun decodeB64(s: String): String = try {
        if (s.isBlank()) "" else String(android.util.Base64.decode(s, android.util.Base64.DEFAULT), Charsets.UTF_8)
    } catch (_: Exception) { s }

    // ---------- HTTP ----------


    private fun get(url: String): String? {
        return try {
            val req = Request.Builder().url(url).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                resp.body?.string()
            }
        } catch (_: Exception) {
            null
        }
    }
}

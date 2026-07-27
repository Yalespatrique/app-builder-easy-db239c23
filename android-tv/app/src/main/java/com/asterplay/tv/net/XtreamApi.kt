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

    /** Retorna true se o painel Xtream aceitar as credenciais. */
    suspend fun authenticate(c: XtreamCreds): Boolean = withContext(Dispatchers.IO) {
        try {
            val body = get(c.playerApi()) ?: return@withContext false
            val json = JSONObject(body)
            val userInfo = json.optJSONObject("user_info") ?: return@withContext false
            val auth = userInfo.optInt("auth", 0)
            val status = userInfo.optString("status")
            auth == 1 && (status.isEmpty() || status.equals("Active", ignoreCase = true))
        } catch (_: Exception) {
            false
        }
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
                    tvgId = null,
                )
            }
        } catch (_: Exception) {}
        return@withContext out
    }

    // ---------- Séries (episódios) ----------

    data class Episode(val id: String, val title: String, val season: Int, val ext: String, val url: String)
    data class SeriesInfo(val seasons: List<Int>, val episodes: Map<Int, List<Episode>>)

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
                    list += Episode(id, title, season, ext, url)
                }
                bySeason[season] = list
            }
            SeriesInfo(bySeason.keys.sorted(), bySeason)
        } catch (_: Exception) {
            null
        }
    }

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

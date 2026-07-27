package com.asterplay.tv.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Cliente mínimo do TMDB (v3). Usa a mesma chave do app Roku original.
 * Endpoints: /trending/movie/week e /trending/tv/week, em pt-BR.
 */
object TmdbApi {
    private const val API_KEY = "ac1b11deda294d510c6a286647825f84"
    private const val BASE = "https://api.themoviedb.org/3"
    private const val IMG = "https://image.tmdb.org/t/p/w342"
    private const val BACKDROP = "https://image.tmdb.org/t/p/w1280"

    data class Item(val title: String, val poster: String?, val tmdbId: Long)

    data class Details(
        val overview: String?,
        val backdrop: String?,
        val poster: String?,
        val voteAverage: Double,
        val runtime: Int?,
        val releaseDate: String?,
        val genres: List<String>,
        val cast: List<String>,
    )

    suspend fun topMovies(limit: Int = 10): List<Item> = fetch("$BASE/trending/movie/week", "title", limit)
    suspend fun topSeries(limit: Int = 10): List<Item> = fetch("$BASE/trending/tv/week", "name", limit)

    suspend fun details(tmdbId: Long, kind: String = "movie"): Details? = withContext(Dispatchers.IO) {
        val url = URL("$BASE/$kind/$tmdbId?api_key=$API_KEY&language=pt-BR&append_to_response=credits")
        val con = url.openConnection() as HttpURLConnection
        con.connectTimeout = 8000; con.readTimeout = 8000
        try {
            if (con.responseCode !in 200..299) return@withContext null
            val body = con.inputStream.bufferedReader().use { it.readText() }
            val o = JSONObject(body)
            val backdrop = o.optString("backdrop_path", "").takeIf { it.isNotBlank() }?.let { "$BACKDROP$it" }
            val poster = o.optString("poster_path", "").takeIf { it.isNotBlank() }?.let { "$IMG$it" }
            val genres = o.optJSONArray("genres")?.let { arr ->
                List(arr.length()) { arr.optJSONObject(it)?.optString("name").orEmpty() }.filter { it.isNotBlank() }
            }.orEmpty()
            val cast = o.optJSONObject("credits")?.optJSONArray("cast")?.let { arr ->
                List(minOf(arr.length(), 8)) { arr.optJSONObject(it)?.optString("name").orEmpty() }.filter { it.isNotBlank() }
            }.orEmpty()
            val runtime = o.optInt("runtime", 0).takeIf { it > 0 }
                ?: o.optJSONArray("episode_run_time")?.let { if (it.length() > 0) it.optInt(0).takeIf { r -> r > 0 } else null }
            Details(
                overview = o.optString("overview").ifBlank { null },
                backdrop = backdrop,
                poster = poster,
                voteAverage = o.optDouble("vote_average", 0.0),
                runtime = runtime,
                releaseDate = o.optString("release_date").ifBlank { o.optString("first_air_date") }.ifBlank { null },
                genres = genres,
                cast = cast,
            )
        } catch (_: Throwable) { null } finally { con.disconnect() }
    }


    private suspend fun fetch(base: String, nameField: String, limit: Int): List<Item> =
        withContext(Dispatchers.IO) {
            val url = URL("$base?api_key=$API_KEY&language=pt-BR&region=BR")
            val con = url.openConnection() as HttpURLConnection
            con.connectTimeout = 8000; con.readTimeout = 8000
            try {
                if (con.responseCode !in 200..299) return@withContext emptyList()
                val body = con.inputStream.bufferedReader().use { it.readText() }
                val results = JSONObject(body).optJSONArray("results") ?: return@withContext emptyList()
                val out = ArrayList<Item>(limit)
                var i = 0
                while (i < results.length() && out.size < limit) {
                    val o = results.optJSONObject(i); i++
                    if (o == null) continue
                    val name = o.optString(nameField).ifBlank { o.optString("title") }
                    if (name.isBlank()) continue
                    val poster = o.optString("poster_path", "").takeIf { it.isNotBlank() }?.let { "$IMG$it" }
                    out += Item(title = name, poster = poster, tmdbId = o.optLong("id"))
                }
                out
            } catch (_: Throwable) { emptyList() } finally { con.disconnect() }
        }
}

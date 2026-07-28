package com.asterplay.tv.net

import com.asterplay.tv.store.XtreamCreds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class PanelResult(
    val ok: Boolean,
    val playlistUrl: String?,
    val xtream: XtreamCreds?,
    val message: String?,
)

object PanelApi {
    private val client get() = Net.client

    private const val BASE = "https://apkasterplay.lovable.app"

    private fun enc(v: String) = URLEncoder.encode(v, "UTF-8")

    suspend fun activateWithMac(mac: String, key: String): PanelResult = withContext(Dispatchers.IO) {
        request("$BASE/api/public/activate?mac=${enc(mac)}&key=${enc(key)}")
    }

    suspend fun activateWithCode(code: String, user: String, pass: String): PanelResult = withContext(Dispatchers.IO) {
        request("$BASE/api/public/code-login?code=${enc(code)}&user=${enc(user)}&pass=${enc(pass)}")
    }

    /**
     * Verifica se a DNS (host) do usuário está cadastrada no painel admin.
     * Retorna null quando não deu pra consultar (erro de rede) — nesse caso
     * o app não deve punir o usuário.
     */
    suspend fun isDnsRegistered(host: String): Boolean? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url("$BASE/api/public/dns-check?host=${enc(host)}").build()
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                val json = try { JSONObject(body) } catch (_: Exception) { return@use null }
                if (!resp.isSuccessful || !json.optBoolean("ok", false)) null
                else json.optBoolean("registered", false)
            }
        } catch (_: Exception) { null }
    }



    private fun request(url: String): PanelResult {
        return try {
            val req = Request.Builder().url(url).build()
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                val json = try { JSONObject(body) } catch (_: Exception) { JSONObject() }
                val playlist = json.optString("playlist_url").ifEmpty { json.optString("url") }
                val xt = parseXtream(json, playlist)
                val ok = resp.isSuccessful && json.optBoolean("ok", playlist.isNotEmpty()) && playlist.isNotEmpty()
                PanelResult(
                    ok = ok,
                    playlistUrl = playlist.ifEmpty { null },
                    xtream = xt,
                    message = json.optString("message").ifEmpty { if (!resp.isSuccessful) "HTTP ${resp.code}" else null },
                )
            }
        } catch (e: Exception) {
            PanelResult(false, null, null, e.message)
        }
    }

    /** Aceita o objeto `xtream` do painel ou parseia da URL (fallback estilo Roku). */
    private fun parseXtream(json: JSONObject, playlist: String): XtreamCreds? {
        json.optJSONObject("xtream")?.let { x ->
            val h = x.optString("host"); val u = x.optString("username"); val p = x.optString("password")
            if (h.isNotEmpty() && u.isNotEmpty() && p.isNotEmpty()) {
                return XtreamCreds(stripTrailing(h), u, p)
            }
        }
        if (playlist.isEmpty()) return null
        return try {
            val q = playlist.indexOf('?')
            if (q <= 0) return null
            val base = playlist.substring(0, q)
            val host = base.substring(0, base.lastIndexOf('/'))
            val query = playlist.substring(q + 1)
            var user = ""; var pass = ""
            for (pair in query.split("&")) {
                val kv = pair.split("=", limit = 2)
                if (kv.size != 2) continue
                when (kv[0]) { "username" -> user = kv[1]; "password" -> pass = kv[1] }
            }
            if (user.isEmpty() || pass.isEmpty()) null
            else XtreamCreds(stripTrailing(host), user, pass)
        } catch (_: Exception) { null }
    }

    private fun stripTrailing(s: String) = s.trimEnd('/')
}

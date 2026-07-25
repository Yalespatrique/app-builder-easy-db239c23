package com.asterplay.tv.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class PanelResult(val ok: Boolean, val playlistUrl: String?, val message: String?)

object PanelApi {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    // Painel de teste hospedado no Lovable Cloud (URL estável do projeto).
    private const val BASE = "https://project--826ec096-5fc1-441d-aae7-3e19857ac979.lovable.app"

    private fun enc(v: String) = URLEncoder.encode(v, "UTF-8")

    suspend fun activateWithMac(mac: String, key: String): PanelResult = withContext(Dispatchers.IO) {
        request("$BASE/api/public/activate?mac=${enc(mac)}&key=${enc(key)}")
    }

    suspend fun activateWithCode(dns: String, user: String, pass: String): PanelResult = withContext(Dispatchers.IO) {
        // O painel resolve DNS+usuário+senha -> playlist_url. `dns` é ignorado se o registro
        // já contiver a URL completa; enviamos apenas code/user/pass.
        request("$BASE/api/public/code-login?code=${enc(dns)}&user=${enc(user)}&pass=${enc(pass)}")
    }

    private fun request(url: String): PanelResult {
        return try {
            val req = Request.Builder().url(url).build()
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                val json = try { JSONObject(body) } catch (_: Exception) { JSONObject() }
                val playlist = json.optString("playlist_url").ifEmpty { json.optString("url") }
                val ok = resp.isSuccessful && json.optBoolean("ok", playlist.isNotEmpty()) && playlist.isNotEmpty()
                PanelResult(ok, playlist.ifEmpty { null }, json.optString("message").ifEmpty { if (!resp.isSuccessful) "HTTP ${resp.code}" else null })
            }
        } catch (e: Exception) {
            PanelResult(false, null, e.message)
        }
    }
}

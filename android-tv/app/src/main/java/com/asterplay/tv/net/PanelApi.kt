package com.asterplay.tv.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class PanelResult(val ok: Boolean, val playlistUrl: String?, val message: String?)

object PanelApi {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private const val BASE = "https://appasterplay.top"

    suspend fun activateWithMac(mac: String, key: String): PanelResult = withContext(Dispatchers.IO) {
        val url = "$BASE/api/activate?mac=$mac&key=$key"
        request(url)
    }

    suspend fun activateWithCode(dns: String, user: String, pass: String): PanelResult = withContext(Dispatchers.IO) {
        val base = if (dns.startsWith("http")) dns else "http://$dns"
        val url = "$base/get.php?username=$user&password=$pass&type=m3u_plus&output=ts"
        // For code login the M3U URL itself is the answer.
        PanelResult(true, url, null)
    }

    private fun request(url: String): PanelResult {
        return try {
            val req = Request.Builder().url(url).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return PanelResult(false, null, "HTTP ${resp.code}")
                val body = resp.body?.string().orEmpty()
                val json = JSONObject(body)
                val playlist = json.optString("playlist_url").ifEmpty { json.optString("url") }
                PanelResult(playlist.isNotEmpty(), playlist.ifEmpty { null }, json.optString("message"))
            }
        } catch (e: Exception) {
            PanelResult(false, null, e.message)
        }
    }
}

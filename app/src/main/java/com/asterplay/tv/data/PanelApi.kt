package com.asterplay.tv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Cliente do painel de ativação — mesmo contrato do Roku
 * (components/tasks/PanelTask.xml). Ordem de fallback:
 *   1) https://appasterplay.top
 *   2) https://painel.appasterplay.top
 *
 * Dois modos:
 *  - fetch(mac, key)                       → polling MAC/Key (fluxo Roku)
 *  - activateWithCode(mac, code, user, pw) → login com código+usuário+senha
 *
 * Resposta esperada (JSON): { ok, m3u_url, status, days_left, message }
 */
data class PanelResponse(
    val ok: Boolean,
    val m3uUrl: String? = null,
    val status: String? = null,
    val daysLeft: String? = null,
    val message: String? = null,
)

object PanelApi {
    private val HOSTS = listOf(
        "https://appasterplay.top",
        "https://painel.appasterplay.top",
    )

    private val JSON = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /** Polling por MAC + Key (o painel decide quando devolver a lista). */
    suspend fun fetch(mac: String, deviceKey: String): PanelResponse = withContext(Dispatchers.IO) {
        val encMac = URLEncoder.encode(mac, "UTF-8")
        val token = (System.currentTimeMillis() / 1000).toString()
        var lastError: String? = null

        for (host in HOSTS) {
            val url = "$host/api/public/playlist?mac=$encMac&key=$deviceKey&_=$token"
            try {
                val req = Request.Builder().url(url).header("Accept", "application/json").build()
                client.newCall(req).execute().use { res ->
                    if (!res.isSuccessful) {
                        lastError = "HTTP ${res.code}"; return@use
                    }
                    return@withContext parseBody(res.body?.string().orEmpty())
                }
            } catch (t: Throwable) {
                lastError = t.message
            }
        }
        PanelResponse(ok = false, message = lastError ?: "Painel indisponível")
    }

    /**
     * Login com código+usuário+senha. O painel resolve o "código" para a DNS
     * real cadastrada no admin e monta a M3U final (Xtream) devolvendo m3u_url.
     */
    suspend fun activateWithCode(
        mac: String,
        code: String,
        username: String,
        password: String,
    ): PanelResponse = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("mac", mac)
            put("code", code)
            put("username", username)
            put("password", password)
        }.toString()

        var lastError: String? = null
        for (host in HOSTS) {
            val url = "$host/api/public/activate-code"
            try {
                val req = Request.Builder()
                    .url(url)
                    .header("Accept", "application/json")
                    .post(payload.toRequestBody(JSON))
                    .build()
                client.newCall(req).execute().use { res ->
                    val body = res.body?.string().orEmpty()
                    if (!res.isSuccessful) {
                        lastError = "HTTP ${res.code}"; return@use
                    }
                    return@withContext parseBody(body)
                }
            } catch (t: Throwable) {
                lastError = t.message
            }
        }
        PanelResponse(ok = false, message = lastError ?: "Painel indisponível")
    }

    private fun parseBody(body: String): PanelResponse {
        val json = JSONObject(body)
        val m3u = json.optString("m3u_url", "")
        val ok = json.optBoolean("ok", m3u.isNotEmpty())
        return PanelResponse(
            ok = ok,
            m3uUrl = m3u.ifEmpty { null },
            status = json.optString("status").ifEmpty { null },
            daysLeft = json.optString("days_left").ifEmpty { null },
            message = json.optString("message").ifEmpty { null },
        )
    }
}

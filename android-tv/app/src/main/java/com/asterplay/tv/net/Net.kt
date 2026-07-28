package com.asterplay.tv.net

import android.content.Context
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress
import java.util.concurrent.TimeUnit

/**
 * Camada de rede compartilhada do app.
 *
 * Muitos provedores de internet bloqueiam listas de IPTV no nível do DNS
 * (o domínio simplesmente "não resolve"). O Android não deixa um app trocar o
 * DNS do sistema, mas dá para resolver os domínios por conta própria usando
 * DNS-over-HTTPS (DoH) — o pedido de resolução viaja dentro de um HTTPS normal,
 * então o provedor não consegue interceptar nem responder por ele.
 *
 * O mesmo [OkHttpClient] é usado pelas APIs (painel, Xtream, TMDB) e pelo
 * player, então a preferência vale para tudo.
 */
object Net {
    const val DNS_SYSTEM = "system"
    const val DNS_GOOGLE = "google"
    const val DNS_CLOUDFLARE = "cloudflare"
    const val DNS_ADGUARD = "adguard"

    @Volatile
    private var mode: String = DNS_SYSTEM

    @Volatile
    private var cached: OkHttpClient? = null

    @Volatile
    private var cachedMode: String? = null

    /** Cliente base (sem DoH) — também serve de bootstrap para o resolvedor DoH. */
    private val base: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /** Chame no início do app para aplicar a preferência salva. */
    fun init(ctx: Context) {
        setMode(com.asterplay.tv.store.SettingsStore.dnsMode(ctx))
    }

    fun mode(): String = mode

    fun setMode(value: String) {
        if (value == mode) return
        mode = value
        cached = null
        cachedMode = null
    }

    /** Cliente HTTP compartilhado, já com o DNS escolhido pelo usuário. */
    val client: OkHttpClient
        get() {
            val m = mode
            cached?.let { if (cachedMode == m) return it }
            val built = if (m == DNS_SYSTEM) base else base.newBuilder().dns(doh(m)).build()
            cached = built
            cachedMode = m
            return built
        }

    private fun doh(m: String): Dns {
        val (url, ips) = when (m) {
            DNS_CLOUDFLARE -> "https://cloudflare-dns.com/dns-query" to
                listOf("1.1.1.1", "1.0.0.1", "2606:4700:4700::1111")
            DNS_ADGUARD -> "https://dns.adguard-dns.com/dns-query" to
                listOf("94.140.14.14", "94.140.15.15")
            else -> "https://dns.google/dns-query" to
                listOf("8.8.8.8", "8.8.4.4", "2001:4860:4860::8888")
        }
        return DnsOverHttps.Builder()
            .client(base)
            .url(url.toHttpUrl())
            .bootstrapDnsHosts(ips.mapNotNull { runCatching { InetAddress.getByName(it) }.getOrNull() })
            .includeIPv6(false)
            .build()
    }
}

/** Fábrica de origem de mídia do ExoPlayer usando o mesmo DNS/HTTP do app. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
fun exoMediaSourceFactory(ctx: Context): androidx.media3.exoplayer.source.MediaSource.Factory {
    val http = androidx.media3.datasource.okhttp.OkHttpDataSource.Factory { req ->
        Net.client.newCall(req)
    }.setUserAgent("Asterplay/${android.os.Build.MODEL}")
    val ds = androidx.media3.datasource.DefaultDataSource.Factory(ctx, http)
    return androidx.media3.exoplayer.source.DefaultMediaSourceFactory(ds)
}

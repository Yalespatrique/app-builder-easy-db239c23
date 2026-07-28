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
    /** Automático: tenta o DNS do provedor e, se ele falhar/bloquear, usa DoH sozinho. */
    const val DNS_AUTO = "auto"
    const val DNS_SYSTEM = "system"
    const val DNS_GOOGLE = "google"
    const val DNS_CLOUDFLARE = "cloudflare"
    const val DNS_ADGUARD = "adguard"

    @Volatile
    private var mode: String = DNS_AUTO

    @Volatile
    private var cached: OkHttpClient? = null

    @Volatile
    private var cachedMode: String? = null

    /** true quando o modo automático precisou cair para o DoH (provedor bloqueando). */
    @Volatile
    var autoFallbackActive: Boolean = false
        private set

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
        autoFallbackActive = false
    }

    /** Cliente HTTP compartilhado, já com o DNS em uso. */
    val client: OkHttpClient
        get() {
            val m = mode
            cached?.let { if (cachedMode == m) return it }
            val dns = when (m) {
                DNS_SYSTEM -> null
                DNS_AUTO -> AutoDns
                else -> doh(m)
            }
            val built = if (dns == null) base else base.newBuilder().dns(dns).build()
            cached = built
            cachedMode = m
            return built
        }

    /**
     * Resolvedor automático: primeiro tenta o DNS do sistema (rápido, sem custo).
     * Se o provedor não resolver o domínio — ou devolver um IP de bloqueio, como
     * loopback/0.0.0.0 — refaz a consulta por DNS-over-HTTPS. Assim o usuário
     * leigo não precisa configurar nada.
     */
    @Volatile
    private var forcedDoh = false

    /**
     * Liga o DNS seguro imediatamente (usado quando um conteúdo demora demais
     * para abrir — sintoma clássico de bloqueio do provedor).
     * Retorna true se algo mudou e vale a pena tentar de novo.
     */
    fun forceSecureDnsNow(): Boolean {
        if (mode != DNS_AUTO || forcedDoh) return false
        forcedDoh = true
        autoFallbackActive = true
        cached = null
        cachedMode = null
        return true
    }

    private object AutoDns : Dns {
        private val fallbacks: List<Dns> by lazy {
            listOf(doh(DNS_CLOUDFLARE), doh(DNS_GOOGLE))
        }

        override fun lookup(hostname: String): List<InetAddress> {
            val system = if (forcedDoh) null else runCatching { Dns.SYSTEM.lookup(hostname) }.getOrNull()
            val ok = system?.filter { it.isUsable() }.orEmpty()
            if (ok.isNotEmpty()) return ok
            for (f in fallbacks) {
                val r = runCatching { f.lookup(hostname) }.getOrNull()
                    ?.filter { it.isUsable() }.orEmpty()
                if (r.isNotEmpty()) {
                    autoFallbackActive = true
                    return r
                }
            }
            return system ?: Dns.SYSTEM.lookup(hostname)
        }

        private fun InetAddress.isUsable(): Boolean =
            !isLoopbackAddress && !isAnyLocalAddress && !isLinkLocalAddress && !isMulticastAddress
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

/**
 * Vigia de abertura de conteúdo.
 *
 * Se o vídeo não começar dentro de [timeoutMs] (ou der erro de rede antes disso),
 * assume bloqueio do provedor, liga o DNS seguro na hora e chama [retry] para
 * recarregar o mesmo conteúdo. Faz isso uma única vez por player.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackStallGuard(
    private val player: androidx.media3.common.Player,
    private val timeoutMs: Long = 9000L,
    private val retry: () -> Unit,
) : androidx.media3.common.Player.Listener {
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var armed = false
    private var used = false

    private val fire = Runnable {
        if (used) return@Runnable
        used = true
        if (Net.forceSecureDnsNow()) retry()
    }

    init {
        player.addListener(this)
    }

    /** Chame logo depois de setMediaItem()/prepare(). */
    fun arm() {
        disarm()
        if (used) return
        armed = true
        handler.postDelayed(fire, timeoutMs)
    }

    fun disarm() {
        armed = false
        handler.removeCallbacks(fire)
    }

    fun release() {
        disarm()
        runCatching { player.removeListener(this) }
    }

    override fun onRenderedFirstFrame() = disarm()

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) disarm()
    }

    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
        if (armed) {
            handler.removeCallbacks(fire)
            handler.post(fire)
        }
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

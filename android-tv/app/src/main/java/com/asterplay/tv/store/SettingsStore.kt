package com.asterplay.tv.store

import android.content.Context

/**
 * Preferências do usuário: controle parental, TMDB e formato de fluxo de vídeo.
 */
object SettingsStore {
    private const val PREF = "asterplay_settings"
    private const val K_PARENTAL = "parental_enabled"
    private const val K_PIN = "parental_pin"
    private const val K_TMDB = "tmdb_enabled"
    private const val K_STREAM = "stream_format"

    const val FORMAT_DEFAULT = "default"
    const val FORMAT_HLS = "hls"
    const val FORMAT_TS = "ts"

    private fun p(ctx: Context) = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    // ---------- Controle parental ----------
    fun parentalEnabled(ctx: Context): Boolean = p(ctx).getBoolean(K_PARENTAL, false)
    fun setParentalEnabled(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean(K_PARENTAL, v).apply()

    fun pin(ctx: Context): String = p(ctx).getString(K_PIN, "0000") ?: "0000"
    fun setPin(ctx: Context, v: String) = p(ctx).edit().putString(K_PIN, v).apply()
    fun checkPin(ctx: Context, v: String): Boolean = v.trim() == pin(ctx)

    private val ADULT_WORDS = listOf(
        "adult", "adulto", "adultos", "xxx", "porn", "porno", "+18", "18+",
        "erotic", "erótic", "erotico", "eróticos", "sexy", "sex", "hot",
        "playboy", "brazzers", "privê", "prive", "swing",
    )

    /** Heurística de detecção de categoria adulta pelo nome. */
    fun isAdultName(name: String?): Boolean {
        val n = (name ?: "").lowercase()
        if (n.isEmpty()) return false
        return ADULT_WORDS.any { n.contains(it) }
    }

    /** true se a categoria deve ser escondida com o controle parental ativo. */
    fun isBlocked(ctx: Context, name: String?): Boolean = parentalEnabled(ctx) && isAdultName(name)

    // ---------- TMDB ----------
    fun tmdbEnabled(ctx: Context): Boolean = p(ctx).getBoolean(K_TMDB, true)
    fun setTmdbEnabled(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean(K_TMDB, v).apply()

    // ---------- DNS (anti-bloqueio de provedor) ----------
    private const val K_DNS = "dns_mode"

    fun dnsMode(ctx: Context): String =
        p(ctx).getString(K_DNS, com.asterplay.tv.net.Net.DNS_SYSTEM) ?: com.asterplay.tv.net.Net.DNS_SYSTEM

    fun setDnsMode(ctx: Context, v: String) {
        p(ctx).edit().putString(K_DNS, v).apply()
        com.asterplay.tv.net.Net.setMode(v)
    }

    fun dnsLabel(mode: String): String = when (mode) {
        com.asterplay.tv.net.Net.DNS_GOOGLE -> "Google (8.8.8.8)"
        com.asterplay.tv.net.Net.DNS_CLOUDFLARE -> "Cloudflare (1.1.1.1)"
        com.asterplay.tv.net.Net.DNS_ADGUARD -> "AdGuard DNS"
        else -> "Padrão do provedor"
    }

    // ---------- Fluxo de vídeo ----------
    fun streamFormat(ctx: Context): String = p(ctx).getString(K_STREAM, FORMAT_DEFAULT) ?: FORMAT_DEFAULT
    fun setStreamFormat(ctx: Context, v: String) = p(ctx).edit().putString(K_STREAM, v).apply()

    fun streamFormatLabel(ctx: Context): String = when (streamFormat(ctx)) {
        FORMAT_HLS -> "HLS (m3u8)"
        FORMAT_TS -> "TS (.ts)"
        else -> "Padrão do provedor"
    }

    /**
     * Ajusta a extensão de um link ao vivo conforme a preferência do usuário.
     * Só afeta streams /live/ (VOD/séries mantêm o container original).
     */
    fun applyFormat(ctx: Context, url: String): String {
        if (url.isEmpty() || !url.contains("/live/")) return url
        return when (streamFormat(ctx)) {
            FORMAT_HLS -> replaceExt(url, "m3u8")
            FORMAT_TS -> replaceExt(url, "ts")
            else -> url
        }
    }

    private fun replaceExt(url: String, ext: String): String {
        val q = url.indexOf('?')
        val base = if (q >= 0) url.substring(0, q) else url
        val tail = if (q >= 0) url.substring(q) else ""
        val dot = base.lastIndexOf('.')
        val slash = base.lastIndexOf('/')
        val newBase = if (dot > slash) base.substring(0, dot) + "." + ext else "$base.$ext"
        return newBase + tail
    }
}

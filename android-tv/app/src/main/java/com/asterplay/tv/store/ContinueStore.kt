package com.asterplay.tv.store

import android.content.Context
import androidx.core.content.edit
import com.asterplay.tv.net.Channel
import org.json.JSONObject

/**
 * "Continuar assistindo": guarda os filmes/séries que o usuário começou a ver.
 * A posição do vídeo continua no ResumeStore — aqui ficam só os metadados do card.
 */
object ContinueStore {
    private const val PREFS = "asterplay_continue"
    private const val MAX = 40

    /** id da categoria virtual usada no BrowseScreen */
    const val CATEGORY_ID = "__continue__"
    const val CATEGORY_NAME = "CONTINUAR ASSISTINDO"

    data class Entry(
        val kind: String,       // "vod" | "series"
        val channel: Channel,   // item aberto no grid (filme ou série)
        val resumeKey: String,  // url usada pelo ResumeStore (filme ou episódio)
        val updatedAt: Long,
    )

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun save(ctx: Context, kind: String, channel: Channel, resumeKey: String) {
        if (channel.url.isBlank()) return
        val json = JSONObject().apply {
            put("kind", kind)
            put("name", channel.name)
            put("url", channel.url)
            put("logo", channel.logo ?: "")
            put("group", channel.group ?: "")
            put("resumeKey", resumeKey)
            put("ts", System.currentTimeMillis())
        }
        prefs(ctx).edit { putString(key(kind, channel.url), json.toString()) }
        trim(ctx)
    }

    fun remove(ctx: Context, kind: String, url: String) =
        prefs(ctx).edit { remove(key(kind, url)) }

    /** Lista mais recentes primeiro. [kind] = "vod" ou "series". */
    fun list(ctx: Context, kind: String): List<Entry> =
        all(ctx).filter { it.kind == kind }
            .filter { !SettingsStore.isBlocked(ctx, it.channel.name) }
            .filter { !SettingsStore.isBlocked(ctx, it.channel.group ?: "") }
            .filter { keep(ctx, it) }

    fun channels(ctx: Context, kind: String): List<Channel> = list(ctx, kind).map { it.channel }

    private fun keep(ctx: Context, e: Entry): Boolean {
        // filme já assistido até o fim sai da fileira; série continua (próximo episódio)
        if (e.kind == "vod" && WatchedStore.isWatched(ctx, e.resumeKey) &&
            ResumeStore.get(ctx, e.resumeKey) <= 0L
        ) return false
        return true
    }

    private fun all(ctx: Context): List<Entry> =
        prefs(ctx).all.values.mapNotNull { raw ->
            val s = raw as? String ?: return@mapNotNull null
            runCatching {
                val o = JSONObject(s)
                Entry(
                    kind = o.optString("kind"),
                    channel = Channel(
                        name = o.optString("name"),
                        url = o.optString("url"),
                        logo = o.optString("logo").ifBlank { null },
                        group = o.optString("group").ifBlank { null },
                    ),
                    resumeKey = o.optString("resumeKey"),
                    updatedAt = o.optLong("ts"),
                )
            }.getOrNull()
        }.sortedByDescending { it.updatedAt }

    private fun key(kind: String, url: String) = "$kind|$url"

    private fun trim(ctx: Context) {
        val entries = all(ctx)
        if (entries.size <= MAX) return
        prefs(ctx).edit {
            entries.drop(MAX).forEach { remove(key(it.kind, it.channel.url)) }
        }
    }
}

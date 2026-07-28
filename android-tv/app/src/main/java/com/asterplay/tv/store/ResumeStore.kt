package com.asterplay.tv.store

import android.content.Context
import androidx.core.content.edit

object ResumeStore {
    private const val PREFS = "asterplay_resume"

    /** Salva a posição. Perto do fim (ou muito no início) limpa, pra não retomar nos créditos. */
    fun save(ctx: Context, url: String, positionMs: Long, durationMs: Long = 0L) {
        if (url.isBlank()) return
        val nearEnd = durationMs > 0 && positionMs >= durationMs - 20_000
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (positionMs < 10_000 || nearEnd) p.edit { remove(url) }
        else p.edit { putLong(url, positionMs) }
    }

    fun clear(ctx: Context, url: String) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { remove(url) }

    fun get(ctx: Context, url: String): Long =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(url, 0L)

    fun recent(ctx: Context, limit: Int = 20): List<String> {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return p.all.entries.sortedByDescending { (it.value as? Long) ?: 0L }
            .take(limit).map { it.key }
    }
}

package com.asterplay.tv.store

import android.content.Context
import androidx.core.content.edit

object ResumeStore {
    private const val PREFS = "asterplay_resume"

    fun save(ctx: Context, url: String, positionMs: Long) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putLong(url, positionMs) }

    fun get(ctx: Context, url: String): Long =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(url, 0L)

    fun recent(ctx: Context, limit: Int = 20): List<String> {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return p.all.entries.sortedByDescending { (it.value as? Long) ?: 0L }
            .take(limit).map { it.key }
    }
}

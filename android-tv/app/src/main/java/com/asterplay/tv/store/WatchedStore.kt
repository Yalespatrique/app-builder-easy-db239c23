package com.asterplay.tv.store

import android.content.Context
import androidx.core.content.edit

/** Marca episódios/filmes já assistidos para o usuário saber o que já viu. */
object WatchedStore {
    private const val PREFS = "asterplay_watched"

    fun mark(ctx: Context, url: String) {
        if (url.isBlank()) return
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit { putLong(url, System.currentTimeMillis()) }
    }

    fun unmark(ctx: Context, url: String) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { remove(url) }

    fun isWatched(ctx: Context, url: String): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).contains(url)

    fun all(ctx: Context): Set<String> =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).all.keys
}

package com.asterplay.tv.store

import android.content.Context
import androidx.core.content.edit

object FavoritesStore {
    private const val PREFS = "asterplay_fav"
    private const val KEY = "urls"

    fun all(ctx: Context): Set<String> =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(KEY, emptySet()) ?: emptySet()

    fun toggle(ctx: Context, url: String): Boolean {
        val cur = all(ctx).toMutableSet()
        val added = if (cur.contains(url)) { cur.remove(url); false } else { cur.add(url); true }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putStringSet(KEY, cur) }
        return added
    }

    fun contains(ctx: Context, url: String) = all(ctx).contains(url)
}

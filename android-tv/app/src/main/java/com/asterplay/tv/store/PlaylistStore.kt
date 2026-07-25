package com.asterplay.tv.store

import android.content.Context
import androidx.core.content.edit

object PlaylistStore {
    private const val PREFS = "asterplay_playlist"
    private const val KEY_URL = "playlist_url"

    fun save(ctx: Context, url: String) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(KEY_URL, url) }

    fun get(ctx: Context): String? =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_URL, null)

    fun clear(ctx: Context) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { remove(KEY_URL) }
}

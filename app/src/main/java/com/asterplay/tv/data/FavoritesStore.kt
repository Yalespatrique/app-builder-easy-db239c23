package com.asterplay.tv.data

import android.content.Context
import androidx.core.content.edit

/**
 * Favoritos por streamId (usa a URL do canal como chave estável — casa com
 * a mesma lógica usada no player e no store de resume).
 */
class FavoritesStore(context: Context) {
    private val prefs = context.getSharedPreferences("asterplay_favorites", Context.MODE_PRIVATE)

    fun all(): Set<String> = prefs.getStringSet(KEY, emptySet())?.toSet() ?: emptySet()

    fun isFavorite(id: String): Boolean = all().contains(id)

    fun toggle(id: String): Boolean {
        val current = all().toMutableSet()
        val added = if (current.contains(id)) {
            current.remove(id); false
        } else {
            current.add(id); true
        }
        prefs.edit { putStringSet(KEY, current) }
        return added
    }

    companion object {
        private const val KEY = "favorites_v1"
    }
}

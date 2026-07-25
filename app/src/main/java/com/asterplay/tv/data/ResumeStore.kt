package com.asterplay.tv.data

import android.content.Context
import androidx.core.content.edit

data class ResumePoint(val positionMs: Long, val durationMs: Long, val updatedAt: Long)

/**
 * Persiste posição/duração por streamId (URL). Usado para a linha
 * "Continuar assistindo" e para reabrir no ponto certo no PlayerActivity.
 */
class ResumeStore(context: Context) {
    private val prefs = context.getSharedPreferences("asterplay_resume", Context.MODE_PRIVATE)

    fun save(id: String, positionMs: Long, durationMs: Long) {
        if (id.isBlank() || positionMs < 10_000L) return
        // se já passou de 95%, remove
        if (durationMs > 0 && positionMs.toFloat() / durationMs.toFloat() > 0.95f) {
            remove(id); return
        }
        prefs.edit {
            putString(id, "$positionMs|$durationMs|${System.currentTimeMillis()}")
        }
    }

    fun get(id: String): ResumePoint? {
        val raw = prefs.getString(id, null) ?: return null
        val parts = raw.split("|")
        if (parts.size < 3) return null
        return ResumePoint(
            positionMs = parts[0].toLongOrNull() ?: return null,
            durationMs = parts[1].toLongOrNull() ?: 0L,
            updatedAt = parts[2].toLongOrNull() ?: 0L,
        )
    }

    fun remove(id: String) = prefs.edit { remove(id) }

    /** Retorna todos os ids salvos, mais recentes primeiro. */
    fun recentIds(limit: Int = 20): List<String> {
        val all = prefs.all
        return all.entries
            .mapNotNull { e ->
                val raw = e.value as? String ?: return@mapNotNull null
                val ts = raw.split("|").getOrNull(2)?.toLongOrNull() ?: 0L
                e.key to ts
            }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }
}

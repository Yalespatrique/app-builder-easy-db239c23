package com.asterplay.tv.data

import android.content.Context
import androidx.core.content.edit

/**
 * Análogo ao RegRead/RegWrite do Roku. Guarda a URL M3U vinculada ao
 * dispositivo, além de status e "days_left" retornados pelo painel.
 */
class AsterStore(context: Context) {
    private val prefs = context.getSharedPreferences("asterplay_state", Context.MODE_PRIVATE)

    var m3uUrl: String
        get() = prefs.getString(KEY_M3U, "") ?: ""
        set(value) = prefs.edit { putString(KEY_M3U, value) }

    var status: String
        get() = prefs.getString(KEY_STATUS, "") ?: ""
        set(value) = prefs.edit { putString(KEY_STATUS, value) }

    var daysLeft: String
        get() = prefs.getString(KEY_DAYS, "") ?: ""
        set(value) = prefs.edit { putString(KEY_DAYS, value) }

    var introSeen: Boolean
        get() = prefs.getBoolean(KEY_INTRO, false)
        set(value) = prefs.edit { putBoolean(KEY_INTRO, value) }

    fun clear() = prefs.edit { clear() }

    companion object {
        private const val KEY_M3U = "m3u_url"
        private const val KEY_STATUS = "status"
        private const val KEY_DAYS = "days_left"
        private const val KEY_INTRO = "intro_seen"
    }
}

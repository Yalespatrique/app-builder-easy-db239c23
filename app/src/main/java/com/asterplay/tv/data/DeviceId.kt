package com.asterplay.tv.data

import android.content.Context
import androidx.core.content.edit

/**
 * Reproduz o mesmo algoritmo do Roku (LoginScene.brs / PanelTask.xml):
 *  - MAC de 12 hex (AE:10:XX:XX:XX:XX) gerado no 1º boot e persistido.
 *  - DeviceKey de 6 dígitos derivada do MAC via hash mod 1_000_000.
 */
object DeviceId {
    private const val PREF = "asterplay_device"
    private const val KEY_MAC = "streamcodes_mac"
    private const val KEY_SEED = "streamcodes_seed"

    fun getMac(context: Context): String {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        prefs.getString(KEY_MAC, null)?.let { if (it.matches(Regex("^[0-9A-F:]{17}$"))) return it }

        val seed = prefs.getString(KEY_SEED, null) ?: run {
            val s = "asterplay-android-${System.currentTimeMillis().toString(36)}-" +
                    (0..7).map { "abcdefghijklmnopqrstuvwxyz0123456789".random() }.joinToString("")
            prefs.edit { putString(KEY_SEED, s) }
            s
        }
        val mac = buildMacFromSeed(seed)
        prefs.edit { putString(KEY_MAC, mac) }
        return mac
    }

    fun deviceKey(mac: String): String {
        val clean = mac.uppercase().replace(Regex("[^0-9A-F]"), "")
        if (clean.length < 6) return "------"
        var hash = 0L
        for (c in clean) {
            val v = "0123456789ABCDEF".indexOf(c)
            hash = (hash * 17 + v) % 1_000_000
        }
        return hash.toString().padStart(6, '0')
    }

    private fun buildMacFromSeed(seed: String): String {
        var h = 0L
        for (c in seed) h = (h * 31 + c.code) % 16_777_215
        if (h < 4096) h += 4096
        val hex = h.toString(16).uppercase().padStart(6, '0').takeLast(6)
        val raw = ("AE10$hex${hex.substring(0, 2)}").take(12).uppercase()
        return raw.chunked(2).joinToString(":")
    }
}

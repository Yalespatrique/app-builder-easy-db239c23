package com.asterplay.tv.core

import android.content.Context
import android.provider.Settings
import java.security.MessageDigest

/**
 * Reproduz o algoritmo do app Roku:
 * - MAC = primeiros 12 hex do SHA-1(ANDROID_ID)
 * - Key = últimos 6 dígitos da soma dos bytes do MAC
 */
object DeviceId {
    fun getMac(ctx: Context): String {
        val androidId = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID) ?: "asterplay"
        val digest = MessageDigest.getInstance("SHA-1").digest(androidId.toByteArray())
        val hex = digest.joinToString("") { "%02x".format(it) }
        return hex.substring(0, 12).uppercase()
    }

    fun getKey(mac: String): String {
        val sum = mac.chunked(2).sumOf { it.toInt(16) }
        return (sum % 1000000).toString().padStart(6, '0')
    }

    fun formatted(mac: String): String =
        mac.chunked(2).joinToString(":")
}

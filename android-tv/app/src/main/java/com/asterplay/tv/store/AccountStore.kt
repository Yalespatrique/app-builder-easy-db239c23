package com.asterplay.tv.store

import android.content.Context
import androidx.core.content.edit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Controla:
 *  - validade da lista do usuário (exp_date vindo do painel Xtream)
 *  - período de teste grátis de 7 dias quando a DNS NÃO está cadastrada no painel
 *
 * As infos de teste ficam num prefs separado que NÃO é limpo no logout,
 * pra ninguém ganhar 7 dias novos só saindo e entrando de novo.
 */
object AccountStore {

    const val TRIAL_DAYS = 7L
    private const val DAY_MS = 24L * 60 * 60 * 1000

    private const val PREFS = "asterplay_account"
    private const val KEY_EXP = "exp_date"          // epoch millis (0 = sem validade/ilimitado)
    private const val KEY_STATUS = "status"

    private const val TRIAL_PREFS = "asterplay_trial"
    private const val KEY_TRIAL_START = "trial_start"
    private const val KEY_DNS_OK = "dns_registered"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun trial(ctx: Context) = ctx.getSharedPreferences(TRIAL_PREFS, Context.MODE_PRIVATE)

    // ---------- validade da lista ----------

    fun saveExpiry(ctx: Context, expiryMillis: Long, status: String?) = prefs(ctx).edit {
        putLong(KEY_EXP, expiryMillis)
        putString(KEY_STATUS, status ?: "")
    }

    /** 0 = sem data (ilimitado / desconhecido). */
    fun expiry(ctx: Context): Long = prefs(ctx).getLong(KEY_EXP, 0L)

    fun status(ctx: Context): String = prefs(ctx).getString(KEY_STATUS, "").orEmpty()

    fun daysLeft(ctx: Context): Long? {
        val exp = expiry(ctx)
        if (exp <= 0L) return null
        val diff = exp - System.currentTimeMillis()
        return if (diff <= 0) 0 else (diff + DAY_MS - 1) / DAY_MS
    }

    fun expiryText(ctx: Context): String {
        val exp = expiry(ctx)
        if (exp <= 0L) return "Lista sem data de expiração"
        val fmt = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
        val d = daysLeft(ctx) ?: return "Lista válida até ${fmt.format(Date(exp))}"
        return when {
            d <= 0L -> "Lista vencida em ${fmt.format(Date(exp))}"
            d == 1L -> "Lista vence amanhã (${fmt.format(Date(exp))})"
            else -> "Lista válida por $d dias — até ${fmt.format(Date(exp))}"
        }
    }

    // ---------- teste grátis (DNS não cadastrada) ----------

    fun dnsRegistered(ctx: Context): Boolean = trial(ctx).getBoolean(KEY_DNS_OK, false)

    /** DNS cadastrada no painel: app liberado, zera qualquer teste em andamento. */
    fun markDnsRegistered(ctx: Context) = trial(ctx).edit {
        putBoolean(KEY_DNS_OK, true)
        remove(KEY_TRIAL_START)
    }

    /** DNS fora do painel: inicia (ou mantém) o teste de 7 dias. */
    fun startTrialIfNeeded(ctx: Context) {
        val p = trial(ctx)
        p.edit { putBoolean(KEY_DNS_OK, false) }
        if (p.getLong(KEY_TRIAL_START, 0L) <= 0L) {
            p.edit { putLong(KEY_TRIAL_START, System.currentTimeMillis()) }
        }
    }

    fun trialStart(ctx: Context): Long = trial(ctx).getLong(KEY_TRIAL_START, 0L)

    fun trialActive(ctx: Context): Boolean =
        !dnsRegistered(ctx) && trialStart(ctx) > 0L && trialDaysLeft(ctx) > 0L

    fun trialExpired(ctx: Context): Boolean =
        !dnsRegistered(ctx) && trialStart(ctx) > 0L && trialDaysLeft(ctx) <= 0L

    fun trialDaysLeft(ctx: Context): Long {
        val start = trialStart(ctx)
        if (start <= 0L) return TRIAL_DAYS
        val end = start + TRIAL_DAYS * DAY_MS
        val diff = end - System.currentTimeMillis()
        return if (diff <= 0) 0 else (diff + DAY_MS - 1) / DAY_MS
    }

    /** Texto pronto pro menu inicial. */
    fun badgeText(ctx: Context): String =
        if (!dnsRegistered(ctx) && trialStart(ctx) > 0L) {
            val d = trialDaysLeft(ctx)
            if (d <= 0L) "Teste grátis encerrado — ative o app"
            else "Teste grátis: $d de $TRIAL_DAYS dias restantes"
        } else expiryText(ctx)

    fun clearExpiry(ctx: Context) = prefs(ctx).edit { clear() }
}

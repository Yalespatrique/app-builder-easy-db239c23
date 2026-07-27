package com.asterplay.tv.ui

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.asterplay.tv.R
import com.asterplay.tv.store.PlaylistCache
import com.asterplay.tv.store.PlaylistStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class LoadingActivity : AppCompatActivity() {

    private lateinit var status: TextView
    private lateinit var sub: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_loading)

        val url = PlaylistStore.get(this) ?: run {
            goPairing(); return
        }
        status = findViewById(R.id.txtStatus)
        sub = findViewById(R.id.txtSub)

        lifecycleScope.launch {
            val cachedCount = withContext(Dispatchers.IO) { PlaylistCache.count(this@LoadingActivity, url) }
            if (cachedCount > 0) {
                goHome(); return@launch
            }
            status.text = "Carregando sua lista..."
            sub.text = "Isso pode levar alguns segundos"
            val count = withContext(Dispatchers.IO) { downloadAndCache(url) }
            if (count > 0) {
                status.text = "Pronto!"
                sub.text = "$count itens carregados"
                goHome()
            } else {
                status.text = "Não foi possível carregar sua lista."
                sub.text = "Verifique seus dados e tente novamente."
                status.postDelayed({
                    PlaylistStore.clear(this@LoadingActivity)
                    goPairing()
                }, 2500)
            }
        }
    }

    private fun goHome() {
        val i = Intent(this, HomeActivity::class.java)
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(i)
        finish()
    }

    private fun goPairing() {
        val i = Intent(this, PairingActivity::class.java)
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(i)
        finish()
    }

    private fun downloadAndCache(url: String) = try {
        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .callTimeout(180, TimeUnit.SECONDS)
            .build()
        client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) return@use 0
            val body = resp.body ?: return@use 0
            var lastProgressAt = 0L
            body.charStream().buffered(64 * 1024).useLines { lines ->
                PlaylistCache.saveFromM3uLines(applicationContext, url, lines) { loaded ->
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastProgressAt > 500L) {
                        lastProgressAt = now
                        runOnUiThread { sub.text = "$loaded itens encontrados" }
                    }
                }
            }
        }
    } catch (_: Exception) { 0 }
}

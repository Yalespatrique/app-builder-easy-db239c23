package com.asterplay.tv.ui

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.asterplay.tv.R
import com.asterplay.tv.net.M3UParser
import com.asterplay.tv.store.PlaylistCache
import com.asterplay.tv.store.PlaylistStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class LoadingActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_loading)

        val url = PlaylistStore.get(this) ?: run {
            startActivity(Intent(this, PairingActivity::class.java))
            finish(); return
        }
        val status = findViewById<TextView>(R.id.txtStatus)

        lifecycleScope.launch {
            val cached = withContext(Dispatchers.IO) { PlaylistCache.load(this@LoadingActivity, url) }
            if (cached != null && cached.isNotEmpty()) {
                goHome(); return@launch
            }
            status.text = "Carregando informações da lista..."
            val channels = withContext(Dispatchers.IO) { downloadAndParse(url) }
            if (channels.isNotEmpty()) {
                withContext(Dispatchers.IO) { PlaylistCache.save(this@LoadingActivity, url, channels) }
                goHome()
            } else {
                status.text = "Não foi possível carregar sua lista."
                findViewById<TextView>(R.id.txtSub).text = "Verifique seus dados e tente novamente."
                status.postDelayed({
                    PlaylistStore.clear(this@LoadingActivity)
                    startActivity(Intent(this@LoadingActivity, PairingActivity::class.java))
                    finish()
                }, 2500)
            }
        }
    }

    private fun goHome() {
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }

    private fun downloadAndParse(url: String) = try {
        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
        client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            M3UParser.parse(resp.body?.string().orEmpty())
        }
    } catch (_: Exception) { emptyList() }
}

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
            sub.text = "Baixando playlist..."
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

    private fun downloadAndCache(url: String): Int { return try {
        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .callTimeout(300, TimeUnit.SECONDS)
            .build()
        val target = PlaylistCache.sourceFile(applicationContext)
        val tmp = java.io.File(target.parentFile, target.name + ".part")

        client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) return@use
            val body = resp.body ?: return@use
            body.byteStream().use { input ->
                tmp.outputStream().buffered(64 * 1024).use { output ->
                    val buf = ByteArray(64 * 1024)
                    var read = input.read(buf)
                    var totalBytes = 0L
                    var lastBytesAt = 0L
                    while (read >= 0) {
                        output.write(buf, 0, read)
                        totalBytes += read
                        val now = SystemClock.elapsedRealtime()
                        if (now - lastBytesAt > 500L) {
                            lastBytesAt = now
                            val mb = totalBytes / 1_000_000.0
                            runOnUiThread { sub.text = String.format("Baixando... %.1f MB", mb) }
                        }
                        read = input.read(buf)
                    }
                }
            }
            if (target.exists()) target.delete()
            tmp.renameTo(target)
        }

        val file = PlaylistCache.sourceFile(applicationContext)
        if (!file.exists() || file.length() == 0L) return 0

        runOnUiThread {
            status.text = "Organizando sua lista..."
            sub.text = "0 itens processados"
        }
        var lastProgressAt = 0L
        var lastCount = 0
        file.bufferedReader(Charsets.UTF_8).useLines { lines ->
            PlaylistCache.saveFromM3uLines(applicationContext, url, lines) { loaded ->
                lastCount = loaded
                val now = SystemClock.elapsedRealtime()
                if (now - lastProgressAt > 500L) {
                    lastProgressAt = now
                    runOnUiThread { sub.text = "$loaded itens processados" }
                }
            }
        }
        lastCount
    } catch (_: Exception) { 0 }
}

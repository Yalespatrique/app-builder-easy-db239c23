package com.asterplay.tv.ui.screens

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.progressindicator.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.asterplay.tv.store.PlaylistCache
import com.asterplay.tv.store.PlaylistStore
import com.asterplay.tv.ui.theme.Accent
import com.asterplay.tv.ui.theme.BgBase
import com.asterplay.tv.ui.theme.BgElevated
import com.asterplay.tv.ui.theme.TextMuted
import com.asterplay.tv.ui.theme.TextPrimary
import com.asterplay.tv.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

@Composable
fun LoadingScreen(onReady: () -> Unit, onFail: () -> Unit) {
    val ctx = LocalContext.current
    var status by remember { mutableStateOf("Preparando sua lista...") }
    var sub by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val url = PlaylistStore.get(ctx) ?: run { onFail(); return@LaunchedEffect }
        val cachedCount = withContext(Dispatchers.IO) { PlaylistCache.count(ctx, url) }
        if (cachedCount > 0) { onReady(); return@LaunchedEffect }

        status = "Carregando sua lista..."; sub = "Baixando playlist..."
        val count = withContext(Dispatchers.IO) {
            downloadAndCache(ctx.applicationContext, url, onBytes = { mb ->
                sub = String.format("Baixando... %.1f MB", mb)
            }, onItems = { n ->
                status = "Organizando sua lista..."
                sub = "$n itens processados"
            })
        }
        if (count > 0) {
            status = "Pronto!"; sub = "$count itens carregados"
            delay(400); onReady()
        } else {
            status = "Não foi possível carregar sua lista."
            sub = "Verifique seus dados e tente novamente."
            delay(2200)
            PlaylistStore.clear(ctx); onFail()
        }
    }

    Box(Modifier.fillMaxSize().background(BgBase), contentAlignment = Alignment.Center) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 80.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(status, color = TextPrimary, style = MaterialTheme.typography.headlineMedium)
            Text(sub, color = TextSecondary, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .background(BgElevated)
            ) {
                // barra indeterminada simples animada por progresso do texto (visual)
                Box(
                    Modifier
                        .fillMaxWidth(0.35f)
                        .height(6.dp)
                        .background(Accent)
                )
            }
            Text("Isso pode levar alguns segundos.", color = TextMuted, style = MaterialTheme.typography.labelMedium)
        }
    }
}

private fun downloadAndCache(
    ctx: android.content.Context,
    url: String,
    onBytes: (Double) -> Unit,
    onItems: (Int) -> Unit,
): Int { return try {
    val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .callTimeout(300, TimeUnit.SECONDS)
        .build()
    val target = PlaylistCache.sourceFile(ctx)
    val tmp = File(target.parentFile, target.name + ".part")

    client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
        if (!resp.isSuccessful) return@use
        val body = resp.body ?: return@use
        body.byteStream().use { input ->
            tmp.outputStream().buffered(64 * 1024).use { output ->
                val buf = ByteArray(64 * 1024)
                var read = input.read(buf)
                var totalBytes = 0L
                var lastAt = 0L
                while (read >= 0) {
                    output.write(buf, 0, read)
                    totalBytes += read
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastAt > 500L) {
                        lastAt = now
                        onBytes(totalBytes / 1_000_000.0)
                    }
                    read = input.read(buf)
                }
            }
        }
        if (target.exists()) target.delete()
        tmp.renameTo(target)
    }

    val file = PlaylistCache.sourceFile(ctx)
    if (!file.exists() || file.length() == 0L) return@try 0

    var lastAt = 0L
    var last = 0
    file.bufferedReader(Charsets.UTF_8).useLines { lines ->
        PlaylistCache.saveFromM3uLines(ctx, url, lines) { loaded ->
            last = loaded
            val now = SystemClock.elapsedRealtime()
            if (now - lastAt > 500L) { lastAt = now; onItems(loaded) }
        }
    }
    last
} catch (_: Exception) { 0 }

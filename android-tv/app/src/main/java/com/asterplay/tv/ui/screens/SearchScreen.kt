package com.asterplay.tv.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.asterplay.tv.net.Channel
import com.asterplay.tv.player.PlayerActivity
import com.asterplay.tv.store.SettingsStore
import com.asterplay.tv.store.CacheDb
import com.asterplay.tv.store.XtreamStore
import com.asterplay.tv.ui.components.PosterCard
import com.asterplay.tv.ui.theme.Accent
import com.asterplay.tv.ui.theme.BgBase
import com.asterplay.tv.ui.theme.BgSurface
import com.asterplay.tv.ui.theme.TextMuted
import com.asterplay.tv.ui.theme.TextPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Busca só no que já foi cacheado (visitado pelo usuário).
 */
@Composable
fun SearchScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val creds = remember { XtreamStore.get(ctx) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var focused by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        if (query.length < 2 || creds == null) { results = emptyList(); return@LaunchedEffect }
        delay(300)
        val account = CacheDb.accountKey(creds.host, creds.username)
        results = withContext(Dispatchers.IO) { CacheDb.get(ctx).search(account, query) }
    }

    Column(Modifier.fillMaxSize().background(BgBase).padding(40.dp)) {
        Text("BUSCA", color = Accent, style = MaterialTheme.typography.labelLarge)
        Text("Encontre canais, filmes e séries", color = TextPrimary, style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(20.dp))
        BasicTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            textStyle = TextStyle(color = TextPrimary, fontSize = 22.sp),
            cursorBrush = SolidColor(Accent),
            modifier = Modifier
                .fillMaxWidth()
                .background(BgSurface, RoundedCornerShape(10.dp))
                .then(if (focused) Modifier.border(2.dp, Accent, RoundedCornerShape(10.dp)) else Modifier)
                .onFocusChanged { focused = it.isFocused }
                .padding(horizontal = 18.dp, vertical = 16.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            when {
                query.isEmpty() -> "Digite pelo menos 2 caracteres"
                results.isEmpty() -> "Nenhum resultado. Navegue por categorias para carregar o conteúdo."
                else -> "${results.size} resultados"
            },
            color = TextMuted, style = MaterialTheme.typography.labelMedium,
        )
        Spacer(Modifier.height(16.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(6),
            contentPadding = PaddingValues(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            items(results, key = { it.url }) { ch ->
                PosterCard(
                    title = ch.name, logo = ch.logo, aspect = 2f / 3f,
                    onClick = {
                        if (ch.url.startsWith("asterplay://")) return@PosterCard
                        val i = Intent(ctx, PlayerActivity::class.java)
                        i.putExtra("url", SettingsStore.applyFormat(ctx, ch.url)); i.putExtra("name", ch.name)
                        ctx.startActivity(i)
                    },
                )
            }
        }
    }
}

package com.asterplay.tv.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.asterplay.tv.net.Channel
import com.asterplay.tv.net.XtreamApi
import com.asterplay.tv.net.XtreamCategory
import com.asterplay.tv.player.PlayerActivity
import com.asterplay.tv.store.CacheDb
import com.asterplay.tv.store.XtreamStore
import com.asterplay.tv.ui.components.CategoryItem
import com.asterplay.tv.ui.components.PosterCard
import com.asterplay.tv.ui.theme.Accent
import com.asterplay.tv.ui.theme.BgBase
import com.asterplay.tv.ui.theme.BgSurface
import com.asterplay.tv.ui.theme.TextMuted
import com.asterplay.tv.ui.theme.TextPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Master-detail via Xtream API + cache SQLite com TTL.
 * Só carrega o que o usuário abre.
 */
@Composable
fun BrowseScreen(type: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val creds = remember { XtreamStore.get(ctx) }

    var categories by remember { mutableStateOf<List<XtreamCategory>>(emptyList()) }
    var selectedIdx by remember { mutableIntStateOf(0) }
    var items by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var loadingItems by remember { mutableStateOf(false) }
    var loadingCats by remember { mutableStateOf(true) }

    val header = when (type) { "vod" -> "FILMES"; "series" -> "SÉRIES"; else -> "CANAIS" }
    val isChannels = type == "live"

    LaunchedEffect(type) {
        if (creds == null) { onBack(); return@LaunchedEffect }
        val account = CacheDb.accountKey(creds.host, creds.username)
        loadingCats = true
        val cats = withContext(Dispatchers.IO) {
            val db = CacheDb.get(ctx)
            db.readCategories(account, type) ?: run {
                val fresh = XtreamApi.categories(creds, type)
                if (fresh.isNotEmpty()) db.writeCategories(account, type, fresh, CacheDb.TTL_CATEGORIES)
                fresh
            }
        }
        categories = cats
        loadingCats = false
        if (cats.isNotEmpty()) {
            selectedIdx = 0
            loadCategory(cats[0].id, account, creds) { items = it; loadingItems = false }
        }
    }

    fun onSelect(idx: Int) {
        if (idx < 0 || idx >= categories.size || creds == null) return
        selectedIdx = idx
        loadingItems = true
        val account = CacheDb.accountKey(creds.host, creds.username)
        scope.launch {
            val list = withContext(Dispatchers.IO) {
                val db = CacheDb.get(ctx)
                db.readStreams(account, type, categories[idx].id) ?: run {
                    val fresh = XtreamApi.streams(creds, type, categories[idx].id)
                    if (fresh.isNotEmpty()) db.writeStreams(account, type, categories[idx].id, fresh, CacheDb.TTL_STREAMS)
                    fresh
                }
            }
            items = list; loadingItems = false
        }
    }

    Row(Modifier.fillMaxSize().background(BgBase)) {
        Column(
            Modifier.width(320.dp).fillMaxHeight().background(BgSurface).padding(vertical = 20.dp),
        ) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text(header, color = Accent, style = MaterialTheme.typography.labelLarge)
                Text(
                    if (loadingCats) "Carregando..." else "${categories.size} categorias",
                    color = TextMuted, style = MaterialTheme.typography.labelMedium,
                )
            }
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                itemsIndexed(categories) { i, cat ->
                    CategoryItem(
                        name = cat.name,
                        count = 0,
                        selected = i == selectedIdx,
                        onClick = { onSelect(i) },
                        onFocus = { if (i != selectedIdx) onSelect(i) },
                    )
                }
            }
        }

        Column(Modifier.fillMaxSize().padding(32.dp)) {
            val catName = categories.getOrNull(selectedIdx)?.name ?: ""
            Text(catName, color = TextPrimary, style = MaterialTheme.typography.headlineLarge)
            Text(
                if (loadingItems) "Carregando..." else "${items.size} itens",
                color = TextMuted, style = MaterialTheme.typography.labelMedium,
            )
            Box(Modifier.fillMaxSize().padding(top = 16.dp)) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(if (isChannels) 5 else 6),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    items(items, key = { it.url }) { ch ->
                        PosterCard(
                            title = ch.name,
                            logo = ch.logo,
                            aspect = if (isChannels) 16f / 9f else 2f / 3f,
                            onClick = {
                                if (ch.url.startsWith("asterplay://series/")) {
                                    // TODO: abrir tela de temporadas/episódios
                                    return@PosterCard
                                }
                                val i = Intent(ctx, PlayerActivity::class.java)
                                i.putExtra("url", ch.url); i.putExtra("name", ch.name)
                                ctx.startActivity(i)
                            },
                        )
                    }
                }
            }
        }
    }
}


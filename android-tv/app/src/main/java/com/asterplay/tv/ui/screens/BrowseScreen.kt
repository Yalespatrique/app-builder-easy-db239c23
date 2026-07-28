package com.asterplay.tv.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
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
import com.asterplay.tv.ui.theme.BgElevated
import com.asterplay.tv.ui.theme.BgSurface
import com.asterplay.tv.ui.theme.TextMuted
import com.asterplay.tv.ui.theme.TextPrimary
import com.asterplay.tv.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Master-detail via Xtream API + cache SQLite com TTL.
 * Só carrega o que o usuário abre.
 */
@Composable
fun BrowseScreen(type: String, onBack: () -> Unit, onOpenMovieDetail: (Channel) -> Unit = {}) {

    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val creds = remember { XtreamStore.get(ctx) }

    var categories by remember { mutableStateOf<List<XtreamCategory>>(emptyList()) }
    var selectedIdx by remember { mutableIntStateOf(0) }
    var items by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var shownCount by remember { mutableIntStateOf(0) }
    var loadingItems by remember { mutableStateOf(false) }
    var loadingCats by remember { mutableStateOf(true) }
    var selectedLive by remember { mutableStateOf<Channel?>(null) }

    val pageSize = 100

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
            loadingItems = true
            val first = withContext(Dispatchers.IO) {
                val db = CacheDb.get(ctx)
                db.readStreams(account, type, cats[0].id) ?: run {
                    val fresh = XtreamApi.streams(creds, type, cats[0].id)
                    if (fresh.isNotEmpty()) db.writeStreams(account, type, cats[0].id, fresh, CacheDb.TTL_STREAMS)
                    fresh
                }
            }
            items = first
            shownCount = minOf(pageSize, first.size)
            loadingItems = false
        }
    }

    fun onSelectCategory(idx: Int) {
        if (idx < 0 || idx >= categories.size || creds == null) return
        selectedIdx = idx
        selectedLive = null
        loadingItems = true
        items = emptyList()
        shownCount = 0
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
            items = list
            shownCount = minOf(pageSize, list.size)
            loadingItems = false
        }
    }

    BackHandler(enabled = selectedLive != null) { selectedLive = null }

    Row(Modifier.fillMaxSize().background(BgBase)) {
        Column(
            Modifier.width(300.dp).fillMaxHeight().background(BgSurface).padding(vertical = 20.dp),
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
                        onClick = { onSelectCategory(i) },
                        onFocus = { if (i != selectedIdx) onSelectCategory(i) },
                    )
                }
            }
        }

        // Painel principal
        if (isChannels && selectedLive != null) {
            LiveChannelPane(
                channels = items,
                current = selectedLive!!,
                onPick = { selectedLive = it },
            )
        } else {
            Column(Modifier.fillMaxSize().padding(32.dp)) {
                val catName = categories.getOrNull(selectedIdx)?.name ?: ""
                Text(catName, color = TextPrimary, style = MaterialTheme.typography.headlineLarge)
                Text(
                    if (loadingItems) "Carregando..."
                    else "Mostrando $shownCount de ${items.size} itens",
                    color = TextMuted, style = MaterialTheme.typography.labelMedium,
                )
                Box(Modifier.fillMaxSize().padding(top = 16.dp)) {
                    val visible = remember(items, shownCount) { items.take(shownCount) }
                    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()

                    LaunchedEffect(gridState, items, shownCount) {
                        androidx.compose.runtime.snapshotFlow {
                            gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                        }.collect { lastVisible ->
                            if (lastVisible >= 0 &&
                                shownCount < items.size &&
                                lastVisible >= visible.size - 10
                            ) {
                                shownCount = minOf(shownCount + pageSize, items.size)
                            }
                        }
                    }

                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(if (isChannels) 5 else 6),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        items(visible, key = { it.url }) { ch ->
                            PosterCard(
                                title = ch.name,
                                logo = ch.logo,
                                aspect = if (isChannels) 16f / 9f else 2f / 3f,
                                onClick = {
                                    if (ch.url.startsWith("asterplay://series/")) return@PosterCard
                                    when (type) {
                                        "vod" -> onOpenMovieDetail(ch)
                                        "live" -> selectedLive = ch
                                        else -> {
                                            val i = Intent(ctx, PlayerActivity::class.java)
                                            i.putExtra("url", ch.url); i.putExtra("name", ch.name)
                                            ctx.startActivity(i)
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Painel de canais ao vivo: lista à esquerda, player 16:9 no topo direito, EPG abaixo.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun LiveChannelPane(
    channels: List<Channel>,
    current: Channel,
    onPick: (Channel) -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val creds = remember { XtreamStore.get(ctx) }

    var epg by remember(current.url) { mutableStateOf<List<XtreamApi.EpgItem>>(emptyList()) }
    var loadingEpg by remember(current.url) { mutableStateOf(true) }

    LaunchedEffect(current.url) {
        loadingEpg = true
        epg = emptyList()
        val streamId = current.xtreamStreamId()
        if (creds != null && streamId != null) {
            val list = withContext(Dispatchers.IO) { XtreamApi.shortEpg(creds, streamId, 10) }
            epg = list
        }
        loadingEpg = false
    }

    // Player persistente entre trocas de canal.
    val player = remember {
        ExoPlayer.Builder(ctx).build().apply { playWhenReady = true }
    }
    LaunchedEffect(current.url) {
        player.setMediaItem(MediaItem.fromUri(current.url))
        player.prepare()
        player.playWhenReady = true
    }
    DisposableEffect(Unit) {
        onDispose { player.release() }
    }

    Row(Modifier.fillMaxSize()) {
        // Lista de canais da categoria
        Column(
            Modifier.width(320.dp).fillMaxHeight().background(BgSurface).padding(vertical = 16.dp),
        ) {
            Text(
                "CANAIS",
                color = Accent,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(channels, key = { it.url }) { ch ->
                    ChannelRowItem(
                        channel = ch,
                        selected = ch.url == current.url,
                        onClick = { onPick(ch) },
                        onFocus = { if (ch.url != current.url) onPick(ch) },
                    )
                }
            }
        }

        // Player + EPG
        Column(Modifier.fillMaxSize().padding(24.dp)) {
            Text(current.name, color = TextPrimary, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black),
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = {
                        PlayerView(it).apply {
                            useController = false
                            this.player = player
                        }
                    },
                )
            }
            Spacer(Modifier.height(16.dp))
            Text("PROGRAMAÇÃO", color = Accent, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            if (loadingEpg) {
                Text("Carregando EPG...", color = TextMuted, style = MaterialTheme.typography.bodyMedium)
            } else if (epg.isEmpty()) {
                Text("EPG não disponível para este canal.", color = TextMuted, style = MaterialTheme.typography.bodyMedium)
            } else {
                val now = System.currentTimeMillis()
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    items(epg) { item ->
                        EpgRow(item, isNow = now in item.start..item.end)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelRowItem(
    channel: Channel,
    selected: Boolean,
    onClick: () -> Unit,
    onFocus: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) BgElevated else Color.Transparent,
            focusedContainerColor = BgElevated,
        ),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            },
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.width(56.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(6.dp)).background(BgBase),
                contentAlignment = Alignment.Center,
            ) {
                if (!channel.logo.isNullOrBlank()) {
                    AsyncImage(
                        model = channel.logo,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Text(
                channel.name,
                color = if (selected || focused) TextPrimary else TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun EpgRow(item: XtreamApi.EpgItem, isNow: Boolean) {
    val fmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val time = "${fmt.format(Date(item.start))} - ${fmt.format(Date(item.end))}"
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (isNow) BgElevated else Color.Transparent)
            .then(if (isNow) Modifier.border(1.dp, Accent, RoundedCornerShape(6.dp)) else Modifier)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            time,
            color = if (isNow) Accent else TextMuted,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(110.dp),
        )
        Text(
            item.title,
            color = if (isNow) TextPrimary else TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

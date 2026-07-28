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
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import com.asterplay.tv.store.SettingsStore
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
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun BrowseScreen(type: String, onBack: () -> Unit, onOpenMovieDetail: (Channel) -> Unit = {}, onOpenSeriesDetail: (Channel) -> Unit = {}) {

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
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var searchLoading by remember { mutableStateOf(false) }
    val hasSearchQuery = query.trim().isNotEmpty()
    val canSearch = query.trim().length >= 2

    LaunchedEffect(query, type) {
        if (creds == null || !canSearch) { results = emptyList(); searchLoading = false; return@LaunchedEffect }
        searchLoading = true
        kotlinx.coroutines.delay(500)
        val account = CacheDb.accountKey(creds.host, creds.username)
        results = withContext(Dispatchers.IO) { CacheDb.get(ctx).searchKind(account, type, query.trim()) }
            .filter { !SettingsStore.isBlocked(ctx, it.name) }
        searchLoading = false
    }


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
        categories = cats.filter { !SettingsStore.isBlocked(ctx, it.name) }
        loadingCats = false
        val visibleCats = cats.filter { !SettingsStore.isBlocked(ctx, it.name) }
        if (visibleCats.isNotEmpty()) {
            selectedIdx = 0
            loadingItems = true
            val first = withContext(Dispatchers.IO) {
                val db = CacheDb.get(ctx)
                db.readStreams(account, type, visibleCats[0].id) ?: run {
                    val fresh = XtreamApi.streams(creds, type, visibleCats[0].id)
                    if (fresh.isNotEmpty()) db.writeStreams(account, type, visibleCats[0].id, fresh, CacheDb.TTL_STREAMS)
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

    val livePlayer = remember {
        ExoPlayer.Builder(ctx).build().apply { playWhenReady = true }
    }
    DisposableEffect(Unit) {
        onDispose { livePlayer.release() }
    }
    LaunchedEffect(selectedLive?.url) {
        val url = selectedLive?.url
        if (url != null) {
            livePlayer.setMediaItem(MediaItem.fromUri(SettingsStore.applyFormat(ctx, url)))
            livePlayer.prepare()
            livePlayer.playWhenReady = true
        } else {
            livePlayer.stop()
        }
    }

    BackHandler(enabled = selectedLive != null) { selectedLive = null }

    Box(Modifier.fillMaxSize().background(BgBase)) {
        Row(Modifier.fillMaxSize()) {
            Column(
                Modifier.width(300.dp).fillMaxHeight().background(BgSurface).padding(vertical = 20.dp),
            ) {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Text(header, color = Accent, style = MaterialTheme.typography.labelLarge)
                    Text(
                        if (loadingCats) "Carregando..." else "${categories.size} categorias",
                        color = TextMuted, style = MaterialTheme.typography.labelMedium,
                    )
                    if (!isChannels) {
                        Spacer(Modifier.height(12.dp))
                        SearchField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = if (type == "vod") "Buscar filme..." else "Buscar série...",
                        )
                    }
                }
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    itemsIndexed(categories) { i, cat ->
                        CategoryItem(
                            name = cat.name,
                            count = 0,
                            selected = i == selectedIdx && !hasSearchQuery,
                            onClick = { query = ""; onSelectCategory(i) },
                            onFocus = { /* não trocar por foco: evita voltar pra primeira */ },
                        )
                    }
                }
            }

            // Painel principal
            if (isChannels && selectedLive != null) {
                LiveChannelPane(
                    channels = items,
                    current = selectedLive!!,
                    player = livePlayer,
                    onPick = { selectedLive = it },
                    onEnterFullscreen = {
                        val i = Intent(ctx, PlayerActivity::class.java)
                        i.putExtra("url", SettingsStore.applyFormat(ctx, selectedLive!!.url))
                        i.putExtra("name", selectedLive!!.name)
                        i.putExtra("type", "live")
                        ctx.startActivity(i)
                    },
                )
            } else {
                Column(Modifier.fillMaxSize().padding(32.dp)) {
                    val catName = categories.getOrNull(selectedIdx)?.name ?: ""
                    Text(
                        if (hasSearchQuery) {
                            if (canSearch) "Resultados para \"$query\"" else "Buscar"
                        } else catName,
                        color = TextPrimary, style = MaterialTheme.typography.headlineLarge,
                    )
                    Text(
                        when {
                            hasSearchQuery && !canSearch -> "Digite pelo menos 2 caracteres"
                            hasSearchQuery && searchLoading -> "Buscando..."
                            hasSearchQuery -> "${results.size} resultados"
                            loadingItems -> "Carregando..."
                            else -> "Mostrando $shownCount de ${items.size} itens"
                        },
                        color = TextMuted, style = MaterialTheme.typography.labelMedium,
                    )
                    Box(Modifier.fillMaxSize().padding(top = 16.dp)) {
                        val visible = remember(items, shownCount, results, hasSearchQuery) {
                            if (hasSearchQuery) results else items.take(shownCount)
                        }
                        val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()

                        LaunchedEffect(gridState, items, shownCount, hasSearchQuery) {
                            if (hasSearchQuery) return@LaunchedEffect
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
                                        when (type) {
                                            "vod" -> onOpenMovieDetail(ch)
                                            "series" -> onOpenSeriesDetail(ch)
                                            "live" -> selectedLive = ch
                                            else -> {
                                                val i = Intent(ctx, PlayerActivity::class.java)
                                                i.putExtra("url", ch.url); i.putExtra("name", ch.name); i.putExtra("type", "vod")
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
}

/**
 * Painel de canais ao vivo: lista à esquerda, player 16:9 no topo direito, EPG abaixo.
 * O PlayerView é criado aqui e usa o ExoPlayer compartilhado — nunca sai da árvore.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun LiveChannelPane(
    channels: List<Channel>,
    current: Channel,
    player: ExoPlayer,
    onPick: (Channel) -> Unit,
    onEnterFullscreen: () -> Unit,
) {
    val ctx = LocalContext.current
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

    // Auto-foco no canal atual da lista.
    val listFocus = remember { FocusRequester() }
    LaunchedEffect(current.url) { runCatching { listFocus.requestFocus() } }

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
                    val isCurrent = ch.url == current.url
                    ChannelRowItem(
                        channel = ch,
                        selected = isCurrent,
                        modifier = if (isCurrent) Modifier.focusRequester(listFocus) else Modifier,
                        onClick = {
                            if (isCurrent) onEnterFullscreen()
                            else onPick(ch)
                        },
                        onFocus = { /* trocar canal só com OK, não com foco */ },
                    )
                }
            }
        }

        // Player + EPG
        Column(Modifier.fillMaxSize().padding(24.dp)) {
            Text(current.name, color = TextPrimary, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black),
            ) {
                SharedPlayerView(player = player)

                // Loader personalizado enquanto o canal sintoniza
                var buffering by remember(current.url) { mutableStateOf(true) }
                DisposableEffect(player) {
                    val l = object : androidx.media3.common.Player.Listener {
                        override fun onPlaybackStateChanged(state: Int) {
                            buffering = state == androidx.media3.common.Player.STATE_BUFFERING ||
                                state == androidx.media3.common.Player.STATE_IDLE
                        }
                    }
                    buffering = player.playbackState == androidx.media3.common.Player.STATE_BUFFERING ||
                        player.playbackState == androidx.media3.common.Player.STATE_IDLE
                    player.addListener(l)
                    onDispose { player.removeListener(l) }
                }
                if (buffering) {
                    Box(
                        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.65f)),
                        contentAlignment = androidx.compose.ui.Alignment.Center,
                    ) {
                        Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                            com.asterplay.tv.ui.components.NeonLoader(Modifier.size(70.dp))
                            Spacer(Modifier.height(14.dp))
                            Text(
                                "sintonizando canal...",
                                color = TextPrimary,
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
                    }
                }
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

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun SharedPlayerView(player: ExoPlayer) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            (android.view.LayoutInflater.from(context)
                .inflate(com.asterplay.tv.R.layout.view_preview_player, null) as PlayerView).apply {
                useController = false
                keepScreenOn = true
                this.player = player
            }
        },
        update = { view ->
            if (view.player != player) view.player = player
        },
    )
}




@Composable
private fun ChannelRowItem(
    channel: Channel,
    selected: Boolean,
    onClick: () -> Unit,
    onFocus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) BgElevated else Color.Transparent,
            focusedContainerColor = BgElevated,
        ),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
        modifier = modifier
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

/**
 * Campo de busca (EditText nativo) — o teclado da TV abre ao apertar OK.
 */
@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit, placeholder: String) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(BgElevated, RoundedCornerShape(8.dp))
            .then(
                if (focused) Modifier.border(2.dp, Accent, RoundedCornerShape(8.dp))
                else Modifier.border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(8.dp)),
            )
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { c ->
                android.widget.EditText(c).apply {
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    setTextColor(android.graphics.Color.WHITE)
                    setHintTextColor(android.graphics.Color.GRAY)
                    hint = placeholder
                    textSize = 16f
                    isSingleLine = true
                    setPadding(0, 0, 0, 0)
                    inputType = android.text.InputType.TYPE_CLASS_TEXT or
                        android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                    isFocusable = true
                    isFocusableInTouchMode = true
                    showSoftInputOnFocus = false

                    fun openKeyboard() {
                        requestFocus()
                        val imm = c.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                            as android.view.inputmethod.InputMethodManager
                        imm.showSoftInput(this, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                    }

                    setOnClickListener { openKeyboard() }
                    setOnKeyListener { _, keyCode, event ->
                        if (event.action == android.view.KeyEvent.ACTION_DOWN &&
                            (keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
                                keyCode == android.view.KeyEvent.KEYCODE_ENTER)
                        ) {
                            openKeyboard(); true
                        } else false
                    }
                    setOnFocusChangeListener { _, hasFocus -> focused = hasFocus }
                    addTextChangedListener(object : android.text.TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, d: Int) {}
                        override fun onTextChanged(s: CharSequence?, a: Int, b: Int, d: Int) {}
                        override fun afterTextChanged(s: android.text.Editable?) {
                            val t = s?.toString().orEmpty()
                            if (t != value) onValueChange(t)
                        }
                    })
                }
            },
            update = { et -> if (et.text.toString() != value) et.setText(value) },
        )
    }
}

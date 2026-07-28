package com.asterplay.tv.ui.screens

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Text
import com.asterplay.tv.BuildConfig
import com.asterplay.tv.R
import com.asterplay.tv.core.DeviceId
import com.asterplay.tv.net.Channel
import com.asterplay.tv.net.TmdbApi
import com.asterplay.tv.net.TopHomePreload
import com.asterplay.tv.player.PlayerActivity
import com.asterplay.tv.store.CacheDb
import com.asterplay.tv.store.PlaylistStore
import com.asterplay.tv.store.TopCacheStore
import com.asterplay.tv.store.XtreamStore
import com.asterplay.tv.ui.components.PosterCard
import com.asterplay.tv.ui.theme.Accent
import com.asterplay.tv.ui.theme.BgBase
import com.asterplay.tv.ui.theme.BgElevated
import com.asterplay.tv.ui.theme.BgSurface
import com.asterplay.tv.ui.theme.BrandGradient
import com.asterplay.tv.ui.theme.TextMuted
import com.asterplay.tv.ui.theme.TextPrimary
import com.asterplay.tv.ui.theme.TextSecondary
import kotlinx.coroutines.launch

/** Item TMDB já resolvido com um Channel real do servidor do usuário. */
private data class TopHit(val tmdb: TmdbApi.Item, val channel: Channel)

@Composable
fun HomeScreen(
    onOpenBrowse: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onLogout: () -> Unit,
    onOpenMovieDetail: (Channel, Long?) -> Unit,
    onOpenSeriesDetail: (Channel, Long?) -> Unit,
) {

    val ctx = LocalContext.current
    val mac = remember { DeviceId.getMac(ctx) }
    val scope = rememberCoroutineScope()

    var topMovies by remember { mutableStateOf<List<TopHit>>(emptyList()) }
    var topSeries by remember { mutableStateOf<List<TopHit>>(emptyList()) }
    var recentMovies by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var recentSeries by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val creds = XtreamStore.get(ctx)
        if (creds == null) { loaded = true; return@LaunchedEffect }
        val account = CacheDb.accountKey(creds.host, creds.username)

        // 1) Cache local: mostra imediatamente se houver.
        val cachedM = TopCacheStore.read(ctx, account, "movie")
        val cachedS = TopCacheStore.read(ctx, account, "series")
        val cachedRecentM = TopCacheStore.read(ctx, account, "recent_movie")
        val cachedRecentS = TopCacheStore.read(ctx, account, "recent_series")
        if (!cachedM.isNullOrEmpty()) topMovies = cachedM.map { it.toHit() }
        if (!cachedS.isNullOrEmpty()) topSeries = cachedS.map { it.toHit() }
        if (!cachedRecentM.isNullOrEmpty()) recentMovies = cachedRecentM.map { it.toChannel() }
        if (!cachedRecentS.isNullOrEmpty()) recentSeries = cachedRecentS.map { it.toChannel() }

        // 2) Libera a UI já. Se falta cache, dispara preload em background
        // sem bloquear a navegação — quando terminar, o Home é atualizado.
        loaded = true
        val missing = cachedM.isNullOrEmpty() || cachedS.isNullOrEmpty() ||
            cachedRecentM.isNullOrEmpty() || cachedRecentS.isNullOrEmpty()
        if (missing) {
            scope.launch {
                TopHomePreload.run(ctx)
                topMovies = TopCacheStore.read(ctx, account, "movie")?.map { it.toHit() }.orEmpty()
                topSeries = TopCacheStore.read(ctx, account, "series")?.map { it.toHit() }.orEmpty()
                recentMovies = TopCacheStore.read(ctx, account, "recent_movie")?.map { it.toChannel() }.orEmpty()
                recentSeries = TopCacheStore.read(ctx, account, "recent_series")?.map { it.toChannel() }.orEmpty()
            }
        }
    }

    Box(Modifier.fillMaxSize().background(BgBase)) {
        Image(
            painter = painterResource(R.drawable.bg_gradient),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(Modifier.fillMaxSize().background(Color(0xCC07070F)))

        Row(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .width(240.dp)
                    .fillMaxHeight()
                    .background(Color(0xE611121C))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(R.drawable.logo_asterplay),
                        contentDescription = "Asterplay",
                        modifier = Modifier.size(40.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Asterplay", color = TextPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(24.dp))
                SideMenuItem(Icons.Default.LiveTv, "Canais") { onOpenBrowse("live") }
                SideMenuItem(Icons.Default.Movie, "Filmes") { onOpenBrowse("vod") }
                SideMenuItem(Icons.Default.Tv, "Séries") { onOpenBrowse("series") }
                SideMenuItem(Icons.Default.Search, "Busca") { onOpenSearch() }
                SideMenuItem(Icons.Default.Favorite, "Favoritos") { /* futuro */ }
                Spacer(Modifier.weight(1f))
                SideMenuItem(Icons.Default.Logout, "Sair") {
                    PlaylistStore.clear(ctx); XtreamStore.clear(ctx); CacheDb.get(ctx).clearAll(); TopCacheStore.clear(ctx); onLogout()
                }
                Text("MAC ${DeviceId.formatted(mac)}", color = TextMuted, style = MaterialTheme.typography.labelMedium)
                Text("v${BuildConfig.VERSION_NAME}", color = TextMuted, style = MaterialTheme.typography.labelMedium)
            }

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 24.dp, end = 16.dp, top = 24.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(R.drawable.logo_asterplay),
                        contentDescription = "Asterplay",
                        modifier = Modifier.size(48.dp),
                    )
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("EM ALTA ESTA SEMANA", color = Accent, style = MaterialTheme.typography.labelMedium)
                        Text("Top 10 do momento", color = TextPrimary, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                    }
                }

                TopRow(
                    title = "🔥 TOP 10 FILMES DA SEMANA",
                    items = topMovies,
                    loaded = loaded,
                    emptyMsg = "Nenhum dos títulos em alta está na sua lista.",
                    onPick = { hit -> onOpenMovieDetail(hit.channel, hit.tmdb.tmdbId) },

                )

                TopRow(
                    title = "🔥 TOP 10 SÉRIES DA SEMANA",
                    items = topSeries,
                    loaded = loaded,
                    emptyMsg = "Nenhuma das séries em alta está na sua lista.",
                    onPick = { hit -> scope.launch { play(ctx, hit.channel) } },
                )

                RecentRow(
                    title = "🆕 FILMES RECENTES",
                    items = recentMovies,
                    loaded = loaded,
                    emptyMsg = "Sem filmes recentes.",
                    onPick = { ch -> onOpenMovieDetail(ch, null) },

                )

                RecentRow(
                    title = "🆕 SÉRIES RECENTES",
                    items = recentSeries,
                    loaded = loaded,
                    emptyMsg = "Sem séries recentes.",
                    onPick = { ch -> scope.launch { play(ctx, ch) } },
                )
            }
        }
    }
}

private fun TopCacheStore.Entry.toHit() = TopHit(
    tmdb = TmdbApi.Item(title = title, poster = poster, tmdbId = tmdbId),
    channel = Channel(name = chName, url = chUrl, logo = chLogo, group = chGroup, tvgId = chTvg),
)

private fun TopCacheStore.Entry.toChannel() = Channel(
    name = chName,
    url = chUrl,
    logo = chLogo ?: poster,
    group = chGroup,
    tvgId = chTvg,
)

private fun play(ctx: android.content.Context, ch: Channel) {
    if (ch.url.startsWith("asterplay://series/")) {
        // TODO: abrir tela de temporadas. Por ora, ignora.
        return
    }
    val i = Intent(ctx, PlayerActivity::class.java)
    i.putExtra("url", ch.url); i.putExtra("name", ch.name)
    ctx.startActivity(i)
}

@Composable
private fun TopRow(
    title: String,
    items: List<TopHit>,
    loaded: Boolean,
    emptyMsg: String,
    onPick: (TopHit) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, color = TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (!loaded) {
            EmptyBar("Carregando…")
        } else if (items.isEmpty()) {
            EmptyBar(emptyMsg)
        } else {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                // Proporções derivadas do cardWidth para manter o número alinhado em qualquer tamanho.
                val maxCardWidth = 180.dp
                val spacing = 12.dp
                val startPadding = 20.dp
                val endPadding = 16.dp
                val visibleCount = 4
                // Cada item ocupa a largura do card + 28% do card visível à esquerda (estilo Netflix).
                val numberExtraRatio = 0.665f
                val fixedSpacing = startPadding + endPadding + spacing * (visibleCount - 1)
                val cardWidth = ((maxWidth - fixedSpacing) / (visibleCount * (1 + numberExtraRatio)))
                    .coerceAtMost(maxCardWidth)
                    .coerceAtLeast(100.dp)
                val numberExtra = cardWidth * numberExtraRatio


                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(spacing),
                    contentPadding = PaddingValues(start = startPadding, end = endPadding, top = 6.dp, bottom = 10.dp),
                ) {
                    itemsIndexed(items, key = { _, it -> it.tmdb.tmdbId }) { index, hit ->
                        RankedPoster(rank = index + 1, hit = hit, cardWidth = cardWidth, onClick = { onPick(hit) })
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentRow(
    title: String,
    items: List<Channel>,
    loaded: Boolean,
    emptyMsg: String,
    onPick: (Channel) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, color = TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (!loaded) {
            EmptyBar("Carregando…")
        } else if (items.isEmpty()) {
            EmptyBar(emptyMsg)
        } else {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val maxCardWidth = 180.dp
                val spacing = 14.dp
                val startPadding = 12.dp
                val endPadding = 24.dp
                val visibleCount = 4
                val totalSpacing = startPadding + endPadding + spacing * (visibleCount - 1)
                val available = maxWidth - totalSpacing
                val cardWidth = (available / visibleCount).coerceAtMost(maxCardWidth).coerceAtLeast(100.dp)

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(spacing),
                    contentPadding = PaddingValues(start = startPadding, end = endPadding, top = 6.dp, bottom = 10.dp),
                ) {
                    items(items, key = { it.url }) { ch ->
                        Box(Modifier.width(cardWidth)) {
                            PosterCard(
                                title = ch.name,
                                logo = ch.logo,
                                aspect = 2f / 3f,
                                onClick = { onPick(ch) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyBar(msg: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(60.dp)
            .background(BgSurface, RoundedCornerShape(12.dp))
            .padding(20.dp)
            .focusable(),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(msg, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun RankedPoster(
    rank: Int,
    hit: TopHit,
    cardWidth: Dp,
    onClick: () -> Unit,
) {
    // Estilo Netflix: número gigante à esquerda "saindo" do pôster.
    // Escalonamento automático baseado na largura do card, com ratio
    // reduzido para dois dígitos para o "10" nunca ser cortado.
    val isDouble = rank >= 10
    // Fonte escala com cardWidth. Single digit é bem alto; "10" fica menor
    // para caber os dois dígitos na área reservada.
    val numberFontSize = if (isDouble) cardWidth.value * 0.95f else cardWidth.value * 1.35f
    // Largura aproximada de cada dígito ≈ 0.55 × fontSize (Black weight).
    // Reserva horizontal para o número (com folga para não cortar).
    val digitCount = if (isDouble) 2 else 1
    val reservedWidth = (numberFontSize * 0.58f * digitCount).dp
    val posterHeight = cardWidth * 1.5f
    val itemWidth = cardWidth + reservedWidth * 0.85f // pôster cobre ~15% do número

    Column(
        modifier = Modifier.width(itemWidth),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .width(itemWidth)
                .height(posterHeight),
        ) {
            // Número À FRENTE, canto inferior esquerdo.
            BasicText(
                text = rank.toString(),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .wrapContentWidth(align = Alignment.Start, unbounded = true),
                style = TextStyle(
                    fontSize = numberFontSize.sp,
                    fontWeight = FontWeight.Black,
                    brush = BrandGradient,
                    shadow = Shadow(
                        color = Color(0xFF000000).copy(alpha = 0.85f),
                        offset = Offset(4f, 6f),
                        blurRadius = 14f,
                    ),
                ),
            )

            // Pôster alinhado à direita: cobre apenas a extremidade direita do número.
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(cardWidth),
            ) {
                PosterCard(
                    title = hit.tmdb.title,
                    logo = hit.tmdb.poster,
                    aspect = 2f / 3f,
                    onClick = onClick,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // Título abaixo do pôster (alinhado ao card, não ao número).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = itemWidth - cardWidth),
        ) {
            Text(
                text = hit.tmdb.title,
                color = TextPrimary,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(cardWidth),
            )
        }
    }
}

@Composable
private fun SideMenuItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = BgElevated,
        ),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
        modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = if (focused) Accent else TextSecondary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(label, color = if (focused) TextPrimary else TextSecondary, style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal)
        }
    }
}

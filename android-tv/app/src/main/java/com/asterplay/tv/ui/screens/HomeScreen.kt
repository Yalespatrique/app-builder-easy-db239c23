package com.asterplay.tv.ui.screens

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
import com.asterplay.tv.net.XtreamApi
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
import com.asterplay.tv.ui.theme.TextMuted
import com.asterplay.tv.ui.theme.TextPrimary
import com.asterplay.tv.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Normalizer

/** Item TMDB já resolvido com um Channel real do servidor do usuário. */
private data class TopHit(val tmdb: TmdbApi.Item, val channel: Channel)

@Composable
fun HomeScreen(
    onOpenBrowse: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onLogout: () -> Unit,
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
        if (!cachedM.isNullOrEmpty()) topMovies = cachedM.map { it.toHit() }
        if (!cachedS.isNullOrEmpty()) topSeries = cachedS.map { it.toHit() }

        // 2) Rede: TMDB top 25 + catálogo do servidor + recentes em paralelo.
        val res = withContext(Dispatchers.IO) {
            val tmdbM = async { TmdbApi.topMovies(25) }
            val tmdbS = async { TmdbApi.topSeries(25) }
            val srvM = async { XtreamApi.allStreams(creds, "vod") }
            val srvS = async { XtreamApi.allStreams(creds, "series") }
            val recM = async { XtreamApi.recentStreams(creds, "vod", 20) }
            val recS = async { XtreamApi.recentStreams(creds, "series", 20) }
            listOf(tmdbM, tmdbS, srvM, srvS, recM, recS).awaitAll()
        }
        @Suppress("UNCHECKED_CAST") val tmdbMovies = res[0] as List<TmdbApi.Item>
        @Suppress("UNCHECKED_CAST") val tmdbSeries = res[1] as List<TmdbApi.Item>
        @Suppress("UNCHECKED_CAST") val srvMovies = res[2] as List<Channel>
        @Suppress("UNCHECKED_CAST") val srvSeries = res[3] as List<Channel>
        @Suppress("UNCHECKED_CAST") val rMovies = res[4] as List<Channel>
        @Suppress("UNCHECKED_CAST") val rSeries = res[5] as List<Channel>

        val mHits = matchTop(tmdbMovies, srvMovies, 10)
        val sHits = matchTop(tmdbSeries, srvSeries, 10)
        topMovies = mHits
        topSeries = sHits
        recentMovies = rMovies
        recentSeries = rSeries
        if (mHits.isNotEmpty()) TopCacheStore.write(ctx, account, "movie", mHits.map { it.toEntry() })
        if (sHits.isNotEmpty()) TopCacheStore.write(ctx, account, "series", sHits.map { it.toEntry() })
        loaded = true
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
                    .padding(horizontal = 40.dp, vertical = 24.dp),
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
                    onPick = { hit -> scope.launch { play(ctx, hit.channel) } },
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
                    onPick = { ch -> scope.launch { play(ctx, ch) } },
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

// -------- Matching TMDB × servidor --------

private val STRIP_TAGS = Regex("\\[[^\\]]*]|\\([^)]*\\)|\\bs\\d{1,2}e\\d{1,3}\\b|\\bs\\d{1,2}\\b|\\b\\d{4}\\b|\\b(4k|fhd|hd|sd|hevc|dub|dublado|leg|legendado|br|nac|multi|imax|remux)\\b")

private fun norm(s: String): String {
    val stripped = s.lowercase().replace(STRIP_TAGS, " ")
    val n = Normalizer.normalize(stripped, Normalizer.Form.NFD)
        .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
    return n.replace(Regex("[^a-z0-9]+"), " ").trim()
}

private fun matchTop(tmdb: List<TmdbApi.Item>, server: List<Channel>, limit: Int = 10): List<TopHit> {
    if (tmdb.isEmpty() || server.isEmpty()) return emptyList()
    data class IdxEntry(val key: String, val ch: Channel)
    val index = ArrayList<IdxEntry>(server.size)
    val seen = HashSet<String>()
    for (c in server) {
        val k = norm(c.name)
        if (k.isNotEmpty() && seen.add(k)) index += IdxEntry(k, c)
    }
    val out = ArrayList<TopHit>(limit)
    val usedUrls = HashSet<String>()
    for (t in tmdb) {
        if (out.size >= limit) break
        val tk = norm(t.title); if (tk.isEmpty()) continue
        var hit: Channel? = index.firstOrNull { it.key == tk }?.ch
        if (hit == null) hit = index.firstOrNull { it.key.startsWith("$tk ") || it.key.endsWith(" $tk") }?.ch
        if (hit == null) hit = index.firstOrNull { it.key.contains(" $tk ") }?.ch
        if (hit == null && tk.length >= 4) hit = index.firstOrNull { it.key.contains(tk) || tk.contains(it.key) }?.ch
        if (hit != null && usedUrls.add(hit.url)) out += TopHit(t, hit)
    }
    return out
}

private fun TopHit.toEntry() = TopCacheStore.Entry(
    title = tmdb.title, poster = tmdb.poster, tmdbId = tmdb.tmdbId,
    chName = channel.name, chUrl = channel.url, chLogo = channel.logo,
    chGroup = channel.group, chTvg = channel.tvgId,
)

private fun TopCacheStore.Entry.toHit() = TopHit(
    tmdb = TmdbApi.Item(title = title, poster = poster, tmdbId = tmdbId),
    channel = Channel(name = chName, url = chUrl, logo = chLogo, group = chGroup, tvgId = chTvg),
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
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 6.dp, bottom = 10.dp),
            ) {
                itemsIndexed(items, key = { _, it -> it.tmdb.tmdbId }) { index, hit ->
                    RankedPoster(rank = index + 1, hit = hit, onClick = { onPick(hit) })
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
private fun RankedPoster(rank: Int, hit: TopHit, onClick: () -> Unit) {
    // Card uniforme para todos os ranks; badge pequeno no canto superior esquerdo.
    Box(Modifier.width(150.dp)) {
        PosterCard(
            title = hit.tmdb.title,
            logo = hit.tmdb.poster,
            aspect = 2f / 3f,
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
        )
        // Badge de rank
        Box(
            Modifier
                .align(Alignment.TopStart)
                .offset(x = (-10).dp, y = (-10).dp)
                .background(Accent, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = rank.toString(),
                color = Color.Black,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
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

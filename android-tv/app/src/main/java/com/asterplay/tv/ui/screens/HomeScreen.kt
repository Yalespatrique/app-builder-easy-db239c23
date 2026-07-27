package com.asterplay.tv.ui.screens

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val creds = XtreamStore.get(ctx)
        if (creds == null) { loaded = true; return@LaunchedEffect }

        // Busca em paralelo TMDB (10+10) + catálogo do servidor (vod + series)
        val res = withContext(Dispatchers.IO) {
            val tmdbM = async { TmdbApi.topMovies(10) }
            val tmdbS = async { TmdbApi.topSeries(10) }
            val srvM = async { XtreamApi.allStreams(creds, "vod") }
            val srvS = async { XtreamApi.allStreams(creds, "series") }
            listOf(tmdbM, tmdbS, srvM, srvS).awaitAll()
        }
        @Suppress("UNCHECKED_CAST")
        val tmdbMovies = res[0] as List<TmdbApi.Item>
        @Suppress("UNCHECKED_CAST")
        val tmdbSeries = res[1] as List<TmdbApi.Item>
        @Suppress("UNCHECKED_CAST")
        val srvMovies = res[2] as List<Channel>
        @Suppress("UNCHECKED_CAST")
        val srvSeries = res[3] as List<Channel>

        topMovies = matchTop(tmdbMovies, srvMovies)
        topSeries = matchTop(tmdbSeries, srvSeries)
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
                    PlaylistStore.clear(ctx); XtreamStore.clear(ctx); CacheDb.get(ctx).clearAll(); onLogout()
                }
                Text("MAC ${DeviceId.formatted(mac)}", color = TextMuted, style = MaterialTheme.typography.labelMedium)
                Text("v${BuildConfig.VERSION_NAME}", color = TextMuted, style = MaterialTheme.typography.labelMedium)
            }

            Column(
                Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 40.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(R.drawable.logo_asterplay),
                        contentDescription = "Asterplay",
                        modifier = Modifier.size(72.dp),
                    )
                    Spacer(Modifier.width(20.dp))
                    Column {
                        Text("EM ALTA ESTA SEMANA", color = Accent, style = MaterialTheme.typography.labelLarge)
                        Text("Top 10 do momento", color = TextPrimary, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black)
                    }
                }

                TopRow(
                    title = "🔥 TOP 10 FILMES DA SEMANA",
                    items = topMovies,
                    loaded = loaded,
                    emptyMsg = "Nenhum dos títulos em alta está na sua lista.",
                    onPick = { hit ->
                        scope.launch { play(ctx, hit.channel) }
                    },
                )

                TopRow(
                    title = "🔥 TOP 10 SÉRIES DA SEMANA",
                    items = topSeries,
                    loaded = loaded,
                    emptyMsg = "Nenhuma das séries em alta está na sua lista.",
                    onPick = { hit ->
                        // Séries: abre a tela de detalhes via BrowseScreen (futuro).
                        // Por enquanto reproduz o primeiro se for movie-like; senão apenas informa.
                        scope.launch { play(ctx, hit.channel) }
                    },
                )
            }
        }
    }
}

// -------- Matching TMDB × servidor --------

private fun norm(s: String): String {
    val n = Normalizer.normalize(s, Normalizer.Form.NFD)
        .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        .lowercase()
    return n.replace(Regex("[^a-z0-9]+"), " ").trim()
}

private fun matchTop(tmdb: List<TmdbApi.Item>, server: List<Channel>): List<TopHit> {
    if (tmdb.isEmpty() || server.isEmpty()) return emptyList()
    // Index normalizado do servidor → primeiro Channel encontrado
    val index = HashMap<String, Channel>(server.size)
    for (c in server) {
        val k = norm(c.name)
        if (k.isNotEmpty() && !index.containsKey(k)) index[k] = c
    }
    val out = ArrayList<TopHit>()
    for (t in tmdb) {
        val tk = norm(t.title)
        if (tk.isEmpty()) continue
        var hit = index[tk]
        if (hit == null) {
            // fallback: qualquer chave do servidor que contenha o título (ou vice-versa)
            for ((k, ch) in index) {
                if (k.contains(tk) || tk.contains(k)) { hit = ch; break }
            }
        }
        if (hit != null) out += TopHit(t, hit)
    }
    return out
}

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
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                contentPadding = PaddingValues(start = 40.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
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
            .padding(20.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(msg, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun RankedPoster(rank: Int, hit: TopHit, onClick: () -> Unit) {
    // Layout estilo Netflix Top 10: número gigante sobreposto à esquerda do pôster.
    BoxWithConstraints(Modifier.width(200.dp).height(230.dp)) {
        // Número grande atrás
        Text(
            text = rank.toString(),
            style = TextStyle(
                fontSize = 180.sp,
                fontWeight = FontWeight.Black,
                color = Color(0xFF0B0B14),
            ),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = (-14).dp, y = 20.dp),
        )
        // Contorno neon do número
        Text(
            text = rank.toString(),
            style = TextStyle(
                fontSize = 180.sp,
                fontWeight = FontWeight.Black,
                color = Accent.copy(alpha = 0.85f),
            ),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = (-10).dp, y = 24.dp),
        )
        // Pôster deslocado à direita para o número aparecer
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .width(130.dp),
        ) {
            PosterCard(
                title = hit.tmdb.title,
                logo = hit.tmdb.poster,
                aspect = 2f / 3f,
                onClick = onClick,
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

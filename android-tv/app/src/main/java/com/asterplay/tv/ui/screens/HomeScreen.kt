package com.asterplay.tv.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import com.asterplay.tv.net.TmdbApi
import com.asterplay.tv.player.PlayerActivity
import com.asterplay.tv.store.CacheDb
import com.asterplay.tv.store.PlaylistStore
import com.asterplay.tv.store.XtreamStore
import com.asterplay.tv.ui.components.PosterCard
import com.asterplay.tv.ui.theme.Accent
import com.asterplay.tv.ui.theme.BgBase
import com.asterplay.tv.ui.theme.BgElevated
import com.asterplay.tv.ui.theme.BgSurface
import com.asterplay.tv.ui.theme.TextMuted
import com.asterplay.tv.ui.theme.TextPrimary
import com.asterplay.tv.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    onOpenBrowse: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onLogout: () -> Unit,
) {
    val ctx = LocalContext.current
    val mac = remember { DeviceId.getMac(ctx) }
    val scope = rememberCoroutineScope()

    var topMovies by remember { mutableStateOf<List<TmdbApi.Item>>(emptyList()) }
    var topSeries by remember { mutableStateOf<List<TmdbApi.Item>>(emptyList()) }

    LaunchedEffect(Unit) {
        topMovies = TmdbApi.topMovies(10)
        topSeries = TmdbApi.topSeries(10)
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
            // Menu lateral
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
                    onPick = { item ->
                        scope.launch { openIfAvailable(ctx, item.title, isSeries = false) }
                    },
                )

                TopRow(
                    title = "🔥 TOP 10 SÉRIES DA SEMANA",
                    items = topSeries,
                    onPick = { item ->
                        scope.launch { openIfAvailable(ctx, item.title, isSeries = true) }
                    },
                )
            }
        }
    }
}

private suspend fun openIfAvailable(
    ctx: android.content.Context,
    title: String,
    isSeries: Boolean,
) {
    val creds = XtreamStore.get(ctx) ?: return
    val account = CacheDb.accountKey(creds.host, creds.username)
    val hit = CacheDb.get(ctx).findFirstByName(account, title)
    if (hit != null && !hit.url.startsWith("asterplay://series/")) {
        val i = Intent(ctx, PlayerActivity::class.java)
        i.putExtra("url", hit.url); i.putExtra("name", hit.name)
        ctx.startActivity(i)
    } else {
        val msg = if (isSeries) "Série ainda não disponível na sua lista"
                  else "Filme ainda não disponível na sua lista"
        Toast.makeText(ctx, "$title — $msg", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun TopRow(
    title: String,
    items: List<TmdbApi.Item>,
    onPick: (TmdbApi.Item) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, color = TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (items.isEmpty()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .background(BgSurface, RoundedCornerShape(12.dp))
                    .padding(20.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text("Carregando…", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                items(items, key = { it.tmdbId }) { it ->
                    Box(Modifier.width(140.dp)) {
                        PosterCard(
                            title = it.title,
                            logo = it.poster,
                            aspect = 2f / 3f,
                            onClick = { onPick(it) },
                        )
                    }
                }
            }
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

package com.asterplay.tv.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.asterplay.tv.net.TmdbApi
import com.asterplay.tv.store.ContinueStore
import com.asterplay.tv.store.SettingsStore
import com.asterplay.tv.net.XtreamApi
import com.asterplay.tv.player.PlayerActivity
import com.asterplay.tv.store.WatchedStore
import com.asterplay.tv.store.XtreamStore
import com.asterplay.tv.ui.theme.Accent
import com.asterplay.tv.ui.theme.BgBase
import com.asterplay.tv.ui.theme.BgElevated
import com.asterplay.tv.ui.theme.TextMuted
import com.asterplay.tv.ui.theme.TextPrimary
import com.asterplay.tv.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

private data class SeriesMerged(
    val title: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val overview: String?,
    val rating: String?,
    val year: String?,
    val genres: List<String>,
    val cast: List<String>,
    val seasons: List<Int>,
    val episodes: Map<Int, List<XtreamApi.Episode>>,
)

@Composable
fun SeriesDetailScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val args = remember { SeriesDetailArgs.consume() }

    if (args == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    val channel = args.channel
    var data by remember { mutableStateOf<SeriesMerged?>(null) }
    var loading by remember { mutableStateOf(true) }
    var selectedSeason by remember { mutableStateOf<Int?>(null) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var watched by remember { mutableStateOf(WatchedStore.all(ctx)) }
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) watched = WatchedStore.all(ctx)
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    LaunchedEffect(channel.url, args.tmdbId) {
        val creds = XtreamStore.get(ctx)
        val seriesId = channel.xtreamSeriesId()
        val merged = withContext(Dispatchers.IO) {
            coroutineScope {
                val fullDef = async {
                    if (creds != null && seriesId != null) XtreamApi.seriesFullInfo(creds, seriesId) else null
                }
                val full = fullDef.await()
                val finalTmdbId = args.tmdbId ?: full?.meta?.tmdbId
                val tmdb = if (finalTmdbId != null && SettingsStore.tmdbEnabled(ctx)) TmdbApi.details(finalTmdbId, "tv") else null

                SeriesMerged(
                    title = channel.name,
                    posterUrl = tmdb?.poster ?: full?.meta?.cover ?: channel.logo,
                    backdropUrl = tmdb?.backdrop ?: full?.meta?.backdrop,
                    overview = tmdb?.overview ?: full?.meta?.plot,
                    rating = tmdb?.voteAverage
                        ?.takeIf { it > 0 }
                        ?.let { String.format("%.1f", it) }
                        ?: full?.meta?.rating,
                    year = (tmdb?.releaseDate ?: full?.meta?.releaseDate)?.take(4)?.takeIf { it.length == 4 },
                    genres = tmdb?.genres.orEmpty().ifEmpty {
                        full?.meta?.genre?.split(",", "/")?.map { it.trim() }?.filter { it.isNotBlank() }.orEmpty()
                    },
                    cast = tmdb?.cast.orEmpty().ifEmpty {
                        full?.meta?.cast?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }?.take(8).orEmpty()
                    },
                    seasons = full?.info?.seasons.orEmpty(),
                    episodes = full?.info?.episodes.orEmpty(),
                )
            }
        }
        data = merged
        selectedSeason = merged.seasons.firstOrNull()
        loading = false
    }

    Box(Modifier.fillMaxSize().background(BgBase)) {
        val d = data
        if (d?.backdropUrl != null) {
            AsyncImage(
                model = d.backdropUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    listOf(Color(0xF20A0B14), Color(0xCC0A0B14), Color(0x800A0B14), Color(0x330A0B14))
                )
            )
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(Color(0x000A0B14), Color(0x550A0B14), Color(0xF20A0B14))
                )
            )
        )

        Column(
            Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Header com poster + info
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Box(
                    Modifier
                        .width(160.dp)
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(BgElevated),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!d?.posterUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = d?.posterUrl,
                            contentDescription = channel.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else if (loading) {
                        Text("Carregando...", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                    }
                }

                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("SÉRIE", color = Accent, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Text(
                        d?.title ?: channel.name,
                        color = TextPrimary,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        d?.rating?.let {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Star, null, tint = Color(0xFFF5C518), modifier = Modifier.width(16.dp).height(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(it, color = TextPrimary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            }
                        }
                        d?.year?.let { SeriesMetaChip(it) }
                        if (d != null && d.seasons.isNotEmpty()) SeriesMetaChip("${d.seasons.size} temporada${if (d.seasons.size > 1) "s" else ""}")
                        d?.genres?.take(3)?.forEach { SeriesMetaChip(it) }
                    }

                    if (loading && d?.overview == null) {
                        Text("Carregando informações...", color = TextMuted, style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Text(
                            d?.overview ?: "Sinopse não disponível.",
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    if (!d?.cast.isNullOrEmpty()) {
                        Text(
                            "Elenco: " + d?.cast?.joinToString(" · ").orEmpty(),
                            color = TextMuted,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            // Temporadas
            if (d != null && d.seasons.isNotEmpty()) {
                Text("TEMPORADAS", color = Accent, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(d.seasons) { season ->
                        SeasonChip(
                            label = "Temporada $season",
                            selected = season == selectedSeason,
                            onClick = { selectedSeason = season },
                        )
                    }
                }

                Text("EPISÓDIOS", color = Accent, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                val eps = d.episodes[selectedSeason].orEmpty()
                if (eps.isEmpty()) {
                    Text("Sem episódios nesta temporada.", color = TextMuted, style = MaterialTheme.typography.bodyMedium)
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    ) {
                        itemsIndexed(eps) { idx, ep ->
                            EpisodeRow(
                                ep = ep,
                                watched = watched.contains(ep.url),
                                onPlay = {
                                    ContinueStore.save(ctx, "series", channel, ep.url)
                                    val i = Intent(ctx, PlayerActivity::class.java)
                                    i.putExtra(PlayerActivity.EXTRA_URL, ep.url)
                                    i.putExtra(
                                        PlayerActivity.EXTRA_NAME,
                                        "${channel.name} • T${ep.season}E${ep.episodeNum} - ${ep.title}",
                                    )
                                    i.putExtra(PlayerActivity.EXTRA_TYPE, "series")
                                    i.putStringArrayListExtra(
                                        PlayerActivity.EXTRA_EP_URLS,
                                        ArrayList(eps.map { it.url }),
                                    )
                                    i.putStringArrayListExtra(
                                        PlayerActivity.EXTRA_EP_NAMES,
                                        ArrayList(eps.map { e -> "${channel.name} • T${e.season}E${e.episodeNum} - ${e.title}" }),
                                    )
                                    i.putExtra(PlayerActivity.EXTRA_EP_INDEX, idx)
                                    ctx.startActivity(i)
                                },
                            )
                        }
                    }
                }
            } else if (loading) {
                Text("Carregando temporadas...", color = TextMuted, style = MaterialTheme.typography.bodyMedium)
            } else {
                Text("Nenhuma temporada disponível.", color = TextMuted, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun SeriesMetaChip(text: String) {
    Box(
        Modifier
            .background(Color(0x33FFFFFF), RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text, color = TextPrimary, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun SeasonChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Accent else Color(0x33FFFFFF),
            focusedContainerColor = if (selected) Color(0xFFB39CFF) else Color(0x66FFFFFF),
        ),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(20.dp)),
    ) {
        Text(
            label,
            color = if (selected) Color.Black else TextPrimary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun EpisodeRow(ep: XtreamApi.Episode, watched: Boolean, onPlay: () -> Unit) {
    Surface(
        onClick = onPlay,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0x22FFFFFF),
            focusedContainerColor = Color(0x55FFFFFF),
        ),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(10.dp)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier
                    .width(120.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(BgElevated),
                contentAlignment = Alignment.Center,
            ) {
                if (!ep.still.isNullOrBlank()) {
                    AsyncImage(
                        model = ep.still,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(Icons.Default.PlayArrow, null, tint = TextMuted, modifier = Modifier.width(28.dp).height(28.dp))
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "E${ep.episodeNum} • ${ep.title}",
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!ep.plot.isNullOrBlank()) {
                    Text(
                        ep.plot,
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (!ep.duration.isNullOrBlank()) {
                    Text(ep.duration, color = TextMuted, style = MaterialTheme.typography.labelSmall)
                }
            }
            if (watched) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.CheckCircle,
                        null,
                        tint = Accent,
                        modifier = Modifier.width(20.dp).height(20.dp),
                    )
                    Text(" ASSISTIDO", color = Accent, style = MaterialTheme.typography.labelSmall)
                }
            }
            Icon(Icons.Default.PlayArrow, null, tint = Accent, modifier = Modifier.width(28.dp).height(28.dp))
        }
    }
}

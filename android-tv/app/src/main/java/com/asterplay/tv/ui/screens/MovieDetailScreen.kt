package com.asterplay.tv.ui.screens

import android.content.Intent
import androidx.compose.foundation.background

import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.asterplay.tv.net.Channel
import com.asterplay.tv.net.TmdbApi
import com.asterplay.tv.store.SettingsStore
import com.asterplay.tv.net.XtreamApi
import com.asterplay.tv.player.PlayerActivity
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private data class MergedDetail(
    val title: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val overview: String?,
    val rating: String?,
    val duration: String?,
    val year: String?,
    val genres: List<String>,
    val cast: List<String>,
    val director: String?,
)

@Composable
fun MovieDetailScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val args = remember { MovieDetailArgs.consume() }

    if (args == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    val channel = args.channel
    var detail by remember { mutableStateOf<MergedDetail?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(channel.url, args.tmdbId) {
        val creds = XtreamStore.get(ctx)
        val streamId = channel.xtreamStreamId()

        val merged = withContext(Dispatchers.IO) {
            coroutineScope {
                val vodDeferred = async {
                    if (creds != null && streamId != null && args.kind == "movie") {
                        XtreamApi.vodInfo(creds, streamId)
                    } else null
                }
                val vod = vodDeferred.await()
                val finalTmdbId = args.tmdbId ?: vod?.tmdbId
                val tmdb = if (finalTmdbId != null && SettingsStore.tmdbEnabled(ctx)) TmdbApi.details(finalTmdbId, args.kind) else null

                MergedDetail(
                    title = channel.name,
                    posterUrl = tmdb?.poster ?: vod?.cover ?: channel.logo,
                    backdropUrl = tmdb?.backdrop ?: vod?.backdrop,
                    overview = tmdb?.overview ?: vod?.plot,
                    rating = tmdb?.voteAverage
                        ?.takeIf { it > 0 }
                        ?.let { String.format("%.1f", it) }
                        ?: vod?.rating,
                    duration = tmdb?.runtime?.let { "${it} min" } ?: vod?.duration,
                    year = (tmdb?.releaseDate ?: vod?.releaseDate)?.take(4)?.takeIf { it.length == 4 },
                    genres = tmdb?.genres.orEmpty().ifEmpty {
                        vod?.genre?.split(",", "/")?.map { it.trim() }?.filter { it.isNotBlank() }.orEmpty()
                    },
                    cast = tmdb?.cast.orEmpty().ifEmpty {
                        vod?.cast?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }?.take(8).orEmpty()
                    },
                    director = vod?.director?.takeIf { it.isNotBlank() },
                )
            }
        }
        detail = merged
        loading = false
    }

    val playFocus = remember { FocusRequester() }
    LaunchedEffect(detail) { if (detail != null) runCatching { playFocus.requestFocus() } }

    // Prévia deve parar quando o player em tela cheia for aberto (ou o app sair de foco).
    var previewSuspended by remember { mutableStateOf(false) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> previewSuspended = true
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> previewSuspended = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }


    Box(Modifier.fillMaxSize().background(BgBase)) {
        // Ciclo de preview de vídeo: 5s de backdrop -> 90s de vídeo -> repete.
        val d = detail
        var showVideo by remember { mutableStateOf(false) }
        var videoRendering by remember { mutableStateOf(false) }
        LaunchedEffect(channel.url, previewSuspended) {
            showVideo = false
            videoRendering = false
            if (previewSuspended) return@LaunchedEffect
            while (true) {
                showVideo = false
                videoRendering = false
                delay(5_000)
                showVideo = true
                delay(90_000)
            }
        }

        // Backdrop estático: some apenas quando o vídeo estiver realmente rodando.
        if (d?.backdropUrl != null && !videoRendering) {
            AsyncImage(
                model = d.backdropUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (showVideo && !previewSuspended) {
            MoviePreviewVideo(
                url = channel.url,
                modifier = Modifier.fillMaxSize(),
                onRendering = { videoRendering = it },
            )
        }



        // Dark gradient overlay
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    listOf(Color(0xF20A0B14), Color(0xCC0A0B14), Color(0x800A0B14), Color(0x000A0B14))
                )
            )
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(Color(0x000A0B14), Color(0x330A0B14), Color(0xE60A0B14))
                )
            )
        )

        Row(
            Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            // Poster à esquerda
            Box(
                Modifier
                    .width(190.dp)
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
                    Text("Carregando...", color = TextMuted, style = MaterialTheme.typography.bodyMedium)
                }
            }

            // Info à direita
            Column(
                Modifier.fillMaxHeight().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("FILME", color = Accent, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text(
                    d?.title ?: channel.name,
                    color = TextPrimary,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                // Linha de meta: nota • ano • duração • gêneros
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
                    d?.year?.let { MetaChip(it) }
                    d?.duration?.let { MetaChip(it) }
                    d?.genres?.take(3)?.forEach { MetaChip(it) }
                }

                // Sinopse
                if (loading && d?.overview == null) {
                    Text("Carregando informações...", color = TextMuted, style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(
                        d?.overview ?: "Sinopse não disponível.",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(640.dp),
                    )
                }

                if (!d?.cast.isNullOrEmpty()) {
                    Text("ELENCO", color = Accent, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text(
                        d?.cast?.joinToString(" · ").orEmpty(),
                        color = TextPrimary,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(640.dp),
                    )
                }

                d?.director?.let {
                    Text("DIREÇÃO", color = Accent, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text(it, color = TextPrimary, style = MaterialTheme.typography.bodySmall)
                }

                Spacer(Modifier.height(8.dp))

                // Botões
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PrimaryButton(
                        label = "ASSISTIR AGORA",
                        icon = Icons.Default.PlayArrow,
                        modifier = Modifier.focusRequester(playFocus),
                        onClick = {
                            val i = Intent(ctx, PlayerActivity::class.java)
                            i.putExtra("url", channel.url); i.putExtra("name", channel.name); i.putExtra("type", "vod")
                            ctx.startActivity(i)
                        },
                    )
                    SecondaryButton(label = "Voltar", onClick = onBack)
                }
            }
        }
    }
}

@Composable
private fun MetaChip(text: String) {
    Box(
        Modifier
            .background(Color(0x33FFFFFF), RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .focusable(false),
    ) {
        Text(text, color = TextPrimary, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun PrimaryButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Accent,
            focusedContainerColor = Color(0xFFB39CFF),
        ),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(10.dp)),
        modifier = modifier,
    ) {
        Row(
            Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = Color.Black, modifier = Modifier.width(18.dp).height(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, color = Color.Black, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun SecondaryButton(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0x33FFFFFF),
            focusedContainerColor = Color(0x66FFFFFF),
        ),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(10.dp)),
    ) {
        Text(
            label,
            color = TextPrimary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
        )
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun MoviePreviewVideo(url: String, modifier: Modifier = Modifier, onRendering: (Boolean) -> Unit = {}) {
    val ctx = LocalContext.current
    val renderCb = rememberUpdatedState(onRendering)
    val player = remember(url) {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(2_000, 8_000, 500, 1_000)
            .build()
        val trackSelector = DefaultTrackSelector(ctx).apply {
            setParameters(buildUponParameters().setMaxVideoSize(854, 480).setMaxVideoBitrate(1_500_000))
        }
        ExoPlayer.Builder(ctx)
            .setLoadControl(loadControl)
            .setTrackSelector(trackSelector)
            .build().apply {
                setMediaItem(MediaItem.fromUri(url))
                volume = 1f
                repeatMode = Player.REPEAT_MODE_OFF
                prepare()
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_READY && duration > 0L) {
                            val mid = duration / 2L
                            if (currentPosition < mid - 1000 || currentPosition > mid + 1000) {
                                seekTo(mid)
                            }
                            playWhenReady = true
                        }
                    }

                    override fun onRenderedFirstFrame() {
                        renderCb.value(true)
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        if (isPlaying) renderCb.value(true)
                    }
                })
            }
    }
    DisposableEffect(player) {
        onDispose {
            renderCb.value(false)
            player.release()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            val inflater = android.view.LayoutInflater.from(context)
            val view = inflater.inflate(com.asterplay.tv.R.layout.view_preview_player, null) as PlayerView
            view.useController = false
            view.player = player
            view.resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            view
        },
    )
}




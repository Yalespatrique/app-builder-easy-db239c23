package com.asterplay.tv.player

import android.content.Context
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.asterplay.tv.store.FavoritesStore
import com.asterplay.tv.store.ResumeStore
import com.asterplay.tv.store.WatchedStore
import com.asterplay.tv.ui.components.NeonLoader
import com.asterplay.tv.ui.theme.AsterplayTheme
import com.asterplay.tv.ui.theme.NeonCyan
import com.asterplay.tv.ui.theme.NeonPurple
import com.asterplay.tv.ui.theme.TextPrimary
import com.asterplay.tv.ui.theme.TextSecondary
import kotlinx.coroutines.delay

class PlayerActivity : ComponentActivity() {

    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_NAME = "name"
        const val EXTRA_TYPE = "type" // live | vod | series
        const val EXTRA_EP_URLS = "epUrls"
        const val EXTRA_EP_NAMES = "epNames"
        const val EXTRA_EP_INDEX = "epIndex"
    }

    private var keyHandler: ((Int) -> Boolean)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val url = intent.getStringExtra(EXTRA_URL) ?: run { finish(); return }
        val name = intent.getStringExtra(EXTRA_NAME).orEmpty()
        val type = intent.getStringExtra(EXTRA_TYPE) ?: "vod"
        val epUrls = intent.getStringArrayListExtra(EXTRA_EP_URLS)
        val epNames = intent.getStringArrayListExtra(EXTRA_EP_NAMES)
        val epIndex = intent.getIntExtra(EXTRA_EP_INDEX, -1)

        setContent {
            AsterplayTheme {
                AsterplayPlayerScreen(
                    initialUrl = url,
                    initialName = name,
                    type = type,
                    epUrls = epUrls,
                    epNames = epNames,
                    startIndex = epIndex,
                    registerKeyHandler = { keyHandler = it },
                    onExit = { finish() },
                )
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyHandler?.invoke(keyCode) == true) return true
        return super.onKeyDown(keyCode, event)
    }
}

private fun fmt(ms: Long): String {
    if (ms <= 0) return "00:00"
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun AsterplayPlayerScreen(
    initialUrl: String,
    initialName: String,
    type: String,
    epUrls: List<String>?,
    epNames: List<String>?,
    startIndex: Int,
    registerKeyHandler: ((Int) -> Boolean) -> Unit,
    onExit: () -> Unit,
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val isLive = type == "live"
    val hasPlaylist = type == "series" && !epUrls.isNullOrEmpty() && startIndex >= 0

    var index by remember { mutableIntStateOf(if (hasPlaylist) startIndex else -1) }
    val url = if (hasPlaylist) epUrls!!.getOrElse(index) { initialUrl } else initialUrl
    val title = if (hasPlaylist) epNames?.getOrNull(index) ?: initialName else initialName

    val player = remember {
        ExoPlayer.Builder(ctx).build().apply { playWhenReady = true }
    }

    var buffering by remember { mutableStateOf(true) }
    var playing by remember { mutableStateOf(true) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var controlsVisible by remember { mutableStateOf(true) }
    var lastInteraction by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var autoNextIn by remember { mutableIntStateOf(-1) }
    var autoNextCancelled by remember { mutableStateOf(false) }
    var resumedFrom by remember { mutableLongStateOf(0L) }
    // > 0 => diálogo "retomar ou reiniciar" aberto
    var pendingResume by remember { mutableLongStateOf(0L) }

    fun touch() { lastInteraction = System.currentTimeMillis(); controlsVisible = true }

    // Troca de mídia + pergunta se deve retomar do último tempo assistido
    LaunchedEffect(url) {
        buffering = true
        resumedFrom = 0L
        val resume = if (isLive) 0L else ResumeStore.get(ctx, url)
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        if (resume > 10_000) {
            pendingResume = resume
            player.playWhenReady = false
        } else {
            pendingResume = 0L
            player.playWhenReady = true
        }
        autoNextIn = -1
        autoNextCancelled = false
        touch()
    }


    // Salva a posição ao sair da mídia atual (troca de episódio ou saída do player)
    DisposableEffect(url) {
        onDispose {
            if (!isLive && pendingResume == 0L) {
                ResumeStore.save(ctx, url, player.currentPosition, player.duration)
            }
        }
    }

    DisposableEffect(url) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_ENDED) {
                    WatchedStore.mark(ctx, url)
                    ResumeStore.clear(ctx, url)
                    if (hasPlaylist && index < (epUrls?.size ?: 0) - 1) index++ else onExit()
                }
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    DisposableEffect(Unit) {
        onDispose { player.release() }
    }

    // Ticker de progresso / auto-próximo / auto-hide / salvamento periódico
    LaunchedEffect(url) {
        var tick = 0
        while (true) {
            position = player.currentPosition.coerceAtLeast(0L)
            duration = player.duration.let { if (it > 0) it else 0L }
            if (!isLive && duration > 0 && pendingResume == 0L) {
                // salva o progresso a cada ~5s pra retomar mesmo se o app for encerrado
                tick++
                if (tick % 10 == 0) ResumeStore.save(ctx, url, position, duration)

                val remaining = ((duration - position) / 1000).toInt()
                if (hasPlaylist && index < (epUrls?.size ?: 0) - 1 &&
                    remaining in 0..120 && !autoNextCancelled
                ) {
                    autoNextIn = remaining
                    controlsVisible = true
                    if (remaining <= 0) {
                        WatchedStore.mark(ctx, url)
                        ResumeStore.clear(ctx, url)
                        index++
                    }
                } else if (autoNextIn >= 0 && (remaining > 120 || autoNextCancelled)) {
                    autoNextIn = -1
                }
                if (position > 0 && duration > 0 && position >= duration * 0.95) {
                    WatchedStore.mark(ctx, url)
                }
            }
            if (controlsVisible && autoNextIn < 0 &&
                System.currentTimeMillis() - lastInteraction > 4000 && playing
            ) controlsVisible = false
            delay(500)
        }
    }

    // Teclas do controle remoto
    registerKeyHandler { key ->
        if (pendingResume > 0L) return@registerKeyHandler false // diálogo cuida da navegação
        when (key) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                if (isLive) { touch() } else {
                    if (player.isPlaying) player.pause() else player.play(); touch()
                }
                true
            }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> {
                if (!isLive) { player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0)); touch() }
                true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                if (!isLive) { player.seekTo(player.currentPosition + 10_000); touch() }
                true
            }
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> { touch(); true }
            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                if (hasPlaylist && index < (epUrls?.size ?: 0) - 1) {
                    WatchedStore.mark(ctx, url); ResumeStore.clear(ctx, url); index++
                }
                true
            }
            KeyEvent.KEYCODE_PROG_YELLOW -> { FavoritesStore.toggle(ctx, url); true }
            else -> false
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { c: Context ->
                PlayerView(c).apply {
                    useController = false
                    keepScreenOn = true
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    this.player = player
                }
            },
        )

        // Loader personalizado (substitui o buffering nativo)
        if (buffering && pendingResume == 0L) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    NeonLoader(Modifier.size(90.dp))
                    Spacer(Modifier.height(18.dp))
                    Text(
                        if (isLive) "sintonizando canal..." else "carregando reprodução...",
                        color = TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (title.isNotBlank() && type != "series") {
                        Spacer(Modifier.height(6.dp))
                        Text(title, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (!isLive && resumedFrom > 0) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "retomando de ${fmt(resumedFrom)}",
                            color = NeonCyan,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }

        // Diálogo: retomar do último tempo ou reiniciar
        if (pendingResume > 0L) {
            ResumeDialog(
                title = title,
                positionMs = pendingResume,
                onResume = {
                    val p = pendingResume
                    pendingResume = 0L
                    resumedFrom = p
                    player.seekTo(p)
                    player.playWhenReady = true
                    touch()
                },
                onRestart = {
                    pendingResume = 0L
                    resumedFrom = 0L
                    ResumeStore.clear(ctx, url)
                    player.seekTo(0)
                    player.playWhenReady = true
                    touch()
                },
            )
        }

        // Barra de controles customizada
        AnimatedVisibility(
            visible = controlsVisible && pendingResume == 0L,

            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                        ),
                    )
                    .padding(horizontal = 40.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    title,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge,
                )

                if (isLive) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFFF3B30)))
                        Spacer(Modifier.width(8.dp))
                        Text("AO VIVO", color = TextSecondary, style = MaterialTheme.typography.labelLarge)
                    }
                } else {
                    NeonProgressBar(
                        progress = if (duration > 0) position.toFloat() / duration else 0f,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(fmt(position), color = TextSecondary, style = MaterialTheme.typography.labelLarge)
                        Text(fmt(duration), color = TextSecondary, style = MaterialTheme.typography.labelLarge)
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ControlButton(Icons.Default.Replay10, "Voltar 10s") {
                            player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0)); touch()
                        }
                        Spacer(Modifier.width(18.dp))
                        ControlButton(
                            if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                            if (playing) "Pausar" else "Reproduzir",
                            big = true,
                        ) {
                            if (player.isPlaying) player.pause() else player.play(); touch()
                        }
                        Spacer(Modifier.width(18.dp))
                        ControlButton(Icons.Default.Forward10, "Avançar 10s") {
                            player.seekTo(player.currentPosition + 10_000); touch()
                        }
                        if (hasPlaylist && index < (epUrls?.size ?: 0) - 1) {
                            Spacer(Modifier.width(18.dp))
                            ControlButton(Icons.Default.SkipNext, "Próximo episódio") {
                                WatchedStore.mark(ctx, url); ResumeStore.clear(ctx, url); index++
                            }
                        }
                    }
                }
            }
        }

        // Aviso de próximo episódio automático
        if (autoNextIn >= 0 && hasPlaylist) {
            Column(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 48.dp, bottom = 190.dp),
                horizontalAlignment = Alignment.End,
            ) {
                androidx.tv.material3.Surface(
                    onClick = { WatchedStore.mark(ctx, url); ResumeStore.clear(ctx, url); index++ },
                    colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
                        containerColor = Color(0xE6111222),
                        focusedContainerColor = NeonCyan,
                    ),
                    shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                ) {
                    Row(
                        Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.tv.material3.Icon(
                            Icons.Default.SkipNext, null, tint = TextPrimary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Próximo episódio em ${autoNextIn / 60}:${(autoNextIn % 60).toString().padStart(2, '0')}",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                androidx.tv.material3.Surface(
                    onClick = { autoNextCancelled = true; autoNextIn = -1 },
                    colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
                        containerColor = Color(0xCC1A1A2E),
                        focusedContainerColor = Color(0xFFFF3B30),
                    ),
                    shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                ) {
                    Text(
                        "Cancelar pular automático",
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    )
                }
            }
        }
        }
    }
}

@Composable
private fun NeonProgressBar(progress: Float, modifier: Modifier = Modifier) {
    Box(
        modifier
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color.White.copy(alpha = 0.15f)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(RoundedCornerShape(4.dp))
                .background(Brush.horizontalGradient(listOf(NeonPurple, NeonCyan))),
        )
    }
}

@Composable
private fun ControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    big: Boolean = false,
    onClick: () -> Unit,
) {
    androidx.tv.material3.Surface(
        onClick = onClick,
        colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.12f),
            focusedContainerColor = NeonCyan,
        ),
        shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(CircleShape),
        modifier = Modifier.size(if (big) 62.dp else 50.dp),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            androidx.tv.material3.Icon(
                icon,
                contentDescription = label,
                tint = TextPrimary,
                modifier = Modifier.size(if (big) 32.dp else 26.dp),
            )
        }
    }
}

@Composable
private fun ResumeDialog(
    title: String,
    positionMs: Long,
    onResume: () -> Unit,
    onRestart: () -> Unit,
) {
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xF2101024))
                .padding(horizontal = 44.dp, vertical = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Continuar assistindo?",
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineSmall,
            )
            if (title.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(title, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Você parou em ${fmt(positionMs)}",
                color = NeonCyan,
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(26.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                DialogButton("Retomar", primary = true, modifier = Modifier.focusRequester(focus), onClick = onResume)
                DialogButton("Começar do início", onClick = onRestart)
            }
        }
    }
}

@Composable
private fun DialogButton(
    label: String,
    primary: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    androidx.tv.material3.Surface(
        onClick = onClick,
        modifier = modifier,
        colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
            containerColor = if (primary) NeonPurple.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.12f),
            focusedContainerColor = NeonCyan,
        ),
        shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
    ) {
        Text(
            label,
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 26.dp, vertical = 14.dp),
        )
    }
}

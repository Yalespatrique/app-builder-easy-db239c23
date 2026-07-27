package com.asterplay.tv.ui.screens

import android.content.Context
import android.view.View
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.RawResourceDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.asterplay.tv.R
import com.asterplay.tv.ui.theme.BgBase
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onDone: (Context) -> Unit) {
    val ctx = LocalContext.current
    var showVideo by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }

    fun finishOnce() {
        if (!finished) {
            finished = true
            onDone(ctx)
        }
    }

    // 1) Logo estático por 1.6s, depois o vídeo aparece.
    LaunchedEffect(Unit) {
        delay(1600)
        showVideo = true
    }

    // Fallback duro: se algo travar, avança em 12s.
    LaunchedEffect(Unit) {
        delay(12_000)
        finishOnce()
    }

    Box(
        Modifier.fillMaxSize().background(BgBase),
        contentAlignment = Alignment.Center,
    ) {
        if (!showVideo) {
            // Fase 1: logo em tela cheia.
            Image(
                painter = painterResource(R.drawable.logo_asterplay),
                contentDescription = "Asterplay",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            var player by remember { mutableStateOf<ExoPlayer?>(null) }

            DisposableEffect(Unit) {
                val p = ExoPlayer.Builder(ctx).build().apply {
                    repeatMode = Player.REPEAT_MODE_OFF
                    setMediaItem(MediaItem.fromUri(RawResourceDataSource.buildRawResourceUri(R.raw.intro)))
                    addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(state: Int) {
                            if (state == Player.STATE_ENDED) finishOnce()
                        }
                        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                            finishOnce()
                        }
                    })
                    prepare()
                    playWhenReady = true
                }
                player = p
                onDispose {
                    p.release()
                    player = null
                }
            }

            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { c ->
                    PlayerView(c).apply {
                        useController = false
                        setBackgroundColor(0xFF000000.toInt())
                        setShutterBackgroundColor(0xFF000000.toInt())
                        systemUiVisibility = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                                View.SYSTEM_UI_FLAG_FULLSCREEN or
                                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    }
                },
                update = { it.player = player },
            )
        }
    }
}

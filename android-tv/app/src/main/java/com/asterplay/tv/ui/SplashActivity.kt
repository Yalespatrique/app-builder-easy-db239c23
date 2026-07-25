package com.asterplay.tv.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.asterplay.tv.R
import com.asterplay.tv.store.PlaylistStore

class SplashActivity : AppCompatActivity() {
    private var advanced = false
    private val handler = Handler(Looper.getMainLooper())
    private var player: ExoPlayer? = null
    private val stuckFallback = Runnable { advance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.Theme_Leanback)
        setContentView(R.layout.activity_splash)

        val splashPhase = findViewById<View>(R.id.splashPhase)
        val playerView = findViewById<PlayerView>(R.id.playerIntro)

        // Fase 1: splash estático original. Depois toca o vídeo limpo e só então abre o login.
        handler.postDelayed({ startVideo(splashPhase, playerView) }, 1600)
    }

    private fun startVideo(splashPhase: View, playerView: PlayerView) {
        val uri = Uri.parse("android.resource://" + packageName + "/" + R.raw.intro)
        val exoPlayer = ExoPlayer.Builder(this).build()
        player = exoPlayer
        playerView.player = exoPlayer
        exoPlayer.repeatMode = Player.REPEAT_MODE_OFF
        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> {
                        splashPhase.visibility = View.GONE
                        playerView.visibility = View.VISIBLE
                        handler.removeCallbacks(stuckFallback)
                    }
                    Player.STATE_ENDED -> advance()
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                handler.removeCallbacks(stuckFallback)
                advance()
            }
        })
        exoPlayer.setMediaItem(MediaItem.fromUri(uri))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true

        // Se o player não preparar por algum motivo, não deixa preso no splash.
        handler.postDelayed(stuckFallback, 12000)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        player?.release()
        player = null
        super.onDestroy()
    }

    override fun onStop() {
        if (!advanced) {
            player?.pause()
        }
        super.onStop()
    }

    private fun advance() {
        if (advanced) return
        advanced = true
        val next = if (PlaylistStore.get(this) != null) BrowseActivity::class.java else PairingActivity::class.java
        startActivity(Intent(this, next))
        finish()
    }
}

package com.asterplay.tv.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.asterplay.tv.R

class PlayerActivity : AppCompatActivity() {
    private var player: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        val url = intent.getStringExtra("url") ?: run { finish(); return }
        val view = findViewById<PlayerView>(R.id.playerView)

        player = ExoPlayer.Builder(this).build().also { p ->
            view.player = p
            p.setMediaItem(MediaItem.fromUri(url))
            p.playWhenReady = true
            p.prepare()
        }
    }

    override fun onStop() {
        super.onStop()
        player?.release()
        player = null
    }
}

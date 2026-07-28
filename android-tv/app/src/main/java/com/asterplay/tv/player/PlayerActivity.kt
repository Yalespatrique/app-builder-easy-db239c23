package com.asterplay.tv.player

import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.asterplay.tv.R
import com.asterplay.tv.store.FavoritesStore
import com.asterplay.tv.store.ResumeStore

class PlayerActivity : AppCompatActivity() {
    private lateinit var player: ExoPlayer
    private lateinit var url: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_player)
        url = intent.getStringExtra("url") ?: run { finish(); return }
        val view = findViewById<PlayerView>(R.id.playerView)
        player = ExoPlayer.Builder(this).build()
        view.player = player
        view.keepScreenOn = true
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        val resume = ResumeStore.get(this, url)
        if (resume > 0) player.seekTo(resume)
        player.playWhenReady = true
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_PROG_YELLOW) {
            val added = FavoritesStore.toggle(this, url)
            Toast.makeText(this, if (added) "Adicionado aos favoritos" else "Removido dos favoritos", Toast.LENGTH_SHORT).show()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onPause() {
        super.onPause()
        ResumeStore.save(this, url, player.currentPosition)
        player.pause()
    }

    override fun onDestroy() {
        ResumeStore.save(this, url, player.currentPosition)
        player.release()
        super.onDestroy()
    }
}

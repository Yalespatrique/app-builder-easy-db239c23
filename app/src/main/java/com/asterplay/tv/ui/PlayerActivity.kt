package com.asterplay.tv.ui

import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.asterplay.tv.R
import com.asterplay.tv.data.FavoritesStore
import com.asterplay.tv.data.ResumeStore

class PlayerActivity : AppCompatActivity() {
    private var player: ExoPlayer? = null
    private lateinit var resume: ResumeStore
    private lateinit var favorites: FavoritesStore
    private var streamId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        val url = intent.getStringExtra("url") ?: run { finish(); return }
        streamId = url
        resume = ResumeStore(this)
        favorites = FavoritesStore(this)

        val view = findViewById<PlayerView>(R.id.playerView)
        val startAt = resume.get(streamId)?.positionMs ?: 0L

        player = ExoPlayer.Builder(this).build().also { p ->
            view.player = p
            p.setMediaItem(MediaItem.fromUri(url))
            if (startAt > 0) p.seekTo(startAt)
            p.playWhenReady = true
            p.prepare()
        }
    }

    /** Y = favorito · BACK/HOME já saem naturalmente. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_PROG_YELLOW || keyCode == KeyEvent.KEYCODE_BUTTON_Y) {
            val added = favorites.toggle(streamId)
            Toast.makeText(
                this,
                if (added) R.string.fav_added else R.string.fav_removed,
                Toast.LENGTH_SHORT
            ).show()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onPause() {
        super.onPause()
        savePosition()
    }

    override fun onStop() {
        super.onStop()
        savePosition()
        player?.release()
        player = null
    }

    private fun savePosition() {
        val p = player ?: return
        val pos = p.currentPosition
        val dur = p.duration.coerceAtLeast(0L)
        if (streamId.isNotBlank()) resume.save(streamId, pos, dur)
    }
}

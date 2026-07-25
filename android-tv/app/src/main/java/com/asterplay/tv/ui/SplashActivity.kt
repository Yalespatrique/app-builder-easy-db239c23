package com.asterplay.tv.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import com.asterplay.tv.R
import com.asterplay.tv.store.PlaylistStore

class SplashActivity : AppCompatActivity() {
    private var advanced = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.Theme_Leanback)
        setContentView(R.layout.activity_splash)

        val video = findViewById<VideoView>(R.id.videoIntro)
        val img = findViewById<ImageView>(R.id.imgSplash)

        val uri = Uri.parse("android.resource://" + packageName + "/" + R.raw.intro)
        video.setVideoURI(uri)
        video.setOnPreparedListener { mp ->
            mp.isLooping = false
            img.visibility = android.view.View.GONE
            video.visibility = android.view.View.VISIBLE
        }
        video.setOnCompletionListener { advance() }
        video.setOnErrorListener { _, _, _ ->
            video.visibility = android.view.View.GONE
            img.visibility = android.view.View.VISIBLE
            Handler(Looper.getMainLooper()).postDelayed({ advance() }, 1500)
            true
        }
        video.start()

        // fallback máximo: 8s
        Handler(Looper.getMainLooper()).postDelayed({ advance() }, 8000)
    }

    private fun advance() {
        if (advanced) return
        advanced = true
        val next = if (PlaylistStore.get(this) != null) BrowseActivity::class.java else PairingActivity::class.java
        startActivity(Intent(this, next))
        finish()
    }
}

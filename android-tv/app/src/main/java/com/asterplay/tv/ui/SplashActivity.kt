package com.asterplay.tv.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import com.asterplay.tv.R
import com.asterplay.tv.store.PlaylistStore

class SplashActivity : AppCompatActivity() {
    private var advanced = false
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.Theme_Leanback)
        setContentView(R.layout.activity_splash)

        val splashPhase = findViewById<View>(R.id.splashPhase)
        val logo = findViewById<ImageView>(R.id.imgLogoSpin)
        val video = findViewById<VideoView>(R.id.videoIntro)

        // Fase 1: logo girando sobre o splash
        logo.startAnimation(AnimationUtils.loadAnimation(this, R.anim.spin))

        // Após 2.2s: esconde a logo/splash e toca o vídeo limpo
        handler.postDelayed({ startVideo(splashPhase, video) }, 2200)
    }

    private fun startVideo(splashPhase: View, video: VideoView) {
        val uri = Uri.parse("android.resource://" + packageName + "/" + R.raw.intro)
        video.setVideoURI(uri)
        video.setOnPreparedListener { mp ->
            mp.isLooping = false
            splashPhase.visibility = View.GONE
            video.visibility = View.VISIBLE
        }
        video.setOnCompletionListener { advance() }
        video.setOnErrorListener { _, _, _ ->
            advance(); true
        }
        video.start()

        // Fallback: se algo travar, avança em 10s
        handler.postDelayed({ advance() }, 10000)
    }

    private fun advance() {
        if (advanced) return
        advanced = true
        val next = if (PlaylistStore.get(this) != null) BrowseActivity::class.java else PairingActivity::class.java
        startActivity(Intent(this, next))
        finish()
    }
}

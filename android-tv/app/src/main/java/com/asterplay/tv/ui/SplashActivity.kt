package com.asterplay.tv.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
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
        val video = findViewById<VideoView>(R.id.videoIntro)

        // Fase 1: splash estático com a logo. Após 1.4s, inicia o vídeo.
        handler.postDelayed({ startVideo(splashPhase, video) }, 1400)
    }

    private fun startVideo(splashPhase: View, video: VideoView) {
        val uri = Uri.parse("android.resource://" + packageName + "/" + R.raw.intro)
        video.setVideoURI(uri)
        video.setOnPreparedListener { mp ->
            mp.isLooping = false
            // Só troca de fase quando o vídeo está realmente pronto para tocar
            splashPhase.visibility = View.GONE
            video.visibility = View.VISIBLE
            video.start()
        }
        video.setOnCompletionListener { advance() }
        video.setOnErrorListener { _, _, _ ->
            // Se o vídeo falhar, avança direto após breve delay
            handler.postDelayed({ advance() }, 800)
            true
        }
        // Fallback duro caso o player trave
        handler.postDelayed({ advance() }, 15000)
    }

    private fun advance() {
        if (advanced) return
        advanced = true
        val next = if (PlaylistStore.get(this) != null) BrowseActivity::class.java else PairingActivity::class.java
        startActivity(Intent(this, next))
        finish()
    }
}

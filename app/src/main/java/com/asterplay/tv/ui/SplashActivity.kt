package com.asterplay.tv.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import com.asterplay.tv.R
import com.asterplay.tv.data.AsterStore

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val store = AsterStore(this)
        val video = findViewById<VideoView>(R.id.introVideo)
        val introUri = Uri.parse("android.resource://$packageName/${R.raw.intro}")
        video.setVideoURI(introUri)
        video.setOnPreparedListener { it.isLooping = false }
        video.setOnCompletionListener { proceed() }
        video.setOnErrorListener { _, _, _ -> proceed(); true }
        video.start()

        // Fail-safe: se em 6s não terminou (ex.: TV muito lenta), segue.
        video.postDelayed({
            if (!isFinishing) proceed()
        }, 6000)

        store.introSeen = true
    }

    private var proceeded = false
    private fun proceed() {
        if (proceeded) return
        proceeded = true
        val store = AsterStore(this)
        val next = if (store.m3uUrl.isBlank()) PairingActivity::class.java else BrowseActivity::class.java
        startActivity(Intent(this, next))
        finish()
    }
}

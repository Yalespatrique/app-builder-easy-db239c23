package com.asterplay.tv.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.asterplay.tv.R
import com.asterplay.tv.store.PlaylistStore

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        Handler(Looper.getMainLooper()).postDelayed({
            val next = if (PlaylistStore.get(this) != null) BrowseActivity::class.java else PairingActivity::class.java
            startActivity(Intent(this, next))
            finish()
        }, 1500)
    }
}

package com.asterplay.tv.ui

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.asterplay.tv.R
import com.asterplay.tv.core.DeviceId
import com.asterplay.tv.store.PlaylistStore

class HomeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        val mac = DeviceId.getMac(this)
        findViewById<TextView>(R.id.txtMac).text = "MAC ${DeviceId.formatted(mac)}"

        findViewById<android.view.View>(R.id.cardLive).apply {
            requestFocus()
            setOnClickListener { openBrowse("live") }
        }
        findViewById<android.view.View>(R.id.cardMovies).setOnClickListener { openBrowse("vod") }
        findViewById<android.view.View>(R.id.cardSeries).setOnClickListener { openBrowse("series") }
        findViewById<android.view.View>(R.id.cardSettings).setOnClickListener {
            PlaylistStore.clear(this)
            com.asterplay.tv.store.PlaylistCache.clear(this)
            startActivity(Intent(this, PairingActivity::class.java))
            finish()
        }
    }

    private fun openBrowse(type: String) {
        startActivity(Intent(this, BrowseActivity::class.java).putExtra("type", type))
    }
}

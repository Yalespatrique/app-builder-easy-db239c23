package com.asterplay.tv.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.asterplay.tv.R
import com.asterplay.tv.data.AsterStore
import com.asterplay.tv.data.DeviceId
import com.asterplay.tv.data.PanelApi
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Espelho da LoginScene.brs do Roku:
 *  - Mostra MAC + DeviceKey + QR pro painel.
 *  - Faz polling a cada 5s no PanelApi. Assim que a lista aparece
 *    (m3u_url != null), persiste e navega pra BrowseActivity.
 */
class PairingActivity : AppCompatActivity() {

    private var pollJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pairing)

        val mac = DeviceId.getMac(this)
        val key = DeviceId.deviceKey(mac)

        findViewById<TextView>(R.id.macText).text = mac
        findViewById<TextView>(R.id.keyText).text = key
        findViewById<ImageView>(R.id.qrImage).setImageBitmap(
            makeQr("https://appasterplay.top/?mac=$mac&key=$key", 520)
        )
        startPolling(mac, key)
    }

    override fun onDestroy() {
        pollJob?.cancel()
        super.onDestroy()
    }

    private fun startPolling(mac: String, key: String) {
        val store = AsterStore(this)
        val status = findViewById<TextView>(R.id.statusText)
        pollJob = lifecycleScope.launch {
            while (isActive) {
                val r = PanelApi.fetch(mac, key)
                if (r.ok && !r.m3uUrl.isNullOrBlank()) {
                    store.m3uUrl = r.m3uUrl
                    store.status = r.status.orEmpty()
                    store.daysLeft = r.daysLeft.orEmpty()
                    startActivity(Intent(this@PairingActivity, BrowseActivity::class.java))
                    finish()
                    return@launch
                }
                status.text = r.message ?: getString(R.string.pairing_waiting)
                delay(5000)
            }
        }
    }

    private fun makeQr(text: String, size: Int): Bitmap {
        val bits = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) for (y in 0 until size) {
            bmp.setPixel(x, y, if (bits[x, y]) Color.BLACK else Color.WHITE)
        }
        return bmp
    }
}

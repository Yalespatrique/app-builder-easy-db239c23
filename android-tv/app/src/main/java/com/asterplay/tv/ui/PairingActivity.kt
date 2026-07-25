package com.asterplay.tv.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.asterplay.tv.R
import com.asterplay.tv.core.DeviceId
import com.asterplay.tv.net.PanelApi
import com.asterplay.tv.store.PlaylistStore
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.launch

class PairingActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pairing)

        val mac = DeviceId.getMac(this)
        val key = DeviceId.getKey(mac)

        findViewById<TextView>(R.id.txtMac).text = DeviceId.formatted(mac)
        findViewById<TextView>(R.id.txtKey).text = key
        findViewById<ImageView>(R.id.imgQr).setImageBitmap(generateQr("https://appasterplay.top/pair?mac=$mac&key=$key"))

        findViewById<Button>(R.id.btnCheckMac).setOnClickListener {
            lifecycleScope.launch {
                val r = PanelApi.activateWithMac(mac, key)
                if (r.ok && r.playlistUrl != null) {
                    PlaylistStore.save(this@PairingActivity, r.playlistUrl)
                    startActivity(Intent(this@PairingActivity, BrowseActivity::class.java))
                    finish()
                } else {
                    Toast.makeText(this@PairingActivity, r.message ?: "Ainda não ativado", Toast.LENGTH_SHORT).show()
                }
            }
        }

        findViewById<Button>(R.id.btnLoginCode).setOnClickListener {
            val dns = findViewById<EditText>(R.id.edtDns).text.toString().trim()
            val user = findViewById<EditText>(R.id.edtUser).text.toString().trim()
            val pass = findViewById<EditText>(R.id.edtPass).text.toString().trim()
            if (dns.isEmpty() || user.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "Preencha DNS, usuário e senha", Toast.LENGTH_SHORT).show(); return@setOnClickListener
            }
            lifecycleScope.launch {
                val r = PanelApi.activateWithCode(dns, user, pass)
                if (r.ok && r.playlistUrl != null) {
                    PlaylistStore.save(this@PairingActivity, r.playlistUrl)
                    startActivity(Intent(this@PairingActivity, BrowseActivity::class.java))
                    finish()
                } else {
                    Toast.makeText(this@PairingActivity, r.message ?: "Falha no login", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun generateQr(text: String): Bitmap {
        val size = 400
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) for (y in 0 until size)
            bmp.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
        return bmp
    }
}

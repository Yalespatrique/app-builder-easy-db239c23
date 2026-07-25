package com.asterplay.tv.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.asterplay.tv.BuildConfig
import com.asterplay.tv.R
import com.asterplay.tv.core.DeviceId
import com.asterplay.tv.net.PanelApi
import com.asterplay.tv.store.PlaylistStore
import kotlinx.coroutines.launch

class PairingActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pairing)

        val mac = DeviceId.getMac(this)
        val key = DeviceId.getKey(mac)

        findViewById<TextView>(R.id.txtMac).text = DeviceId.formatted(mac)
        findViewById<TextView>(R.id.txtKey).text = key
        findViewById<TextView>(R.id.txtVersion).text =
            "Asterplay v${BuildConfig.VERSION_NAME}  •  build ${BuildConfig.VERSION_CODE}"

        findViewById<Button>(R.id.btnCheckMac).setOnClickListener {
            lifecycleScope.launch {
                val r = PanelApi.activateWithMac(mac, key)
                if (r.ok && r.playlistUrl != null) {
                    PlaylistStore.save(this@PairingActivity, r.playlistUrl)
                    startActivity(Intent(this@PairingActivity, LoadingActivity::class.java))
                    finish()
                } else {
                    Toast.makeText(this@PairingActivity, r.message ?: "Ainda não ativado", Toast.LENGTH_SHORT).show()
                }
            }
        }

        findViewById<Button>(R.id.btnLoginCode).setOnClickListener {
            val code = findViewById<EditText>(R.id.edtDns).text.toString().trim()
            val user = findViewById<EditText>(R.id.edtUser).text.toString().trim()
            val pass = findViewById<EditText>(R.id.edtPass).text.toString().trim()
            if (code.isEmpty() || user.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "Preencha código, usuário e senha", Toast.LENGTH_SHORT).show(); return@setOnClickListener
            }
            lifecycleScope.launch {
                val r = PanelApi.activateWithCode(code, user, pass)
                if (r.ok && r.playlistUrl != null) {
                    PlaylistStore.save(this@PairingActivity, r.playlistUrl)
                    startActivity(Intent(this@PairingActivity, LoadingActivity::class.java))
                    finish()
                } else {
                    Toast.makeText(this@PairingActivity, r.message ?: "Falha no login", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

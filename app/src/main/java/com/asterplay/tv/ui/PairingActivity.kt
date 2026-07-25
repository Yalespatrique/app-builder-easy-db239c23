package com.asterplay.tv.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.asterplay.tv.R
import com.asterplay.tv.data.AsterStore
import com.asterplay.tv.data.DeviceId
import com.asterplay.tv.data.PanelApi
import com.asterplay.tv.data.PanelResponse
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Tela única de ativação. Dois caminhos que coexistem:
 *  - Esquerda inferior: MAC · Direita inferior: Key (polling automático)
 *  - Topo direito: formulário Código / Usuário / Senha
 *
 * Qualquer um dos dois que retornar m3u_url primeiro vence e navega pra Browse.
 */
class PairingActivity : AppCompatActivity() {

    private var pollJob: Job? = null
    private lateinit var store: AsterStore
    private lateinit var status: TextView
    private lateinit var mac: String
    private lateinit var key: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pairing)

        store = AsterStore(this)
        mac = DeviceId.getMac(this)
        key = DeviceId.deviceKey(mac)

        findViewById<TextView>(R.id.macText).text = mac
        findViewById<TextView>(R.id.keyText).text = key
        status = findViewById(R.id.statusText)

        val codeInput = findViewById<EditText>(R.id.codeInput)
        val userInput = findViewById<EditText>(R.id.userInput)
        val passInput = findViewById<EditText>(R.id.passInput)
        val loginBtn = findViewById<Button>(R.id.loginBtn)

        loginBtn.setOnClickListener {
            val c = codeInput.text.toString().trim()
            val u = userInput.text.toString().trim()
            val p = passInput.text.toString()
            if (c.isEmpty() || u.isEmpty() || p.isEmpty()) {
                Toast.makeText(this, R.string.pairing_fill_all, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            loginWithCode(c, u, p)
        }

        startPolling()
    }

    override fun onDestroy() {
        pollJob?.cancel()
        super.onDestroy()
    }

    private fun startPolling() {
        pollJob = lifecycleScope.launch {
            while (isActive) {
                val r = PanelApi.fetch(mac, key)
                if (accept(r)) return@launch
                status.text = r.message ?: getString(R.string.pairing_waiting)
                delay(5000)
            }
        }
    }

    private fun loginWithCode(code: String, user: String, pass: String) {
        status.text = getString(R.string.pairing_login_progress)
        lifecycleScope.launch {
            val r = PanelApi.activateWithCode(mac, code, user, pass)
            if (!accept(r)) {
                status.text = r.message ?: getString(R.string.pairing_login_error)
            }
        }
    }

    /** Persiste + navega quando a resposta trouxer m3u_url válida. */
    private fun accept(r: PanelResponse): Boolean {
        if (!r.ok || r.m3uUrl.isNullOrBlank()) return false
        store.m3uUrl = r.m3uUrl
        store.status = r.status.orEmpty()
        store.daysLeft = r.daysLeft.orEmpty()
        pollJob?.cancel()
        startActivity(Intent(this, BrowseActivity::class.java))
        finish()
        return true
    }
}

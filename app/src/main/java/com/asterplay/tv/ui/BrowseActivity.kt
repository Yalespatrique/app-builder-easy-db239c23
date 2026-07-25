package com.asterplay.tv.ui

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import com.asterplay.tv.R
import com.asterplay.tv.data.AsterStore
import com.asterplay.tv.ui.fragments.MainBrowseFragment

class BrowseActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_browse)

        val store = AsterStore(this)
        if (store.m3uUrl.isBlank()) {
            startActivity(Intent(this, PairingActivity::class.java))
            finish(); return
        }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.browse_fragment, MainBrowseFragment())
                .commit()
        }
    }

    /** Tecla Y (amarela) alterna favorito no item em foco. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_PROG_YELLOW || keyCode == KeyEvent.KEYCODE_BUTTON_Y) {
            val frag = supportFragmentManager.findFragmentById(R.id.browse_fragment)
                    as? MainBrowseFragment
            frag?.let {
                // A ação real acontece no player (long-click) — aqui só informa.
                Toast.makeText(this, R.string.fav_hint, Toast.LENGTH_SHORT).show()
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}

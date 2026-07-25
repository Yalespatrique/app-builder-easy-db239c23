package com.asterplay.tv.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.asterplay.tv.R
import com.asterplay.tv.net.Channel
import com.asterplay.tv.net.M3UParser
import com.asterplay.tv.player.PlayerActivity
import com.asterplay.tv.store.FavoritesStore
import com.asterplay.tv.store.PlaylistStore
import com.asterplay.tv.store.ResumeStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class BrowseActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_browse)
        val url = PlaylistStore.get(this) ?: run { finish(); return }
        val type = intent.getStringExtra("type") // live | vod | series | null
        val loading = findViewById<TextView>(R.id.txtLoading)
        val container = findViewById<LinearLayout>(R.id.rowsContainer)

        lifecycleScope.launch {
            val channels = withContext(Dispatchers.IO) { downloadAndParse(url) }
            loading.visibility = View.GONE
            if (channels.isEmpty()) {
                val tv = TextView(this@BrowseActivity).apply {
                    text = "Não foi possível carregar sua lista.\nVerifique código, usuário e senha."
                    setTextColor(0xFFFFFFFF.toInt())
                    textSize = 18f
                }
                container.addView(tv)
                return@launch
            }
            renderRows(container, channels, type)
        }
    }

    private fun downloadAndParse(url: String): List<Channel> = try {
        val client = OkHttpClient()
        client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            M3UParser.parse(resp.body?.string().orEmpty())
        }
    } catch (e: Exception) { emptyList() }

    private fun renderRows(container: LinearLayout, all: List<Channel>, type: String?) {
        container.removeAllViews()
        val favs = FavoritesStore.all(this)
        val resumeUrls = ResumeStore.recent(this).toSet()

        val filtered = when (type) {
            "live" -> all.filter { matches(it.group, listOf("live", "ao vivo", "canais", "tv")) || (it.group == null) }
            "vod" -> all.filter { matches(it.group, listOf("filme", "movie", "vod")) }
            "series" -> all.filter { matches(it.group, listOf("serie", "série", "series")) }
            else -> all
        }.ifEmpty { all }

        val resume = filtered.filter { it.url in resumeUrls }
        if (resume.isNotEmpty()) container.addView(buildRow("Continuar assistindo", resume))

        val favList = filtered.filter { it.url in favs }
        if (favList.isNotEmpty()) container.addView(buildRow("Favoritos", favList))

        filtered.groupBy { it.group ?: "Outros" }.forEach { (grp, list) ->
            container.addView(buildRow(grp, list))
        }
    }

    private fun matches(group: String?, needles: List<String>): Boolean {
        val g = group?.lowercase() ?: return false
        return needles.any { g.contains(it) }
    }


    private fun buildRow(title: String, items: List<Channel>): View {
        val v = layoutInflater.inflate(R.layout.row_channels, null)
        v.findViewById<TextView>(R.id.rowTitle).text = title
        val rv = v.findViewById<RecyclerView>(R.id.rowList)
        rv.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rv.adapter = ChannelAdapter(items) { ch ->
            startActivity(Intent(this, PlayerActivity::class.java).apply {
                putExtra("url", ch.url); putExtra("name", ch.name)
            })
        }
        return v
    }
}

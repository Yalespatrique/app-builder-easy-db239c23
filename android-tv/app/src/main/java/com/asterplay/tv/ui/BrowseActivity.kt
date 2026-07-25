package com.asterplay.tv.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.asterplay.tv.R
import com.asterplay.tv.net.Channel
import com.asterplay.tv.net.M3UParser
import com.asterplay.tv.player.PlayerActivity
import com.asterplay.tv.store.PlaylistCache
import com.asterplay.tv.store.PlaylistStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class BrowseActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_browse)
        val url = PlaylistStore.get(this) ?: run { finish(); return }
        val type = intent.getStringExtra("type") // live | vod | series
        val group = intent.getStringExtra("group") // categoria escolhida
        val loading = findViewById<TextView>(R.id.txtLoading)
        val container = findViewById<LinearLayout>(R.id.rowsContainer)

        lifecycleScope.launch {
            val channels = withContext(Dispatchers.IO) {
                PlaylistCache.load(this@BrowseActivity, url)
                    ?: downloadAndParse(url).also {
                        if (it.isNotEmpty()) PlaylistCache.save(this@BrowseActivity, url, it)
                    }
            }
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
            val filtered = filterByType(channels, type)
            if (group != null) {
                renderContents(container, group, filtered.filter { (it.group ?: "Outros") == group })
            } else {
                renderCategories(container, type, filtered)
            }
        }
    }

    private fun downloadAndParse(url: String): List<Channel> = try {
        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
        client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            M3UParser.parse(resp.body?.string().orEmpty())
        }
    } catch (_: Exception) { emptyList() }

    private fun filterByType(all: List<Channel>, type: String?): List<Channel> {
        val f = when (type) {
            "vod" -> all.filter { matches(it.group, listOf("filme", "movie", "vod")) }
            "series" -> all.filter { matches(it.group, listOf("serie", "série", "series")) }
            "live" -> all.filter {
                val g = it.group?.lowercase() ?: return@filter true
                !listOf("filme", "movie", "vod", "serie", "série", "series").any { n -> g.contains(n) }
            }
            else -> all
        }
        return f.ifEmpty { all }
    }

    private fun matches(group: String?, needles: List<String>): Boolean {
        val g = group?.lowercase() ?: return false
        return needles.any { g.contains(it) }
    }

    private fun renderCategories(container: LinearLayout, type: String?, items: List<Channel>) {
        container.removeAllViews()

        val title = TextView(this).apply {
            text = when (type) {
                "vod" -> "FILMES  ·  Categorias"
                "series" -> "SÉRIES  ·  Categorias"
                "live" -> "CANAIS  ·  Categorias"
                else -> "Categorias"
            }
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 22f
            setPadding(0, 0, 0, 16)
        }
        container.addView(title)

        val groups = items.groupBy { it.group?.trim().orEmpty().ifEmpty { "Outros" } }
            .toSortedMap()

        val rv = RecyclerView(this)
        rv.layoutManager = GridLayoutManager(this, 4)
        rv.adapter = CategoryAdapter(groups.map { (name, list) -> name to list.size }) { name ->
            startActivity(Intent(this, BrowseActivity::class.java).apply {
                putExtra("type", type)
                putExtra("group", name)
            })
        }
        container.addView(rv)
    }

    private fun renderContents(container: LinearLayout, group: String, items: List<Channel>) {
        container.removeAllViews()

        val title = TextView(this).apply {
            text = group
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 22f
            setPadding(0, 0, 0, 16)
        }
        container.addView(title)

        val rv = RecyclerView(this)
        rv.layoutManager = GridLayoutManager(this, 5)
        rv.adapter = ChannelAdapter(items) { ch ->
            startActivity(Intent(this, PlayerActivity::class.java).apply {
                putExtra("url", ch.url); putExtra("name", ch.name)
            })
        }
        container.addView(rv)
    }
}

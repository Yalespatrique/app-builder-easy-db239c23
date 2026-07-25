package com.asterplay.tv.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.asterplay.tv.R
import com.asterplay.tv.net.Channel
import com.asterplay.tv.player.PlayerActivity
import com.asterplay.tv.store.PlaylistCache
import com.asterplay.tv.store.PlaylistStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
            }
            loading.visibility = View.GONE
            if (channels == null || channels.isEmpty()) {
                startActivity(Intent(this@BrowseActivity, LoadingActivity::class.java))
                finish()
                return@launch
            }
            val filtered = filterByType(channels, type)
            if (filtered.isEmpty()) {
                val tv = TextView(this@BrowseActivity).apply {
                    text = "Nenhuma categoria encontrada para esta seção."
                    setTextColor(0xFFFFFFFF.toInt())
                    textSize = 18f
                }
                container.addView(tv)
                return@launch
            }
            if (group != null) {
                renderContents(container, group, filtered.filter { (it.group ?: "Outros") == group })
            } else {
                renderCategories(container, type, filtered)
            }
        }
    }

    private fun filterByType(all: List<Channel>, type: String?): List<Channel> {
        return when (type) {
            "vod" -> all.filter { isMovie(it) }.ifEmpty { all.filter { !isSeries(it) } }
            "series" -> all.filter { isSeries(it) }
            "live" -> all.filter {
                !isMovie(it) && !isSeries(it)
            }
            else -> all
        }
    }

    private fun matches(group: String?, needles: List<String>): Boolean {
        val g = group?.lowercase() ?: return false
        return needles.any { g.contains(it) }
    }

    private fun isMovie(item: Channel): Boolean {
        val groupMatch = matches(item.group, listOf("filme", "filmes", "movie", "movies", "vod", "cinema"))
        val url = item.url.lowercase()
        return groupMatch || url.contains("/movie/") || url.endsWith(".mp4") || url.endsWith(".mkv") || url.endsWith(".avi")
    }

    private fun isSeries(item: Channel): Boolean {
        val groupMatch = matches(item.group, listOf("serie", "série", "series", "temporada", "novela"))
        return groupMatch || item.url.lowercase().contains("/series/")
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
        rv.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f
        )
        rv.layoutManager = GridLayoutManager(this, 4)
        rv.adapter = CategoryAdapter(groups.map { (name, list) -> name to list.size }) { name ->
            startActivity(Intent(this, BrowseActivity::class.java).apply {
                putExtra("type", type)
                putExtra("group", name)
            })
        }
        container.addView(rv)
        rv.requestFocus()
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
        rv.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f
        )
        rv.layoutManager = GridLayoutManager(this, 5)
        rv.adapter = ChannelAdapter(items) { ch ->
            startActivity(Intent(this, PlayerActivity::class.java).apply {
                putExtra("url", ch.url); putExtra("name", ch.name)
            })
        }
        container.addView(rv)
        rv.requestFocus()
    }
}

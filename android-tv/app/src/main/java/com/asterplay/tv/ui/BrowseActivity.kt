package com.asterplay.tv.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
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

    private lateinit var listCategories: RecyclerView
    private lateinit var gridContents: RecyclerView
    private lateinit var header: TextView
    private var groups: List<Pair<String, List<Channel>>> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_browse)
        val url = PlaylistStore.get(this) ?: run { finish(); return }
        val type = intent.getStringExtra("type")

        header = findViewById(R.id.txtHeader)
        listCategories = findViewById(R.id.listCategories)
        gridContents = findViewById(R.id.gridContents)

        header.text = when (type) {
            "vod" -> "FILMES"
            "series" -> "SÉRIES"
            "live" -> "CANAIS"
            else -> "CONTEÚDO"
        }

        listCategories.layoutManager = LinearLayoutManager(this)
        gridContents.layoutManager = GridLayoutManager(this, 4)

        lifecycleScope.launch {
            val channels = withContext(Dispatchers.IO) { PlaylistCache.load(this@BrowseActivity, url) }
            if (channels == null || channels.isEmpty()) {
                startActivity(Intent(this@BrowseActivity, LoadingActivity::class.java))
                finish()
                return@launch
            }
            val filtered = filterByType(channels, type)
            groups = filtered.groupBy { it.group?.trim().orEmpty().ifEmpty { "Outros" } }
                .toSortedMap()
                .map { (k, v) -> k to v }

            if (groups.isEmpty()) {
                header.text = "${header.text}  ·  nenhuma categoria"
                return@launch
            }

            val adapter = SideCategoryAdapter(groups.map { it.first to it.second.size }) { idx ->
                showGroup(idx)
            }
            listCategories.adapter = adapter
            showGroup(0)
            listCategories.post {
                listCategories.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()
            }
        }
    }

    private fun showGroup(index: Int) {
        val (name, items) = groups.getOrNull(index) ?: return
        header.text = "${headerTypeLabel()}  ·  $name"
        gridContents.adapter = ChannelAdapter(items) { ch ->
            startActivity(Intent(this, PlayerActivity::class.java).apply {
                putExtra("url", ch.url); putExtra("name", ch.name)
            })
        }
    }

    private fun headerTypeLabel(): String = when (intent.getStringExtra("type")) {
        "vod" -> "FILMES"
        "series" -> "SÉRIES"
        "live" -> "CANAIS"
        else -> "CONTEÚDO"
    }

    private fun filterByType(all: List<Channel>, type: String?): List<Channel> {
        return when (type) {
            "vod" -> all.filter { isMovie(it) }.ifEmpty { all.filter { !isSeries(it) } }
            "series" -> all.filter { isSeries(it) }
            "live" -> all.filter { !isMovie(it) && !isSeries(it) }
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
}

private class SideCategoryAdapter(
    private val items: List<Pair<String, Int>>,
    private val onSelect: (Int) -> Unit
) : RecyclerView.Adapter<SideCategoryAdapter.VH>() {

    private var selected = 0

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val name: TextView = v.findViewById(R.id.catName)
        val count: TextView = v.findViewById(R.id.catCount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_side_category, parent, false)
        return VH(v)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, i: Int) {
        val (name, count) = items[i]
        h.name.text = name.ifBlank { "Outros" }
        h.count.text = "$count itens"
        val bg = if (i == selected) 0xFF1E2A44.toInt() else 0xFF151522.toInt()
        h.itemView.setBackgroundColor(bg)
        h.itemView.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                val prev = selected
                selected = i
                notifyItemChanged(prev)
                notifyItemChanged(i)
                onSelect(i)
            }
        }
        h.itemView.setOnClickListener { onSelect(i) }
    }
}

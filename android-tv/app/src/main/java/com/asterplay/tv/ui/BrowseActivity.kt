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

        val dbType = when (type) { "vod" -> "vod"; "series" -> "series"; else -> "live" }

        lifecycleScope.launch {
            val cats = withContext(Dispatchers.IO) { PlaylistCache.groups(this@BrowseActivity, dbType) }
            if (cats.isEmpty()) {
                if (!PlaylistCache.has(this@BrowseActivity, url)) {
                    startActivity(Intent(this@BrowseActivity, LoadingActivity::class.java))
                    finish()
                    return@launch
                }
                header.text = "${header.text}  ·  nenhuma categoria"
                return@launch
            }
            groups = cats.map { (name, _) -> name to emptyList() }
            val adapter = SideCategoryAdapter(cats) { idx ->
                showGroup(dbType, cats[idx].first)
            }
            listCategories.adapter = adapter
            showGroup(dbType, cats[0].first)
            listCategories.post {
                listCategories.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()
            }
        }
    }

    private fun showGroup(type: String, group: String) {
        header.text = "${headerTypeLabel()}  ·  $group"
        lifecycleScope.launch {
            val items = withContext(Dispatchers.IO) {
                PlaylistCache.byGroup(this@BrowseActivity, type, group)
            }
            gridContents.adapter = ChannelAdapter(items) { ch ->
                val i = Intent(this@BrowseActivity, PlayerActivity::class.java)
                i.putExtra("url", ch.url)
                i.putExtra("name", ch.name)
                startActivity(i)
            }
        }
    }

    private fun headerTypeLabel(): String = when (intent.getStringExtra("type")) {
        "vod" -> "FILMES"
        "series" -> "SÉRIES"
        "live" -> "CANAIS"
        else -> "CONTEÚDO"
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

package com.asterplay.tv.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.asterplay.tv.R
import com.asterplay.tv.data.AsterStore
import com.asterplay.tv.data.Channel
import com.asterplay.tv.data.M3UParser
import kotlinx.coroutines.launch

class BrowseActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_browse)

        val store = AsterStore(this)
        val status = findViewById<TextView>(R.id.statusText)
        val list = findViewById<RecyclerView>(R.id.channelsList)
        list.layoutManager = LinearLayoutManager(this)

        if (store.m3uUrl.isBlank()) {
            startActivity(Intent(this, PairingActivity::class.java))
            finish()
            return
        }

        status.text = "Carregando lista…"
        lifecycleScope.launch {
            val channels = M3UParser.download(store.m3uUrl)
            status.text = if (channels.isEmpty())
                getString(R.string.browse_empty)
            else
                "${channels.size} itens · ${store.status.ifBlank { "ok" }}"
            list.adapter = ChannelAdapter(channels) { ch ->
                val i = Intent(this@BrowseActivity, PlayerActivity::class.java)
                    .putExtra("url", ch.url)
                    .putExtra("title", ch.name)
                startActivity(i)
            }
        }
    }

    private class ChannelAdapter(
        private val items: List<Channel>,
        private val onClick: (Channel) -> Unit,
    ) : RecyclerView.Adapter<ChannelAdapter.VH>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_channel, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val c = items[position]
            holder.name.text = c.name
            holder.group.text = c.group
            holder.itemView.setOnClickListener { onClick(c) }
        }

        override fun getItemCount(): Int = items.size

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val name: TextView = v.findViewById(R.id.channelName)
            val group: TextView = v.findViewById(R.id.channelGroup)
        }
    }
}

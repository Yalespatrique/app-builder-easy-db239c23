package com.asterplay.tv.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.asterplay.tv.R
import com.asterplay.tv.net.Channel

class ChannelAdapter(
    private val items: List<Channel>,
    private val onClick: (Channel) -> Unit
) : RecyclerView.Adapter<ChannelAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val img: ImageView = v.findViewById(R.id.cardImg)
        val name: TextView = v.findViewById(R.id.cardName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.card_channel, parent, false)
        v.isFocusable = true; v.isFocusableInTouchMode = true
        return VH(v)
    }
    override fun getItemCount() = items.size
    override fun onBindViewHolder(h: VH, i: Int) {
        val c = items[i]
        h.name.text = c.name
        c.logo?.let { h.img.load(it) }
        h.itemView.setOnClickListener { onClick(c) }
    }
}

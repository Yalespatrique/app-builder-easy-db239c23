package com.asterplay.tv.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class CategoryAdapter(
    private val items: List<Pair<String, Int>>,
    private val onClick: (String) -> Unit
) : RecyclerView.Adapter<CategoryAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val name: TextView = v.findViewById(android.R.id.text1)
        val count: TextView = v.findViewById(android.R.id.text2)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val ctx = parent.context
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(8, 8, 8, 8) }
            setPadding(20, 24, 20, 24)
            setBackgroundColor(0xFF151522.toInt())
            isFocusable = true
            isFocusableInTouchMode = true
            isClickable = true
        }
        val t1 = TextView(ctx).apply {
            id = android.R.id.text1
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 16f
            maxLines = 2
        }
        val t2 = TextView(ctx).apply {
            id = android.R.id.text2
            setTextColor(0xFF00E676.toInt())
            textSize = 12f
        }
        root.addView(t1)
        root.addView(t2)
        return VH(root)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, i: Int) {
        val (name, count) = items[i]
        h.name.text = name
        h.count.text = "$count itens"
        h.itemView.setOnClickListener { onClick(name) }
    }
}

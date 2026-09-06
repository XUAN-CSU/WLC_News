package com.wlc.news

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class EpisodeAdapter(
    private val onItemClick: (Int) -> Unit,
) : RecyclerView.Adapter<EpisodeAdapter.VH>() {

    private val items = mutableListOf<Episode>()
    var playingIndex: Int = -1
        set(value) {
            val old = field
            field = value
            if (old in items.indices) notifyItemChanged(old)
            if (value in items.indices) notifyItemChanged(value)
        }

    fun submit(list: List<Episode>) {
        items.clear()
        items.addAll(list)
        playingIndex = -1
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_episode, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val ep = items[position]
        holder.name.text = ep.filename
        holder.meta.text = ep.displaySize + if (ep.downloaded) "  •  downloaded" else ""
        holder.state.text = when {
            position == playingIndex -> "▶"
            ep.downloaded -> "✓"
            else -> ""
        }
        holder.row.setBackgroundColor(holder.itemView.context.getColor(R.color.white))
        holder.itemView.setOnClickListener { onItemClick(position) }
    }

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val row: View = itemView.findViewById(R.id.row)
        val name: TextView = itemView.findViewById(R.id.tvName)
        val meta: TextView = itemView.findViewById(R.id.tvMeta)
        val state: TextView = itemView.findViewById(R.id.tvState)
    }
}

package com.watermelon.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class CardAdapter(private val onClick: (Card) -> Unit) : RecyclerView.Adapter<CardAdapter.VH>() {

    private val items = mutableListOf<Card>()

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val img: ImageView = view.findViewById(R.id.cardImage)
        val title: TextView = view.findViewById(R.id.cardTitle)
        val source: TextView = view.findViewById(R.id.cardSource)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_card, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val c = items[position]
        holder.title.text = c.title
        holder.source.text = "[${c.source}]"
        Glide.with(holder.img)
            .load(c.image.ifEmpty { null })
            .centerCrop()
            .into(holder.img)
        holder.itemView.setOnClickListener { onClick(c) }
    }

    fun setItems(list: List<Card>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }
}

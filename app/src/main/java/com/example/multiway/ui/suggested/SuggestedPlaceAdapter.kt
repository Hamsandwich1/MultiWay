package com.example.multiway.ui.suggested

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.example.multiway.R
import com.example.multiway.ui.suggested.SuggestedFragment.SuggestedPlaceItem

class SuggestedPlaceAdapter(
    private val items: List<SuggestedPlaceItem>,
    private val onClick: (SuggestedPlaceItem) -> Unit
) : RecyclerView.Adapter<SuggestedPlaceAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.suggestedPlaceTitle)
        val card: CardView = view.findViewById(R.id.suggestedPlaceCard)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.suggestedroute, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val place = items[position]
        holder.title.text = place.name
        holder.card.setOnClickListener { onClick(place) }
    }
}

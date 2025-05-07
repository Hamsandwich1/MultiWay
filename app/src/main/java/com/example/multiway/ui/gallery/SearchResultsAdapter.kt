package com.example.multiway.ui.gallery

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.multiway.R

// 🔥 Your custom data class

class SearchResultsAdapter(
    private val onItemClick: (SearchResultItem) -> Unit
) : RecyclerView.Adapter<SearchResultsAdapter.SuggestionViewHolder>() {

    // ✅ Correct type (flat list)
    private var suggestions: List<SearchResultItem> = emptyList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SuggestionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_search_result, parent, false)
        return SuggestionViewHolder(view)
    }

    override fun onBindViewHolder(holder: SuggestionViewHolder, position: Int) {
        val item = suggestions[position]
        holder.bind(item)
        holder.itemView.setOnClickListener {
            onItemClick(item)  // 🔥 Make sure this matches the lambda type
        }
    }

    override fun getItemCount(): Int = suggestions.size

    // ✅ Update method
    fun update(newSuggestions: List<SearchResultItem>) {
        suggestions = newSuggestions
        notifyDataSetChanged()
    }

    // ✅ Use SearchResultItem instead of PlaceAutocompleteSuggestion
    class SuggestionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        fun bind(item: SearchResultItem) {
            val placeNameTextView: TextView = itemView.findViewById(R.id.placeName)
            val distanceTextView: TextView = itemView.findViewById(R.id.distanceTextView)

            placeNameTextView.text = item.name
            distanceTextView.text = String.format("%.1f km", item.distanceKm)
        }
    }
}
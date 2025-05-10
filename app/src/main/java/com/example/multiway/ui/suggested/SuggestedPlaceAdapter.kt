//Joey Teahan - 20520316
//SuggestedPlaceAdapter class, this class is used to display and interact with my suggested places class

package com.example.multiway.ui.suggested

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.mapbox.geojson.Point
import com.example.multiway.R
data class SuggestedPlaceItem(
    val name: String,
    val coordinate: Point?,
    val distanceKm: Double,
    var isSelected: Boolean = false

)

class SuggestedPlaceAdapter(
    private val places: List<SuggestedPlaceItem>,
    private val onClick: (SuggestedPlaceItem) -> Unit
) : RecyclerView.Adapter<SuggestedPlaceAdapter.ViewHolder>() {

    inner class ViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        fun bind(item: SuggestedPlaceItem) {
            val nameText = itemView.findViewById<TextView>(R.id.place_name)
            nameText.text = item.name
            itemView.setBackgroundColor(
                if (item.isSelected) Color.parseColor("#D0F0C0") else Color.WHITE
            )
        }

    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_suggestion, parent, false)
        return ViewHolder(view)
    }


    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(places[position])
    }

    override fun getItemCount(): Int = places.size
}

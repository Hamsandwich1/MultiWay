package com.example.multiway.ui.suggested

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.multiway.databinding.SuggestedrouteBinding
import com.mapbox.geojson.Point

data class SuggestedPlaceItem(
    val name: String,
    val coordinate: Point?,
    val distanceKm: Double
)

class SuggestedPlaceAdapter(
    private val places: List<SuggestedPlaceItem>,
    private val onClick: (SuggestedPlaceItem) -> Unit
) : RecyclerView.Adapter<SuggestedPlaceAdapter.ViewHolder>() {

    inner class ViewHolder(private val binding: SuggestedrouteBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: SuggestedPlaceItem) {
            binding.textName.text = item.name
            binding.textDistance.text = "Distance: %.2f km".format(item.distanceKm)

            binding.root.setOnClickListener {
                onClick(item)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = SuggestedrouteBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(places[position])
    }

    override fun getItemCount(): Int = places.size
}

package com.example.multiway.ui.gallery

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.multiway.R
import com.example.multiway.ui.gallery.GalleryFragment.SimilarPlace


class SimilarPlacesAdapter(
    private var places: List<SimilarPlace>
) : RecyclerView.Adapter<SimilarPlacesAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val placeName: TextView = view.findViewById(R.id.similarPlaceName)
        val distance: TextView = view.findViewById(R.id.similarPlaceDistance)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_similar_place, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = places.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val place = places[position]
        holder.placeName.text = place.name
        holder.distance.text = "${"%.1f".format(place.distanceKm)} km"
    }
}

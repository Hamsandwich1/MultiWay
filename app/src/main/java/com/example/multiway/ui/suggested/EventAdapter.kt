package com.example.multiway.ui.suggested

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.multiway.R

data class EventItem(
    val name: String,
    val date: String,
    val venue: String,
    val url: String
)

class EventAdapter(
    private val context: Context,
    private val events: List<EventItem>
) : RecyclerView.Adapter<EventAdapter.EventViewHolder>() {

    inner class EventViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.itemEventTitle)
        val date: TextView = itemView.findViewById(R.id.itemEventDate)
        val venue: TextView = itemView.findViewById(R.id.itemEventVenue)
        val viewMore: Button = itemView.findViewById(R.id.itemViewMore)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.eventitem, parent, false)
        return EventViewHolder(view)
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        val event = events[position]
        holder.title.text = "🎤 ${event.name}"
        holder.date.text = "📅 ${event.date}"
        holder.venue.text = "📍 ${event.venue}"

        holder.viewMore.setOnClickListener {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(event.url))
            context.startActivity(browserIntent)
        }
    }

    override fun getItemCount(): Int = events.size
}

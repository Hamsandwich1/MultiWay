//Joey Teahan - 20520316
// EventAdapter class this class has the information that i use in my events class
package com.example.multiway.ui.events

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

//The info that is displayed when an event is selected
data class EventItem(
    val name: String,
    val date: String,
    val venue: String,
    val url: String
)

//Class that displays the events
class EventAdapter(
    private val context: Context,
    private val events: List<EventItem>
) : RecyclerView.Adapter<EventAdapter.EventViewHolder>() {

    //Has the views that are displayed in the recycler view
    inner class EventViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.itemEventTitle)
        val date: TextView = itemView.findViewById(R.id.itemEventDate)
        val venue: TextView = itemView.findViewById(R.id.itemEventVenue)
        val viewMore: Button = itemView.findViewById(R.id.itemViewMore)
    }

    //When the recycler view needs a new view holder to display an event
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        //Inflates the view
        val view = LayoutInflater.from(parent.context).inflate(R.layout.eventitem, parent, false)
        //Returns the view holder
        return EventViewHolder(view)
    }

    //When the recycler view needs to bind data to a view holder
    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        //The position of the event
        val event = events[position]
        //The details that are displayed, i added emojis so it wouldnt look so plain
        holder.title.text = "🎤 ${event.name}"
        holder.date.text = "📅 ${event.date}"
        holder.venue.text = "📍 ${event.venue}"

        //When the user clicks the button it takes them to the event website
        holder.viewMore.setOnClickListener {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(event.url))
            context.startActivity(browserIntent)
        }
    }

    //Gets the number of events
    override fun getItemCount(): Int = events.size
}

//Joey Teahan - 20520316
//HistoryAdapter class - This class helps the History Fragment class
package com.example.multiway.ui.history

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.example.multiway.R

class HistoryAdapter(private val items: List<HistoryItem>) :
    RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

        //Setting up the ViewHolder class
    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.history_title)
        val subtitle: TextView = view.findViewById(R.id.history_subtitle)
        val card: CardView = view.findViewById(R.id.history_card)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.history_card, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = items.size



    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.title.text = item.title
        holder.subtitle.text = item.subtitle
    }
}

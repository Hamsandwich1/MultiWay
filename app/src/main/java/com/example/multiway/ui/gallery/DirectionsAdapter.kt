package com.example.multiway.ui.gallery

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.multiway.R

class DirectionsAdapter(private val steps: List<String>) : RecyclerView.Adapter<DirectionsAdapter.StepViewHolder>() {

    inner class StepViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val stepText: TextView = view.findViewById(R.id.stepText)
        val stepIcon: ImageView = view.findViewById(R.id.stepIcon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StepViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.directions, parent, false)
        return StepViewHolder(view)
    }

    override fun onBindViewHolder(holder: StepViewHolder, position: Int) {
        holder.stepText.text = steps[position]
        holder.stepIcon.setImageResource(R.drawable.step) // Use your own icons if you want to differentiate
    }

    override fun getItemCount(): Int = steps.size
}

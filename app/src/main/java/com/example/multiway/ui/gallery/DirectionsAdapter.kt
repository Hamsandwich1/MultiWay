//Joey Teahan - 20520316
//DirectionsAdapter class, this class is used to display POI directions

package com.example.multiway.ui.gallery //Package name

import android.view.LayoutInflater //Import the LayoutInflater class
import android.view.View //The view function
import android.view.ViewGroup //Import the ViewGroup class
import android.widget.ImageView
import android.widget.TextView //Import the TextView class
import androidx.recyclerview.widget.RecyclerView //Import the RecyclerView class
import com.example.multiway.R

//The DirectionsAdapter class
class DirectionsAdapter(private val steps: List<String>) : RecyclerView.Adapter<DirectionsAdapter.StepViewHolder>() {

    //Sets up the text and icons
    inner class StepViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        //Sets the text and icons
        val stepText: TextView = view.findViewById(R.id.stepText)
        val stepIcon: ImageView = view.findViewById(R.id.stepIcon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StepViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.directions, parent, false)
        return StepViewHolder(view)
    }

    //The symbol that will show up beside directions
    override fun onBindViewHolder(holder: StepViewHolder, position: Int) {
        holder.stepText.text = steps[position]
        holder.stepIcon.setImageResource(R.drawable.step) // Use your own icons if you want to differentiate
    }

    override fun getItemCount(): Int = steps.size
}

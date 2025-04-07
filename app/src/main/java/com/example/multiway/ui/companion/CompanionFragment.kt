package com.example.multiway.ui.companion

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.multiway.R

class CompanionFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_companion, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Find views
        val horizontalScrollView = view.findViewById<HorizontalScrollView>(R.id.horizontalscrollview)
        val backbutton = view.findViewById<Button>(R.id.backbutton)

        // Detailed views for each item
        val detailedView1 = view.findViewById<LinearLayout>(R.id.detailedview1)
        val detailedView2 = view.findViewById<LinearLayout>(R.id.detailedview2)
        val detailedView3 = view.findViewById<LinearLayout>(R.id.detailedview3)
        val detailedView4 = view.findViewById<LinearLayout>(R.id.detailedview4)

        // Title and content text views for each detailed section
        val detailedTitle1 = view.findViewById<TextView>(R.id.detailedtitle1)
        val detailedContent1 = view.findViewById<TextView>(R.id.detailedcontent1)

        val detailedTitle2 = view.findViewById<TextView>(R.id.detailedtitle2)
        val detailedContent2 = view.findViewById<TextView>(R.id.detailedcontent2)

        val detailedTitle3 = view.findViewById<TextView>(R.id.detailedtitle3)
        val detailedContent3 = view.findViewById<TextView>(R.id.detailedcontent3)

        val detailedTitle4 = view.findViewById<TextView>(R.id.detailedtitle4)
        val detailedContent4 = view.findViewById<TextView>(R.id.detailedcontent4)
        // Other views to hide
        val welcomeText = view.findViewById<TextView>(R.id.title)
        val peopleText = view.findViewById<TextView>(R.id.welcomeText)

        // Item views
        val item1 = view.findViewById<TextView>(R.id.item1)
        val item2 = view.findViewById<TextView>(R.id.item2)
        val item3 = view.findViewById<TextView>(R.id.item3)
        val item4 = view.findViewById<TextView>(R.id.item4)

        // Show detailed view when an item is clicked
        item1.setOnClickListener {
            showDetailedView(
                "Person 1", "Male", "Travelling with a group of friends", "Irish",
                horizontalScrollView, detailedView1, detailedTitle1, detailedContent1,
                welcomeText, peopleText, backbutton
            )
        }

        item2.setOnClickListener {
            showDetailedView(
                "Person 2", "Female", "Travelling with a partner", "English",
                horizontalScrollView, detailedView2, detailedTitle2, detailedContent2,
                welcomeText, peopleText, backbutton
            )
        }

        item3.setOnClickListener {
            showDetailedView(
                "Person 3", "Male", "Travelling solo", "French",
                horizontalScrollView, detailedView3, detailedTitle3, detailedContent3,
                welcomeText, peopleText, backbutton
            )
        }

        item4.setOnClickListener {
            showDetailedView(
                "Person 4", "Female", "Travelling with a group of friends", "Spanish",
                horizontalScrollView, detailedView4, detailedTitle4, detailedContent4,
                welcomeText, peopleText, backbutton
            )
        }

        // Back button to return to the scrollable view
        backbutton.setOnClickListener {
            horizontalScrollView.visibility = View.VISIBLE
            detailedView1.visibility = View.GONE
            detailedView2.visibility = View.GONE
            detailedView3.visibility = View.GONE
            detailedView4.visibility = View.GONE
            welcomeText.visibility = View.VISIBLE
            peopleText.visibility = View.VISIBLE
            backbutton.visibility = View.GONE
        }
    }

    private fun showDetailedView(
        title: String,
        gender: String,
        travelStyle: String,
        nationality: String,
        horizontalScrollView: HorizontalScrollView,
        detailedView: LinearLayout,
        detailedTitle: TextView,
        detailedContent: TextView,
        welcomeText: TextView,
        peopleText: TextView,
        backbutton: Button

    ) {
        // Hide the text views
        welcomeText.visibility = View.GONE
        peopleText.visibility = View.GONE

        // Update content
        detailedTitle.text = title
        detailedContent.text = "Gender: $gender\nTravel Style: $travelStyle\nNationality: $nationality"

        // Hide the scroll view and show the detailed view
        horizontalScrollView.visibility = View.GONE
        detailedView.visibility = View.VISIBLE
        backbutton.visibility = View.VISIBLE
    }
}

package com.example.multiway

import androidx.test.espresso.Espresso.onData
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.anything



@RunWith(AndroidJUnit4::class)
class EventsTesting {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)


    @Test
    //To see if the map loads
    fun mapViewIsVisible() {
        onView(allOf(withId(R.id.mapView), isAssignableFrom(com.mapbox.maps.MapView::class.java)))
            .check(matches(isDisplayed()))
    }



    @Test
    fun radiusButton() {
        //Test to see if the radius button can be selected
        onView(withId(R.id.radiusButton))
            .check(matches(isDisplayed()))
            .perform(click())

        onView(withText("Select Radius"))
            .check(matches(isDisplayed()))
    }



    @Test
    fun categories() {
        onView(withId(R.id.categorySpinner))
            .perform(click())
        //Clicks the second option in the spinner
        onData(anything()).atPosition(1)
            .perform(click())
    }






}

package com.example.multiway

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.multiway.ui.gallery.GalleryFragment
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GalleryFragmentTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun UIStuffIsVisible() {
        // Check if key UI components are visible
        onView(withId(R.id.btnSearch)).check(matches(isDisplayed()))
        onView(withId(R.id.queryEditText)).check(matches(isDisplayed()))
    }


    @Test
    fun sideButtons_areClickable() {
        //Test to see if the buttons can be selected
        onView(withId(R.id.toggleMenuButton)).perform(click())
        onView(withId(R.id.location)).perform(click())
        onView(withId(R.id.btnToggleBottomSheetVisibility)).perform(click())
        onView(withId(R.id.radiusButton1)).perform(click())

    }

    @Test
    fun ClickSearchButton() {
        //Clicks on the search button
        onView(withId(R.id.btnSearch)).perform(click())
    }


    @Test
    fun testCategorySpinnerSelection() {
        // Click spinner and choose 'Pubs'
        onView(withId(R.id.categorySpinner)).perform(click())
        onView(withText("Pubs")).perform(click())
    }
}

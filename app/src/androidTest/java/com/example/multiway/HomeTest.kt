package com.example.multiway

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class HomeTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)


    @Test
    fun testHomeButtonsAreVisible() {
        //Test to see if the user can actually see the buttons
        onView(withId(R.id.gallerybtn)).check(matches(isDisplayed()))
        onView(withId(R.id.routesbtn)).check(matches(isDisplayed()))
        onView(withId(R.id.historybtn)).check(matches(isDisplayed()))
        onView(withId(R.id.eventsbtn)).check(matches(isDisplayed()))
    }

    //Was not sure how to test all the buttons at the same time so i split it into 4 tests
    @Test
    fun testGalleryButtonNavigates() {
        onView(withId(R.id.gallerybtn))
            .check(matches(isDisplayed()))
            .perform(click())

    }

    @Test
    fun testRoutesButtonNavigates() {
        onView(withId(R.id.routesbtn))
            .check(matches(isDisplayed()))
            .perform(click())

    }

    @Test
    fun testHistoryButtonNavigates() {
        onView(withId(R.id.historybtn))
            .check(matches(isDisplayed()))
            .perform(click())
    }

    @Test
    fun testEventsButtonNavigates() {
        onView(withId(R.id.eventsbtn))
            .check(matches(isDisplayed()))
            .perform(click())
    }

}



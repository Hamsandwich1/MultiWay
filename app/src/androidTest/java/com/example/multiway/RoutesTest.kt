package com.example.multiway

import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.test.rule.GrantPermissionRule

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.hamcrest.CoreMatchers.allOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class SuggestedFragmentTest {


    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun moodSection() {
        //I had to add a 15 sec delay as the location permission kept coming up
        //and the test would fail
        Thread.sleep(15_000)
        onView(withId(R.id.moodButton)).perform(click())
        onView(withText("Chill Afternoon")).perform(click())
        onView(withId(R.id.moodButton)).check(matches(withText("Chill Afternoon")))
    }

    @Test
    fun chipGroup() {
        Thread.sleep(15_000)
        //Checks if the chip group is hidden
        onView(withId(R.id.routeChipGroup)).check(matches(withEffectiveVisibility(Visibility.GONE)))
        //Clicks the chip group
        onView(withId(R.id.toggleChipsButton)).perform(click())
        //Checks to see if it opens
        onView(withId(R.id.routeChipGroup)).check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
        //Checks to see if clicking the button again closes it
        onView(withId(R.id.toggleChipsButton)).perform(click())
        //Checks to see if its hidden again
        onView(withId(R.id.routeChipGroup)).check(matches(withEffectiveVisibility(Visibility.GONE)))
    }

    @Test
    fun pubCrawlWarningText() {
        //Test that sees is the warning text appears when you select pub crawl
        Thread.sleep(15_000)

        // Make sure chips are visible
        onView(withId(R.id.toggleChipsButton)).perform(scrollTo(), click())

        onView(withId(R.id.chipPubCrawl)).perform(scrollTo(), click())

        // Verify the warning text appears
        onView(withId(R.id.pubCrawlHeader))
            .check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
    }





}

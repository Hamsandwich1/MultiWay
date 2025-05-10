package com.example.multiway

import android.view.View
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers
import androidx.test.espresso.matcher.RootMatchers.withDecorView
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import org.hamcrest.CoreMatchers.`is`
import org.junit.Rule
import org.junit.Test
import org.hamcrest.Matchers.not


class LoginTest {

    @get:Rule
    val activityScenarioRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun loginWithEmptyFields_showsError() {
        onView(withId(R.id.login_button)).perform(click())

        // Let the toast appear (small delay)
        Thread.sleep(2000)

        // Then perform the toast check outside the onActivity block
        onView(withText("Please enter email and password"))
            .inRoot(RootMatchers.withDecorView(not(`is`(getCurrentDecorView()))))
            .check(matches(isDisplayed()))
    }

    // Helper function to get the decor view safely
    private fun getCurrentDecorView(): View? {
        var decorView: View? = null
        activityScenarioRule.scenario.onActivity { activity ->
            decorView = activity.window.decorView
        }
        return decorView
    }


}
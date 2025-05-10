package com.example.multiway

import android.app.Activity
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.withDecorView
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.core.IsNot.not
import org.junit.Rule
import org.junit.Test
import android.app.PendingIntent.getActivity
import android.view.View
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.typeText
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.Matcher
import org.junit.Before


class SignupTest {

    @get:Rule
    val scenarioRule = ActivityScenarioRule(MainActivity::class.java)
    private var decorView: View? = null

    @Before
    fun setup() {
        scenarioRule.scenario.onActivity {
            decorView = it.window.decorView
        }
    }



    @Test
    fun showPolicyText() {
        //To see if the button that shows the policy is working
        //Clicks the button
        onView(withId(R.id.privacy_policy_toggle)).perform(click())
        //Checks if the text is displayed
        onView(withId(R.id.privacypolicytext)).check(matches(isDisplayed()))
    }



    @Test
    fun invalidEmail() {
        //Puts info into fields, with an invalid email on purpose
        onView(withId(R.id.signup_name)).perform(typeText("Test User"))
        onView(withId(R.id.signup_email)).perform(typeText("invalid-email"))
        onView(withId(R.id.signup_username)).perform(typeText("TestUser"))
        onView(withId(R.id.signup_password)).perform(typeText("password123"))
        onView(withId(R.id.privacy_checkbox)).perform(click())
        //Deletes the keyboard
        closeSoftKeyboard()
        //Clicks the signup button
        onView(withId(R.id.signup_button)).perform(click())
        //Checks if the error message is displayed
        onView(withId(R.id.signup_email)).check(matches(isDisplayed()))

    }

    @Test
    fun validInput() {
        //Proper details
        onView(withId(R.id.signup_name)).perform(typeText("Test User"))
        onView(withId(R.id.signup_email)).perform(typeText("testuser@example.com"))
        onView(withId(R.id.signup_username)).perform(typeText("TestUser123"))
        onView(withId(R.id.signup_password)).perform(typeText("SecurePassword123"))
        onView(withId(R.id.privacy_checkbox)).perform(click())
        closeSoftKeyboard()
        //Clicking the signup button
        onView(withId(R.id.signup_button)).perform(click())
    }


}
package com.example.multiway

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.matches

import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.rules.ActivityScenarioRule

import org.junit.Rule
import org.junit.Test


class LoginTest {

    @get:Rule
    val activityScenarioRule = ActivityScenarioRule(MainActivity::class.java)



    @Test
    fun emptyInputs() {
        //Test if the user does not enter any inputs and clicks the log in button
        onView(withId(R.id.login_button)).perform(click())
        Thread.sleep(1500)
        onView(withId(R.id.login_button)).check(matches(isDisplayed()))
    }

    @Test
    fun invalidInputs() {
        //Tests if the user enters invalid inputs and clicks the log in button
        //Set the input values to "example@email.com" and "examplepassword"
        onView(withId(R.id.login_username)).perform(typeText("example@email.com"), closeSoftKeyboard())
        onView(withId(R.id.login_password)).perform(typeText("examplepassword"), closeSoftKeyboard())
        onView(withId(R.id.login_button)).perform(click())
        Thread.sleep(1500)
        // Checks to see if the user is still on the log in page
        onView(withId(R.id.login_button)).check(matches(isDisplayed()))
    }



}
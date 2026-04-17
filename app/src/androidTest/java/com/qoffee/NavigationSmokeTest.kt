package com.qoffee

import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class NavigationSmokeTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun topLevelNavigationShowsExpectedScreens() {
        composeRule.onNodeWithText("Analytics").assertExists()
        composeRule.onNodeWithText("Records").performClick()
        composeRule.onNodeWithText("Record filters").assertExists()

        composeRule.onNodeWithText("Catalog").performClick()
        composeRule.onNodeWithText("Build a reusable catalog").assertExists()

        composeRule.onNodeWithText("Settings").performClick()
        composeRule.onNodeWithText("Tune the local experience").assertExists()
    }
}

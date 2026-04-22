package com.qoffee

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.qoffee.ui.QoffeeTestTags
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
        composeRule.onNodeWithTag(QoffeeTestTags.BREW_SCREEN).fetchSemanticsNode()

        composeRule.onNodeWithTag(QoffeeTestTags.NAV_HISTORY).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(QoffeeTestTags.HISTORY_SCREEN).fetchSemanticsNode()

        composeRule.onNodeWithTag(QoffeeTestTags.NAV_MY).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(QoffeeTestTags.MY_SCREEN).fetchSemanticsNode()
    }
}

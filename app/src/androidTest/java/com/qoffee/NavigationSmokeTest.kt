package com.qoffee

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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
        composeRule.onNodeWithTag(QoffeeTestTags.HISTORY_INSIGHTS).fetchSemanticsNode()

        composeRule.onNodeWithTag(QoffeeTestTags.NAV_MY).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(QoffeeTestTags.MY_SCREEN).fetchSemanticsNode()
    }

    @Test
    fun settingsEnvironmentScreenCanOpen() {
        composeRule.onNodeWithTag(QoffeeTestTags.NAV_MY).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(QoffeeTestTags.PROFILE_SETTINGS).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(QoffeeTestTags.SETTINGS_SCREEN).fetchSemanticsNode()

        composeRule.onNodeWithTag(QoffeeTestTags.SETTINGS_ENVIRONMENT_ITEM).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(QoffeeTestTags.SETTINGS_ENVIRONMENT_SCREEN).fetchSemanticsNode()
    }

    @Test
    fun addRecordEntryCanOpenEditor() {
        composeRule.onNodeWithTag(QoffeeTestTags.BREW_SCREEN).fetchSemanticsNode()

        composeRule.onNodeWithTag(QoffeeTestTags.BREW_ADD_RECORD).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(QoffeeTestTags.RECORD_EDITOR_SCREEN).fetchSemanticsNode()
        composeRule.onNodeWithTag(QoffeeTestTags.RECORD_EDITOR_SUMMARY).fetchSemanticsNode()
        composeRule.onNodeWithTag(QoffeeTestTags.RECORD_EDITOR_BOTTOM_ACTION).fetchSemanticsNode()
    }

    @Test
    fun brewScreenShowsRecordLoopWorkbench() {
        composeRule.onNodeWithTag(QoffeeTestTags.BREW_SCREEN).fetchSemanticsNode()
        composeRule.onNodeWithText("记录工作台").fetchSemanticsNode()
        composeRule.onNodeWithText("今日行动").fetchSemanticsNode()
        composeRule.onNodeWithText("快速开始").fetchSemanticsNode()
        composeRule.onNodeWithTag(QoffeeTestTags.BREW_HERO_ACTION).fetchSemanticsNode()
        composeRule.onNodeWithTag(QoffeeTestTags.BREW_SOURCE_RAIL).fetchSemanticsNode()
    }
}

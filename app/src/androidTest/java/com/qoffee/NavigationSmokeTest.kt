package com.qoffee

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
        composeRule.onNodeWithText("分析")
        composeRule.onNodeWithText("记录").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("记录筛选")

        composeRule.onNodeWithText("资料库").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("维护你的资料库")

        composeRule.onNodeWithText("设置").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("调整本地使用体验")
    }
}

package com.example.audioloop

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Accessibility Test Matrix for AudioLoop.
 * 
 * This test ensures that the application meets the accessibility standards 
 * required for a professional mass-market application.
 */
@RunWith(AndroidJUnit4::class)
class AccessibilityTest {

    @Rule
    @JvmField
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun verifyMainNavigationLabels() {
        // Wait for onboarding to be finished or handled
        if (composeTestRule.onAllNodesWithText("Continue").fetchSemanticsNodes().isNotEmpty()) {
             composeTestRule.onNodeWithText("Continue").assertHasClickAction()
        }

        // Check for bottom navigation labels
        composeTestRule.onNodeWithContentDescription("Library", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithContentDescription("Coach", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithContentDescription("Settings", useUnmergedTree = true).assertExists()
    }

    @Test
    fun verifyRecordingButtonsHaveLabels() {
        // Using "Record" as content description for navigation item
        composeTestRule.onNodeWithContentDescription("Record").assertExists()
    }
}

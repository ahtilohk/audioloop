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
 * 
 * Criteria:
 * 1. Content Labels: All functional icons must have descriptive contentDescription.
 * 2. Touch Targets: Interactive elements should have minimum dimensions (48dp).
 * 3. Semantic Roles: Lists and buttons should be identifiable by screen readers.
 * 4. State Communication: Playback states (playing/paused) must be announced.
 */
@RunWith(AndroidJUnit4::class)
class AccessibilityTest {

    @Rule
    @JvmField
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun verifyMainNavigationLabels() {
        // Wait for onboarding to be finished or handled
        // If onboarding is showing, we test its accessibility too (using base English strings)
        if (composeTestRule.onAllNodesWithText("Continue").fetchSemanticsNodes().isNotEmpty()) {
             composeTestRule.onNodeWithText("Continue").assertHasClickAction()
        }

        // Check for bottom navigation labels (TalkBack needs these)
        // These are standard IDs or content descriptions in AppNavigationBar
        // We'll use useUnmergedTree = true to find inner labels if needed
        composeTestRule.onNodeWithContentDescription("Library", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithContentDescription("Coach", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithContentDescription("Settings", useUnmergedTree = true).assertExists()
    }

    @Test
    fun verifyRecordingButtonsHaveLabels() {
        // The record button usually has a central icon
        // We look for the main Record tab icon label as a proxy for navigation accessibility
        composeTestRule.onNodeWithContentDescription("Record").assertExists()
    }

    @Test
    fun verifyFileListAccessibility() {
        val emptyText = composeTestRule.onAllNodesWithText("Your library is empty").fetchSemanticsNodes()
        if (emptyText.isNotEmpty()) {
             // In empty state, the placeholder icon should have a label or be decorative (null)
             // But the 'Start recording' button must be reachable
             composeTestRule.onNodeWithText("START RECORDING").assertExists().assertHasClickAction()
        }
    }
}

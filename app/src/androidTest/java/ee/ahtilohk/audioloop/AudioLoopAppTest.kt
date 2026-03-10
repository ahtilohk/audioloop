package ee.ahtilohk.audioloop

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AudioLoopAppTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun appStartsAndShowsLibraryAfterOnboarding() {
        // Handle onboarding if it shows up
        // Note: The visibility of onboarding depends on SharedPreferences. 
        // For a clean test, it should show up the first time.

        val welcomeTitle = composeTestRule.activity.getString(R.string.onboarding_welcome_title)
        val continueBtn = composeTestRule.activity.getString(R.string.btn_continue)
        val gotItBtn = composeTestRule.activity.getString(R.string.btn_got_it)
        val studentUseCase = composeTestRule.activity.getString(R.string.onboarding_usecase_student)
        val appTitle = composeTestRule.activity.getString(R.string.header_title)
        val coachTab = composeTestRule.activity.getString(R.string.nav_coach)

        // 1. Welcome Step
        if (composeTestRule.onAllNodesWithText(welcomeTitle).fetchSemanticsNodes().isNotEmpty()) {
            composeTestRule.onNodeWithText(continueBtn).performClick()
            
            // 2. Use Case Step
            composeTestRule.onNodeWithText(studentUseCase).performClick()
            
            // 3. Value Prop Step
            composeTestRule.onNodeWithText(continueBtn).performClick()
            
            // 4. Final Step
            composeTestRule.onNodeWithText(gotItBtn).performClick()
        }

        // Wait for library to show up
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText(appTitle).fetchSemanticsNodes().isNotEmpty()
        }

        // Verify main header is visible
        composeTestRule.onNodeWithText(appTitle).assertIsDisplayed()

        // 2. Navigate to Coach Tab
        composeTestRule.onNodeWithText(coachTab).performClick()

        // Verify Coach Tab content (e.g., "Smart Coach")
        val coachHeader = composeTestRule.activity.getString(R.string.coach_title)
        composeTestRule.onNodeWithText(coachHeader).assertIsDisplayed()
    }
}

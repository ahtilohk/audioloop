package ee.ahtilohk.audioloop

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class CoachEngineTest {

    private lateinit var context: Context
    private lateinit var stats: PracticeStatsManager
    private lateinit var engine: CoachEngine

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        stats = mockk(relaxed = true)
        engine = CoachEngine(context, stats)
    }

    @Test
    fun `recommendation suggests loop aha moment if not used`() {
        // Arrange
        every { stats.weeklyMinutes() } returns 0f
        every { stats.weeklyGoalMinutes() } returns 120
        every { stats.hasEventOccurred("aha_loop_used") } returns false
        every { context.getString(R.string.aha_loop_title) } returns "Try Looping"

        // Act
        val recommendation = engine.recommend()

        // Assert
        assertEquals("Try Looping", recommendation.title)
    }

    @Test
    fun `recommendation suggests speed aha moment if loop used but speed not`() {
        // Arrange
        every { stats.weeklyMinutes() } returns 0f
        every { stats.weeklyGoalMinutes() } returns 120
        every { stats.hasEventOccurred("aha_loop_used") } returns true
        every { stats.hasEventOccurred("aha_speed_used") } returns false
        every { context.getString(R.string.aha_speed_title) } returns "Try Speed"

        // Act
        val recommendation = engine.recommend()

        // Assert
        assertEquals("Try Speed", recommendation.title)
    }

    @Test
    fun `recommendation shows goal reached when minutes meet goal`() {
        // Arrange
        every { stats.weeklyMinutes() } returns 120f
        every { stats.weeklyGoalMinutes() } returns 120
        every { context.getString(R.string.coach_goal_reached) } returns "Goal Reached"

        // Act
        val recommendation = engine.recommend()

        // Assert
        assertEquals("Goal Reached", recommendation.title)
    }

    @Test
    fun `recommendation suggests starting today when goal not met and today is 0`() {
        // Arrange
        every { stats.weeklyMinutes() } returns 50f
        every { stats.weeklyGoalMinutes() } returns 120
        every { stats.todayMinutes() } returns 0f
        every { stats.streak() } returns 0
        every { stats.hasEventOccurred("aha_loop_used") } returns true
        every { stats.hasEventOccurred("aha_speed_used") } returns true
        every { context.getString(R.string.coach_start_today) } returns "Start Today"

        // Act
        val recommendation = engine.recommend()

        // Assert
        assertEquals("Start Today", recommendation.title)
    }

    @Test
    fun `recommendation suggests continuing when goal not met but today has progress`() {
        // Arrange
        every { stats.weeklyMinutes() } returns 50f
        every { stats.weeklyGoalMinutes() } returns 120
        every { stats.todayMinutes() } returns 10f
        every { stats.hasEventOccurred("aha_loop_used") } returns true
        every { stats.hasEventOccurred("aha_speed_used") } returns true
        every { context.getString(R.string.coach_continue_practicing) } returns "Keep Going"

        // Act
        val recommendation = engine.recommend()

        // Assert
        assertEquals("Keep Going", recommendation.title)
    }
}

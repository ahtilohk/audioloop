package ee.ahtilohk.audioloop

import android.content.Context
import android.content.SharedPreferences
import io.mockk.*
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class PracticeStatsManagerTest {

    private val context = mockk<Context>(relaxed = true)
    private val prefs = mockk<SharedPreferences>(relaxed = true)
    private val editor = mockk<SharedPreferences.Editor>(relaxed = true)
    private lateinit var manager: PracticeStatsManager

    @Before
    fun setup() {
        every { context.getSharedPreferences("PracticeStats", Context.MODE_PRIVATE) } returns prefs
        every { prefs.edit() } returns editor
        every { editor.putString(any(), any()) } returns editor
        every { editor.putInt(any(), any()) } returns editor
        every { editor.putLong(any(), any()) } returns editor
        every { editor.apply() } returns Unit
        
        manager = PracticeStatsManager(context)
    }

    @Test
    fun `logSession ignores durations under 3 seconds`() {
        manager.logSession(2000L) // 2s
        verify(exactly = 0) { editor.putString("daily_log", any()) }
    }

    @Test
    fun `logSession updates daily minutes for longer sessions`() {
        // Mock empty log initially
        every { prefs.getString("daily_log", "[]") } returns "[]"
        
        manager.logSession(120_000L) // 2 minutes
        
        // Check that it saved a log with roughly 2 minutes
        verify { editor.putString("daily_log", match { it.contains("\"m\":2") }) }
    }

    @Test
    fun `weeklyGoalMinutes returns default 120`() {
        every { prefs.getInt("weekly_goal_minutes", 120) } returns 120
        assertEquals(120, manager.weeklyGoalMinutes())
    }

    @Test
    fun `setWeeklyGoal updates preferences`() {
        manager.setWeeklyGoal(300)
        verify { editor.putInt("weekly_goal_minutes", 300) }
        verify { editor.apply() }
    }

    @Test
    fun `setUserIntent updates preferences and logs event`() {
        // Mock event log
        every { prefs.getString("event_log", "[]") } returns "[]"
        
        manager.setUserIntent("practice")
        verify { editor.putString("user_intent", "practice") }
        verify { editor.putString("event_log", match { it.contains("intent_selected") }) }
    }
}

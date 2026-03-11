package ee.ahtilohk.audioloop

/**
 * Rule-based practice coach that generates simple recommendations
 * based on current stats. V1: pure if/then logic, no ML needed.
 */
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

@Singleton
class CoachEngine @Inject constructor(@ApplicationContext private val context: android.content.Context, private val stats: PracticeStatsManager) {

    data class Recommendation(
        val title: String,
        val subtitle: String,
        val actionLabel: String,
        val suggestedMinutes: Int
    )

    /** Generate a "next best session" recommendation. */
    fun recommend(): Recommendation {
        val weekMin = stats.weeklyMinutes()
        val goal = stats.weeklyGoalMinutes()
        val remaining = (goal - weekMin).coerceAtLeast(0f)
        val streak = stats.streak()
        val todayMin = stats.todayMinutes()
        val daysLeftInWeek = daysRemainingThisWeek()

        // ── Priority 1: Aha-moment Discovery (If haven't hit goal yet) ──
        if (remaining > 0f) {
            if (!stats.hasEventOccurred("aha_loop_used")) {
                return Recommendation(
                    title = context.getString(R.string.aha_loop_title),
                    subtitle = context.getString(R.string.aha_loop_desc),
                    actionLabel = context.getString(R.string.btn_aha_try),
                    suggestedMinutes = 5
                )
            }
            if (!stats.hasEventOccurred("aha_speed_used")) {
                return Recommendation(
                    title = context.getString(R.string.aha_speed_title),
                    subtitle = context.getString(R.string.aha_speed_desc),
                    actionLabel = context.getString(R.string.btn_aha_try),
                    suggestedMinutes = 5
                )
            }
        }

        // ── Priority 2: Goal Progress ──
        // Already hit the goal this week
        if (remaining <= 0f) {
            return Recommendation(
                title = context.getString(R.string.coach_goal_reached),
                subtitle = context.getString(R.string.coach_goal_reached_desc, formatMin(weekMin)),
                actionLabel = context.getString(R.string.coach_bonus_session),
                suggestedMinutes = 10
            )
        }

        // Haven't practiced today yet
        if (todayMin < 1f) {
            val perDay = if (daysLeftInWeek > 0) (remaining / daysLeftInWeek).toInt().coerceIn(5, 45) else 15
            return Recommendation(
                title = context.getString(R.string.coach_start_today),
                subtitle = buildRemainingText(remaining, daysLeftInWeek, streak),
                actionLabel = context.getString(R.string.coach_action_listen, perDay),
                suggestedMinutes = perDay
            )
        }

        // Already practiced today but still behind goal
        val perDay = if (daysLeftInWeek > 0) (remaining / daysLeftInWeek).toInt().coerceIn(5, 30) else 10
        return Recommendation(
            title = context.getString(R.string.coach_continue_practicing),
            subtitle = context.getString(R.string.coach_today_done, formatMin(todayMin), formatMin(remaining)),
            actionLabel = context.getString(R.string.coach_action_add, perDay),
            suggestedMinutes = perDay
        )
    }

    private fun buildRemainingText(remaining: Float, daysLeft: Int, streak: Int): String {
        val parts = mutableListOf<String>()
        if (daysLeft > 0) {
            val perDay = (remaining / daysLeft).toInt().coerceAtLeast(1)
            parts.add(context.getString(R.string.coach_per_day, perDay))
        }
        if (streak > 1) {
            parts.add(context.getString(R.string.coach_streak_format, streak))
        }
        return if (parts.isEmpty()) context.getString(R.string.coach_start_listening) else parts.joinToString(" · ")
    }

    private fun formatMin(minutes: Float): String {
        val m = minutes.toInt()
        return if (m < 60) context.getString(R.string.label_min, m)
        else "${m / 60}${context.getString(R.string.label_hour_short)} ${m % 60}${context.getString(R.string.label_minute_short)}"
    }

    private fun daysRemainingThisWeek(): Int {
        val cal = java.util.Calendar.getInstance()
        val dow = cal.get(java.util.Calendar.DAY_OF_WEEK) // 1=Sun, 2=Mon, ...
        // ISO week: Mon=1 .. Sun=7
        val isoDow = if (dow == java.util.Calendar.SUNDAY) 7 else dow - 1
        return 7 - isoDow // days remaining including today
    }
}

package com.example.audioloop

/**
 * Rule-based practice coach that generates simple recommendations
 * based on current stats. V1: pure if/then logic, no ML needed.
 */
class CoachEngine(private val stats: PracticeStatsManager) {

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

        // Already hit the goal this week
        if (remaining <= 0f) {
            return Recommendation(
                title = "Goal reached!",
                subtitle = "You've already practiced ${formatMin(weekMin)} this week. Great job!",
                actionLabel = "Bonus session",
                suggestedMinutes = 10
            )
        }

        // Haven't practiced today yet
        if (todayMin < 1f) {
            val perDay = if (daysLeftInWeek > 0) (remaining / daysLeftInWeek).toInt().coerceIn(5, 45) else 15
            return Recommendation(
                title = "Start today's session",
                subtitle = buildRemainingText(remaining, daysLeftInWeek, streak),
                actionLabel = "Listen ${perDay} min",
                suggestedMinutes = perDay
            )
        }

        // Already practiced today but still behind goal
        val perDay = if (daysLeftInWeek > 0) (remaining / daysLeftInWeek).toInt().coerceIn(5, 30) else 10
        return Recommendation(
            title = "Continue practicing",
            subtitle = "${formatMin(todayMin)} done today. ${formatMin(remaining)} left to reach your goal.",
            actionLabel = "Add ${perDay} min",
            suggestedMinutes = perDay
        )
    }

    private fun buildRemainingText(remaining: Float, daysLeft: Int, streak: Int): String {
        val parts = mutableListOf<String>()
        if (daysLeft > 0) {
            val perDay = (remaining / daysLeft).toInt().coerceAtLeast(1)
            parts.add("~${perDay} min/day to reach your goal")
        }
        if (streak > 1) {
            parts.add("$streak day streak!")
        }
        return if (parts.isEmpty()) "Start listening" else parts.joinToString(" · ")
    }

    private fun formatMin(minutes: Float): String {
        val m = minutes.toInt()
        return if (m < 60) "${m} min" else "${m / 60}h ${m % 60}m"
    }

    private fun daysRemainingThisWeek(): Int {
        val cal = java.util.Calendar.getInstance()
        val dow = cal.get(java.util.Calendar.DAY_OF_WEEK) // 1=Sun, 2=Mon, ...
        // ISO week: Mon=1 .. Sun=7
        val isoDow = if (dow == java.util.Calendar.SUNDAY) 7 else dow - 1
        return 7 - isoDow // days remaining including today
    }
}

package ee.ahtilohk.audioloop

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Manages practice statistics stored locally via SharedPreferences + JSON.
 *
 * Tracked metrics:
 *  - Daily listening/practice minutes
 *  - Streak (consecutive days with ≥ 1 session)
 *  - Edits per week (trim, normalize, split, fade, auto-trim)
 *  - Weekly goal progress
 *  - Session count
 */
class PracticeStatsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("PracticeStats", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_DAILY_LOG = "daily_log"       // JSON array of {date, minutes, sessions}
        private const val KEY_EDIT_LOG = "edit_log"          // JSON array of {date, type}
        private const val KEY_EVENT_LOG = "event_log"        // JSON array of {name, ts, meta}
        private const val KEY_WEEKLY_GOAL_MIN = "weekly_goal_minutes"
        private const val KEY_STREAK = "current_streak"
        private const val KEY_LAST_ACTIVE_DATE = "last_active_date"
        private const val KEY_USER_INTENT = "user_intent"    // "listener" | "practice" | ""
        private const val MAX_LOG_DAYS = 90 // keep 90 days of history
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    }

    // ── Public API: Recording Events ──

    /** Call when a listening/practice session ends. durationMs = time spent listening. */
    fun logSession(durationMs: Long) {
        if (durationMs < 3000) return // ignore sessions < 3s
        val minutes = (durationMs / 60_000.0).toFloat()
        val today = todayStr()

        val log = loadDailyLog()
        val todayEntry = log.find { it.date == today }
        if (todayEntry != null) {
            todayEntry.minutes += minutes
            todayEntry.sessions += 1
        } else {
            log.add(DayEntry(today, minutes, 1))
        }
        // Trim old entries
        val cutoff = daysAgoStr(MAX_LOG_DAYS)
        log.removeAll { it.date < cutoff }
        saveDailyLog(log)
        updateStreak(today)
    }

    /** Call when user performs an edit operation (trim, normalize, etc.). */
    fun logEdit(editType: String) {
        val log = loadEditLog()
        log.add(EditEntry(todayStr(), editType))
        // Keep last 90 days
        val cutoff = daysAgoStr(MAX_LOG_DAYS)
        log.removeAll { it.date < cutoff }
        saveEditLog(log)
    }

    /** Log a named analytics event (intent_selected, trial_start, pay_start, etc.). */
    fun logEvent(name: String, meta: String = "") {
        val log = loadEventLog()
        log.add(EventEntry(name, System.currentTimeMillis(), meta))
        // Keep last 500 events
        if (log.size > 500) log.removeAt(0)
        saveEventLog(log)
    }

    // ── Public API: Reading Stats ──

    /** Minutes practiced this week (Mon-Sun). */
    fun weeklyMinutes(): Float {
        val log = loadDailyLog()
        val weekStart = weekStartStr()
        return log.filter { it.date >= weekStart }.sumOf { it.minutes.toDouble() }.toFloat()
    }

    /** Minutes practiced today. */
    fun todayMinutes(): Float {
        val log = loadDailyLog()
        val today = todayStr()
        return log.find { it.date == today }?.minutes ?: 0f
    }

    /** Current streak in days. */
    fun streak(): Int = prefs.getInt(KEY_STREAK, 0)

    /** Number of edit operations this week. */
    fun weeklyEdits(): Int {
        val log = loadEditLog()
        val weekStart = weekStartStr()
        return log.count { it.date >= weekStart }
    }

    /** Sessions this week. */
    fun weeklySessions(): Int {
        val log = loadDailyLog()
        val weekStart = weekStartStr()
        return log.filter { it.date >= weekStart }.sumOf { it.sessions }
    }

    /** Weekly goal in minutes. Default 120. */
    fun weeklyGoalMinutes(): Int = prefs.getInt(KEY_WEEKLY_GOAL_MIN, 120)

    fun setWeeklyGoal(minutes: Int) {
        prefs.edit().putInt(KEY_WEEKLY_GOAL_MIN, minutes).apply()
    }

    /** Goal progress 0.0–1.0. */
    fun goalProgress(): Float {
        val goal = weeklyGoalMinutes()
        if (goal <= 0) return 1f
        return (weeklyMinutes() / goal).coerceIn(0f, 1f)
    }

    /** User intent: "listener", "practice", or "" (not set). */
    fun userIntent(): String = prefs.getString(KEY_USER_INTENT, "") ?: ""

    fun setUserIntent(intent: String) {
        prefs.edit().putString(KEY_USER_INTENT, intent).apply()
        logEvent("intent_selected", intent)
    }

    /** Daily minutes for the last N days (oldest first). */
    fun dailyMinutesHistory(days: Int = 7): List<Pair<String, Float>> {
        val log = loadDailyLog()
        val result = mutableListOf<Pair<String, Float>>()
        for (i in days - 1 downTo 0) {
            val date = daysAgoStr(i)
            val minutes = log.find { it.date == date }?.minutes ?: 0f
            result.add(date to minutes)
        }
        return result
    }

    /** Check if a specific event has occurred at least once. */
    fun hasEventOccurred(name: String): Boolean {
        return loadEventLog().any { it.name == name }
    }

    /** Average daily minutes over last 7 days. */
    fun averageDailyMinutes(): Float {
        val history = dailyMinutesHistory(7)
        val total = history.sumOf { it.second.toDouble() }.toFloat()
        return total / 7f
    }

    // ── Streak Logic ──

    private fun updateStreak(today: String) {
        val lastActive = prefs.getString(KEY_LAST_ACTIVE_DATE, "") ?: ""
        val currentStreak = prefs.getInt(KEY_STREAK, 0)

        val newStreak = when {
            lastActive == today -> currentStreak // same day, no change
            lastActive == yesterdayStr() -> currentStreak + 1 // consecutive
            else -> 1 // streak broken, start fresh
        }
        prefs.edit()
            .putInt(KEY_STREAK, newStreak)
            .putString(KEY_LAST_ACTIVE_DATE, today)
            .apply()
    }

    // ── Data Classes ──

    private data class DayEntry(val date: String, var minutes: Float, var sessions: Int)
    private data class EditEntry(val date: String, val type: String)
    data class EventEntry(val name: String, val timestamp: Long, val meta: String)

    // ── Persistence (JSON in SharedPreferences) ──

    private fun loadDailyLog(): MutableList<DayEntry> {
        val json = prefs.getString(KEY_DAILY_LOG, "[]") ?: "[]"
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                DayEntry(
                    obj.getString("d"),
                    obj.getDouble("m").toFloat(),
                    obj.optInt("s", 1)
                )
            }.toMutableList()
        } catch (_: Exception) { mutableListOf() }
    }

    private fun saveDailyLog(log: List<DayEntry>) {
        val arr = JSONArray()
        log.forEach { entry ->
            arr.put(JSONObject().apply {
                put("d", entry.date)
                put("m", entry.minutes.toDouble())
                put("s", entry.sessions)
            })
        }
        prefs.edit().putString(KEY_DAILY_LOG, arr.toString()).apply()
    }

    private fun loadEditLog(): MutableList<EditEntry> {
        val json = prefs.getString(KEY_EDIT_LOG, "[]") ?: "[]"
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                EditEntry(obj.getString("d"), obj.getString("t"))
            }.toMutableList()
        } catch (_: Exception) { mutableListOf() }
    }

    private fun saveEditLog(log: List<EditEntry>) {
        val arr = JSONArray()
        log.forEach { entry ->
            arr.put(JSONObject().apply {
                put("d", entry.date)
                put("t", entry.type)
            })
        }
        prefs.edit().putString(KEY_EDIT_LOG, arr.toString()).apply()
    }

    private fun loadEventLog(): MutableList<EventEntry> {
        val json = prefs.getString(KEY_EVENT_LOG, "[]") ?: "[]"
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                EventEntry(obj.getString("n"), obj.getLong("ts"), obj.optString("m", ""))
            }.toMutableList()
        } catch (_: Exception) { mutableListOf() }
    }

    private fun saveEventLog(log: List<EventEntry>) {
        val arr = JSONArray()
        log.forEach { entry ->
            arr.put(JSONObject().apply {
                put("n", entry.name)
                put("ts", entry.timestamp)
                put("m", entry.meta)
            })
        }
        prefs.edit().putString(KEY_EVENT_LOG, arr.toString()).apply()
    }

    // ── Date Helpers ──

    private fun todayStr(): String = dateFormat.format(Date())

    private fun yesterdayStr(): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -1)
        return dateFormat.format(cal.time)
    }

    private fun daysAgoStr(days: Int): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -days)
        return dateFormat.format(cal.time)
    }

    /** Monday of the current week (ISO). */
    private fun weekStartStr(): String {
        val cal = Calendar.getInstance()
        cal.firstDayOfWeek = Calendar.MONDAY
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        return dateFormat.format(cal.time)
    }
}

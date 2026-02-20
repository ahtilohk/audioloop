package com.example.audioloop.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Helper to update the AudioLoop home screen widget with fresh data.
 * Writes state to SharedPreferences and triggers a Glance widget refresh.
 */
object WidgetStateHelper {

    private const val PREFS_NAME = "AudioLoopWidgetPrefs"

    /**
     * Update widget state with the latest app data.
     * Call this when: recording finishes, category changes, theme changes.
     */
    fun updateWidget(
        context: Context,
        category: String? = null,
        lastFileName: String? = null,
        lastFileDuration: String? = null,
        themeName: String? = null
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()

        category?.let { editor.putString("current_category", it) }
        lastFileName?.let { editor.putString("last_file_name", it) }
        lastFileDuration?.let { editor.putString("last_file_duration", it) }
        themeName?.let { editor.putString("theme_name", it) }

        editor.apply()

        // Trigger widget refresh
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AudioLoopWidget().updateAll(context)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

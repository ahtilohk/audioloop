package ee.ahtilohk.audioloop.data

import android.content.Context
import ee.ahtilohk.audioloop.AppTheme
import ee.ahtilohk.audioloop.ThemeMode
import ee.ahtilohk.audioloop.AppLog
import org.json.JSONArray
import java.io.File

class SettingsManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("AudioLoopPrefs", Context.MODE_PRIVATE)
    private val filesDir: File get() = context.filesDir

    // --- App Settings ---

    fun getTheme(): AppTheme {
        val name = prefs.getString("app_theme", AppTheme.CYAN.name) ?: AppTheme.CYAN.name
        return try { AppTheme.valueOf(name) } catch (_: Exception) { AppTheme.CYAN }
    }

    fun saveTheme(theme: AppTheme) {
        prefs.edit().putString("app_theme", theme.name).apply()
    }

    fun getThemeMode(): ThemeMode {
        val name = prefs.getString("app_theme_mode", "DARK") ?: "DARK"
        return try { ThemeMode.valueOf(name) } catch (_: Exception) { ThemeMode.DARK }
    }

    fun saveThemeMode(mode: ThemeMode) {
        prefs.edit().putString("app_theme_mode", mode.name).apply()
    }

    fun getLanguage(): String {
        val deviceLang = java.util.Locale.getDefault().language
        val supported = listOf("en", "et", "de", "pl")
        return prefs.getString("app_language", if (deviceLang in supported) deviceLang else "en") ?: "en"
    }

    fun saveLanguage(lang: String) {
        prefs.edit().putString("app_language", lang).apply()
    }

    fun isSmartCoachEnabled(): Boolean = prefs.getBoolean("smart_coach_enabled", true)

    fun saveSmartCoachEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("smart_coach_enabled", enabled).apply()
    }

    fun isSmartCoachExpanded(): Boolean = prefs.getBoolean("smart_coach_expanded", false)

    fun saveSmartCoachExpanded(expanded: Boolean) {
        prefs.edit().putBoolean("smart_coach_expanded", expanded).apply()
    }

    fun isFirstLaunch(): Boolean = prefs.getBoolean("is_first_launch", true)

    fun setFirstLaunchComplete() {
        prefs.edit().putBoolean("is_first_launch", false).apply()
    }

    fun getUsePublicStorage(): Boolean = prefs.getBoolean("use_public_storage", true)

    fun setUsePublicStorage(enabled: Boolean) {
        prefs.edit().putBoolean("use_public_storage", enabled).apply()
    }

    // --- File/Category Order Persistence ---

    private fun getOrderFile(category: String): File {
        val dir = if (category == "General") filesDir else File(filesDir, category)
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "ordering.json")
    }

    fun saveFileOrder(category: String, order: List<String>) {
        try {
            val file = getOrderFile(category)
            val jsonArray = JSONArray()
            order.forEach { jsonArray.put(it) }
            file.writeText(jsonArray.toString())
        } catch (e: Exception) {
            AppLog.e("Failed to save file order for $category", e)
        }
    }

    fun loadFileOrder(category: String): List<String> {
        val list = mutableListOf<String>()
        try {
            val file = getOrderFile(category)
            if (file.exists()) {
                val content = file.readText()
                if (content.isNotBlank()) {
                    val array = JSONArray(content)
                    for (i in 0 until array.length()) list.add(array.getString(i))
                }
            }
        } catch (e: Exception) {
            AppLog.e("Failed to load file order for $category", e)
        }
        return list
    }

    private fun getCategoryOrderFile(): File = File(filesDir, "category_order.json")

    fun saveCategoryOrder(categories: List<String>) {
        try {
            val jsonArray = JSONArray()
            categories.forEach { jsonArray.put(it) }
            getCategoryOrderFile().writeText(jsonArray.toString())
        } catch (e: Exception) {
            AppLog.e("Failed to save category order", e)
        }
    }

    fun loadCategoryOrder(): List<String> {
        val list = mutableListOf<String>()
        try {
            val file = getCategoryOrderFile()
            if (file.exists()) {
                val array = JSONArray(file.readText())
                for (i in 0 until array.length()) list.add(array.getString(i))
            }
        } catch (e: Exception) {
            AppLog.e("Failed to load category order", e)
        }
        return list
    }
}

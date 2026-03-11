package ee.ahtilohk.audioloop

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import ee.ahtilohk.audioloop.AppLog

class PlaylistManager(private val context: Context) {

    private val playlistDir: File
        get() = File(context.filesDir, ".playlists").also { it.mkdirs() }

    // --- CRUD ---

    fun loadAll(): List<Playlist> {
        val dir = playlistDir
        if (!dir.exists()) return emptyList()
        return dir.listFiles()
            ?.filter { it.extension == "json" }
            ?.mapNotNull { parsePlaylist(it) }
            ?.sortedByDescending { it.createdAt }
            ?: emptyList()
    }

    fun save(playlist: Playlist) {
        val json = JSONObject().apply {
            put("id", playlist.id)
            put("name", playlist.name)
            put("createdAt", playlist.createdAt)
            put("gapSeconds", playlist.gapSeconds)
            put("shuffle", playlist.shuffle)
            put("playCount", playlist.playCount)
            put("speed", playlist.speed.toDouble())
            put("pitch", playlist.pitch.toDouble())
            put("loopCount", playlist.loopCount)
            put("sleepMinutes", playlist.sleepMinutes)
            put("files", JSONArray(playlist.files))
        }
        File(playlistDir, "${playlist.id}.json").writeText(json.toString(2))
    }

    fun delete(id: String) {
        File(playlistDir, "$id.json").delete()
    }

    fun createNew(name: String): Playlist {
        return Playlist(
            id = UUID.randomUUID().toString(),
            name = name,
            files = emptyList(),
            createdAt = System.currentTimeMillis()
        )
    }

    fun incrementPlayCount(id: String) {
        val playlists = loadAll()
        val playlist = playlists.find { it.id == id } ?: return
        save(playlist.copy(playCount = playlist.playCount + 1))
    }

    // --- File Resolution ---

    fun resolveFiles(playlist: Playlist, allRecordings: List<RecordingItem>): List<RecordingItem> {
        val fileList = if (playlist.shuffle) playlist.files.shuffled() else playlist.files
        val items = mutableListOf<RecordingItem>()

        for (relativePath in fileList) {
            val fileName = relativePath.substringAfter("/")
            val item = allRecordings.find { 
                it.name == fileName || it.file.absolutePath.replace("\\", "/").endsWith("/$relativePath")
            }
            if (item != null) items.add(item)
        }
        return items
    }

    // --- Duration Helpers ---

    fun totalDuration(playlist: Playlist, allRecordings: List<RecordingItem>): Long {
        var total = 0L
        for (relativePath in playlist.files) {
            val fileName = relativePath.substringAfter("/")
            val item = allRecordings.find { 
                it.name == fileName || it.file.absolutePath.replace("\\", "/").endsWith("/$relativePath")
            }
            if (item != null) total += item.durationMillis
        }
        // Add gaps
        if (playlist.files.size > 1) {
            total += (playlist.files.size - 1) * playlist.gapSeconds * 1000L
        }
        return total
    }

    fun formatTotalDuration(playlist: Playlist, allRecordings: List<RecordingItem>): String {
        val ms = totalDuration(playlist, allRecordings)
        if (ms <= 0) return "0 min"
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return if (minutes > 0) {
            if (seconds > 30) "~${minutes + 1} min" else "~$minutes min"
        } else {
            "${seconds}s"
        }
    }

    // --- Category info for display ---

    fun getCategoryForFile(relativePath: String): String {
        return if (relativePath.contains("/")) {
            relativePath.substringBefore("/")
        } else {
            "General"
        }
    }

    // --- Private helpers ---

    private fun parsePlaylist(file: File): Playlist? {
        return try {
            val json = JSONObject(file.readText())
            val filesArray = json.getJSONArray("files")
            val files = (0 until filesArray.length()).map { filesArray.getString(it) }
            Playlist(
                id = json.getString("id"),
                name = json.getString("name"),
                files = files,
                createdAt = json.getLong("createdAt"),
                gapSeconds = json.optInt("gapSeconds", 0),
                shuffle = json.optBoolean("shuffle", false),
                playCount = json.optInt("playCount", 0),
                speed = json.optDouble("speed", 1.0).toFloat(),
                pitch = json.optDouble("pitch", 1.0).toFloat(),
                loopCount = json.optInt("loopCount", 1),
                sleepMinutes = json.optInt("sleepMinutes", 0)
            )
        } catch (e: Exception) {
            AppLog.e("Error parsing playlist JSON at ${file.name}", e)
            null
        }
    }

    private fun getFileDuration(file: File): Long {
        if (!file.exists() || file.length() < 10) return 0L
        val mmr = MediaMetadataRetriever()
        return try {
            mmr.setDataSource(file.absolutePath)
            mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (_: Exception) { 0L }
        finally { try { mmr.release() } catch (_: Exception) {} }
    }

    private fun formatDuration(millis: Long): String {
        if (millis <= 0) return "00:00"
        val totalSeconds = millis / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }
}

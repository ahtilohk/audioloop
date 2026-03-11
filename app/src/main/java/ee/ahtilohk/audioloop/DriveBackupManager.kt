package ee.ahtilohk.audioloop

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class BackupInfo(
    val id: String,
    val name: String,
    val date: String,
    val sizeBytes: Long
)

class DriveBackupManager(private val context: Context) {

    companion object {
        private const val DRIVE_FILES_URL = "https://www.googleapis.com/drive/v3/files"
        private const val DRIVE_UPLOAD_URL = "https://www.googleapis.com/upload/drive/v3/files"
        private const val SCOPE = "https://www.googleapis.com/auth/drive.appdata"
        private val JSON_MEDIA_TYPE = "application/json; charset=UTF-8".toMediaType()
    }

    private var account: GoogleSignInAccount? = null
    private val client = NetworkHelper.newClient()

    // --- Sign-In ---

    fun getSignInClient(): GoogleSignInClient {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(SCOPE))
            .build()
        return GoogleSignIn.getClient(context, gso)
    }

    fun getSignInIntent(): Intent = getSignInClient().signInIntent

    fun isSignedIn(): Boolean = GoogleSignIn.getLastSignedInAccount(context) != null

    fun getSignedInEmail(): String? = GoogleSignIn.getLastSignedInAccount(context)?.email

    fun handleSignInResult(acct: GoogleSignInAccount?) {
        account = acct
    }

    fun initFromLastAccount(): Boolean {
        account = GoogleSignIn.getLastSignedInAccount(context) ?: return false
        return true
    }

    suspend fun signOut() {
        withContext(Dispatchers.Main) {
            getSignInClient().signOut()
        }
        account = null
    }

    // --- Token helper ---

    private suspend fun getAccessToken(): String = withContext(Dispatchers.IO) {
        val acct = account ?: GoogleSignIn.getLastSignedInAccount(context) ?: throw Exception("Not signed in")
        com.google.android.gms.auth.GoogleAuthUtil.getToken(context, acct.account!!, "oauth2:$SCOPE")
    }

    // --- Backup ---

    suspend fun createAndUploadBackup(
        onProgress: (String) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val token = getAccessToken()

            onProgress(context.getString(R.string.msg_backup_creating))
            val zipFile = createBackupZip(onProgress)

            val dateStr = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date())
            val fileName = "audioloop_backup_$dateStr.zip"

            onProgress(context.getString(R.string.msg_backup_uploading))
            NetworkHelper.executeWithRetry {
                uploadToDrive(token, zipFile, fileName) { current, total ->
                    val percent = if (total > 0) (current * 100 / total).toInt() else 0
                    onProgress(context.getString(R.string.msg_backup_uploading_perc, percent))
                }
            }
            zipFile.delete()

            onProgress(context.getString(R.string.msg_backup_complete))
            Result.success(fileName)
        } catch (e: Exception) {
            AppLog.e("Drive backup creation failed", e)
            Result.failure(e)
        }
    }

    private fun createBackupZip(onProgress: (String) -> Unit): File {
        val zipFile = File(context.cacheDir, "backup_temp.zip")
        ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
            val filesDir = context.filesDir

            // 1. Audio files (root = General category)
            val audioFiles = filesDir.listFiles()?.filter {
                it.isFile && !it.name.startsWith(".") && it.name != "category_order.json" && !it.name.endsWith(".json")
            } ?: emptyList()
            var fileCount = 0
            audioFiles.forEach { file ->
                onProgress("Packing: ${file.name}")
                addFileToZip(zos, file, "audio/${file.name}")
                fileCount++
            }

            // 2. Audio files in category subdirectories
            val categoryDirs = filesDir.listFiles()?.filter {
                it.isDirectory && !it.name.startsWith(".") && it.name != ".trash" && it.name != ".notes" && it.name != ".waveforms"
            } ?: emptyList()
            categoryDirs.forEach { dir ->
                dir.listFiles()?.filter { it.isFile && !it.name.endsWith(".json") }?.forEach { file ->
                    onProgress("Packing: ${dir.name}/${file.name}")
                    addFileToZip(zos, file, "audio/${dir.name}/${file.name}")
                    fileCount++
                }
                // Also pack ordering.json for each category
                val orderFile = File(dir, "ordering.json")
                if (orderFile.exists()) {
                    addFileToZip(zos, orderFile, "audio/${dir.name}/ordering.json")
                }
            }

            // 3. Notes
            val notesDir = File(filesDir, ".notes")
            if (notesDir.exists()) {
                notesDir.listFiles()?.filter { it.isFile }?.forEach { noteFile ->
                    addFileToZip(zos, noteFile, "notes/${noteFile.name}")
                }
            }

            // 4. Category order
            val categoryOrderFile = File(filesDir, "category_order.json")
            if (categoryOrderFile.exists()) {
                addFileToZip(zos, categoryOrderFile, "category_order.json")
            }

            // 5. Settings
            val prefs = context.getSharedPreferences("AudioLoopPrefs", Context.MODE_PRIVATE)
            val settings = JSONObject().apply {
                put("app_theme", prefs.getString("app_theme", "CYAN"))
                put("app_lang", prefs.getString("app_lang", "et"))
                put("use_public_storage", prefs.getBoolean("use_public_storage", true))
            }
            val settingsEntry = ZipEntry("settings.json")
            zos.putNextEntry(settingsEntry)
            zos.write(settings.toString().toByteArray())
            zos.closeEntry()

            onProgress("Packed $fileCount audio files")
        }
        return zipFile
    }

    private fun addFileToZip(zos: ZipOutputStream, file: File, entryPath: String) {
        val entry = ZipEntry(entryPath)
        zos.putNextEntry(entry)
        FileInputStream(file).use { fis ->
            fis.copyTo(zos, bufferSize = 8192)
        }
        zos.closeEntry()
    }

    private fun uploadToDrive(token: String, file: File, fileName: String, onProgress: (Long, Long) -> Unit) {
        val boundary = "===backup_boundary_${System.currentTimeMillis()}==="
        
        val metadata = JSONObject().apply {
            put("name", fileName)
            put("parents", JSONArray().put("appDataFolder"))
        }

        // related multipart is slightly non-standard in okhttp but we can set the type manually
        val requestBody = MultipartBody.Builder(boundary)
            .setType("multipart/related".toMediaType())
            .addPart(metadata.toString().toRequestBody(JSON_MEDIA_TYPE))
            .addPart(NetworkHelper.createProgressRequestBody(file, "application/zip", onProgress))
            .build()

        val request = Request.Builder()
            .url("$DRIVE_UPLOAD_URL?uploadType=multipart")
            .header("Authorization", "Bearer $token")
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: ""
                throw IOException("Upload failed (${response.code}): $errorBody")
            }
        }
    }

    // --- List Backups ---

    suspend fun listBackups(): Result<List<BackupInfo>> = withContext(Dispatchers.IO) {
        try {
            val token = getAccessToken()
            val request = Request.Builder()
                .url("$DRIVE_FILES_URL?spaces=appDataFolder&fields=files(id,name,createdTime,size)&orderBy=createdTime%20desc&pageSize=20")
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            NetworkHelper.executeWithRetry {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("List failed (${response.code})")
                    
                    val body = response.body?.string() ?: throw IOException("Empty response")
                    val json = JSONObject(body)
                    val files = json.optJSONArray("files") ?: JSONArray()

                    val backups = mutableListOf<BackupInfo>()
                    for (i in 0 until files.length()) {
                        val file = files.getJSONObject(i)
                        val name = file.getString("name")
                        val dateStr = try {
                            val datePart = name.removePrefix("audioloop_backup_").removeSuffix(".zip")
                            val parsed = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).parse(datePart)
                            SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.US).format(parsed ?: Date())
                        } catch (_: Exception) { file.optString("createdTime", "Unknown") }

                        backups.add(BackupInfo(
                            id = file.getString("id"),
                            name = name,
                            date = dateStr,
                            sizeBytes = file.optLong("size", 0L)
                        ))
                    }
                    Result.success(backups)
                }
            }
        } catch (e: Exception) {
            AppLog.e("Drive backup listing failed", e)
            Result.failure(e)
        }
    }

    // --- Restore ---

    suspend fun downloadAndRestore(
        backupId: String,
        onProgress: (String) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = getAccessToken()

            onProgress("Downloading backup...")
            val tempZip = File(context.cacheDir, "restore_temp.zip")

            val request = Request.Builder()
                .url("$DRIVE_FILES_URL/$backupId?alt=media")
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            NetworkHelper.executeWithRetry {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Download failed (${response.code})")
                    
                    val totalSize = response.body?.contentLength() ?: -1L
                    var bytesRead = 0L
                    
                    response.body?.byteStream()?.use { input ->
                        FileOutputStream(tempZip).use { output ->
                            val buffer = ByteArray(8192)
                            var read: Int
                            while (input.read(buffer).also { read = it } != -1) {
                                output.write(buffer, 0, read)
                                bytesRead += read
                                if (totalSize > 0) {
                                    val percent = (bytesRead * 100 / totalSize).toInt()
                                    onProgress("Downloading: $percent%")
                                }
                            }
                        }
                    }
                }
            }

            onProgress("Restoring files...")
            restoreFromZip(tempZip, onProgress)
            tempZip.delete()

            onProgress("Restore complete!")
            Result.success(Unit)
        } catch (e: Exception) {
            AppLog.e("Drive backup restore failed", e)
            // Cleanup on failure
            try { File(context.cacheDir, "restore_temp.zip").delete() } catch(_: Exception) {}
            Result.failure(e)
        }
    }

    private fun copyWithLimit(input: InputStream, output: OutputStream, limit: Long): Long {
        var total = 0L
        val buffer = ByteArray(8192)
        var read: Int
        while (input.read(buffer).also { read = it } != -1) {
            total += read
            if (total > limit) throw IOException("Restore size limit exceeded (Zip Bomb protection)")
            output.write(buffer, 0, read)
        }
        return total
    }

    private fun restoreFromZip(zipFile: File, onProgress: (String) -> Unit) {
        val filesDir = context.filesDir
        var fileCount = 0
        var totalBytesRead = 0L
        val MAX_TOTAL_SIZE = 1000 * 1024 * 1024L // 1GB total safety limit
        val MAX_ENTRIES = 2000 // 2000 items safety limit

        ZipInputStream(FileInputStream(zipFile)).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            var entryCount = 0
            while (entry != null) {
                entryCount++
                if (entryCount > MAX_ENTRIES) throw IOException("Backup has too many entries")

                val name = entry.name
                
                // 1. Security Check: Zip Slip protection
                // We ensure the target file path is within our app's internal files directory
                val targetFile = when {
                    name.startsWith("audio/") -> File(filesDir, name.removePrefix("audio/"))
                    name.startsWith("notes/") -> File(File(filesDir, ".notes"), name.removePrefix("notes/"))
                    name == "category_order.json" -> File(filesDir, "category_order.json")
                    name == "settings.json" -> null // Handled separately
                    else -> null
                }

                if (targetFile != null) {
                    if (!targetFile.canonicalPath.startsWith(filesDir.canonicalPath)) {
                        throw IOException("Security error: Illegal entry path in backup ($name)")
                    }
                }

                when {
                    name.startsWith("audio/") -> {
                        val outFile = targetFile!!
                        outFile.parentFile?.mkdirs()
                        onProgress("Restoring: ${outFile.name}")
                        FileOutputStream(outFile).use { fos -> 
                            totalBytesRead += copyWithLimit(zis, fos, MAX_TOTAL_SIZE - totalBytesRead)
                        }
                        if (name.endsWith(".m4a") || name.endsWith(".mp3") || name.endsWith(".wav")) {
                           fileCount++
                        }
                    }
                    name.startsWith("notes/") -> {
                        val outFile = targetFile!!
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { fos -> 
                            totalBytesRead += copyWithLimit(zis, fos, MAX_TOTAL_SIZE - totalBytesRead)
                        }
                    }
                    name == "category_order.json" -> {
                        FileOutputStream(targetFile!!).use { fos -> 
                            totalBytesRead += copyWithLimit(zis, fos, MAX_TOTAL_SIZE - totalBytesRead)
                        }
                    }
                    name == "settings.json" -> {
                        // Limit reading of settings JSON for safety
                        val MAX_SETTINGS_SIZE = 1 * 1024 * 1024 // 1MB for settings
                        val baout = ByteArrayOutputStream()
                        totalBytesRead += copyWithLimit(zis, baout, Math.min(MAX_SETTINGS_SIZE.toLong(), MAX_TOTAL_SIZE - totalBytesRead))
                        
                        val content = baout.toString()
                        val json = JSONObject(content)
                        val prefs = context.getSharedPreferences("AudioLoopPrefs", Context.MODE_PRIVATE)
                        prefs.edit().apply {
                            if (json.has("app_theme")) putString("app_theme", json.getString("app_theme"))
                            if (json.has("app_lang")) putString("app_lang", json.getString("app_lang"))
                            if (json.has("use_public_storage")) putBoolean("use_public_storage", json.getBoolean("use_public_storage"))
                            apply()
                        }
                    }
                }

                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        onProgress("Restored $fileCount audio files")
    }

    // --- Delete ---

    suspend fun deleteBackup(backupId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val token = getAccessToken()
            val request = Request.Builder()
                .url("$DRIVE_FILES_URL/$backupId")
                .header("Authorization", "Bearer $token")
                .delete()
                .build()

            NetworkHelper.executeWithRetry {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Delete failed (${response.code})")
                    Result.success(Unit)
                }
            }
        } catch (e: Exception) {
            AppLog.e("Drive backup deletion failed", e)
            Result.failure(e)
        }
    }
}

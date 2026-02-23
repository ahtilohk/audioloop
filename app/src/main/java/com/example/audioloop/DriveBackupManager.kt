package com.example.audioloop

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
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
    }

    private var account: GoogleSignInAccount? = null

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
        val acct = account ?: throw Exception("Not signed in")
        // Use GoogleAuthUtil to get a fresh token
        com.google.android.gms.auth.GoogleAuthUtil.getToken(
            context,
            acct.account!!,
            "oauth2:$SCOPE"
        )
    }

    // --- Backup ---

    suspend fun createAndUploadBackup(
        onProgress: (String) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val token = getAccessToken()

            onProgress("Creating backup...")
            val zipFile = createBackupZip(onProgress)

            onProgress("Uploading to Google Drive...")
            val dateStr = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date())
            val fileName = "audioloop_backup_$dateStr.zip"

            uploadToDrive(token, zipFile, fileName)
            zipFile.delete()

            onProgress("Backup complete!")
            Result.success(fileName)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    private fun createBackupZip(onProgress: (String) -> Unit): File {
        val zipFile = File(context.cacheDir, "backup_temp.zip")
        ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
            val filesDir = context.filesDir

            // 1. Audio files (root = General category)
            val audioFiles = filesDir.listFiles()?.filter {
                it.isFile && !it.name.startsWith(".") && it.name != "category_order.json"
            } ?: emptyList()
            var fileCount = 0
            audioFiles.forEach { file ->
                onProgress("Packing: ${file.name}")
                addFileToZip(zos, file, "audio/${file.name}")
                fileCount++
            }

            // 2. Audio files in category subdirectories
            val categoryDirs = filesDir.listFiles()?.filter {
                it.isDirectory && !it.name.startsWith(".")
            } ?: emptyList()
            categoryDirs.forEach { dir ->
                dir.listFiles()?.filter { it.isFile }?.forEach { file ->
                    onProgress("Packing: ${dir.name}/${file.name}")
                    addFileToZip(zos, file, "audio/${dir.name}/${file.name}")
                    fileCount++
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

    private fun uploadToDrive(token: String, file: File, fileName: String) {
        val boundary = "===backup_boundary_${System.currentTimeMillis()}==="
        val url = URL("$DRIVE_UPLOAD_URL?uploadType=multipart")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
        conn.doOutput = true

        conn.outputStream.use { os ->
            val writer = OutputStreamWriter(os, Charsets.UTF_8)

            // Metadata part
            writer.write("--$boundary\r\n")
            writer.write("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            val metadata = JSONObject().apply {
                put("name", fileName)
                put("parents", JSONArray().put("appDataFolder"))
            }
            writer.write(metadata.toString())
            writer.write("\r\n")

            // File content part
            writer.write("--$boundary\r\n")
            writer.write("Content-Type: application/zip\r\n\r\n")
            writer.flush()

            FileInputStream(file).use { fis ->
                fis.copyTo(os, bufferSize = 8192)
            }

            writer.write("\r\n--$boundary--\r\n")
            writer.flush()
        }

        val responseCode = conn.responseCode
        if (responseCode !in 200..299) {
            val errorBody = try { conn.errorStream?.bufferedReader()?.readText() } catch (_: Exception) { "" }
            throw Exception("Upload failed ($responseCode): $errorBody")
        }
    }

    // --- List Backups ---

    suspend fun listBackups(): Result<List<BackupInfo>> = withContext(Dispatchers.IO) {
        try {
            val token = getAccessToken()
            val url = URL("$DRIVE_FILES_URL?spaces=appDataFolder&fields=files(id,name,createdTime,size)&orderBy=createdTime%20desc&pageSize=20")
            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("Authorization", "Bearer $token")

            val responseCode = conn.responseCode
            if (responseCode !in 200..299) {
                val errorBody = try { conn.errorStream?.bufferedReader()?.readText() } catch (_: Exception) { "" }
                return@withContext Result.failure(Exception("List failed ($responseCode): $errorBody"))
            }

            val body = conn.inputStream.bufferedReader().readText()
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
        } catch (e: Exception) {
            e.printStackTrace()
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

            val url = URL("$DRIVE_FILES_URL/$backupId?alt=media")
            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("Authorization", "Bearer $token")

            val responseCode = conn.responseCode
            if (responseCode !in 200..299) {
                val errorBody = try { conn.errorStream?.bufferedReader()?.readText() } catch (_: Exception) { "" }
                return@withContext Result.failure(Exception("Download failed ($responseCode): $errorBody"))
            }

            FileOutputStream(tempZip).use { fos ->
                conn.inputStream.copyTo(fos, bufferSize = 8192)
            }

            onProgress("Restoring files...")
            restoreFromZip(tempZip, onProgress)
            tempZip.delete()

            onProgress("Restore complete!")
            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    private fun restoreFromZip(zipFile: File, onProgress: (String) -> Unit) {
        val filesDir = context.filesDir
        var fileCount = 0

        ZipInputStream(FileInputStream(zipFile)).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val name = entry.name

                when {
                    name.startsWith("audio/") -> {
                        val relativePath = name.removePrefix("audio/")
                        val targetFile = File(filesDir, relativePath)
                        targetFile.parentFile?.mkdirs()
                        onProgress("Restoring: $relativePath")
                        FileOutputStream(targetFile).use { fos -> zis.copyTo(fos) }
                        fileCount++
                    }
                    name.startsWith("notes/") -> {
                        val noteName = name.removePrefix("notes/")
                        val notesDir = File(filesDir, ".notes")
                        notesDir.mkdirs()
                        FileOutputStream(File(notesDir, noteName)).use { fos -> zis.copyTo(fos) }
                    }
                    name == "category_order.json" -> {
                        FileOutputStream(File(filesDir, "category_order.json")).use { fos -> zis.copyTo(fos) }
                    }
                    name == "settings.json" -> {
                        val content = zis.bufferedReader().readText()
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
            val url = URL("$DRIVE_FILES_URL/$backupId")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "DELETE"
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.responseCode // trigger request
            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
}

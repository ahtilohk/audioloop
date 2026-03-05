package com.example.audioloop

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle

import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.audioloop.ui.theme.AudioLoopTheme
import com.example.audioloop.R
import com.example.audioloop.ui.theme.Zinc300
import com.example.audioloop.ui.theme.Zinc400
import com.example.audioloop.ui.theme.Zinc700
import com.example.audioloop.ui.theme.Zinc900
import com.example.audioloop.ui.theme.Sunset400
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * MainActivity â€” slim UI host.
 *
 * All state and business logic lives in AudioLoopViewModel.
 * This Activity is responsible for:
 * - Lifecycle (onCreate, onDestroy, onNewIntent)
 * - Permission launchers (recording, screen capture, sign-in)
 * - Setting Compose content
 */
class MainActivity : androidx.appcompat.app.AppCompatActivity() {

    private var pendingRecordingName = ""
    private var pendingCategory = ""
    private lateinit var mediaProjectionManager: android.media.projection.MediaProjectionManager

    // Late-bound ViewModel ref (set in setContent)
    private var vm: AudioLoopViewModel? = null

    private val screenCaptureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val resultCode = result.resultCode
            val data = result.data!!
            val finalName = if (pendingCategory == "General") pendingRecordingName else {
                val folder = File(filesDir, pendingCategory)
                if (!folder.exists()) folder.mkdirs()
                "$pendingCategory/$pendingRecordingName"
            }
            val serviceIntent = Intent(this, AudioService::class.java).apply {
                action = AudioService.ACTION_START_INTERNAL_RECORDING
                putExtra(AudioService.EXTRA_FILENAME, finalName)
                putExtra(AudioService.EXTRA_RESULT_CODE, resultCode)
                putExtra(AudioService.EXTRA_DATA, data)
            }
            startInternalAudioService(serviceIntent)
        } else {
            vm?.showSnackbar(getString(R.string.msg_permission_required), isError = true)
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) vm?.showSnackbar(getString(R.string.msg_permission_granted))
        else vm?.showSnackbar(getString(R.string.msg_permission_denied), isError = true)
    }

    private val signInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (data != null) {
            val task = com.google.android.gms.auth.api.signin.GoogleSignIn.getSignedInAccountFromIntent(data)
            if (task.isSuccessful) {
                vm?.handleSignInResult(task.result)
            } else {
                val e = task.exception
                val msg = if (e is com.google.android.gms.common.api.ApiException) {
                    when (e.statusCode) {
                        12501 -> getString(R.string.signin_cancelled)
                        12500 -> getString(R.string.signin_failed_play_services)
                        10 -> getString(R.string.signin_dev_error)
                        4 -> getString(R.string.signin_interrupted)
                        else -> getString(R.string.signin_error, e.statusCode)
                    }
                } else {
                    getString(R.string.signin_failed, e?.localizedMessage ?: "unknown error")
                }
                vm?.handleSignInError(msg)
            }
        } else {
            vm?.driveBackupManager?.getSignInClient()?.silentSignIn()
                ?.addOnSuccessListener { account ->
                    vm?.handleSignInResult(account)
                }
                ?.addOnFailureListener { e ->
                    vm?.handleSignInError(getString(R.string.signin_failed, e.localizedMessage))
                }
        }
        vm?.setBackupRunning(false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as android.media.projection.MediaProjectionManager
        } catch (e: Exception) { e.printStackTrace() }

        setContent {
            val viewModel: AudioLoopViewModel = viewModel()
            vm = viewModel

            // Initialize ViewModel once
            LaunchedEffect(Unit) {
                viewModel.initialize()
                viewModel.refreshFileList()
                viewModel.startProgressTracking()
            }

            val uiState by viewModel.uiState.collectAsState()
            val darkTheme = when (uiState.themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.AUTO -> isSystemInDarkTheme()
            }

            AudioLoopTheme(darkTheme = darkTheme, appTheme = uiState.currentTheme) {
                val snackbarHostState = remember { SnackbarHostState() }
                val coroutineScope = rememberCoroutineScope()
                val context = this@MainActivity

                // Snackbar handler â€” replaces Toast
                LaunchedEffect(uiState.snackbarMessage) {
                    uiState.snackbarMessage?.let { msg ->
                        val result = snackbarHostState.showSnackbar(
                            message = msg.text,
                            actionLabel = msg.actionLabel,
                            duration = when (msg.duration) {
                                SnackbarDuration.Short -> androidx.compose.material3.SnackbarDuration.Short
                                SnackbarDuration.Long -> androidx.compose.material3.SnackbarDuration.Long
                                SnackbarDuration.Indefinite -> androidx.compose.material3.SnackbarDuration.Indefinite
                            }
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            if (msg.actionLabel == "Undo") {
                                viewModel.undoDelete()
                            }
                        }
                        viewModel.clearSnackbar()
                    }
                }

                // Broadcast receiver for recording saved
                DisposableEffect(Unit) {
                    val receiver = object : BroadcastReceiver() {
                        override fun onReceive(ctx: Context?, intent: Intent?) {
                            if (intent?.action == AudioService.ACTION_RECORDING_SAVED) {
                                coroutineScope.launch {
                                    try {
                                        viewModel.refreshFileList()
                                        delay(500)
                                        viewModel.refreshFileList()
                                        // Update widget
                                        val items = viewModel.uiState.value.savedItems
                                        val latestItem = items.firstOrNull()
                                        com.example.audioloop.widget.WidgetStateHelper.updateWidget(
                                            context,
                                            category = uiState.currentCategory,
                                            lastFileName = latestItem?.name?.substringBeforeLast(".") ?: "",
                                            lastFileDuration = latestItem?.durationString ?: ""
                                        )
                                    } catch (e: Exception) { e.printStackTrace() }
                                }
                            }
                        }
                    }
                    val filter = IntentFilter(AudioService.ACTION_RECORDING_SAVED)
                    ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
                    onDispose { context.unregisterReceiver(receiver) }
                }

                // Request permissions
                LaunchedEffect(Unit) {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                        requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                }

                // Welcome Dialog
                var showWelcomeDialog by remember { mutableStateOf(viewModel.isFirstLaunch()) }
                if (showWelcomeDialog) {
                    WelcomeDialog(
                        themeColors = uiState.currentTheme.palette,
                        onDismiss = {
                            viewModel.setFirstLaunchComplete()
                            showWelcomeDialog = false
                        }
                    )
                }

                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) }
                ) { paddingValues ->
                    Box(modifier = Modifier.padding(paddingValues)) {
                        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                                                        com.example.audioloop.ui.AudioLoopMainScreen(
                                context = context,
                                uiState = uiState,
                                viewModel = viewModel,
                                onStartRecord = { name, useRaw ->
                                    if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                        if (useRaw && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                            pendingRecordingName = name
                                            pendingCategory = uiState.currentCategory
                                            val captureIntent = mediaProjectionManager.createScreenCaptureIntent()
                                            screenCaptureLauncher.launch(captureIntent)
                                            true
                                        } else {
                                            startRecording(name, uiState.currentCategory, false)
                                            true
                                        }
                                    } else {
                                        requestPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                        false
                                    }
                                },
                                onStopRecord = { stopRecording() },
                                onBackupSignIn = {
                                    viewModel.setBackupRunning(true)
                                    signInLauncher.launch(viewModel.driveBackupManager.getSignInIntent())
                                }
                            )

                        }
                    }
                }
            }
        }
        handleIncomingIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        val uris = extractUrisFromIntent(intent)
        if (uris.isEmpty()) return
        uris.forEach { uri -> vm?.importFile(uri) }
    }

    private fun extractUrisFromIntent(intent: Intent): List<Uri> {
        val results = LinkedHashSet<Uri>()
        when (intent.action) {
            Intent.ACTION_SEND -> intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let { results.add(it) }
            Intent.ACTION_SEND_MULTIPLE -> intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.let { results.addAll(it) }
            Intent.ACTION_VIEW -> intent.data?.let { results.add(it) }
        }
        intent.clipData?.let { clip ->
            for (i in 0 until clip.itemCount) clip.getItemAt(i).uri?.let { results.add(it) }
        }
        return results.toList()
    }

    private fun startRecording(fileName: String, category: String, useRawAudio: Boolean) {
        val usePublic = vm?.getPublicStoragePref() ?: true
        val finalName = if (category == "General") fileName else {
            if (usePublic) fileName else {
                val folder = File(filesDir, category)
                if (!folder.exists()) folder.mkdirs()
                "$category/$fileName"
            }
        }
        val intent = Intent(this, AudioService::class.java).apply {
            action = AudioService.ACTION_START_RECORDING
            putExtra(AudioService.EXTRA_FILENAME, finalName)
            putExtra(AudioService.EXTRA_USE_PUBLIC_STORAGE, usePublic)
            putExtra(AudioService.EXTRA_CATEGORY, category)
            val source = if (useRawAudio && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                MediaRecorder.AudioSource.UNPROCESSED
            } else { MediaRecorder.AudioSource.MIC }
            putExtra(AudioService.EXTRA_AUDIO_SOURCE, source)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
    }

    private fun startInternalAudioService(serviceIntent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(serviceIntent) else startService(serviceIntent)
    }

    private fun stopRecording() {
        val intent = Intent(this, AudioService::class.java).apply { action = AudioService.ACTION_STOP_RECORDING }
        startService(intent)
    }
}

// â”€â”€ Welcome Dialog (extracted) â”€â”€

@Composable
private fun WelcomeDialog(
    themeColors: com.example.audioloop.ui.theme.AppColorPalette,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { },
        containerColor = Zinc900,
        titleContentColor = Color.White,
        textContentColor = Zinc300,
        title = {
            Text(
                stringResource(R.string.welcome_title),
                style = androidx.compose.ui.text.TextStyle(
                    brush = Brush.linearGradient(
                        listOf(themeColors.primary400, themeColors.primary200)
                    ),
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.welcome_modes_intro), color = Color.White, fontWeight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("\uD83C\uDFA4", fontSize = 16.sp)
                    Column {
                        Text(stringResource(R.string.mode_speech), color = themeColors.primary300, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.mode_speech_desc), color = Zinc400, fontSize = 13.sp)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("\uD83D\uDD0A", fontSize = 16.sp)
                    Column {
                        Text(stringResource(R.string.mode_stream), color = themeColors.primary300, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.mode_stream_desc), color = Zinc400, fontSize = 13.sp)
                    }
                }
                HorizontalDivider(color = Zinc700)
                Text(stringResource(R.string.welcome_stream_note), color = Sunset400, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                Text(
                    stringResource(R.string.welcome_stream_warning),
                    color = Zinc400, fontSize = 13.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = themeColors.primary600)
            ) {
                Text(stringResource(R.string.btn_got_it), color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    )
}


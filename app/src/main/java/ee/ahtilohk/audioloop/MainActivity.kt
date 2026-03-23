package ee.ahtilohk.audioloop

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
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AppCompatActivity
import android.media.projection.MediaProjectionManager
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.lifecycle.viewmodel.compose.viewModel
import ee.ahtilohk.audioloop.ui.theme.AudioLoopTheme
import ee.ahtilohk.audioloop.R
import ee.ahtilohk.audioloop.ui.theme.Zinc300
import ee.ahtilohk.audioloop.ui.theme.Zinc400
import ee.ahtilohk.audioloop.ui.theme.Zinc700
import ee.ahtilohk.audioloop.ui.theme.Zinc900
import ee.ahtilohk.audioloop.ui.theme.Sunset400
import ee.ahtilohk.audioloop.ui.AudioLoopMainScreen
import ee.ahtilohk.audioloop.ui.components.OnboardingScreen
import ee.ahtilohk.audioloop.ui.components.UpgradeSheet
import ee.ahtilohk.audioloop.widget.WidgetStateHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * MainActivity — slim UI host.
 *
 * All state and business logic lives in AudioLoopViewModel.
 * This Activity is responsible for:
 * - Lifecycle (onCreate, onDestroy, onNewIntent)
 * - Permission launchers (recording, screen capture, sign-in)
 * - Setting Compose content
 */
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private val viewModel: AudioLoopViewModel by viewModels()
    private var pendingRecordingName = ""
    private var pendingCategory = ""
    private lateinit var mediaProjectionManager: MediaProjectionManager

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

    private val requestPermissionsLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        val recordGranted = results[Manifest.permission.RECORD_AUDIO] ?: false
        
        if (recordGranted) {
            vm?.showSnackbar(getString(R.string.msg_permission_granted))
        } else if (results.isNotEmpty()) {
            vm?.showSnackbar(getString(R.string.msg_permission_denied), isError = true)
        }
        vm?.setReady(true)
    }


    @Suppress("DEPRECATION")
    private val signInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (data != null) {
            val task = com.google.android.gms.auth.api.signin.GoogleSignIn.getSignedInAccountFromIntent(data)
            if (task.isSuccessful) {
                val acct = task.result
                vm?.driveBackupManager?.handleSignInResult(acct)
                vm?.handleSignInResult(acct?.email)
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
                    vm?.driveBackupManager?.handleSignInResult(account)
                    vm?.handleSignInResult(account?.email)
                }
                ?.addOnFailureListener { e ->
                    vm?.handleSignInError(getString(R.string.signin_failed, e.localizedMessage))
                }
        }
        vm?.setBackupRunning(false)
    }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    private fun checkAndRequestNecessaryPermissions() {
        val permissions = mutableListOf<String>()
        permissions.add(Manifest.permission.RECORD_AUDIO)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val toRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (toRequest.isNotEmpty()) {
            requestPermissionsLauncher.launch(toRequest.toTypedArray())
        }
    }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        
        splashScreen.setKeepOnScreenCondition { !viewModel.uiState.value.isReady }

        try {
            mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as android.media.projection.MediaProjectionManager
        } catch (e: Exception) { AppLog.e("Error getting MediaProjectionManager", e) }

        setContent {
            vm = viewModel

            // Initialize ViewModel once
            LaunchedEffect(Unit) {
                viewModel.initialize()
                viewModel.refreshFileList()
                viewModel.startProgressTracking()
                
                // Permission check
                val permissions = mutableListOf<String>()
                permissions.add(Manifest.permission.RECORD_AUDIO)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                    permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
                } else {
                    permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
                
                val toRequest = permissions.filter {
                    ContextCompat.checkSelfPermission(this@MainActivity, it) != PackageManager.PERMISSION_GRANTED
                }
                
                if (toRequest.isNotEmpty()) {
                    requestPermissionsLauncher.launch(toRequest.toTypedArray())
                    // isReady is set in the permission result callback or after a small delay
                    // However, for better UX, we can set isReady after triggering the prompt 
                    // or even better, wait for the result.
                } else {
                    viewModel.setReady(true)
                }
            }

            val uiState by viewModel.uiState.collectAsState()
            
            // UI visibility check to prevent flashing before permissions
            if (!uiState.isReady) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                return@setContent
            }
            
            val windowSizeClass = calculateWindowSizeClass(this)

            
            val darkTheme = when (uiState.themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.AUTO -> isSystemInDarkTheme()
            }

            AudioLoopTheme(darkTheme = darkTheme, appTheme = uiState.currentTheme) {
                val snackbarHostState = remember { SnackbarHostState() }
                val coroutineScope = rememberCoroutineScope()
                val context = this@MainActivity

                // Snackbar handler — replaces Toast
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
                            if (msg.actionLabel == context.getString(R.string.btn_undo)) {
                                viewModel.undoDelete()
                            }
                        }
                        viewModel.clearSnackbar()
                    }
                }

                // Permissions are checked in the ViewModel LaunchedEffect above

                // Onboarding Overlay
                if (uiState.showOnboarding) {
                    OnboardingScreen(
                        uiState = uiState,
                        viewModel = viewModel,
                        themeColors = uiState.currentTheme.palette
                    )
                }

                // Billing / Pro Overlay
                UpgradeSheet(
                    isVisible = uiState.showUpgradeSheet,
                    onDismiss = { viewModel.setUpgradeSheetVisible(false) },
                    products = uiState.billingProducts,
                    onPurchase = { activity, product -> viewModel.purchasePro(activity, product) },
                    onRestorePurchases = { viewModel.restorePurchases() },
                    themeColors = uiState.currentTheme.palette,
                    isPro = uiState.isProUser
                )

                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) }
                ) { paddingValues ->
                    Box(modifier = Modifier.padding(paddingValues)) {
                        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                            AudioLoopMainScreen(
                                context = context,
                                uiState = uiState,
                                viewModel = viewModel,
                                windowSizeClass = windowSizeClass,
                                onStartRecord = { name, useRaw ->
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                        if (useRaw && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
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
                                        checkAndRequestNecessaryPermissions()
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
            Intent.ACTION_SEND -> {
                val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
                uri?.let { results.add(it) }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val uris = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
                }
                uris?.let { results.addAll(it) }
            }
            Intent.ACTION_VIEW -> intent.data?.let { results.add(it) }
        }
        intent.clipData?.let { clip ->
            for (i in 0 until clip.itemCount) clip.getItemAt(i).uri?.let { results.add(it) }
        }
        return results.toList()
    }

    private fun startRecording(fileName: String, category: String, useRawAudio: Boolean) {
        val usePublic = vm?.getPublicStoragePref() ?: true
        val finalName = fileName
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



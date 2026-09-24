package com.example.app_blindenstock_add_on.framework.activity

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.example.app_blindenstock_add_on.framework.system_services.AppForegroundService
import com.example.app_blindenstock_add_on.framework.ui.CameraScreen
import com.example.app_blindenstock_add_on.framework.ui.StartScreen
import com.example.app_blindenstock_add_on.framework.ui.theme.App_BlindenstockaddonTheme
import com.example.app_blindenstock_add_on.framework.viewmodel.AppScreen
import com.example.app_blindenstock_add_on.framework.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: MainViewModel

    private val requiredPermissions = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.POST_NOTIFICATIONS,
        Manifest.permission.NEARBY_WIFI_DEVICES,
        Manifest.permission.CAMERA,
        Manifest.permission.ACTIVITY_RECOGNITION,
    )

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            checkBatteryOptimization()
        } else {
            permissions.filter { !it.value }.forEach { (perm, _) ->
                android.util.Log.e("MainActivity", "Permission denied: $perm")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        viewModel = MainViewModel()
        viewModel.loadUrl(this)

        setContent {
            App_BlindenstockaddonTheme {
                val uiState by viewModel.uiState.collectAsState()
                when (uiState.currentScreen) {
                    AppScreen.START -> StartScreen(
                        viewModel = viewModel,
                        onStartBackground = { sourceType, url -> startBackgroundService(sourceType, url) },
                        onStopBackground = ::stopBackgroundService,
                    )
                    AppScreen.CAMERA -> CameraScreen(viewModel = viewModel)
                }
            }
        }

        if (!hasAllPermissions()) {
            permissionLauncher.launch(requiredPermissions)
        } else {
            checkBatteryOptimization()
        }
    }

    private var lastVolDirection = 0
    private var lastVolTime = 0L

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val isBackground = viewModel.uiState.value.isBackgroundRunning

        // Wenn der Hintergrunddienst läuft, kümmert sich dieser um den Trigger.
        // Das verhindert, dass Gemini zweimal gleichzeitig API-Calls feuert!
        if (isBackground) return super.onKeyDown(keyCode, event)

        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            // Verhindert mehrfaches Auslösen, wenn die Taste gedrückt gehalten wird
            if (event?.repeatCount == 0) {
                val direction = if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) 1 else -1
                val now = System.currentTimeMillis()

                // Zeitfenster auf 800ms erhöht (analog zum Background Service)
                if (now - lastVolTime < 800L && direction != lastVolDirection) {
                    lastVolTime = 0L
                    viewModel.triggerSceneDescription()
                } else {
                    lastVolDirection = direction
                    lastVolTime = now
                }
            }
            return super.onKeyDown(keyCode, event) // Lautstärke normal ändern lassen
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun hasAllPermissions(): Boolean = requiredPermissions.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("BatteryLife")
    private fun checkBatteryOptimization() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = "package:$packageName".toUri()
            }
            startActivity(intent)
        }
    }

    private fun startBackgroundService(sourceType: String, url: String) {
        viewModel.stopStream()

        val intent = Intent(this, AppForegroundService::class.java).apply {
            putExtra("EXTRA_SOURCE_TYPE", sourceType)
            putExtra("EXTRA_STREAM_URL", url)
            putExtra("EXTRA_ALLOW_SURFACE", viewModel.uiState.value.isSurfaceScanEnabled)
        }
        try {
            startForegroundService(intent)
            viewModel.setBackgroundRunning(running = true)
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Start-Fehler: ${e.message}", e)
        }
    }

    private fun stopBackgroundService() {
        val intent = Intent(this, AppForegroundService::class.java)
        stopService(intent)
        viewModel.setBackgroundRunning(running = false)
    }
}
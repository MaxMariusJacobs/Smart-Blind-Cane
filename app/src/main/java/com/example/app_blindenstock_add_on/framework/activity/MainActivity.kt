package com.example.app_blindenstock_add_on.framework.activity

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.example.app_blindenstock_add_on.data.yolov11n_gpu.YoloDetector
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
        Manifest.permission.ACTIVITY_RECOGNITION
    )

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
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

        val detector = YoloDetector(this)
        viewModel = MainViewModel(detector)

        setContent {
            App_BlindenstockaddonTheme {
                val uiState by viewModel.uiState.collectAsState()
                when (uiState.currentScreen) {
                    AppScreen.START -> StartScreen(
                        viewModel = viewModel,
                        onStartBackground = { sourceType, url -> startBackgroundService(sourceType, url) },
                        onStopBackground = { stopBackgroundService() }
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

    private fun hasAllPermissions(): Boolean = requiredPermissions.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

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
        // GPU-Schutz: UI-Stream schließen, bevor der Service die GPU anfordert
        viewModel.stopStream()

        val intent = Intent(this, AppForegroundService::class.java).apply {
            putExtra("EXTRA_SOURCE_TYPE", sourceType)
            putExtra("EXTRA_STREAM_URL", url)
            putExtra("EXTRA_ALLOW_ROADWAY", viewModel.uiState.value.isRoadwayAlertsEnabled)
        }
        try {
            startForegroundService(intent)
            viewModel.setBackgroundRunning(true)
            android.util.Log.d("MainActivity", "Background service started with type $sourceType.")
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Failed to start service: ${e.message}", e)
        }
    }

    private fun stopBackgroundService() {
        val intent = Intent(this, AppForegroundService::class.java)
        stopService(intent)
        viewModel.setBackgroundRunning(false)
        android.util.Log.d("MainActivity", "Background service stopped.")
    }
}
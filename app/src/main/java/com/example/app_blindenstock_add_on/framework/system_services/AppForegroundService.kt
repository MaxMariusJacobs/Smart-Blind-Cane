package com.example.app_blindenstock_add_on.framework.system_services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.example.app_blindenstock_add_on.AppConfig
import com.example.app_blindenstock_add_on.data.audio.SpeechFeedbackManager
import com.example.app_blindenstock_add_on.data.sensor.UserMotionTracker
import com.example.app_blindenstock_add_on.data.video.LocalCameraSource
import com.example.app_blindenstock_add_on.data.video.MjpegStreamer
import com.example.app_blindenstock_add_on.data.video.VideoSource
import com.example.app_blindenstock_add_on.data.yolov11n_gpu.YoloDetector
import com.example.app_blindenstock_add_on.domain.model.Detection
import com.example.app_blindenstock_add_on.domain.synthesizer.GuidanceSynthesizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

data class ServiceMetrics(
    val fps: Int = 0,
    val inferenceTime: Long = 0,
    val isWalking: Boolean = false,
    val detectionCount: Int = 0,
    val isRunning: Boolean = false,
    val currentPhrase: String = "Clear",
    val detections: List<Detection> = emptyList(),
    val primarySurface: String = "unknown"
)

class AppForegroundService : Service(), LifecycleOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private var videoSource: VideoSource? = null
    private var detector: YoloDetector? = null
    private var motionTracker: UserMotionTracker? = null
    private var speechManager: SpeechFeedbackManager? = null

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    private var fpsCounter = 0
    private var fpsWindowStart = 0L
    private var smoothedFps = 0

    companion object {
        private const val CHANNEL_ID = "AppForegroundServiceChannel"
        private const val NOTIFICATION_ID = 1

        @Volatile
        var allowSurfaceScans: Boolean = false

        private val _metrics = MutableStateFlow(ServiceMetrics())
        val metrics: StateFlow<ServiceMetrics> = _metrics.asStateFlow()
    }

    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        createNotificationChannel()

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AppForegroundService::CpuLock")

        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "AppForegroundService::WifiLock")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED

        if (wakeLock?.isHeld != true) wakeLock?.acquire(10 * 60 * 60 * 1000L)
        if (wifiLock?.isHeld != true) wifiLock?.acquire()

        val sourceType = intent?.getStringExtra("EXTRA_SOURCE_TYPE") ?: "MJPEG"
        val url = intent?.getStringExtra("EXTRA_STREAM_URL") ?: "http://192.168.4.1/stream"
        if (intent?.hasExtra("EXTRA_ALLOW_SURFACE") == true) {
            allowSurfaceScans = intent.getBooleanExtra("EXTRA_ALLOW_SURFACE", false)
        }

        val isEspMode = (sourceType != "CAMERA")

        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            _metrics.value = _metrics.value.copy(isRunning = true)
        } catch (e: Exception) {
            android.util.Log.e("AppForegroundService", "Start-Fehler: ${e.message}", e)
        }

        fpsCounter = 0
        fpsWindowStart = System.currentTimeMillis()
        smoothedFps = 0

        serviceScope.launch {
            try {
                detector = YoloDetector(this@AppForegroundService)
                motionTracker = UserMotionTracker(this@AppForegroundService).apply { start() }
                speechManager = SpeechFeedbackManager(this@AppForegroundService)

                videoSource = if (sourceType == "CAMERA") {
                    LocalCameraSource(this@AppForegroundService, this@AppForegroundService)
                } else {
                    MjpegStreamer(OkHttpClient(), url) { message ->
                        speechManager?.speakUrgent(message)
                    }
                }

                videoSource?.getFrames()
                    ?.conflate()
                    ?.collect { bitmap ->
                        val currentTime = System.currentTimeMillis()

                        fpsCounter++
                        val elapsed = currentTime - fpsWindowStart
                        if (elapsed >= 1000L) {
                            smoothedFps = ((fpsCounter * 1000L) / elapsed).toInt()
                            fpsCounter = 0
                            fpsWindowStart = currentTime
                        }

                        val isWalking = motionTracker?.isUserWalking ?: false
                        val currentConfig = AppConfig.currentState.value

                        val analysisResult = detector?.detect(bitmap, isWalking, currentConfig, allowSurfaceScans) ?: return@collect

                        val guidance = GuidanceSynthesizer.synthesize(
                            result = analysisResult,
                            isUserWalking = isWalking,
                            allowSurfaceScans = allowSurfaceScans,
                            isEsp32 = isEspMode,
                            config = currentConfig
                        )
                        speechManager?.processGuidance(guidance)

                        _metrics.value = ServiceMetrics(
                            fps = smoothedFps,
                            inferenceTime = detector?.lastInferenceTime ?: 0L,
                            isWalking = isWalking,
                            detectionCount = analysisResult.detections.size,
                            isRunning = true,
                            currentPhrase = guidance.phrase,
                            detections = analysisResult.detections,
                            primarySurface = analysisResult.primarySurface
                        )
                    }
            } catch (e: Exception) {
                android.util.Log.e("AppForegroundService", "Pipeline-Fehler: ${e.message}", e)
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        super.onDestroy()

        videoSource?.stop()
        videoSource = null
        motionTracker?.stop()
        motionTracker = null
        speechManager?.shutdown()
        speechManager = null

        val det = detector
        detector = null
        CoroutineScope(Dispatchers.IO).launch {
            det?.close()
        }

        if (wifiLock?.isHeld == true) wifiLock?.release()
        if (wakeLock?.isHeld == true) wakeLock?.release()

        _metrics.value = ServiceMetrics(isRunning = false)
        serviceJob.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Smart Cane Active")
            .setContentText("Background environment tracking is running.")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(CHANNEL_ID, "App Assistance Service", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(serviceChannel)
        }
    }
}
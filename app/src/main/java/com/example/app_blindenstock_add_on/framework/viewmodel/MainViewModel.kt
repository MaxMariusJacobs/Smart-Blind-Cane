package com.example.app_blindenstock_add_on.framework.viewmodel

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.app_blindenstock_add_on.AppConfig
import com.example.app_blindenstock_add_on.data.audio.SpeechFeedbackManager
import com.example.app_blindenstock_add_on.data.sensor.UserMotionTracker
import com.example.app_blindenstock_add_on.data.video.LocalCameraSource
import com.example.app_blindenstock_add_on.data.video.MjpegStreamer
import com.example.app_blindenstock_add_on.data.video.VideoSource
import com.example.app_blindenstock_add_on.data.yolov11n_gpu.YoloDetector
import com.example.app_blindenstock_add_on.domain.model.CorridorAnalysis
import com.example.app_blindenstock_add_on.domain.model.Detection
import com.example.app_blindenstock_add_on.domain.synthesizer.GuidanceSynthesizer
import com.example.app_blindenstock_add_on.framework.system_services.AppForegroundService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class AppScreen { START, CAMERA }
enum class SourceType { MJPEG, CAMERA }

data class MainUiState(
    val currentScreen: AppScreen = AppScreen.START,
    val currentFrame: Bitmap? = null,
    val detections: List<Detection> = emptyList(),
    val corridor: CorridorAnalysis = CorridorAnalysis(),
    val primarySurface: String = "unknown",
    val currentGuidancePhrase: String = "Clear",
    val isSurfaceScanEnabled: Boolean = false,
    val detectionLogs: List<String> = emptyList(),
    val errorMessage: String? = null,
    val streamUrl: String = "",
    val savedUrls: List<String> = emptyList(),
    val sourceType: SourceType = SourceType.MJPEG,
    val isStreaming: Boolean = false,
    val isLoading: Boolean = false,
    val isBackgroundRunning: Boolean = false,
    val isUserWalking: Boolean = false,
    val inferenceTime: Long = 0,
    val currentFps: Int = 0
)

class MainViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var streamJob: Job? = null
    private var cleanupJob: Job? = null

    private var videoSource: VideoSource? = null
    private var detector: YoloDetector? = null
    private var motionTracker: UserMotionTracker? = null
    private var speechManager: SpeechFeedbackManager? = null

    private var fpsCounter = 0
    private var fpsWindowStart = 0L
    private var smoothedFps = 0

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

    init {
        viewModelScope.launch {
            AppForegroundService.metrics.collect { metrics ->
                if (_uiState.value.isBackgroundRunning || metrics.isRunning) {
                    _uiState.value = _uiState.value.copy(
                        isBackgroundRunning = metrics.isRunning,
                        currentFps = metrics.fps,
                        inferenceTime = metrics.inferenceTime,
                        isUserWalking = metrics.isWalking,
                        currentGuidancePhrase = metrics.currentPhrase,
                        detections = metrics.detections,
                        primarySurface = metrics.primarySurface
                    )
                }
            }
        }
    }

    fun navigateTo(screen: AppScreen) { _uiState.value = _uiState.value.copy(currentScreen = screen) }
    fun updateUrl(newUrl: String) { _uiState.value = _uiState.value.copy(streamUrl = newUrl) }
    fun updateSourceType(type: SourceType) { _uiState.value = _uiState.value.copy(sourceType = type) }
    fun setBackgroundRunning(running: Boolean) { _uiState.value = _uiState.value.copy(isBackgroundRunning = running) }

    fun toggleSurfaceScan() {
        val next = !_uiState.value.isSurfaceScanEnabled
        _uiState.value = _uiState.value.copy(isSurfaceScanEnabled = next)
        AppForegroundService.allowSurfaceScans = next
    }

    fun loadUrl(context: Context) {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val savedString = prefs.getString("saved_urls", "") ?: ""
        val urls = savedString.split(",").filter { it.isNotBlank() }

        _uiState.value = _uiState.value.copy(
            streamUrl = "",
            savedUrls = urls
        )
    }

    fun saveUrl(context: Context, url: String) {
        if (url.isBlank()) return
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

        val currentUrls = _uiState.value.savedUrls.toMutableList()
        currentUrls.remove(url)
        currentUrls.add(0, url)
        val trimmedUrls = currentUrls.take(3)

        prefs.edit().putString("saved_urls", trimmedUrls.joinToString(",")).apply()

        _uiState.value = _uiState.value.copy(
            streamUrl = url,
            savedUrls = trimmedUrls
        )
    }

    fun startStream(context: Context, lifecycleOwner: androidx.lifecycle.LifecycleOwner) {
        if (_uiState.value.isStreaming || _uiState.value.isLoading) return

        val urlToUse = _uiState.value.streamUrl
        if (_uiState.value.sourceType == SourceType.MJPEG && urlToUse.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Please enter or select a network address")
            return
        }

        val serviceIntent = Intent(context, AppForegroundService::class.java)
        context.stopService(serviceIntent)
        _uiState.value = _uiState.value.copy(isBackgroundRunning = false)

        val type = _uiState.value.sourceType
        val isEspMode = (type == SourceType.MJPEG)

        fpsCounter = 0
        fpsWindowStart = System.currentTimeMillis()
        smoothedFps = 0

        _uiState.value = _uiState.value.copy(isStreaming = true, isLoading = true)

        streamJob = viewModelScope.launch(Dispatchers.Default) {
            cleanupJob?.join()

            detector = YoloDetector(context)
            speechManager = SpeechFeedbackManager(context)
            motionTracker = UserMotionTracker(context).apply { start() }

            videoSource = when (type) {
                SourceType.MJPEG -> MjpegStreamer(okhttp3.OkHttpClient(), urlToUse) { message ->
                    speechManager?.speakUrgent(message)
                }
                SourceType.CAMERA -> {
                    val activity = context as? androidx.activity.ComponentActivity
                        ?: throw IllegalStateException("Context must be a ComponentActivity")
                    LocalCameraSource(context, activity)
                }
            }

            try {
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
                        val runSurface = _uiState.value.isSurfaceScanEnabled

                        val result = detector?.detect(bitmap, isWalking, currentConfig, runSurface) ?: return@collect
                        val infTime = detector?.lastInferenceTime ?: 0L

                        val guidance = GuidanceSynthesizer.synthesize(
                            result = result,
                            isUserWalking = isWalking,
                            allowSurfaceScans = runSurface,
                            isEsp32 = isEspMode,
                            config = currentConfig
                        )
                        val wasSpoken = speechManager?.processGuidance(guidance) ?: false

                        val updatedLogs = if (wasSpoken) {
                            val logEntry = "[${timeFormat.format(Date(currentTime))}] ${guidance.phrase}"
                            (listOf(logEntry) + _uiState.value.detectionLogs).take(30)
                        } else {
                            _uiState.value.detectionLogs
                        }

                        _uiState.value = _uiState.value.copy(
                            currentFrame = bitmap,
                            detections = result.detections,
                            primarySurface = result.primarySurface,
                            currentGuidancePhrase = guidance.phrase,
                            detectionLogs = updatedLogs,
                            inferenceTime = infTime,
                            currentFps = smoothedFps,
                            isUserWalking = isWalking,
                            isLoading = false
                        )
                    }
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Stream error: ${e.message}", e)
                _uiState.value = _uiState.value.copy(isStreaming = false, isLoading = false, errorMessage = e.message)
            }
        }
    }

    fun stopStream() {
        val jobToCancel = streamJob
        streamJob = null

        val vs = videoSource
        val mt = motionTracker
        val sm = speechManager
        val det = detector

        videoSource = null
        motionTracker = null
        speechManager = null
        detector = null

        _uiState.value = _uiState.value.copy(
            isStreaming = false,
            isLoading = false,
            currentFrame = null,
            detections = emptyList()
        )

        cleanupJob = viewModelScope.launch(Dispatchers.IO) {
            jobToCancel?.cancel()
            jobToCancel?.join()

            vs?.stop()
            mt?.stop()
            sm?.shutdown()
            det?.close()
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopStream()
    }

    fun saveTuningConfig(context: Context) {
        val prefs = context.getSharedPreferences("tuning_prefs", Context.MODE_PRIVATE)
        val s = com.example.app_blindenstock_add_on.AppConfig.currentState.value
        prefs.edit().apply {
            putFloat("corridorLeft", s.corridorLeft)
            putFloat("corridorRight", s.corridorRight)
            putFloat("stopFloorPhone", s.stopFloorPhone)
            putFloat("personFloorPhone", s.personFloorPhone)
            putFloat("objectFloorPhone", s.objectFloorPhone)
            putFloat("stairsFloorPhone", s.stairsFloorPhone)
            putFloat("stopAreaPhone", s.stopAreaPhone)
            putFloat("warningAreaPhone", s.warningAreaPhone)

            putFloat("stopFloorEsp", s.stopFloorEsp)
            putFloat("personFloorEsp", s.personFloorEsp)
            putFloat("objectFloorEsp", s.objectFloorEsp)
            putFloat("stairsFloorEsp", s.stairsFloorEsp)
            putFloat("stopAreaEsp", s.stopAreaEsp)
            putFloat("warningAreaEsp", s.warningAreaEsp)

            putFloat("confThresholdObjects", s.confThresholdObjects)
            putFloat("confThresholdSurface", s.confThresholdSurface)
            putFloat("nmsIouThreshold", s.nmsIouThreshold)
            putInt("hazardFramesRequired", s.hazardFramesRequired)
            putInt("surfaceFramesRequired", s.surfaceFramesRequired)
            putInt("clearFramesRequired", s.clearFramesRequired)
        }.apply()
    }

    fun loadTuningConfig(context: Context) {
        val prefs = context.getSharedPreferences("tuning_prefs", Context.MODE_PRIVATE)
        if (!prefs.contains("corridorLeft")) return

        val loaded = com.example.app_blindenstock_add_on.AppConfigState(
            corridorLeft = prefs.getFloat("corridorLeft", 0.32f),
            corridorRight = prefs.getFloat("corridorRight", 0.68f),
            stopFloorPhone = prefs.getFloat("stopFloorPhone", 0.85f),
            personFloorPhone = prefs.getFloat("personFloorPhone", 0.45f),
            objectFloorPhone = prefs.getFloat("objectFloorPhone", 0.50f),
            stairsFloorPhone = prefs.getFloat("stairsFloorPhone", 0.50f),
            stopAreaPhone = prefs.getFloat("stopAreaPhone", 0.35f),
            warningAreaPhone = prefs.getFloat("warningAreaPhone", 0.15f),

            stopFloorEsp = prefs.getFloat("stopFloorEsp", 0.84f),
            personFloorEsp = prefs.getFloat("personFloorEsp", 0.62f),
            objectFloorEsp = prefs.getFloat("objectFloorEsp", 0.64f),
            stairsFloorEsp = prefs.getFloat("stairsFloorEsp", 0.62f),
            stopAreaEsp = prefs.getFloat("stopAreaEsp", 0.35f),
            warningAreaEsp = prefs.getFloat("warningAreaEsp", 0.15f),

            confThresholdObjects = prefs.getFloat("confThresholdObjects", 0.40f),
            confThresholdSurface = prefs.getFloat("confThresholdSurface", 0.50f),
            nmsIouThreshold = prefs.getFloat("nmsIouThreshold", 0.40f),
            hazardFramesRequired = prefs.getInt("hazardFramesRequired", 3),
            surfaceFramesRequired = prefs.getInt("surfaceFramesRequired", 8),
            clearFramesRequired = prefs.getInt("clearFramesRequired", 6)
        )
        com.example.app_blindenstock_add_on.AppConfig.update(loaded)
    }

    fun resetTuningConfig() {
        com.example.app_blindenstock_add_on.AppConfig.update(com.example.app_blindenstock_add_on.AppConfigState())
    }

    fun removeUrl(context: Context, url: String) {
        val currentUrls = _uiState.value.savedUrls.toMutableList()
        currentUrls.remove(url)

        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("saved_urls", currentUrls.joinToString(",")).apply()

        val newStreamUrl = if (_uiState.value.streamUrl == url) "" else _uiState.value.streamUrl
        _uiState.value = _uiState.value.copy(
            streamUrl = newStreamUrl,
            savedUrls = currentUrls
        )
    }
}
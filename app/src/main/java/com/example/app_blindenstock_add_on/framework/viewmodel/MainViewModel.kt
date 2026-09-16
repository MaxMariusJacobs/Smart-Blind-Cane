package com.example.app_blindenstock_add_on.framework.viewmodel

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    val isRoadwayAlertsEnabled: Boolean = false,
    val detectionLogs: List<String> = emptyList(),
    val errorMessage: String? = null,
    val streamUrl: String = "http://192.168.4.1/stream",
    val sourceType: SourceType = SourceType.MJPEG,
    val isStreaming: Boolean = false,
    val isBackgroundRunning: Boolean = false,
    val isUserWalking: Boolean = false,
    val inferenceTime: Long = 0,
    val currentFps: Int = 0
)

class MainViewModel(
    private val detector: YoloDetector
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var streamJob: Job? = null
    private var videoSource: VideoSource? = null
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
                        isUserWalking = metrics.isWalking
                    )
                }
            }
        }
    }

    fun navigateTo(screen: AppScreen) { _uiState.value = _uiState.value.copy(currentScreen = screen) }
    fun updateUrl(newUrl: String) { _uiState.value = _uiState.value.copy(streamUrl = newUrl) }
    fun updateSourceType(type: SourceType) { _uiState.value = _uiState.value.copy(sourceType = type) }
    fun setBackgroundRunning(running: Boolean) { _uiState.value = _uiState.value.copy(isBackgroundRunning = running) }

    fun toggleRoadwayAlerts() {
        val next = !_uiState.value.isRoadwayAlertsEnabled
        _uiState.value = _uiState.value.copy(isRoadwayAlertsEnabled = next)
        AppForegroundService.allowRoadwayAlerts = next
    }

    fun startStream(context: Context, lifecycleOwner: androidx.lifecycle.LifecycleOwner) {
        if (_uiState.value.isStreaming) return

        val serviceIntent = Intent(context, AppForegroundService::class.java)
        context.stopService(serviceIntent)
        _uiState.value = _uiState.value.copy(isBackgroundRunning = false)

        speechManager = SpeechFeedbackManager(context)
        motionTracker = UserMotionTracker(context).apply { start() }

        val type = _uiState.value.sourceType
        val url = _uiState.value.streamUrl
        val isEspMode = (type == SourceType.MJPEG)

        videoSource = when (type) {
            SourceType.MJPEG -> MjpegStreamer(okhttp3.OkHttpClient(), url)
            SourceType.CAMERA -> {
                val activity = context as? androidx.activity.ComponentActivity
                    ?: throw IllegalStateException("Context must be a ComponentActivity")
                LocalCameraSource(context, activity)
            }
        }

        fpsCounter = 0
        fpsWindowStart = System.currentTimeMillis()
        smoothedFps = 0

        _uiState.value = _uiState.value.copy(isStreaming = true)

        streamJob = viewModelScope.launch(Dispatchers.Default) {
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
                        val result = detector.detect(bitmap, isWalking)
                        val infTime = detector.lastInferenceTime

                        val guidance = GuidanceSynthesizer.synthesize(
                            result = result,
                            isUserWalking = isWalking,
                            allowRoadwayAlerts = _uiState.value.isRoadwayAlertsEnabled,
                            isEsp32 = isEspMode
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
                            isUserWalking = isWalking
                        )
                    }
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Stream error: ${e.message}", e)
                _uiState.value = _uiState.value.copy(isStreaming = false, errorMessage = e.message)
            }
        }
    }

    fun stopStream() {
        streamJob?.cancel()
        streamJob = null
        videoSource?.stop()
        videoSource = null
        motionTracker?.stop()
        motionTracker = null
        speechManager?.shutdown()
        speechManager = null
        _uiState.value = _uiState.value.copy(isStreaming = false, currentFrame = null, detections = emptyList())
    }

    override fun onCleared() {
        super.onCleared()
        stopStream()
        detector.close()
    }
}
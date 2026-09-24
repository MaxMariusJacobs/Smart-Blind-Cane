package com.example.app_blindenstock_add_on.framework.viewmodel

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.app_blindenstock_add_on.AppConfig
import com.example.app_blindenstock_add_on.data.audio.SceneDescriptionService
import com.example.app_blindenstock_add_on.data.audio.SpeechFeedbackManager
import com.example.app_blindenstock_add_on.data.sensor.UserMotionTracker
import com.example.app_blindenstock_add_on.data.video.LocalCameraSource
import com.example.app_blindenstock_add_on.data.video.MjpegStreamer
import com.example.app_blindenstock_add_on.data.video.VideoSource
import com.example.app_blindenstock_add_on.data.yolov11n_gpu.YoloDetector
import com.example.app_blindenstock_add_on.domain.model.Detection
import com.example.app_blindenstock_add_on.domain.synthesizer.GuidanceSynthesizer
import com.example.app_blindenstock_add_on.framework.system_services.AppForegroundService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
enum class GeminiStatus { IDLE, ANALYZING, SUCCESS, ERROR }

data class MainUiState(
    val currentScreen: AppScreen = AppScreen.START,
    val currentFrame: Bitmap? = null,
    val detections: List<Detection> = emptyList(),
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
    val currentFps: Int = 0,
    val geminiStatus: GeminiStatus = GeminiStatus.IDLE
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

    private val sceneDescriptionService = SceneDescriptionService()

    private var fpsCounter = 0
    private var fpsWindowStart = 0L
    private var smoothedFps = 0

    private var isFetchingGemini = false
    private var lastWalkTimeMs = System.currentTimeMillis()

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
                        primarySurface = metrics.primarySurface,
                        geminiStatus = metrics.geminiStatus
                    )

                    if (metrics.isWalking) {
                        lastWalkTimeMs = System.currentTimeMillis()
                    }
                }
            }
        }
    }

    fun triggerSceneDescription() {
        if (isFetchingGemini) return

        val frame = _uiState.value.currentFrame
        if (frame == null) {
            speechManager?.speakUrgent("No image available")
            return
        }

        isFetchingGemini = true
        _uiState.value = _uiState.value.copy(geminiStatus = GeminiStatus.ANALYZING)
        speechManager?.speakUrgent("Analyzing")

        viewModelScope.launch(Dispatchers.Main) {
            try {
                val description = sceneDescriptionService.describeScene(frame)

                speechManager?.speakUrgent(description)

                if (description.contains("No connection") || description.contains("could not") || description.contains("too long")) {
                    _uiState.value = _uiState.value.copy(geminiStatus = GeminiStatus.ERROR)
                } else {
                    _uiState.value = _uiState.value.copy(geminiStatus = GeminiStatus.SUCCESS)
                }
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Gemini error", e)
                speechManager?.speakUrgent("Failed to analyze")
                _uiState.value = _uiState.value.copy(geminiStatus = GeminiStatus.ERROR)
            } finally {
                isFetchingGemini = false
                delay(3000)
                _uiState.value = _uiState.value.copy(geminiStatus = GeminiStatus.IDLE)
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

            // WICHTIG: Gib dem ESP32 und dem OS Zeit, den alten Socket des Background-Services zu bereinigen
            if (isEspMode) delay(300)

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

                        if (isWalking) lastWalkTimeMs = currentTime

                        val result = detector?.detect(bitmap, isWalking, currentConfig, runSurface, isEsp32 = isEspMode) ?: return@collect
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
            detections = emptyList(),
            currentFps = 0,
            inferenceTime = 0
        )

        cleanupJob = viewModelScope.launch(Dispatchers.IO) {
            // 1. ZUERST das Netzwerk abklemmen (verhindert Deadlocks im I/O)
            vs?.stop()

            // 2. JETZT auf die Coroutine warten (sie kann nun beenden, da I/O gekappt ist)
            jobToCancel?.cancel()
            jobToCancel?.join()

            // 3. Restliche Ressourcen schließen
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
            putFloat("corridorLeftPhone", s.corridorLeftPhone)
            putFloat("corridorRightPhone", s.corridorRightPhone)
            putFloat("corridorLeftEsp", s.corridorLeftEsp)
            putFloat("corridorRightEsp", s.corridorRightEsp)

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

            putFloat("audioEmergencySec", s.audioEmergencySec)
            putFloat("audioDirectionChangeSec", s.audioDirectionChangeSec)
            putFloat("audioStandardSec", s.audioStandardSec)
            putFloat("audioPersistentRepeatSec", s.audioPersistentRepeatSec)

        }.apply()
    }

    fun loadTuningConfig(context: Context) {
        val prefs = context.getSharedPreferences("tuning_prefs", Context.MODE_PRIVATE)
        if (!prefs.contains("stopFloorPhone")) return

        val oldLeft = prefs.getFloat("corridorLeft", 0.32f)
        val oldRight = prefs.getFloat("corridorRight", 0.68f)

        val loaded = com.example.app_blindenstock_add_on.AppConfigState(
            corridorLeftPhone = prefs.getFloat("corridorLeftPhone", oldLeft),
            corridorRightPhone = prefs.getFloat("corridorRightPhone", oldRight),
            corridorLeftEsp = prefs.getFloat("corridorLeftEsp", oldLeft),
            corridorRightEsp = prefs.getFloat("corridorRightEsp", oldRight),

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
            clearFramesRequired = prefs.getInt("clearFramesRequired", 6),

            audioEmergencySec = prefs.getFloat("audioEmergencySec", 1.2f),
            audioDirectionChangeSec = prefs.getFloat("audioDirectionChangeSec", 1.4f),
            audioStandardSec = prefs.getFloat("audioStandardSec", 2.2f),
            audioPersistentRepeatSec = prefs.getFloat("audioPersistentRepeatSec", 8.0f)
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
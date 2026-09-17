package com.example.app_blindenstock_add_on

import kotlinx.coroutines.flow.MutableStateFlow

data class AppConfigState(
    // Geh-Korridor
    val corridorLeft: Float = 0.32f,
    val corridorRight: Float = 0.68f,

    // Warnabstände für interne Smartphone-Kamera
    val stopFloorPhone: Float = 0.85f,
    val personFloorPhone: Float = 0.45f,
    val objectFloorPhone: Float = 0.50f,
    val stairsFloorPhone: Float = 0.50f,

    // Warnabstände für ESP32-Kamera
    val stopFloorEsp: Float = 0.84f,
    val personFloorEsp: Float = 0.62f,
    val objectFloorEsp: Float = 0.64f,
    val stairsFloorEsp: Float = 0.62f,

    // KI-Erkennungs-Schwellenwerte
    val confThresholdObjects: Float = 0.40f,
    val confThresholdSurface: Float = 0.50f,
    val nmsIouThreshold: Float = 0.40f,

    // Frame-Filter (Entprellung)
    val hazardFramesRequired: Int = 3,
    val clearFramesRequired: Int = 6,

    // Dynamik & Logik
    val evadeLockMs: Long = 3500L,
    val trendGrowthPerSec: Float = 0.15f
)

object AppConfig {
    val currentState = MutableStateFlow(AppConfigState())

    fun update(newState: AppConfigState) {
        currentState.value = newState
    }
}
package com.example.app_blindenstock_add_on

import kotlinx.coroutines.flow.MutableStateFlow

data class AppConfigState(
    // Geh-Korridor für interne Smartphone-Kamera
    val corridorLeftPhone: Float = 0.35f,
    val corridorRightPhone: Float = 0.65f,

    // Geh-Korridor für ESP32-Kamera (Vertikal: fast gesamte Breite relevant)
    val corridorLeftEsp: Float = 0.28f,
    val corridorRightEsp: Float = 0.72f,

    // Warnabstände (Kante) für interne Smartphone-Kamera
    val stopFloorPhone: Float = 0.85f,
    val personFloorPhone: Float = 0.45f,
    val objectFloorPhone: Float = 0.50f,
    val stairsFloorPhone: Float = 0.50f,

    // Warnabstände (Fläche/Größe) für interne Smartphone-Kamera
    val stopAreaPhone: Float = 0.40f,
    val warningAreaPhone: Float = 0.20f,

    // Warnabstände (Kante) für ESP32-Kamera (Vertikal: Kante sitzt tiefer)
    val stopFloorEsp: Float = 0.85f,
    val personFloorEsp: Float = 0.50f,
    val objectFloorEsp: Float = 0.55f,
    val stairsFloorEsp: Float = 0.55f,

    // Warnabstände (Fläche/Größe) für ESP32-Kamera (Vertikal: Objekte füllen mehr Fläche)
    val stopAreaEsp: Float = 0.45f,
    val warningAreaEsp: Float = 0.25f,

    // KI-Erkennungs-Schwellenwerte
    val confThresholdObjects: Float = 0.50f,
    val confThresholdSurface: Float = 0.60f,
    val nmsIouThreshold: Float = 0.40f,

    // Frame-Filter (Entprellung)
    val hazardFramesRequired: Int = 4,
    val surfaceFramesRequired: Int = 6,
    val clearFramesRequired: Int = 6,

    // Dynamik & Logik
    val evadeLockMs: Long = 3500L,
    val trendGrowthPerSec: Float = 0.15f,

    // Audio Feedback Timings (in Sekunden)
    val audioEmergencySec: Float = 1.5f,       // Notfall-Stop
    val audioDirectionChangeSec: Float = 2f, // Richtungswechsel
    val audioStandardSec: Float = 2.5f,        // Normale neue Objekte
    val audioPersistentRepeatSec: Float = 9f // Wiederholung des gleichen Objekts

)

object AppConfig {
    val currentState = MutableStateFlow(AppConfigState())

    fun update(newState: AppConfigState) {
        currentState.value = newState
    }
}
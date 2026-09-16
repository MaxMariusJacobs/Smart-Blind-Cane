package com.example.app_blindenstock_add_on

object AppConfig {
    // Gehkorridor-Grenzen (x-Achse)
    const val CORRIDOR_LEFT = 0.32f
    const val CORRIDOR_RIGHT = 0.68f

    // Distanz-Schwellenwerte für interne Smartphone Kamera
    const val STOP_FLOOR_PHONE = 0.84f
    const val PERSON_FLOOR_PHONE = 0.60f
    const val OBJECT_FLOOR_PHONE = 0.62f
    const val STAIRS_FLOOR_PHONE = 0.60f

    // Distanz-Schwellenwerte für ESP32-CAM (Ausgleich des Bildwinkels)
    const val STOP_FLOOR_ESP = 0.84f
    const val PERSON_FLOOR_ESP = 0.62f
    const val OBJECT_FLOOR_ESP = 0.64f
    const val STAIRS_FLOOR_ESP = 0.62f

    // Warn-Logik & Hysterese
    const val EVADE_LOCK_MS = 3500L
    const val CLEAR_FRAMES_REQUIRED = 6
    const val TREND_GROWTH_PER_SEC = 0.15f // Flächenzuwachs pro Sekunde
}
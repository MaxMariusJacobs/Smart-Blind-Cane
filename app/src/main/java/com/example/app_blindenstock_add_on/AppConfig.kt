package com.example.app_blindenstock_add_on

object AppConfig {
    const val CORRIDOR_LEFT = 0.32f  // Höher = Korridor schmaler, Tiefer = breiter
    const val CORRIDOR_RIGHT = 0.68f // Tiefer = Korridor schmaler, Höher = breiter

    // Warnzeitpunkte für interne Smartphone-Kamera
    const val STOP_FLOOR_PHONE = 0.84f   // Tiefer = Stoppt früher (weiter weg), Höher = später (näher dran)
    const val PERSON_FLOOR_PHONE = 0.60f // Tiefer = Warnt früher (weiter weg), Höher = später (näher dran)
    const val OBJECT_FLOOR_PHONE = 0.62f // Tiefer = Warnt früher (weiter weg), Höher = später (näher dran)
    const val STAIRS_FLOOR_PHONE = 0.60f // Tiefer = Warnt früher (weiter weg), Höher = später (näher dran)

    // Warnzeiten für ESP32-Kamera
    const val STOP_FLOOR_ESP = 0.84f     // Tiefer = Stoppt früher (weiter weg), Höher = später (näher dran)
    const val PERSON_FLOOR_ESP = 0.62f   // Tiefer = Warnt früher (weiter weg), Höher = später (näher dran)
    const val OBJECT_FLOOR_ESP = 0.64f   // Tiefer = Warnt früher (weiter weg), Höher = später (näher dran)
    const val STAIRS_FLOOR_ESP = 0.62f   // Tiefer = Warnt früher (weiter weg), Höher = später (näher dran)

    const val EVADE_LOCK_MS = 3500L           // Tiefer = Wechselt Ausweichrichtung schneller, Höher = Richtung bleibt länger stabil
    const val CLEAR_FRAMES_REQUIRED = 8       // Tiefer = Gibt schneller Entwarnung, Höher = robuster gegen falsche Entwarnungen
    const val TREND_GROWTH_PER_SEC = 0.15f    // Tiefer = Löst bei langsamer Annäherung aus, Höher = Löst nur bei schneller Annäherung aus
}
package com.example.app_blindenstock_add_on.data.audio

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.util.Log
import com.example.app_blindenstock_add_on.domain.synthesizer.GuidanceSynthesizer.GuidanceOutput
import java.util.Locale

class SpeechFeedbackManager(context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = TextToSpeech(context, this)
    private var isInitialized = false

    private var lastSpokenPhrase = ""
    private var lastSpokenTime = 0L
    private var lastSpokenPriority = 1

    // Entprellung gegen Frame-Dropouts
    private var consecutiveClearFrames = 0
    private val requiredClearFrames = 8

    private val emergencyCooldownMs = 1200L
    private val persistentWarningRepeatMs = 3500L // Wiederholt aktive Warnung alle 3.5s
    private val clearHeartbeatMs = 8000L

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.let { engine ->
                val result = engine.setLanguage(Locale.US)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    engine.language = Locale.ENGLISH
                }
                engine.setSpeechRate(1.20f)

                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                engine.setAudioAttributes(audioAttributes)

                isInitialized = true
                Log.d("SpeechFeedbackManager", "TTS ready on Media Channel.")
            }
        } else {
            Log.e("SpeechFeedbackManager", "TTS initialization failed: $status")
        }
    }

    fun processGuidance(guidance: GuidanceOutput): Boolean {
        if (!isInitialized) return false

        val now = System.currentTimeMillis()
        val elapsed = now - lastSpokenTime

        if (guidance.priorityLevel == 1) {
            consecutiveClearFrames++
        } else {
            consecutiveClearFrames = 0
        }

        // =========================================================================
        // REAKTION 1: KONTROLLIERTES "CLEAR"
        // =========================================================================
        val isHazardClearing = (guidance.priorityLevel == 1) && (lastSpokenPriority >= 2)

        if (isHazardClearing) {
            val minPlayTimeMs = if (lastSpokenPriority == 3) 700L else 1400L
            val isAudioFinished = (tts?.isSpeaking != true) && (elapsed >= minPlayTimeMs)
            val isStableClear = consecutiveClearFrames >= requiredClearFrames

            if (isAudioFinished && isStableClear) {
                lastSpokenPhrase = "Clear"
                lastSpokenTime = now
                lastSpokenPriority = 1
                consecutiveClearFrames = 0

                tts?.speak("Clear", TextToSpeech.QUEUE_FLUSH, null, "CLEAR_CONFIRMED")
                return true
            }
            return false
        }

        // =========================================================================
        // REAKTION 2: NOTFALL ("STOP!")
        // =========================================================================
        val isEmergencyEscalation = guidance.priorityLevel == 3 && lastSpokenPriority < 3

        if (tts?.isSpeaking == true && !isEmergencyEscalation) {
            return false
        }

        // Richtungswechsel erkennen
        val isDirectionChange = (guidance.phrase.contains("step left") && lastSpokenPhrase.contains("step right")) ||
                (guidance.phrase.contains("step right") && lastSpokenPhrase.contains("step left"))

        val isSameOrSimilar = isSimilarOrSame(guidance.phrase, lastSpokenPhrase)

        // Mindestabstand berechnen
        val requiredInterval = when {
            isEmergencyEscalation -> emergencyCooldownMs
            isDirectionChange -> 1400L // Richtungsanpassung sofort nach Ausreden der alten Phrase
            isSameOrSimilar -> persistentWarningRepeatMs // Gleiche Warnung alle 3.5s wiederholen
            guidance.priorityLevel == 2 -> 2200L
            else -> clearHeartbeatMs
        }

        if (elapsed < requiredInterval && !isEmergencyEscalation) {
            return false
        }

        lastSpokenPhrase = guidance.phrase
        lastSpokenTime = now
        lastSpokenPriority = guidance.priorityLevel

        tts?.speak(guidance.phrase, TextToSpeech.QUEUE_FLUSH, null, "GUIDANCE_UTTERANCE")
        Log.d("SpeechFeedbackManager", "Spoken: ${guidance.phrase}")
        return true
    }

    private fun isSimilarOrSame(newPhrase: String, oldPhrase: String): Boolean {
        if (oldPhrase.isBlank()) return false
        if (newPhrase.equals(oldPhrase, ignoreCase = true)) return true

        val cleanNew = newPhrase.lowercase().replace(Regex("[^a-z0-9 ]"), " ")
        val cleanOld = oldPhrase.lowercase().replace(Regex("[^a-z0-9 ]"), " ")

        // Gegensätzliche Richtungen dürfen NIEMALS als ähnlich gelten
        val newHasLeft = cleanNew.contains("step left")
        val oldHasLeft = cleanOld.contains("step left")
        val newHasRight = cleanNew.contains("step right")
        val oldHasRight = cleanOld.contains("step right")
        if ((newHasLeft && oldHasRight) || (newHasRight && oldHasLeft)) {
            return false
        }

        val wordsNew = cleanNew.split("\\s+".toRegex()).filter { it.isNotBlank() }.toSet()
        val wordsOld = cleanOld.split("\\s+".toRegex()).filter { it.isNotBlank() }.toSet()

        if (wordsNew.isEmpty() || wordsOld.isEmpty()) return false

        val intersection = wordsNew.intersect(wordsOld).size
        val union = wordsNew.union(wordsOld).size

        return (intersection.toFloat() / union.toFloat()) >= 0.75f
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}
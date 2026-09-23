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

    private var consecutiveClearFrames = 0
    private val requiredClearFrames = 8

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

        val isHazardClearing = (guidance.priorityLevel == 1) && (lastSpokenPriority >= 2)

        if (isHazardClearing) {
            val minPlayTimeMs = if (lastSpokenPriority == 3) 750L else 1500L
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

        if (guidance.priorityLevel == 1 && lastSpokenPriority == 1) {
            return false
        }

        val isEmergencyEscalation = guidance.priorityLevel == 3 && lastSpokenPriority < 3

        if (tts?.isSpeaking == true && !isEmergencyEscalation) {
            return false
        }

        // Richtungswechsel generisch erkennen (left vs right)
        val isDirectionChange = (guidance.phrase.contains("left", ignoreCase = true) && lastSpokenPhrase.contains("right", ignoreCase = true)) ||
                (guidance.phrase.contains("right", ignoreCase = true) && lastSpokenPhrase.contains("left", ignoreCase = true))

        val isSameOrSimilar = isSimilarOrSame(guidance.phrase, lastSpokenPhrase)

        // Aktuelle Config live abrufen
        val config = com.example.app_blindenstock_add_on.AppConfig.currentState.value

        val requiredInterval = when {
            isEmergencyEscalation -> (config.audioEmergencySec * 1000).toLong()
            isDirectionChange -> (config.audioDirectionChangeSec * 1000).toLong()
            isSameOrSimilar -> (config.audioPersistentRepeatSec * 1000).toLong()
            else -> (config.audioStandardSec * 1000).toLong()
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
        val newHasLeft = cleanNew.contains("left")
        val oldHasLeft = cleanOld.contains("left")
        val newHasRight = cleanNew.contains("right")
        val oldHasRight = cleanOld.contains("right")
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

    fun speakUrgent(text: String) {
        if (!isInitialized) return
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "URGENT_SYSTEM_MSG")
    }
}
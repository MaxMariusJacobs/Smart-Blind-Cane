package com.example.app_blindenstock_add_on.data.audio

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.app_blindenstock_add_on.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

class SceneDescriptionService {

    // HTTP/1.1 zwingend vorgeben, damit kein Http2Stream Timeout fliegt.
    // CallTimeout setzt das globale Limit für den gesamten Request inklusive Upload & Server-Generierung.
    private val client = OkHttpClient.Builder()
        .protocols(listOf(Protocol.HTTP_1_1))
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val modelName = "gemini-3.6-flash"

    private val systemPrompt = """
        You are an assistive system for a blind person.
        Describe the image in two steps:
        1. First, state the type of environment in one short sentence (e.g., park, sidewalk, indoor space, street, train station).
        2. Then describe the most important objects relevant to pedestrians, their positions (left/right/center/ahead), and whether the direct path is clear.
        Always address the person directly using "you".
        Answer in 2 to 4 short, clear sentences. Ignore unimportant details such as colors, weather, or architecture, unless they are relevant for mobility.
    """.trimIndent()

    suspend fun describeScene(bitmap: Bitmap): String {
        return withContext(Dispatchers.IO) {
            try {
                val base64Image = bitmapToBase64(bitmap)
                val requestBody = buildRequestBody(base64Image)

                // x-goog-api-key Header ist der offizielle Standard für AQ-Keys
                val request = Request.Builder()
                    .url("https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent")
                    .header("x-goog-api-key", BuildConfig.GEMINI_API_KEY)
                    .header("Content-Type", "application/json")
                    .post(requestBody)
                    .build()

                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string().orEmpty()

                    if (!response.isSuccessful) {
                        Log.e("SceneDescription", "API-Fehler HTTP ${response.code}: $responseBody")
                        return@withContext "Not able to get description"
                    }

                    parseResponse(responseBody)
                }
            } catch (e: java.net.SocketTimeoutException) {
                Log.e("SceneDescription", "SocketTimeoutException nach angepasstem Timeout", e)
                "Scene analysis took too long"
            } catch (e: Exception) {
                Log.e("SceneDescription", "Netzwerkfehler", e)
                "No connection to scene description"
            }
        }
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        // Qualität auf 70 reduziert: spart 40% Payload-Größe bei identischer Bilderkennungsqualität
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
        val imageBytes = outputStream.toByteArray()
        return Base64.encodeToString(imageBytes, Base64.NO_WRAP)
    }

    private fun buildRequestBody(base64Image: String): okhttp3.RequestBody {
        val json = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", systemPrompt)
                        })
                        put(JSONObject().apply {
                            put("inline_data", JSONObject().apply {
                                put("mime_type", "image/jpeg")
                                put("data", base64Image)
                            })
                        })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("thinkingConfig", JSONObject().apply {
                    put("thinkingBudget", 0)
                })
            })
        }
        return json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
    }

    private fun parseResponse(responseBody: String): String {
        return try {
            val json = JSONObject(responseBody)
            val candidates = json.getJSONArray("candidates")
            val content = candidates.getJSONObject(0).getJSONObject("content")
            val parts = content.getJSONArray("parts")
            parts.getJSONObject(0).getString("text").trim().replace("*", "")
        } catch (e: Exception) {
            Log.e("SceneDescription", "Fehler beim Parsen der API-Antwort: $responseBody", e)
            "Beschreibung konnte nicht gelesen werden."
        }
    }
}
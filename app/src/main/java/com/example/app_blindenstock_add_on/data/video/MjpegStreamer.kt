package com.example.app_blindenstock_add_on.data.video

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class MjpegStreamer(
    private val client: OkHttpClient,
    private val url: String,
    private val onStatusMessage: ((String) -> Unit)? = null
) : VideoSource {

    @Volatile
    private var activeCall: Call? = null

    @Volatile
    private var isManuallyStopped = false

    private val streamClient = client.newBuilder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val decodeOptions = BitmapFactory.Options().apply {
        // inSampleSize = 2 ENTFERNT (siehe Punkt 2)
        inPreferredConfig = Bitmap.Config.RGB_565
    }

    override fun getFrames(): Flow<Bitmap> = flow {
        isManuallyStopped = false
        var retryAttempt = 0
        var wasConnectedBefore = false

        while (currentCoroutineContext().isActive && !isManuallyStopped) {
            val request = Request.Builder()
                .url(url)
                .header("Cache-Control", "no-cache")
                .header("Connection", "keep-alive")
                .build()

            val call = streamClient.newCall(request)
            activeCall = call

            try {
                val response = call.execute()
                if (!response.isSuccessful) {
                    response.close()
                    throw IllegalStateException("HTTP error code: ${response.code}")
                }

                val body = response.body ?: run {
                    response.close()
                    throw IllegalStateException("Empty response body")
                }

                if (retryAttempt > 0) {
                    onStatusMessage?.invoke("Connection restored.")
                }
                retryAttempt = 0
                wasConnectedBefore = true

                // --- NEU: Okio nutzen für extrem schnelles, GC-schonendes Lesen ---
                val source = body.source()
                var contentLength = -1

                try {
                    while (currentCoroutineContext().isActive && !isManuallyStopped) {
                        val line = source.readUtf8Line() ?: break

                        if (line.startsWith("Content-Length:", ignoreCase = true)) {
                            contentLength = line.substringAfter(":").trim().toIntOrNull() ?: -1

                            // Die leere Zeile (\r\n) nach dem Header überspringen
                            source.readUtf8Line()

                            if (contentLength > 0) {
                                // Blockiert, bis das gesamte Bild im RAM ist - ohne Byte-Schleife
                                val imageBytes = source.readByteArray(contentLength.toLong())
                                val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, decodeOptions)
                                if (bitmap != null) {
                                    emit(bitmap)
                                }
                            }
                        }
                    }
                } finally {
                    response.close()
                }
            } catch (e: Exception) {
                if (isManuallyStopped || !currentCoroutineContext().isActive) break

                retryAttempt++
                Log.w("MjpegStreamer", "Stream disconnected: ${e.message}. Reconnect attempt #$retryAttempt")

                if (retryAttempt == 1 && wasConnectedBefore) {
                    onStatusMessage?.invoke("Connection lost, reconnecting.")
                }

                val backoffMs = minOf(1000L * (1L shl minOf(retryAttempt - 1, 2)), 5000L)
                delay(backoffMs)
            } finally {
                activeCall = null
            }
        }
    }.flowOn(Dispatchers.IO)

    override fun stop() {
        isManuallyStopped = true
        try {
            activeCall?.cancel()
        } catch (e: Exception) {
            Log.w("MjpegStreamer", "Error stopping call: ${e.message}")
        }
        activeCall = null
    }
}
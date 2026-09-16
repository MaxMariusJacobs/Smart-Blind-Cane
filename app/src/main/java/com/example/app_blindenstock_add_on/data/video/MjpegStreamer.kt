package com.example.app_blindenstock_add_on.data.video

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit

class MjpegStreamer(
    private val client: OkHttpClient,
    private val url: String
) : VideoSource {

    @Volatile
    private var activeCall: Call? = null

    private val streamClient = client.newBuilder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val decodeOptions = BitmapFactory.Options().apply {
        inSampleSize = 2
        inPreferredConfig = Bitmap.Config.RGB_565
    }

    override fun getFrames(): Flow<Bitmap> = flow {
        val request = Request.Builder()
            .url(url)
            .header("Cache-Control", "no-cache")
            .header("Connection", "keep-alive")
            .build()

        val call = streamClient.newCall(request)
        activeCall = call

        val response = try {
            call.execute()
        } catch (e: Exception) {
            Log.e("MjpegStreamer", "Verbindung zu $url fehlgeschlagen: ${e.message}")
            return@flow
        }

        if (!response.isSuccessful) {
            Log.e("MjpegStreamer", "HTTP-Fehler: ${response.code}")
            response.close()
            return@flow
        }

        val body = response.body
        if (body == null) {
            response.close()
            return@flow
        }

        val bufferedInput = BufferedInputStream(body.byteStream(), 65536)

        try {
            while (true) {
                // Deadlock-Schutz: Bricht sofort ab, wenn UI den Job stoppt
                if (!currentCoroutineContext().isActive) break

                val frameBytes = readJpegFrame(bufferedInput) ?: break
                val bitmap = BitmapFactory.decodeByteArray(frameBytes, 0, frameBytes.size, decodeOptions)
                if (bitmap != null) {
                    emit(bitmap)
                }
            }
        } catch (e: Exception) {
            Log.w("MjpegStreamer", "Stream getrennt: ${e.message}")
        } finally {
            response.close()
            activeCall = null
        }
    }.flowOn(Dispatchers.IO)

    override fun stop() {
        try {
            activeCall?.cancel()
        } catch (e: Exception) {
            Log.w("MjpegStreamer", "Fehler beim Stoppen: ${e.message}")
        }
        activeCall = null
    }

    private fun readJpegFrame(stream: InputStream): ByteArray? {
        val buffer = ByteArrayOutputStream(32768)
        var prevByte = -1
        var inFrame = false

        while (true) {
            val currByte = stream.read()
            if (currByte == -1) return null

            if (!inFrame) {
                if (prevByte == 0xFF && currByte == 0xD8) {
                    inFrame = true
                    buffer.write(prevByte)
                    buffer.write(currByte)
                }
            } else {
                buffer.write(currByte)
                if (prevByte == 0xFF && currByte == 0xD9) {
                    return buffer.toByteArray()
                }
            }
            prevByte = currByte
        }
    }
}
package com.example.app_blindenstock_add_on.data.video

import android.graphics.Bitmap
import kotlinx.coroutines.flow.Flow

interface VideoSource {
    fun getFrames(): Flow<Bitmap>
    fun stop()
}

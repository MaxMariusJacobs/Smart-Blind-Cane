package com.example.app_blindenstock_add_on.data.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

class UserMotionTracker(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val stepDetector = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
    private val linearAcc = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)

    @Volatile
    var isUserWalking: Boolean = false
        private set

    private var lastMovementTime = 0L
    private val movementTimeoutMs = 1500L

    fun start() {
        try {
            stepDetector?.let {
                // Polling-Rate für Akku-Schonung gedrosselt
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            }
        } catch (e: SecurityException) {
            android.util.Log.w("UserMotionTracker", "Step Detector permission missing.")
        }

        linearAcc?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        val now = System.currentTimeMillis()
        if (event == null) return

        when (event.sensor.type) {
            Sensor.TYPE_STEP_DETECTOR -> {
                isUserWalking = true
                lastMovementTime = now
            }
            Sensor.TYPE_LINEAR_ACCELERATION -> {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                val magnitude = sqrt((x * x + y * y + z * z).toDouble()).toFloat()

                if (magnitude > 1.2f) {
                    isUserWalking = true
                    lastMovementTime = now
                }
            }
        }

        if (now - lastMovementTime > movementTimeoutMs) {
            isUserWalking = false
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
package com.example.app_blindenstock_add_on.data.yolov11n_gpu

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.util.Log
import com.example.app_blindenstock_add_on.domain.model.*
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.sqrt

class YoloDetector(private val context: Context) {

    private var objectInterpreter: Interpreter? = null
    private var surfaceInterpreter: Interpreter? = null
    private var objectGpuDelegate: GpuDelegate? = null
    private var surfaceGpuDelegate: GpuDelegate? = null

    // Zero-Allocation Resampling: Wiederverwendbare Objekte im RAM
    private val scaledBitmap = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888)
    private val scaleCanvas = Canvas(scaledBitmap)
    private val scaleMatrix = Matrix()
    private val scalePaint = Paint(Paint.FILTER_BITMAP_FLAG)

    private val inputBuffer: ByteBuffer = ByteBuffer.allocateDirect(1 * 3 * 320 * 320 * 4).apply {
        order(ByteOrder.nativeOrder())
    }
    private val pixelArray = IntArray(320 * 320)

    private val outputObjects = Array(1) { Array(84) { FloatArray(2100) } }
    private val outputSurface0 = Array(1) { Array(40) { FloatArray(2100) } }
    private val outputSurface1 = Array(1) { Array(32) { Array(80) { FloatArray(80) } } }
    private var surfaceHasDualOutputs = false

    val surfaceLabels = listOf("sidewalk", "roadway", "path", "stairs")

    val objectLabels = listOf(
        "person", "bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck", "boat", "traffic light",
        "fire hydrant", "stop sign", "parking meter", "bench", "bird", "cat", "dog", "horse", "sheep", "cow",
        "elephant", "bear", "zebra", "giraffe", "backpack", "umbrella", "handbag", "tie", "suitcase", "frisbee",
        "skis", "snowboard", "sports ball", "kite", "baseball bat", "baseball glove", "skateboard", "surfboard",
        "tennis racket", "bottle", "wine glass", "cup", "fork", "knife", "spoon", "bowl", "banana", "apple",
        "sandwich", "orange", "broccoli", "carrot", "hot dog", "pizza", "donut", "cake", "chair", "couch",
        "potted plant", "bed", "dining table", "toilet", "tv", "laptop", "mouse", "remote", "keyboard", "cell phone",
        "microwave", "oven", "toaster", "sink", "refrigerator", "book", "clock", "vase", "scissors", "teddy bear",
        "hair drier", "toothbrush"
    )

    private val relevantObstacles = setOf(
        "person", "bicycle", "car", "motorcycle", "bus", "truck", "dog",
        "bench", "chair", "fire hydrant", "stop sign", "traffic light", "potted plant",
        "parking meter", "suitcase", "backpack"
    )

    private val classCounters = mutableMapOf<String, Int>()
    private var trackedObjects = mutableListOf<TrackedState>()
    private val maxDistanceThreshold = 0.20f
    private val objectGracePeriodMs = 400L

    private var frameSequence: Long = 0
    private var cachedSurfaces: List<Detection> = emptyList()

    var lastInferenceTime: Long = 0
        private set

    data class TrackedState(
        val trackId: Int,
        var detection: Detection,
        var lastSeen: Long,
        var prevX: Float,
        var prevY: Float,
        var prevFloorY: Float,
        var prevArea: Float
    )

    init {
        loadModels()
    }

    private fun loadModels() {
        val compatList = CompatibilityList()

        try {
            val optA = Interpreter.Options().apply {
                setNumThreads(4)
                if (compatList.isDelegateSupportedOnThisDevice) {
                    val delegateOptions = compatList.bestOptionsForThisDevice.apply {
                        setInferencePreference(GpuDelegate.Options.INFERENCE_PREFERENCE_SUSTAINED_SPEED)
                    }
                    objectGpuDelegate = GpuDelegate(delegateOptions)
                    addDelegate(objectGpuDelegate)
                }
            }
            val bufA = loadModelFile(context, "yolo11n_objects.tflite")
            objectInterpreter = Interpreter(bufA, optA)
        } catch (e: Exception) {
            Log.e("YoloDetector", "Error loading yolo11n_objects.tflite", e)
        }

        try {
            val optB = Interpreter.Options().apply {
                setNumThreads(4)
                if (compatList.isDelegateSupportedOnThisDevice) {
                    val delegateOptions = compatList.bestOptionsForThisDevice.apply {
                        setInferencePreference(GpuDelegate.Options.INFERENCE_PREFERENCE_SUSTAINED_SPEED)
                    }
                    surfaceGpuDelegate = GpuDelegate(delegateOptions)
                    addDelegate(surfaceGpuDelegate)
                }
            }
            val bufB = loadModelFile(context, "yolo11n_surface.tflite")
            surfaceInterpreter = Interpreter(bufB, optB)
            surfaceHasDualOutputs = (surfaceInterpreter?.outputTensorCount ?: 0) > 1
        } catch (e: Exception) {
            Log.e("YoloDetector", "Error loading yolo11n_surface.tflite", e)
        }
    }

    private fun loadModelFile(context: Context, modelPath: String): ByteBuffer {
        val fd = context.assets.openFd(modelPath)
        val channel = FileInputStream(fd.fileDescriptor).channel
        return channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
    }

    fun detect(bitmap: Bitmap, isUserWalking: Boolean = true): FrameAnalysisResult {
        val startTime = System.currentTimeMillis()
        frameSequence++

        // Bild skalieren ohne neue Bitmap im Heap zu erzeugen
        scaleMatrix.reset()
        scaleMatrix.setScale(320f / bitmap.width.toFloat(), 320f / bitmap.height.toFloat())
        scaleCanvas.drawBitmap(bitmap, scaleMatrix, scalePaint)
        preprocessNCHW(scaledBitmap)

        val rawObjects = mutableListOf<Detection>()
        objectInterpreter?.let { interp ->
            inputBuffer.rewind()
            interp.run(inputBuffer, outputObjects)
            rawObjects.addAll(processObjectTensors(outputObjects[0]))
        }

        val rawSurfaces = if (frameSequence % 2L == 0L || cachedSurfaces.isEmpty()) {
            val freshSurfaces = mutableListOf<Detection>()
            surfaceInterpreter?.let { interp ->
                inputBuffer.rewind()
                if (surfaceHasDualOutputs) {
                    val outputs = mutableMapOf<Int, Any>(0 to outputSurface0, 1 to outputSurface1)
                    interp.runForMultipleInputsOutputs(arrayOf(inputBuffer), outputs)
                } else {
                    interp.run(inputBuffer, outputSurface0)
                }
                freshSurfaces.addAll(processSurfaceTensors(outputSurface0[0]))
            }
            cachedSurfaces = freshSurfaces
            freshSurfaces
        } else {
            cachedSurfaces
        }

        val filteredObjects = applyNMS(rawObjects, 0.45f)
        val filteredSurfaces = applyNMS(rawSurfaces, 0.45f)

        val combinedCandidates = filteredSurfaces + filteredObjects
        val stabilizedDetections = updateTracking(combinedCandidates, isUserWalking)

        val topSurface = stabilizedDetections
            .filter { it.className in listOf("sidewalk", "path", "roadway") }
            .maxByOrNull { it.score }

        lastInferenceTime = System.currentTimeMillis() - startTime

        return FrameAnalysisResult(
            detections = stabilizedDetections,
            corridor = CorridorAnalysis(),
            primarySurface = topSurface?.className ?: "unknown",
            surfaceConfidence = topSurface?.score ?: 0f
        )
    }

    private fun preprocessNCHW(bitmap: Bitmap) {
        inputBuffer.rewind()
        bitmap.getPixels(pixelArray, 0, 320, 0, 0, 320, 320)
        val totalPixels = 320 * 320

        for (i in 0 until totalPixels) inputBuffer.putFloat(((pixelArray[i] shr 16) and 0xFF) * 0.003921569f)
        for (i in 0 until totalPixels) inputBuffer.putFloat(((pixelArray[i] shr 8) and 0xFF) * 0.003921569f)
        for (i in 0 until totalPixels) inputBuffer.putFloat((pixelArray[i] and 0xFF) * 0.003921569f)
    }

    private fun processObjectTensors(data: Array<FloatArray>): List<Detection> {
        val candidates = mutableListOf<Detection>()
        val confThreshold = 0.28f

        for (c in 0 until 2100) {
            var maxScore = 0f
            var classId = -1

            for (r in 4 until 84) {
                val score = data[r][c]
                if (score > maxScore) {
                    maxScore = score
                    classId = r - 4
                }
            }

            if (maxScore > confThreshold && classId in objectLabels.indices) {
                val label = objectLabels[classId]
                if (label !in relevantObstacles) continue

                val isNormalized = data[0][c] <= 1.0f && data[2][c] <= 1.0f
                val scale = if (isNormalized) 1.0f else 320.0f

                val cx = data[0][c] / scale
                val cy = data[1][c] / scale
                val w = data[2][c] / scale
                val h = data[3][c] / scale

                candidates.add(
                    Detection(
                        boundingBox = BoundingBox(
                            x = (cx - w / 2f).coerceIn(0f, 1f),
                            y = (cy - h / 2f).coerceIn(0f, 1f),
                            width = w.coerceIn(0f, 1f),
                            height = h.coerceIn(0f, 1f)
                        ),
                        classId = classId + 100,
                        className = label,
                        score = maxScore
                    )
                )
            }
        }
        return candidates
    }

    private fun processSurfaceTensors(data: Array<FloatArray>): List<Detection> {
        val candidates = mutableListOf<Detection>()
        val confThreshold = 0.38f

        for (c in 0 until 2100) {
            var maxScore = 0f
            var classId = -1

            for (r in 4 until 8) {
                val score = data[r][c]
                if (score > maxScore) {
                    maxScore = score
                    classId = r - 4
                }
            }

            if (maxScore > confThreshold && classId in surfaceLabels.indices) {
                val isNormalized = data[0][c] <= 1.0f && data[2][c] <= 1.0f
                val scale = if (isNormalized) 1.0f else 320.0f

                val cx = data[0][c] / scale
                val cy = data[1][c] / scale
                val w = data[2][c] / scale
                val h = data[3][c] / scale

                candidates.add(
                    Detection(
                        boundingBox = BoundingBox(
                            x = (cx - w / 2f).coerceIn(0f, 1f),
                            y = (cy - h / 2f).coerceIn(0f, 1f),
                            width = w.coerceIn(0f, 1f),
                            height = h.coerceIn(0f, 1f)
                        ),
                        classId = classId,
                        className = surfaceLabels[classId],
                        score = maxScore
                    )
                )
            }
        }
        return candidates
    }

    private fun updateTracking(currentDetections: List<Detection>, isUserWalking: Boolean): List<Detection> {
        val now = System.currentTimeMillis()
        val matchedCurrent = BooleanArray(currentDetections.size)
        val result = mutableListOf<Detection>()

        for (tracked in trackedObjects) {
            val tCenter = getCenter(tracked.detection.boundingBox)
            var bestIdx = -1
            var minDistance = Float.MAX_VALUE

            for (i in currentDetections.indices) {
                if (matchedCurrent[i]) continue
                if (currentDetections[i].className != tracked.detection.className) continue

                val cCenter = getCenter(currentDetections[i].boundingBox)
                val dist = distance(tCenter, cCenter)
                if (dist < minDistance && dist < maxDistanceThreshold) {
                    minDistance = dist
                    bestIdx = i
                }
            }

            if (bestIdx != -1) {
                matchedCurrent[bestIdx] = true
                val rawDet = currentDetections[bestIdx]
                val b = rawDet.boundingBox
                val cx = b.x + b.width / 2f
                val cy = b.y + b.height / 2f
                val floorY = (b.y + b.height).coerceIn(0f, 1f)
                val area = b.width * b.height

                val dt = (now - tracked.lastSeen) / 1000.0f
                val dFloorY = if (dt > 0) (floorY - tracked.prevFloorY) / dt else 0f
                val dArea = area - tracked.prevArea

                val trend = when {
                    dArea > 0.015f -> "growing"
                    dArea < -0.015f -> "shrinking"
                    else -> "constant"
                }

                val motion = when {
                    isUserWalking && dFloorY > 0.04f -> "user_approaching"
                    dFloorY > 0.08f -> "approaching_fast"
                    else -> "stationary"
                }

                val ttcSec = if (dFloorY > 0.02f && floorY < 0.98f) {
                    ((1.0f - floorY) / dFloorY).coerceIn(0.1f, 10.0f)
                } else -1.0f

                val clockPos = when {
                    cx < 0.35f -> "10_o_clock"
                    cx < 0.45f -> "11_o_clock"
                    cx <= 0.55f -> "12_o_clock"
                    cx <= 0.65f -> "1_o_clock"
                    else -> "2_o_clock"
                }

                val priority = when {
                    floorY > 0.80f || (ttcSec in 0.2f..1.3f && trend == "growing") -> "CRITICAL"
                    trend == "growing" || floorY > 0.60f -> "HIGH"
                    floorY > 0.40f -> "MEDIUM"
                    else -> "LOW"
                }

                val updatedDet = rawDet.copy(
                    classId = tracked.trackId,
                    motion = motion,
                    trend = trend,
                    priority = priority,
                    clockPos = clockPos,
                    ttcSec = ttcSec
                )

                tracked.detection = updatedDet
                tracked.lastSeen = now
                tracked.prevX = cx
                tracked.prevY = cy
                tracked.prevFloorY = floorY
                tracked.prevArea = area

                result.add(updatedDet)
            }
        }

        for (i in currentDetections.indices) {
            if (!matchedCurrent[i]) {
                val rawDet = currentDetections[i]
                val nextId = (classCounters[rawDet.className] ?: 0) + 1
                classCounters[rawDet.className] = nextId

                val b = rawDet.boundingBox
                val cx = b.x + b.width / 2f
                val floorY = b.y + b.height

                val clockPos = when {
                    cx < 0.35f -> "10_o_clock"
                    cx < 0.45f -> "11_o_clock"
                    cx <= 0.55f -> "12_o_clock"
                    cx <= 0.65f -> "1_o_clock"
                    else -> "2_o_clock"
                }

                val priority = when {
                    floorY > 0.80f -> "CRITICAL"
                    floorY > 0.60f -> "HIGH"
                    else -> "LOW"
                }

                val newDet = rawDet.copy(
                    classId = nextId,
                    clockPos = clockPos,
                    priority = priority
                )

                trackedObjects.add(
                    TrackedState(
                        trackId = nextId,
                        detection = newDet,
                        lastSeen = now,
                        prevX = cx,
                        prevY = b.y + b.height / 2f,
                        prevFloorY = floorY,
                        prevArea = b.width * b.height
                    )
                )
                result.add(newDet)
            }
        }

        trackedObjects.removeAll { now - it.lastSeen > objectGracePeriodMs }
        return result
    }

    private fun getCenter(b: BoundingBox) = Pair(b.x + b.width / 2f, b.y + b.height / 2f)

    private fun distance(p1: Pair<Float, Float>, p2: Pair<Float, Float>): Float {
        val dx = p1.first - p2.first
        val dy = p1.second - p2.second
        return sqrt((dx * dx + dy * dy).toDouble()).toFloat()
    }

    private fun applyNMS(detections: List<Detection>, threshold: Float): List<Detection> {
        val sorted = detections.sortedByDescending { it.score }
        val selected = mutableListOf<Detection>()
        val active = BooleanArray(sorted.size) { true }

        for (i in sorted.indices) {
            if (active[i]) {
                selected.add(sorted[i])
                for (j in i + 1 until sorted.size) {
                    if (active[j]) {
                        if (calculateIoU(sorted[i].boundingBox, sorted[j].boundingBox) > threshold) {
                            active[j] = false
                        }
                    }
                }
            }
        }
        return selected
    }

    private fun calculateIoU(box1: BoundingBox, box2: BoundingBox): Float {
        val x1 = maxOf(box1.x, box2.x)
        val y1 = maxOf(box1.y, box2.y)
        val x2 = minOf(box1.x + box1.width, box2.x + box2.width)
        val y2 = minOf(box1.y + box1.height, box2.y + box2.height)

        val intersection = maxOf(0f, x2 - x1) * maxOf(0f, y2 - y1)
        val union = (box1.width * box1.height) + (box2.width * box2.height) - intersection
        return if (union > 0f) intersection / union else 0f
    }

    fun close() {
        objectInterpreter?.close()
        surfaceInterpreter?.close()
        objectGpuDelegate?.close()
        surfaceGpuDelegate?.close()
        scaledBitmap.recycle()
    }
}
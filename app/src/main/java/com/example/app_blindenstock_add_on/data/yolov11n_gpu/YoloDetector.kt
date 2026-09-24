package com.example.app_blindenstock_add_on.data.yolov11n_gpu

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.util.Log
import com.example.app_blindenstock_add_on.AppConfigState
import com.example.app_blindenstock_add_on.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

class YoloDetector(private val context: Context) {

    private var objectInterpreter: Interpreter? = null
    private var surfaceInterpreter: Interpreter? = null
    private var objectGpuDelegate: GpuDelegate? = null
    private var surfaceGpuDelegate: GpuDelegate? = null

    private val mutex = Mutex()

    private val scaledBitmap = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888)
    private val scaleCanvas = Canvas(scaledBitmap)
    private val scaleMatrix = Matrix()
    private val scalePaint = Paint(Paint.FILTER_BITMAP_FLAG)

    private val inputBufferObjects: ByteBuffer = ByteBuffer.allocateDirect(1 * 3 * 320 * 320 * 4).apply {
        order(ByteOrder.nativeOrder())
    }
    private val inputBufferSurface: ByteBuffer = ByteBuffer.allocateDirect(1 * 3 * 320 * 320 * 4).apply {
        order(ByteOrder.nativeOrder())
    }

    private val pixelArray = IntArray(320 * 320)
    private val floatArray = FloatArray(3 * 320 * 320)

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

    // Erweiterter Filter für urbane Sicherheit
    private val relevantObstacles = setOf(
        "person", "bicycle", "car", "motorcycle", "bus", "truck", "train",
        "dog", "cat", "bench", "chair", "fire hydrant", "stop sign",
        "traffic light", "potted plant", "parking meter", "suitcase",
        "backpack", "umbrella", "skateboard", "sports ball"
    )

    var lastInferenceTime: Long = 0
        private set

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
            Log.e("YoloDetector", "Fehler: yolo11n_objects.tflite", e)
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
            Log.e("YoloDetector", "Fehler: yolo11n_surface.tflite", e)
        }
    }

    private fun loadModelFile(context: Context, modelPath: String): ByteBuffer {
        val fd = context.assets.openFd(modelPath)
        val channel = FileInputStream(fd.fileDescriptor).channel
        return channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
    }

    suspend fun detect(
        bitmap: Bitmap,
        isUserWalking: Boolean = true,
        config: AppConfigState,
        runSurfaceModel: Boolean = true,
        isEsp32: Boolean = false // <-- NEUER PARAMETER
    ): FrameAnalysisResult = mutex.withLock {
        coroutineScope {
            if (objectInterpreter == null && surfaceInterpreter == null) {
                return@coroutineScope FrameAnalysisResult(emptyList(), "unknown", 0f) // <-- Angepasst, CorridorAnalysis entfernt
            }

            val startTime = System.currentTimeMillis()

            val scale = minOf(320f / bitmap.width.toFloat(), 320f / bitmap.height.toFloat())
            val dx = (320f - bitmap.width * scale) / 2f
            val dy = (320f - bitmap.height * scale) / 2f

            scaleMatrix.reset()
            scaleMatrix.postScale(scale, scale)
            scaleMatrix.postTranslate(dx, dy)
            scaleCanvas.drawColor(android.graphics.Color.BLACK)
            scaleCanvas.drawBitmap(bitmap, scaleMatrix, scalePaint)

            preprocessNCHW(scaledBitmap, inputBufferObjects)
            if (runSurfaceModel) {
                inputBufferSurface.rewind()
                inputBufferSurface.asFloatBuffer().put(floatArray)
            }

            val padX = dx / 320f
            val padY = dy / 320f
            val activeW = (bitmap.width * scale) / 320f
            val activeH = (bitmap.height * scale) / 320f

            val deferredObjects = async(Dispatchers.Default) {
                val rawObjects = mutableListOf<Detection>()
                objectInterpreter?.let { interp ->
                    inputBufferObjects.rewind()
                    interp.run(inputBufferObjects, outputObjects)
                    rawObjects.addAll(processObjectTensors(outputObjects[0], padX, padY, activeW, activeH, config.confThresholdObjects))
                }
                applyNMS(rawObjects, config.nmsIouThreshold)
            }

            val deferredSurfaces = async(Dispatchers.Default) {
                if (!runSurfaceModel) {
                    return@async emptyList<Detection>()
                }

                val rawSurfaces = mutableListOf<Detection>()
                surfaceInterpreter?.let { interp ->
                    inputBufferSurface.rewind()
                    if (surfaceHasDualOutputs) {
                        val outputs = mutableMapOf<Int, Any>(0 to outputSurface0, 1 to outputSurface1)
                        interp.runForMultipleInputsOutputs(arrayOf(inputBufferSurface), outputs)
                    } else {
                        interp.run(inputBufferSurface, outputSurface0)
                    }
                    rawSurfaces.addAll(processSurfaceTensors(outputSurface0[0], padX, padY, activeW, activeH, config.confThresholdSurface))
                }
                applyNMS(rawSurfaces, config.nmsIouThreshold)
            }

            val filteredObjects = deferredObjects.await()
            val filteredSurfaces = deferredSurfaces.await()

            val combinedCandidates = filteredSurfaces + filteredObjects
            val prioritizedDetections = assignPriorities(combinedCandidates, config, isEsp32) // <-- KORRIGIERTER AUFRUF

            val topSurface = prioritizedDetections
                .filter { it.className in listOf("sidewalk", "path", "roadway") }
                .maxByOrNull { it.score }

            lastInferenceTime = System.currentTimeMillis() - startTime

            FrameAnalysisResult(
                detections = prioritizedDetections,
                primarySurface = topSurface?.className ?: "unknown",
                surfaceConfidence = topSurface?.score ?: 0f
            )
        }
    }

    private fun preprocessNCHW(bitmap: Bitmap, targetBuffer: ByteBuffer) {
        bitmap.getPixels(pixelArray, 0, 320, 0, 0, 320, 320)
        val totalPixels = 320 * 320

        for (i in 0 until totalPixels) {
            val pixel = pixelArray[i]
            floatArray[i] = ((pixel shr 16) and 0xFF) * 0.003921569f
            floatArray[totalPixels + i] = ((pixel shr 8) and 0xFF) * 0.003921569f
            floatArray[2 * totalPixels + i] = (pixel and 0xFF) * 0.003921569f
        }

        targetBuffer.rewind()
        targetBuffer.asFloatBuffer().put(floatArray)
    }

    private fun processObjectTensors(data: Array<FloatArray>, padX: Float, padY: Float, activeW: Float, activeH: Float, confThreshold: Float): List<Detection> {
        val candidates = mutableListOf<Detection>()
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

                val cxRaw = data[0][c] / scale
                val cyRaw = data[1][c] / scale
                val wRaw = data[2][c] / scale
                val hRaw = data[3][c] / scale

                val cx = ((cxRaw - padX) / activeW).coerceIn(0f, 1f)
                val cy = ((cyRaw - padY) / activeH).coerceIn(0f, 1f)
                val w = (wRaw / activeW).coerceIn(0f, 1f)
                val h = (hRaw / activeH).coerceIn(0f, 1f)

                candidates.add(
                    Detection(
                        boundingBox = BoundingBox(
                            x = (cx - w / 2f).coerceIn(0f, 1f),
                            y = (cy - h / 2f).coerceIn(0f, 1f),
                            width = w,
                            height = h
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

    private fun processSurfaceTensors(data: Array<FloatArray>, padX: Float, padY: Float, activeW: Float, activeH: Float, confThreshold: Float): List<Detection> {
        val candidates = mutableListOf<Detection>()
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

                val cxRaw = data[0][c] / scale
                val cyRaw = data[1][c] / scale
                val wRaw = data[2][c] / scale
                val hRaw = data[3][c] / scale

                val cx = ((cxRaw - padX) / activeW).coerceIn(0f, 1f)
                val cy = ((cyRaw - padY) / activeH).coerceIn(0f, 1f)
                val w = (wRaw / activeW).coerceIn(0f, 1f)
                val h = (hRaw / activeH).coerceIn(0f, 1f)

                candidates.add(
                    Detection(
                        boundingBox = BoundingBox(
                            x = (cx - w / 2f).coerceIn(0f, 1f),
                            y = (cy - h / 2f).coerceIn(0f, 1f),
                            width = w,
                            height = h
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

    // --- KORRIGIERTE FUNKTION MIT CONFIG-ANBINDUNG ---
    private fun assignPriorities(currentDetections: List<Detection>, config: AppConfigState, isEsp32: Boolean): List<Detection> {
        val result = mutableListOf<Detection>()

        val stopFloorThreshold = if (isEsp32) config.stopFloorEsp else config.stopFloorPhone
        val stopAreaThreshold = if (isEsp32) config.stopAreaEsp else config.stopAreaPhone
        val warningAreaThreshold = if (isEsp32) config.warningAreaEsp else config.warningAreaPhone

        // Medium Warning nehmen wir als Basiswert leicht über dem Person/Object Threshold
        val mediumFloorThreshold = if (isEsp32) minOf(config.personFloorEsp, config.objectFloorEsp) else minOf(config.personFloorPhone, config.objectFloorPhone)

        for (rawDet in currentDetections) {
            val b = rawDet.boundingBox
            val floorY = b.y + b.height
            val area = b.width * b.height

            val priority = when {
                floorY > stopFloorThreshold || area >= stopAreaThreshold -> "CRITICAL"
                floorY > (stopFloorThreshold + mediumFloorThreshold) / 2f || area >= warningAreaThreshold -> "HIGH"
                floorY > mediumFloorThreshold -> "MEDIUM"
                else -> "LOW"
            }

            result.add(rawDet.copy(priority = priority))
        }

        return result
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

    suspend fun close() = mutex.withLock {
        try {
            objectInterpreter?.close()
            surfaceInterpreter?.close()
            objectGpuDelegate?.close()
            surfaceGpuDelegate?.close()
        } catch (e: Exception) {
            Log.w("YoloDetector", "Error closing interpreters: ${e.message}")
        } finally {
            objectInterpreter = null
            surfaceInterpreter = null
            objectGpuDelegate = null
            surfaceGpuDelegate = null
        }
    }
}
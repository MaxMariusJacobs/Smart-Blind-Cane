package com.example.app_blindenstock_add_on.domain.model

data class Detection(
    val boundingBox: BoundingBox,
    val classId: Int,
    val className: String,
    val score: Float,
    val priority: String = "LOW",
) {
    val entityId: String get() = "${className}_$classId"
}

data class BoundingBox(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float
)

data class FrameAnalysisResult(
    val detections: List<Detection>,
    val primarySurface: String,
    val surfaceConfidence: Float
)
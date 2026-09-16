package com.example.app_blindenstock_add_on.domain.model

data class Detection(
    val boundingBox: BoundingBox,
    val classId: Int,
    val className: String,
    val score: Float,
    val motion: String = "stationary",
    val trend: String = "constant",
    val priority: String = "LOW",
    val clockPos: String = "12_o_clock",
    val ttcSec: Float = -1.0f
) {
    val entityId: String get() = "${className}_$classId"
}

data class BoundingBox(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float
)

data class SectorInfo(
    val status: String = "clear",
    val blocker: String? = null,
    val clock: String? = null,
    val ttcSec: Float? = null,
    val priority: String? = null
)

data class CorridorAnalysis(
    val left: SectorInfo = SectorInfo(),
    val center: SectorInfo = SectorInfo(),
    val right: SectorInfo = SectorInfo(),
    val criticalBlocker: Detection? = null,
    val guidance: String = "proceed_straight"
)

data class FrameAnalysisResult(
    val detections: List<Detection>,
    val corridor: CorridorAnalysis,
    val primarySurface: String,
    val surfaceConfidence: Float
)
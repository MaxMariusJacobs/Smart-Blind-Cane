package com.example.app_blindenstock_add_on.domain.synthesizer

import com.example.app_blindenstock_add_on.AppConfig
import com.example.app_blindenstock_add_on.domain.model.FrameAnalysisResult

object GuidanceSynthesizer {

    data class GuidanceOutput(
        val phrase: String,
        val isEmergency: Boolean,
        val priorityLevel: Int
    )

    private var lockedEvadeSide: String = "step left"
    private var lastEvadeLockTime: Long = 0L
    private var clearFrameCounter = 0

    fun synthesize(
        result: FrameAnalysisResult,
        isUserWalking: Boolean,
        allowRoadwayAlerts: Boolean = false,
        isEsp32: Boolean = false
    ): GuidanceOutput {
        val now = System.currentTimeMillis()
        val detections = result.detections
        val hasStairsVisible = detections.any { it.className == "stairs" }

        val stopFloorThreshold = if (isEsp32) AppConfig.STOP_FLOOR_ESP else AppConfig.STOP_FLOOR_PHONE
        val personFloorMin = if (isEsp32) AppConfig.PERSON_FLOOR_ESP else AppConfig.PERSON_FLOOR_PHONE
        val objectFloorMin = if (isEsp32) AppConfig.OBJECT_FLOOR_ESP else AppConfig.OBJECT_FLOOR_PHONE
        val stairsFloorMin = if (isEsp32) AppConfig.STAIRS_FLOOR_ESP else AppConfig.STAIRS_FLOOR_PHONE

        val imminentThreat = detections.firstOrNull { det ->
            if (det.className in listOf("sidewalk", "path")) return@firstOrNull false
            if (!allowRoadwayAlerts && det.className == "roadway") return@firstOrNull false

            val boxLeft = det.boundingBox.x
            val boxRight = det.boundingBox.x + det.boundingBox.width
            val floorY = det.boundingBox.y + det.boundingBox.height
            val area = det.boundingBox.width * det.boundingBox.height

            val inCorridor = (boxLeft < AppConfig.CORRIDOR_RIGHT && boxRight > AppConfig.CORRIDOR_LEFT)

            val isDistanceCritical = floorY > stopFloorThreshold
            val isTorsoImminent = (det.className == "person") && (area > 0.15f || det.boundingBox.width > 0.28f) && (floorY > 0.55f)
            val isTtcCritical = det.ttcSec in 0.2f..1.3f && det.trend == "growing"

            inCorridor && (isDistanceCritical || isTorsoImminent || isTtcCritical)
        }

        if (imminentThreat != null) {
            clearFrameCounter = 0
            return GuidanceOutput("Stop!", isEmergency = true, priorityLevel = 3)
        }

        val stairsHazard = detections.firstOrNull { det ->
            val boxLeft = det.boundingBox.x
            val boxRight = det.boundingBox.x + det.boundingBox.width
            val floorY = det.boundingBox.y + det.boundingBox.height
            det.className == "stairs" && (boxLeft < AppConfig.CORRIDOR_RIGHT && boxRight > AppConfig.CORRIDOR_LEFT) && floorY > stairsFloorMin
        }

        if (stairsHazard != null && isUserWalking) {
            clearFrameCounter = 0
            return GuidanceOutput("Caution, stairs ahead.", isEmergency = false, priorityLevel = 2)
        }

        if (allowRoadwayAlerts) {
            val isRoadwayReliable = !hasStairsVisible &&
                    result.primarySurface == "roadway" &&
                    result.surfaceConfidence > 0.75f &&
                    detections.none { it.className == "sidewalk" && it.score > 0.45f }

            val roadwayGroundCheck = detections.firstOrNull { det ->
                val floorY = det.boundingBox.y + det.boundingBox.height
                val roadwayMin = if (isEsp32) 0.86f else 0.84f
                det.className == "roadway" && det.boundingBox.y > 0.25f && floorY > roadwayMin
            }

            if ((isRoadwayReliable || roadwayGroundCheck != null) && isUserWalking) {
                clearFrameCounter = 0
                return GuidanceOutput("Warning, roadway surface.", isEmergency = false, priorityLevel = 2)
            }
        }

        val corridorPerson = detections.firstOrNull { det ->
            if (det.className != "person") return@firstOrNull false
            val boxLeft = det.boundingBox.x
            val boxRight = det.boundingBox.x + det.boundingBox.width
            val floorY = det.boundingBox.y + det.boundingBox.height

            val inCorridor = (boxLeft < AppConfig.CORRIDOR_RIGHT && boxRight > AppConfig.CORRIDOR_LEFT)
            inCorridor && (floorY in personFloorMin..stopFloorThreshold)
        }

        if (corridorPerson != null) {
            clearFrameCounter = 0

            val isLockExpired = (now - lastEvadeLockTime) > AppConfig.EVADE_LOCK_MS
            if (isLockExpired) {
                val pCx = corridorPerson.boundingBox.x + corridorPerson.boundingBox.width / 2f
                val flankThreshold = 0.62f

                val hasObstacleLeft = detections.any {
                    it != corridorPerson &&
                            (allowRoadwayAlerts || it.className != "roadway") &&
                            (it.boundingBox.x + it.boundingBox.width / 2f) < AppConfig.CORRIDOR_LEFT &&
                            (it.boundingBox.y + it.boundingBox.height) > flankThreshold
                }
                val hasObstacleRight = detections.any {
                    it != corridorPerson &&
                            (allowRoadwayAlerts || it.className != "roadway") &&
                            (it.boundingBox.x + it.boundingBox.width / 2f) > AppConfig.CORRIDOR_RIGHT &&
                            (it.boundingBox.y + it.boundingBox.height) > flankThreshold
                }

                lockedEvadeSide = when {
                    !hasObstacleLeft && (hasObstacleRight || pCx >= 0.50f) -> "step left"
                    !hasObstacleRight -> "step right"
                    else -> "step left"
                }
                lastEvadeLockTime = now
            }

            return GuidanceOutput("Person ahead, $lockedEvadeSide.", isEmergency = false, priorityLevel = 2)
        }

        val vehicleHazard = detections.firstOrNull { det ->
            val isVehicle = det.className in listOf("bicycle", "car", "motorcycle", "bus", "truck")
            if (!isVehicle) return@firstOrNull false

            val boxLeft = det.boundingBox.x
            val boxRight = det.boundingBox.x + det.boundingBox.width
            val floorY = det.boundingBox.y + det.boundingBox.height
            val area = det.boundingBox.width * det.boundingBox.height

            val inCorridor = (boxLeft < AppConfig.CORRIDOR_RIGHT && boxRight > AppConfig.CORRIDOR_LEFT)
            val inDistance = floorY in objectFloorMin..stopFloorThreshold || area > 0.10f

            inCorridor && inDistance
        }

        if (vehicleHazard != null && isUserWalking) {
            clearFrameCounter = 0
            val vehicleName = formatSpecificObjectName(vehicleHazard.className)
            return GuidanceOutput("$vehicleName ahead.", isEmergency = false, priorityLevel = 2)
        }

        val staticObstacle = detections.firstOrNull { det ->
            val boxLeft = det.boundingBox.x
            val boxRight = det.boundingBox.x + det.boundingBox.width
            val floorY = det.boundingBox.y + det.boundingBox.height
            val isRoadwayIgnored = !allowRoadwayAlerts && det.className == "roadway"

            !isRoadwayIgnored &&
                    det.className !in listOf("sidewalk", "path", "roadway", "stairs", "person", "bicycle", "car", "motorcycle", "bus", "truck") &&
                    (boxLeft < AppConfig.CORRIDOR_RIGHT && boxRight > AppConfig.CORRIDOR_LEFT) && floorY in objectFloorMin..stopFloorThreshold
        }

        if (staticObstacle != null && isUserWalking) {
            clearFrameCounter = 0
            val obstacleName = formatSpecificObjectName(staticObstacle.className)
            return GuidanceOutput("$obstacleName ahead.", isEmergency = false, priorityLevel = 2)
        }

        val hasCloseProximityHazard = detections.any {
            it.className !in listOf("sidewalk", "path") &&
                    (it.boundingBox.width * it.boundingBox.height > 0.12f) &&
                    (it.boundingBox.y + it.boundingBox.height > 0.60f)
        }

        if (!hasCloseProximityHazard) {
            clearFrameCounter++
        } else {
            clearFrameCounter = 0
        }

        if (clearFrameCounter >= AppConfig.CLEAR_FRAMES_REQUIRED) {
            lastEvadeLockTime = 0L
            return GuidanceOutput("Clear", isEmergency = false, priorityLevel = 1)
        }

        return GuidanceOutput("Clear", isEmergency = false, priorityLevel = 1)
    }

    private fun formatSpecificObjectName(rawClass: String): String = when (rawClass.lowercase()) {
        "bench" -> "Bench"
        "chair", "couch" -> "Chair"
        "fire hydrant" -> "Hydrant"
        "stop sign" -> "Sign"
        "traffic light" -> "Traffic light"
        "potted plant" -> "Plant"
        "dog" -> "Dog"
        "bicycle" -> "Bicycle"
        "car" -> "Car"
        "motorcycle" -> "Motorcycle"
        "bus" -> "Bus"
        "truck" -> "Truck"
        "parking meter" -> "Pole"
        "suitcase", "backpack" -> "Obstacle"
        else -> "Obstacle"
    }
}
package com.example.app_blindenstock_add_on.domain.synthesizer

import com.example.app_blindenstock_add_on.AppConfigState
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
    private var hazardFrameCounter = 0

    // Status für Stop-Situation
    private var isStopActive = false
    private var stopEvadeDirection = "Turn left"

    fun synthesize(
        result: FrameAnalysisResult,
        isUserWalking: Boolean,
        allowRoadwayAlerts: Boolean = false,
        isEsp32: Boolean = false,
        config: AppConfigState
    ): GuidanceOutput {
        val now = System.currentTimeMillis()
        val detections = result.detections
        val hasStairsVisible = detections.any { it.className == "stairs" }

        val stopFloorThreshold = if (isEsp32) config.stopFloorEsp else config.stopFloorPhone
        val personFloorMin = if (isEsp32) config.personFloorEsp else config.personFloorPhone
        val objectFloorMin = if (isEsp32) config.objectFloorEsp else config.objectFloorPhone
        val stairsFloorMin = if (isEsp32) config.stairsFloorEsp else config.stairsFloorPhone

        val imminentThreat = detections.firstOrNull { det ->
            if (det.className in listOf("sidewalk", "path")) return@firstOrNull false
            if (!allowRoadwayAlerts && det.className == "roadway") return@firstOrNull false

            val boxLeft = det.boundingBox.x
            val boxRight = det.boundingBox.x + det.boundingBox.width
            val floorY = det.boundingBox.y + det.boundingBox.height
            val area = det.boundingBox.width * det.boundingBox.height

            val inCorridor = (boxLeft < config.corridorRight && boxRight > config.corridorLeft)

            val isDistanceCritical = floorY > stopFloorThreshold
            val isTorsoImminent = (det.className == "person") && (area > 0.15f || det.boundingBox.width > 0.28f) && (floorY > 0.55f)
            val isTtcCritical = det.ttcSec in 0.2f..1.3f && det.trend == "growing"

            inCorridor && (isDistanceCritical || isTorsoImminent || isTtcCritical)
        }

        if (imminentThreat != null) {
            hazardFrameCounter++
            clearFrameCounter = 0
            if (hazardFrameCounter >= config.hazardFramesRequired) {
                val tLeft = imminentThreat.boundingBox.x
                val tRight = imminentThreat.boundingBox.x + imminentThreat.boundingBox.width
                val tCx = tLeft + imminentThreat.boundingBox.width / 2f

                val threatBlocksLeft = tLeft < 0.25f
                val threatBlocksRight = tRight > 0.75f
                val flankThreshold = 0.60f

                val hasObstacleLeft = threatBlocksLeft || detections.any {
                    it != imminentThreat &&
                            (allowRoadwayAlerts || it.className != "roadway") &&
                            (it.boundingBox.x + it.boundingBox.width / 2f) < config.corridorLeft &&
                            (it.boundingBox.y + it.boundingBox.height) > flankThreshold
                }
                val hasObstacleRight = threatBlocksRight || detections.any {
                    it != imminentThreat &&
                            (allowRoadwayAlerts || it.className != "roadway") &&
                            (it.boundingBox.x + it.boundingBox.width / 2f) > config.corridorRight &&
                            (it.boundingBox.y + it.boundingBox.height) > flankThreshold
                }

                // Dynamische Flankenauswertung
                val currentBestDirection = when {
                    !hasObstacleLeft && !hasObstacleRight -> if (tCx >= 0.50f) "Turn left" else "Turn right"
                    !hasObstacleLeft -> "Turn left"
                    !hasObstacleRight -> "Turn right"
                    else -> "Path blocked"
                }

                if (!isStopActive) {
                    isStopActive = true
                    stopEvadeDirection = currentBestDirection
                    return GuidanceOutput("Stop!", isEmergency = true, priorityLevel = 3)
                } else {
                    // Falls "Path blocked" aktiv war oder die bisherige Richtung neu versperrt ist, sofort aktualisieren
                    if (stopEvadeDirection == "Path blocked" ||
                        (stopEvadeDirection == "Turn left" && hasObstacleLeft) ||
                        (stopEvadeDirection == "Turn right" && hasObstacleRight)
                    ) {
                        stopEvadeDirection = currentBestDirection
                    }

                    val phrase = if (stopEvadeDirection == "Path blocked") "Path blocked." else "$stopEvadeDirection."
                    return GuidanceOutput(phrase, isEmergency = true, priorityLevel = 3)
                }
            }
            return GuidanceOutput("Clear", isEmergency = false, priorityLevel = 1)
        } else {
            isStopActive = false
        }

        val stairsHazard = detections.firstOrNull { det ->
            val boxLeft = det.boundingBox.x
            val boxRight = det.boundingBox.x + det.boundingBox.width
            val floorY = det.boundingBox.y + det.boundingBox.height
            det.className == "stairs" && (boxLeft < config.corridorRight && boxRight > config.corridorLeft) && floorY > stairsFloorMin
        }

        if (stairsHazard != null && isUserWalking) {
            hazardFrameCounter++
            clearFrameCounter = 0
            if (hazardFrameCounter >= config.hazardFramesRequired) {
                return GuidanceOutput("Caution, stairs ahead.", isEmergency = false, priorityLevel = 2)
            }
            return GuidanceOutput("Clear", isEmergency = false, priorityLevel = 1)
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
                hazardFrameCounter++
                clearFrameCounter = 0
                if (hazardFrameCounter >= config.hazardFramesRequired) {
                    return GuidanceOutput("Warning, roadway surface.", isEmergency = false, priorityLevel = 2)
                }
                return GuidanceOutput("Clear", isEmergency = false, priorityLevel = 1)
            }
        }

        val corridorPerson = detections.firstOrNull { det ->
            if (det.className != "person") return@firstOrNull false
            val boxLeft = det.boundingBox.x
            val boxRight = det.boundingBox.x + det.boundingBox.width
            val floorY = det.boundingBox.y + det.boundingBox.height

            val inCorridor = (boxLeft < config.corridorRight && boxRight > config.corridorLeft)
            inCorridor && (floorY in personFloorMin..stopFloorThreshold)
        }

        if (corridorPerson != null) {
            hazardFrameCounter++
            clearFrameCounter = 0

            if (hazardFrameCounter >= config.hazardFramesRequired) {
                val pLeft = corridorPerson.boundingBox.x
                val pRight = corridorPerson.boundingBox.x + corridorPerson.boundingBox.width
                val pCx = pLeft + corridorPerson.boundingBox.width / 2f

                val personBlocksLeft = pLeft < 0.25f
                val personBlocksRight = pRight > 0.75f
                val flankThreshold = 0.62f

                val hasObstacleLeft = personBlocksLeft || detections.any {
                    it != corridorPerson &&
                            (allowRoadwayAlerts || it.className != "roadway") &&
                            (it.boundingBox.x + it.boundingBox.width / 2f) < config.corridorLeft &&
                            (it.boundingBox.y + it.boundingBox.height) > flankThreshold
                }
                val hasObstacleRight = personBlocksRight || detections.any {
                    it != corridorPerson &&
                            (allowRoadwayAlerts || it.className != "roadway") &&
                            (it.boundingBox.x + it.boundingBox.width / 2f) > config.corridorRight &&
                            (it.boundingBox.y + it.boundingBox.height) > flankThreshold
                }

                val currentSide = when {
                    !hasObstacleLeft && !hasObstacleRight -> if (pCx >= 0.50f) "step left" else "step right"
                    !hasObstacleLeft -> "step left"
                    !hasObstacleRight -> "step right"
                    else -> "path blocked"
                }

                // Lock erlischt bei Timeout ODER wenn "path blocked" aktiv war und sich eine Flanke öffnet
                val isLockExpired = (now - lastEvadeLockTime) > config.evadeLockMs ||
                        (lockedEvadeSide == "path blocked" && currentSide != "path blocked")

                if (isLockExpired) {
                    lockedEvadeSide = currentSide
                    lastEvadeLockTime = now
                } else if (
                    (lockedEvadeSide == "step left" && hasObstacleLeft) ||
                    (lockedEvadeSide == "step right" && hasObstacleRight)
                ) {
                    // Sofortiger Wechsel falls die gewählte Richtung versperrt wurde
                    lockedEvadeSide = currentSide
                    lastEvadeLockTime = now
                }

                val phrase = if (lockedEvadeSide == "path blocked") "Person ahead, path blocked." else "Person ahead, $lockedEvadeSide."
                return GuidanceOutput(phrase, isEmergency = false, priorityLevel = 2)
            }
            return GuidanceOutput("Clear", isEmergency = false, priorityLevel = 1)
        }

        val vehicleHazard = detections.firstOrNull { det ->
            val isVehicle = det.className in listOf("bicycle", "car", "motorcycle", "bus", "truck")
            if (!isVehicle) return@firstOrNull false

            val boxLeft = det.boundingBox.x
            val boxRight = det.boundingBox.x + det.boundingBox.width
            val floorY = det.boundingBox.y + det.boundingBox.height
            val area = det.boundingBox.width * det.boundingBox.height

            val inCorridor = (boxLeft < config.corridorRight && boxRight > config.corridorLeft)
            val inDistance = floorY in objectFloorMin..stopFloorThreshold || area > 0.10f

            inCorridor && inDistance
        }

        if (vehicleHazard != null && isUserWalking) {
            hazardFrameCounter++
            clearFrameCounter = 0
            if (hazardFrameCounter >= config.hazardFramesRequired) {
                val vehicleName = formatSpecificObjectName(vehicleHazard.className)
                return GuidanceOutput("$vehicleName ahead.", isEmergency = false, priorityLevel = 2)
            }
            return GuidanceOutput("Clear", isEmergency = false, priorityLevel = 1)
        }

        val staticObstacle = detections.firstOrNull { det ->
            val boxLeft = det.boundingBox.x
            val boxRight = det.boundingBox.x + det.boundingBox.width
            val floorY = det.boundingBox.y + det.boundingBox.height
            val isRoadwayIgnored = !allowRoadwayAlerts && det.className == "roadway"

            !isRoadwayIgnored &&
                    det.className !in listOf("sidewalk", "path", "roadway", "stairs", "person", "bicycle", "car", "motorcycle", "bus", "truck") &&
                    (boxLeft < config.corridorRight && boxRight > config.corridorLeft) && floorY in objectFloorMin..stopFloorThreshold
        }

        if (staticObstacle != null && isUserWalking) {
            hazardFrameCounter++
            clearFrameCounter = 0
            if (hazardFrameCounter >= config.hazardFramesRequired) {
                val obstacleName = formatSpecificObjectName(staticObstacle.className)
                return GuidanceOutput("$obstacleName ahead.", isEmergency = false, priorityLevel = 2)
            }
            return GuidanceOutput("Clear", isEmergency = false, priorityLevel = 1)
        }

        hazardFrameCounter = 0

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

        if (clearFrameCounter >= config.clearFramesRequired) {
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
package com.example.app_blindenstock_add_on.framework.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.app_blindenstock_add_on.framework.viewmodel.AppScreen
import com.example.app_blindenstock_add_on.framework.viewmodel.MainViewModel

@Composable
fun CameraScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        color = Color(0xFF090C10)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Oberes Telemetrie-Band
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF161B22))
                    .border(1.dp, Color(0xFF30363D), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TelemetryPill(label = "FPS", value = "${uiState.currentFps}", color = Color(0xFF58A6FF))
                TelemetryPill(label = "LAT", value = "${uiState.inferenceTime}ms", color = Color(0xFF58A6FF))
                TelemetryPill(label = "MOTION", value = if (uiState.isUserWalking) "WALK" else "STAND", color = Color(0xFFD29922))
                TelemetryPill(label = "SURFACE", value = uiState.primarySurface.uppercase(), color = Color.White)
            }

            // Roadway-Filter Switch
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF161B22))
                    .border(1.dp, Color(0xFF30363D), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ROADWAY ALERTS",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (uiState.isRoadwayAlertsEnabled) Color(0xFF58A6FF) else Color(0xFF8B949E)
                )

                Switch(
                    checked = uiState.isRoadwayAlertsEnabled,
                    onCheckedChange = { viewModel.toggleRoadwayAlerts() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF238636),
                        uncheckedThumbColor = Color(0xFF8B949E),
                        uncheckedTrackColor = Color(0xFF21262D)
                    )
                )
            }

            // Seitenverhältnis-treuer Videocontainer (Kein Verzerren bei 640x480)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black)
                    .border(1.dp, Color(0xFF30363D), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                val frame = uiState.currentFrame
                if (frame != null) {
                    val frameAspect = frame.width.toFloat() / frame.height.toFloat()

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .wrapContentSize(Alignment.Center)
                            .aspectRatio(frameAspect)
                            .clip(RoundedCornerShape(8.dp))
                    ) {
                        Image(
                            bitmap = frame.asImageBitmap(),
                            contentDescription = "Video Feed",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.FillBounds
                        )

                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val canvasWidth = size.width
                            val canvasHeight = size.height

                            // Gehkorridor-Hilfslinien (35% und 65%)
                            drawLine(
                                color = Color(0x3358A6FF),
                                start = Offset(canvasWidth * 0.35f, 0f),
                                end = Offset(canvasWidth * 0.35f, canvasHeight),
                                strokeWidth = 2f
                            )
                            drawLine(
                                color = Color(0x3358A6FF),
                                start = Offset(canvasWidth * 0.65f, 0f),
                                end = Offset(canvasWidth * 0.65f, canvasHeight),
                                strokeWidth = 2f
                            )

                            // Bounding Boxes
                            uiState.detections.forEach { det ->
                                if (det.className in listOf("sidewalk", "path")) return@forEach
                                if (!uiState.isRoadwayAlertsEnabled && det.className == "roadway") return@forEach

                                val left = det.boundingBox.x * canvasWidth
                                val top = det.boundingBox.y * canvasHeight
                                val width = det.boundingBox.width * canvasWidth
                                val height = det.boundingBox.height * canvasHeight

                                val boxColor = when (det.priority) {
                                    "CRITICAL" -> Color(0xFFFF3333)
                                    "HIGH" -> Color(0xFFFF9800)
                                    "MEDIUM" -> Color(0xFFFFEB3B)
                                    else -> Color(0xFF4CAF50)
                                }

                                drawRect(
                                    color = boxColor,
                                    topLeft = Offset(left, top),
                                    size = Size(width, height),
                                    style = Stroke(width = 3.5f)
                                )

                                val tag = "${det.className} ${(det.score * 100).toInt()}%"
                                drawContext.canvas.nativeCanvas.apply {
                                    val paint = android.graphics.Paint().apply {
                                        color = android.graphics.Color.WHITE
                                        textSize = 28f
                                        isFakeBoldText = true
                                        setShadowLayer(4f, 0f, 0f, android.graphics.Color.BLACK)
                                    }
                                    drawText(tag, left + 8f, (top - 10f).coerceAtLeast(30f), paint)
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        text = "WAITING FOR CAMERA STREAM...",
                        color = Color(0xFF8B949E),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                }
            }

            // Aktives Sprach-Banner
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        if (uiState.currentGuidancePhrase.startsWith("Stop")) Color(0xFFF85149) else Color(0xFF238636),
                        RoundedCornerShape(8.dp)
                    ),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "AUDIO STATUS",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            color = Color(0xFF8B949E)
                        )
                        Text(
                            text = uiState.currentGuidancePhrase,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                uiState.currentGuidancePhrase.startsWith("Stop") -> Color(0xFFFF7B72)
                                uiState.currentGuidancePhrase == "Clear" -> Color(0xFF7EE787)
                                else -> Color(0xFFFFA657)
                            }
                        )
                    }

                    Text(
                        text = "${uiState.detections.size} OBJECTS",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = Color(0xFF8B949E)
                    )
                }
            }

            Button(
                onClick = {
                    viewModel.stopStream()
                    viewModel.navigateTo(AppScreen.START)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF21262D),
                    contentColor = Color(0xFFF85149)
                ),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = "STOP PIPELINE",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun TelemetryPill(label: String, value: String, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            color = Color(0xFF8B949E)
        )
        Text(
            text = value,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}
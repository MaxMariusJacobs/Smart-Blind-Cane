package com.example.app_blindenstock_add_on.framework.ui

import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.app_blindenstock_add_on.framework.viewmodel.AppScreen
import com.example.app_blindenstock_add_on.framework.viewmodel.GeminiStatus
import com.example.app_blindenstock_add_on.framework.viewmodel.MainViewModel

@Composable
fun CameraScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Live View",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Surface Scans",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(end = 8.dp),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Switch(
                        checked = uiState.isSurfaceScanEnabled,
                        onCheckedChange = { viewModel.toggleSurfaceScan() }
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.Black),
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
                            .clip(RoundedCornerShape(24.dp))
                    ) {
                        Image(
                            bitmap = frame.asImageBitmap(),
                            contentDescription = "Live Video",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.FillBounds
                        )

                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val canvasWidth = size.width
                            val canvasHeight = size.height

                            drawLine(
                                color = Color.White.copy(alpha = 0.2f),
                                start = Offset(canvasWidth * 0.32f, 0f),
                                end = Offset(canvasWidth * 0.32f, canvasHeight),
                                strokeWidth = 3f
                            )
                            drawLine(
                                color = Color.White.copy(alpha = 0.2f),
                                start = Offset(canvasWidth * 0.68f, 0f),
                                end = Offset(canvasWidth * 0.68f, canvasHeight),
                                strokeWidth = 3f
                            )

                            uiState.detections.forEach { det ->
                                if (det.className in listOf("sidewalk", "path")) return@forEach

                                val left = det.boundingBox.x * canvasWidth
                                val top = det.boundingBox.y * canvasHeight
                                val width = det.boundingBox.width * canvasWidth
                                val height = det.boundingBox.height * canvasHeight

                                val boxColor = when (det.priority) {
                                    "CRITICAL" -> Color(0xFFEF4444)
                                    "HIGH" -> Color(0xFFF97316)
                                    "MEDIUM" -> Color(0xFFEAB308)
                                    else -> Color(0xFF22C55E)
                                }

                                drawRect(
                                    color = boxColor,
                                    topLeft = Offset(left, top),
                                    size = Size(width, height),
                                    style = Stroke(width = 5f)
                                )

                                val tag = det.className.replaceFirstChar { it.uppercase() }
                                drawContext.canvas.nativeCanvas.apply {
                                    val paint = android.graphics.Paint().apply {
                                        color = android.graphics.Color.WHITE
                                        textSize = 36f
                                        isFakeBoldText = true
                                        typeface = android.graphics.Typeface.SANS_SERIF
                                        setShadowLayer(8f, 0f, 0f, android.graphics.Color.BLACK)
                                    }
                                    drawText(tag, left + 12f, (top - 16f).coerceAtLeast(45f), paint)
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(16.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.Black.copy(alpha = 0.5f))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .animateContentSize(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text("${uiState.currentFps} FPS", color = Color.White, style = MaterialTheme.typography.labelMedium)
                        Text("${uiState.inferenceTime} ms", color = Color.White, style = MaterialTheme.typography.labelMedium)
                        Text(if (uiState.isUserWalking) "Walking" else "Standing", color = Color.White, style = MaterialTheme.typography.labelMedium)
                    }

                    // --- NEU: Ausgelagertes Overlay aufrufen ---
                    GeminiStatusOverlay(
                        status = uiState.geminiStatus,
                        modifier = Modifier.align(Alignment.Center)
                    )

                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(36.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 3.dp
                        )
                        Text(
                            text = "Connecting camera...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Current Audio Guidance",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AnimatedContent(
                            targetState = uiState.currentGuidancePhrase,
                            transitionSpec = {
                                fadeIn() togetherWith fadeOut() using SizeTransform(clip = false)
                            },
                            label = "phrase_morph"
                        ) { targetPhrase ->
                            Text(
                                text = targetPhrase,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (targetPhrase.startsWith("Stop"))
                                    MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }

                        Text(
                            text = "${uiState.detections.size} Objects",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            Button(
                onClick = {
                    viewModel.stopStream()
                    viewModel.navigateTo(AppScreen.START)
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text(
                    text = "Stop",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

// --- NEU: Ausgelagerte Composable um den ColumnScope-Konflikt zu vermeiden ---
@Composable
fun GeminiStatusOverlay(
    status: GeminiStatus,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = status != GeminiStatus.IDLE,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = modifier
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.Black.copy(alpha = 0.7f),
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                when (status) {
                    GeminiStatus.ANALYZING -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = "Analyzing Scene...",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    GeminiStatus.SUCCESS -> {
                        Text(
                            text = "Analysis Complete",
                            color = Color(0xFF22C55E),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    GeminiStatus.ERROR -> {
                        Text(
                            text = "Analysis Failed",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    else -> {}
                }
            }
        }
    }
}
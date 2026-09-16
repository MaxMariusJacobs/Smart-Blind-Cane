package com.example.app_blindenstock_add_on.framework.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.app_blindenstock_add_on.framework.viewmodel.AppScreen
import com.example.app_blindenstock_add_on.framework.viewmodel.MainViewModel
import com.example.app_blindenstock_add_on.framework.viewmodel.SourceType

@Composable
fun StartScreen(
    viewModel: MainViewModel,
    onStartBackground: (String, String) -> Unit,
    onStopBackground: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

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
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header
            Column {
                Text(
                    text = "VISION PIPELINE CONFIG",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Assistive Object Tracking & Navigation",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = Color(0xFF8B949E)
                )
            }

            // Input Selection Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF30363D), RoundedCornerShape(8.dp)),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "VIDEO SOURCE",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF8B949E)
                    )

                    // Source Toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF0D1117))
                            .border(1.dp, Color(0xFF21262D), RoundedCornerShape(6.dp))
                            .padding(2.dp)
                    ) {
                        SourceTab(
                            title = "ESP32-CAM (MJPEG)",
                            isSelected = uiState.sourceType == SourceType.MJPEG,
                            modifier = Modifier.weight(1f)
                        ) {
                            viewModel.updateSourceType(SourceType.MJPEG)
                        }
                        SourceTab(
                            title = "INTERNAL CAM",
                            isSelected = uiState.sourceType == SourceType.CAMERA,
                            modifier = Modifier.weight(1f)
                        ) {
                            viewModel.updateSourceType(SourceType.CAMERA)
                        }
                    }

                    if (uiState.sourceType == SourceType.MJPEG) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = uiState.streamUrl,
                                onValueChange = { viewModel.updateUrl(it) },
                                label = { Text("STREAM ENDPOINT URL", fontFamily = FontFamily.Monospace, fontSize = 10.sp) },
                                singleLine = true,
                                textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color(0xFF58A6FF),
                                    unfocusedBorderColor = Color(0xFF30363D),
                                    focusedLabelColor = Color(0xFF58A6FF),
                                    unfocusedLabelColor = Color(0xFF8B949E)
                                )
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                PresetChip(text = "AP: 192.168.4.1/stream") {
                                    viewModel.updateUrl("http://192.168.4.1/stream")
                                }
                                PresetChip(text = "Hotspot: 192.168.43.1/stream") {
                                    viewModel.updateUrl("http://192.168.43.1/stream")
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "Standard Android CameraX Pipeline aktiv (320x240 RGB).",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = Color(0xFF8B949E)
                        )
                    }
                }
            }

            // Live Service Telemetry Card (nur sichtbar wenn Service aktiv)
            if (uiState.isBackgroundRunning) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFF238636), RoundedCornerShape(8.dp)),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1117))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "SERVICE ACTIVE",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF3FB950)
                            )
                            Text(
                                text = "${uiState.currentFps} FPS",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF58A6FF)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "LATENCY: ${uiState.inferenceTime} ms",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = Color(0xFF8B949E)
                            )
                            Text(
                                text = if (uiState.isUserWalking) "WALKING" else "STANDING",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFD29922)
                            )
                        }
                    }
                }
            }

            // Action Buttons
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        viewModel.navigateTo(AppScreen.CAMERA)
                        viewModel.startStream(context, lifecycleOwner)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF238636))
                ) {
                    Text(
                        text = "Test- and Preview Mode",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                if (uiState.isBackgroundRunning) {
                    Button(
                        onClick = { onStopBackground() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDA3633))
                    ) {
                        Text(
                            text = "Stop Background Service",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                } else {
                    OutlinedButton(
                        onClick = { onStartBackground(uiState.sourceType.name, uiState.streamUrl) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        shape = RoundedCornerShape(6.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF30363D)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFC9D1D9))
                    ) {
                        Text(
                            text = "Run in Background",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceTab(
    title: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (isSelected) Color(0xFF21262D) else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) Color(0xFF58A6FF) else Color(0xFF8B949E)
        )
    }
}

@Composable
private fun PresetChip(text: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable { onClick() },
        shape = RoundedCornerShape(4.dp),
        color = Color(0xFF21262D)
    ) {
        Text(
            text = text,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            color = Color(0xFF8B949E),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
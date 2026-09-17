package com.example.app_blindenstock_add_on.framework.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.app_blindenstock_add_on.AppConfig
import com.example.app_blindenstock_add_on.framework.viewmodel.AppScreen
import com.example.app_blindenstock_add_on.framework.viewmodel.MainViewModel
import com.example.app_blindenstock_add_on.framework.viewmodel.SourceType
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartScreen(
    viewModel: MainViewModel,
    onStartBackground: (String, String) -> Unit,
    onStopBackground: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val configState by AppConfig.currentState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var isSaved by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(24.dp)
                .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessLow)),
            verticalArrangement = Arrangement.SpaceBetween
        ) {

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Smart Cane Assistance",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Environment Tracking & Navigation",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    onClick = { showSettings = true },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Text(
                        text = "Camera Connection",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SourceOption(
                            title = "Cane (ESP32)",
                            isSelected = uiState.sourceType == SourceType.MJPEG,
                            modifier = Modifier.weight(1f)
                        ) { viewModel.updateSourceType(SourceType.MJPEG) }

                        SourceOption(
                            title = "Smartphone",
                            isSelected = uiState.sourceType == SourceType.CAMERA,
                            modifier = Modifier.weight(1f)
                        ) { viewModel.updateSourceType(SourceType.CAMERA) }
                    }

                    // NEU: Flüssige AnimatedContent Slide- & Fade-Transition
                    AnimatedContent(
                        targetState = uiState.sourceType,
                        transitionSpec = {
                            (fadeIn(animationSpec = tween(200)) + slideInVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { it / 6 }) togetherWith
                                    (fadeOut(animationSpec = tween(200)) + slideOutVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { -it / 6 })
                        },
                        label = "sourceTypeTransition"
                    ) { type ->
                        if (type == SourceType.MJPEG) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedTextField(
                                    value = uiState.streamUrl,
                                    onValueChange = {
                                        viewModel.updateUrl(it)
                                        isSaved = false
                                    },
                                    label = { Text("Network Address") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    trailingIcon = {
                                        val buttonColor by animateColorAsState(
                                            targetValue = if (isSaved) Color(0xFF22C55E) else MaterialTheme.colorScheme.primary,
                                            animationSpec = tween(300), label = "saveBtnColor"
                                        )
                                        val contentColor by animateColorAsState(
                                            targetValue = if (isSaved) Color.White else MaterialTheme.colorScheme.onPrimary,
                                            animationSpec = tween(300), label = "saveContentColor"
                                        )

                                        Surface(
                                            modifier = Modifier
                                                .padding(end = 6.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable {
                                                    if (!isSaved) {
                                                        viewModel.saveUrl(context, uiState.streamUrl)
                                                        isSaved = true
                                                        scope.launch {
                                                            delay(2.seconds)
                                                            isSaved = false
                                                        }
                                                    }
                                                },
                                            color = buttonColor
                                        ) {
                                            Box(
                                                modifier = Modifier.animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                AnimatedContent(
                                                    targetState = isSaved,
                                                    transitionSpec = {
                                                        (fadeIn() + scaleIn()) togetherWith (fadeOut() + scaleOut()) using SizeTransform(clip = false)
                                                    },
                                                    label = "save_button_animation"
                                                ) { savedState ->
                                                    if (savedState) {
                                                        Row(
                                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                        ) {
                                                            Text("✓", color = contentColor, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold)
                                                            Text("Saved", color = contentColor, style = MaterialTheme.typography.labelMedium)
                                                        }
                                                    } else {
                                                        Text(
                                                            text = "Save",
                                                            color = contentColor,
                                                            style = MaterialTheme.typography.labelMedium,
                                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                )

                                if (uiState.savedUrls.isNotEmpty()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        uiState.savedUrls.forEach { url ->
                                            Surface(
                                                onClick = {
                                                    viewModel.updateUrl(url)
                                                    isSaved = true
                                                    scope.launch {
                                                        delay(2.seconds)
                                                        isSaved = false
                                                    }
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                                color = MaterialTheme.colorScheme.secondaryContainer
                                            ) {
                                                Text(
                                                    text = url.removePrefix("http://").removeSuffix("/stream"),
                                                    style = MaterialTheme.typography.labelMedium,
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            Text(
                                text = "Uses the internal smartphone camera for environment tracking.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (uiState.isBackgroundRunning) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Service is Active",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "${uiState.currentFps} FPS",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Latency: ${uiState.inferenceTime} ms",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                            Text(
                                text = if (uiState.isUserWalking) "Walking" else "Standing",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        viewModel.navigateTo(AppScreen.CAMERA)
                        viewModel.startStream(context, lifecycleOwner)
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Preview & Test Mode", style = MaterialTheme.typography.titleMedium)
                }

                if (uiState.isBackgroundRunning) {
                    Button(
                        onClick = { onStopBackground() },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Stop Background Service", style = MaterialTheme.typography.titleMedium)
                    }
                } else {
                    OutlinedButton(
                        onClick = { onStartBackground(uiState.sourceType.name, uiState.streamUrl) },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Run in Background", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }

    if (showSettings) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showSettings = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Text("Live Tuning Parameters", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.saveTuningConfig(context) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Save")
                        }
                        OutlinedButton(
                            onClick = { viewModel.loadTuningConfig(context) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Load")
                        }
                        OutlinedButton(
                            onClick = { viewModel.resetTuningConfig() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Reset")
                        }
                    }
                }

                item {
                    SectionHeader(
                        title = "Corridor Boundaries",
                        description = "Defines the active walking path. Adjusting these narrows or widens the area where hazards trigger alerts."
                    )
                }
                item { SettingSlider("Corridor Left", configState.corridorLeft, 0.1f..0.5f) { AppConfig.update(configState.copy(corridorLeft = it)) } }
                item { SettingSlider("Corridor Right", configState.corridorRight, 0.5f..0.9f) { AppConfig.update(configState.copy(corridorRight = it)) } }

                item {
                    SectionHeader(
                        title = "Smartphone Distances",
                        description = "Safety limits for the internal camera. Lower values mean alerts trigger further away (earlier). Higher values trigger closer (later)."
                    )
                }
                item { SettingSlider("Stop Floor", configState.stopFloorPhone, 0.6f..0.95f) { AppConfig.update(configState.copy(stopFloorPhone = it)) } }
                item { SettingSlider("Person Floor", configState.personFloorPhone, 0.3f..0.8f) { AppConfig.update(configState.copy(personFloorPhone = it)) } }
                item { SettingSlider("Object Floor", configState.objectFloorPhone, 0.3f..0.8f) { AppConfig.update(configState.copy(objectFloorPhone = it)) } }
                item { SettingSlider("Stairs Floor", configState.stairsFloorPhone, 0.3f..0.8f) { AppConfig.update(configState.copy(stairsFloorPhone = it)) } }

                item {
                    SectionHeader(
                        title = "ESP32 Distances",
                        description = "Safety limits for the external cane camera. Lower = further away, Higher = closer."
                    )
                }
                item { SettingSlider("Stop Floor", configState.stopFloorEsp, 0.6f..0.95f) { AppConfig.update(configState.copy(stopFloorEsp = it)) } }
                item { SettingSlider("Person Floor", configState.personFloorEsp, 0.3f..0.8f) { AppConfig.update(configState.copy(personFloorEsp = it)) } }
                item { SettingSlider("Object Floor", configState.objectFloorEsp, 0.3f..0.8f) { AppConfig.update(configState.copy(objectFloorEsp = it)) } }
                item { SettingSlider("Stairs Floor", configState.stairsFloorEsp, 0.3f..0.8f) { AppConfig.update(configState.copy(stairsFloorEsp = it)) } }

                item {
                    SectionHeader(
                        title = "AI Confidence",
                        description = "Detection thresholds. Higher values reduce false positives but require the AI to be more certain."
                    )
                }
                item { SettingSlider("Object Threshold", configState.confThresholdObjects, 0.1f..0.8f) { AppConfig.update(configState.copy(confThresholdObjects = it)) } }
                item { SettingSlider("Surface Threshold", configState.confThresholdSurface, 0.1f..0.8f) { AppConfig.update(configState.copy(confThresholdSurface = it)) } }
                item { SettingSlider("NMS Overlap (IoU)", configState.nmsIouThreshold, 0.1f..0.8f) { AppConfig.update(configState.copy(nmsIouThreshold = it)) } }

                item {
                    SectionHeader(
                        title = "Debouncing & Filter",
                        description = "Controls alert stability. Higher frame counts make alerts more robust but slightly delay the audio response."
                    )
                }
                item { SettingSliderInt("Required Hazard Frames", configState.hazardFramesRequired, 1..10) { AppConfig.update(configState.copy(hazardFramesRequired = it)) } }
                item { SettingSliderInt("Required Clear Frames", configState.clearFramesRequired, 1..15) { AppConfig.update(configState.copy(clearFramesRequired = it)) } }

                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, description: String) {
    Column(modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Text(text = description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun SmallButton(text: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.size(32.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                fontWeight = FontWeight.ExtraBold,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onValueChange: (Float) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallButton("-") {
                    val newVal = (Math.round((value - 0.01f) * 100f) / 100f).coerceIn(range)
                    onValueChange(newVal)
                }
                Text(
                    text = String.format(java.util.Locale.US, "%.2f", value),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(42.dp),
                    textAlign = TextAlign.Center
                )
                SmallButton("+") {
                    val newVal = (Math.round((value + 0.01f) * 100f) / 100f).coerceIn(range)
                    onValueChange(newVal)
                }
            }
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            thumb = {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(28.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                )
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingSliderInt(label: String, value: Int, range: IntRange, onValueChange: (Int) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallButton("-") { onValueChange((value - 1).coerceIn(range)) }
                Text(
                    text = "$value",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(42.dp),
                    textAlign = TextAlign.Center
                )
                SmallButton("+") { onValueChange((value + 1).coerceIn(range)) }
            }
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first - 1).coerceAtLeast(0),
            thumb = {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(28.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                )
            }
        )
    }
}

@Composable
private fun SourceOption(
    title: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val containerColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        animationSpec = tween(durationMillis = 300),
        label = "sourceOptionContainer"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(durationMillis = 300),
        label = "sourceOptionContent"
    )

    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        tonalElevation = if (isSelected) 0.dp else 2.dp
    ) {
        Box(modifier = Modifier.padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
            Text(text = title, style = MaterialTheme.typography.labelLarge, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, color = contentColor)
        }
    }
}
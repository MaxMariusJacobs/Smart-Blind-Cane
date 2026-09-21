package com.example.app_blindenstock_add_on.framework.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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

    val isKeyboardOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    var isSaved by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }

    val topSpacerHeight by animateDpAsState(
        targetValue = if (isKeyboardOpen) 10.dp else 140.dp,
        animationSpec = tween(durationMillis = 250),
        label = "topSpacerAnimation"
    )

    val bottomSpacerHeight by animateDpAsState(
        targetValue = if (isKeyboardOpen) 350.dp else 0.dp,
        animationSpec = tween(durationMillis = 250),
        label = "bottomSpacerAnimation"
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Smart-Blind-Cane",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "AI-minor Project",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = { showSettings = true },
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(topSpacerHeight))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        Text(
                            text = "Camera Selection",
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

                        AnimatedContent(
                            targetState = uiState.sourceType,
                            transitionSpec = {
                                (fadeIn(animationSpec = tween(200)) + slideInVertically(animationSpec = tween(200)) { it / 6 }) togetherWith
                                        (fadeOut(animationSpec = tween(200)) + slideOutVertically(animationSpec = tween(200)) { -it / 6 })
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
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .bringIntoViewRequester(bringIntoViewRequester)
                                            .onFocusEvent { focusState ->
                                                if (focusState.isFocused) {
                                                    scope.launch {
                                                        delay(100)
                                                        bringIntoViewRequester.bringIntoView()
                                                    }
                                                }
                                            },
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
                                                                Icon(
                                                                    imageVector = Icons.Default.Check,
                                                                    contentDescription = "Saved",
                                                                    tint = contentColor,
                                                                    modifier = Modifier.size(16.dp)
                                                                )
                                                                Text(
                                                                    text = "Saved",
                                                                    color = contentColor,
                                                                    style = MaterialTheme.typography.labelMedium
                                                                )
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
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            uiState.savedUrls.forEach { url ->
                                                Surface(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = MaterialTheme.colorScheme.secondaryContainer
                                                ) {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = url.removePrefix("http://").removeSuffix("/stream"),
                                                            style = MaterialTheme.typography.labelMedium,
                                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                            modifier = Modifier
                                                                .weight(1f)
                                                                .clickable {
                                                                    viewModel.updateUrl(url)
                                                                    isSaved = true
                                                                    scope.launch {
                                                                        delay(2.seconds)
                                                                        isSaved = false
                                                                    }
                                                                }
                                                        )

                                                        Box(
                                                            modifier = Modifier
                                                                .size(24.dp)
                                                                .clip(RoundedCornerShape(6.dp))
                                                                .clickable { viewModel.removeUrl(context, url) },
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Text(
                                                                text = "✕",
                                                                style = MaterialTheme.typography.labelMedium,
                                                                fontWeight = FontWeight.Bold,
                                                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
                                                            )
                                                        }
                                                    }
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
                                if (uiState.currentFps > 0) {
                                    Text(
                                        text = "${uiState.currentFps} FPS",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(14.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "Connecting...",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (uiState.currentFps > 0) "Latency: ${uiState.inferenceTime} ms" else "Waiting for stream...",
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

                Spacer(modifier = Modifier.height(bottomSpacerHeight))
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FilledTonalButton(
                    onClick = {
                        if (!uiState.isLoading) {
                            viewModel.navigateTo(AppScreen.CAMERA)
                            viewModel.startStream(context, lifecycleOwner)
                        }
                    },
                    enabled = !uiState.isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    if (uiState.isLoading && uiState.isStreaming) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.5.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text("Connecting Camera...", style = MaterialTheme.typography.titleMedium)
                        }
                    } else {
                        Text("Preview & Test Mode", style = MaterialTheme.typography.titleMedium)
                    }
                }

                if (uiState.isBackgroundRunning) {
                    Button(
                        onClick = { onStopBackground() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Stop Background Service", style = MaterialTheme.typography.titleMedium)
                    }
                } else {
                    FilledTonalButton(
                        onClick = { onStartBackground(uiState.sourceType.name, uiState.streamUrl) },
                        enabled = !uiState.isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Text("Background Mode", style = MaterialTheme.typography.titleMedium)
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
                    Text("Live Tuning Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AnimatedSettingsButton(
                            text = "Save",
                            successText = "Save",
                            isOutlined = false,
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.saveTuningConfig(context) }
                        )
                        AnimatedSettingsButton(
                            text = "Load",
                            successText = "Load",
                            isOutlined = true,
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.loadTuningConfig(context) }
                        )
                        AnimatedSettingsButton(
                            text = "Reset",
                            successText = "Reset",
                            isOutlined = true,
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.resetTuningConfig() }
                        )
                    }
                }

                item {
                    SectionHeader(
                        title = "Walking Corridor Boundaries",
                        description = "Defines the active walking path. Adjusting these narrows or widens the area where hazards trigger alerts."
                    )
                }
                item { SettingSlider("Corridor Edge Left", configState.corridorLeft, 0.1f..0.5f) { AppConfig.update(configState.copy(corridorLeft = it)) } }
                item { SettingSlider("Corridor Edge Right", configState.corridorRight, 0.5f..0.9f) { AppConfig.update(configState.copy(corridorRight = it)) } }

                item {
                    SectionHeader(
                        title = "Smartphone Warning Distances",
                        description = "Thresholds for the internal camera. Triggers as soon as either the bottom edge or the bounding area exceeds the limit. Lower(-) = Earlier. Higher(+) = Later."
                    )
                }
                item { SettingSlider("Stop Distance", configState.stopFloorPhone, 0.6f..0.95f) { AppConfig.update(configState.copy(stopFloorPhone = it)) } }
                item { SettingSlider("Person Warning Distance", configState.personFloorPhone, 0.3f..0.8f) { AppConfig.update(configState.copy(personFloorPhone = it)) } }
                item { SettingSlider("Object Warning Distance", configState.objectFloorPhone, 0.3f..0.8f) { AppConfig.update(configState.copy(objectFloorPhone = it)) } }
                item { SettingSlider("Stairs Warning Distance", configState.stairsFloorPhone, 0.3f..0.8f) { AppConfig.update(configState.copy(stairsFloorPhone = it)) } }
                item { SettingSlider("Warning Area (Size)", configState.warningAreaPhone, 0.05f..0.6f) { AppConfig.update(configState.copy(warningAreaPhone = it)) } }
                item { SettingSlider("Stop Area (Size)", configState.stopAreaPhone, 0.1f..0.8f) { AppConfig.update(configState.copy(stopAreaPhone = it)) } }

                item {
                    SectionHeader(
                        title = "ESP32 Warning Distances",
                        description = "Thresholds for the external ESP32 Cam. Triggers as soon as either the bottom edge or the bounding area exceeds the limit. Lower(-) = Earlier. Higher(+) = Later."
                    )
                }
                item { SettingSlider("Stop Distance", configState.stopFloorEsp, 0.6f..0.95f) { AppConfig.update(configState.copy(stopFloorEsp = it)) } }
                item { SettingSlider("Person Warning Distance", configState.personFloorEsp, 0.3f..0.8f) { AppConfig.update(configState.copy(personFloorEsp = it)) } }
                item { SettingSlider("Object Warning Distance", configState.objectFloorEsp, 0.3f..0.8f) { AppConfig.update(configState.copy(objectFloorEsp = it)) } }
                item { SettingSlider("Stairs Warning Distance", configState.stairsFloorEsp, 0.3f..0.8f) { AppConfig.update(configState.copy(stairsFloorEsp = it)) } }
                item { SettingSlider("Warning Area (Size)", configState.warningAreaEsp, 0.05f..0.6f) { AppConfig.update(configState.copy(warningAreaEsp = it)) } }
                item { SettingSlider("Stop Area (Size)", configState.stopAreaEsp, 0.1f..0.8f) { AppConfig.update(configState.copy(stopAreaEsp = it)) } }

                item {
                    SectionHeader(
                        title = "AI Confidence",
                        description = "Detection thresholds. Higher values reduce false positives but require the AI to be more certain."
                    )
                }
                item { SettingSlider("Required Object Confidence", configState.confThresholdObjects, 0.1f..0.8f) { AppConfig.update(configState.copy(confThresholdObjects = it)) } }
                item { SettingSlider("Required Surface Confidence", configState.confThresholdSurface, 0.1f..0.8f) { AppConfig.update(configState.copy(confThresholdSurface = it)) } }
                item { SettingSlider("Box Overlap Limit", configState.nmsIouThreshold, 0.1f..0.8f) { AppConfig.update(configState.copy(nmsIouThreshold = it)) } }

                item {
                    SectionHeader(
                        title = "Reliability & Accuracy",
                        description = "Controls alert stability. Higher frame counts make alerts more reliable but slightly delay the audio response."
                    )
                }
                item { SettingSliderInt("Required Hazard Frames", configState.hazardFramesRequired, 1..10) { AppConfig.update(configState.copy(hazardFramesRequired = it)) } }
                item { SettingSliderInt("Required Surface Frames", configState.surfaceFramesRequired, 1..15) { AppConfig.update(configState.copy(surfaceFramesRequired = it)) } }
                item { SettingSliderInt("Required Clear Frames", configState.clearFramesRequired, 1..15) { AppConfig.update(configState.copy(clearFramesRequired = it)) } }

                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun AnimatedSettingsButton(
    text: String,
    successText: String,
    isOutlined: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var isSuccess by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val targetContainer = if (isSuccess) Color(0xFF22C55E) else if (isOutlined) Color.Transparent else MaterialTheme.colorScheme.primary
    val targetContent = if (isSuccess) Color.White else if (isOutlined) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimary
    val targetBorder = if (isSuccess) Color(0xFF22C55E) else MaterialTheme.colorScheme.outline

    val containerColor by animateColorAsState(targetValue = targetContainer, animationSpec = tween(300), label = "container")
    val contentColor by animateColorAsState(targetValue = targetContent, animationSpec = tween(300), label = "content")
    val borderColor by animateColorAsState(targetValue = targetBorder, animationSpec = tween(300), label = "border")

    val content: @Composable RowScope.() -> Unit = {
        AnimatedContent(
            targetState = isSuccess,
            transitionSpec = {
                (fadeIn() + scaleIn()) togetherWith (fadeOut() + scaleOut()) using SizeTransform(clip = false)
            },
            label = "button_animation"
        ) { success ->
            if (success) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Success",
                        modifier = Modifier.size(18.dp)
                    )
                    Text(text = successText)
                }
            } else {
                Text(text = text)
            }
        }
    }

    if (isOutlined) {
        OutlinedButton(
            onClick = {
                if (!isSuccess) {
                    onClick()
                    isSuccess = true
                    scope.launch {
                        delay(2.seconds)
                        isSuccess = false
                    }
                }
            },
            modifier = modifier,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = containerColor, contentColor = contentColor),
            border = BorderStroke(1.dp, borderColor),
            content = content
        )
    } else {
        Button(
            onClick = {
                if (!isSuccess) {
                    onClick()
                    isSuccess = true
                    scope.launch {
                        delay(2.seconds)
                        isSuccess = false
                    }
                }
            },
            modifier = modifier,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = containerColor, contentColor = contentColor),
            content = content
        )
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
        animationSpec = tween(durationMillis = 180),
        label = "sourceContainer"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(durationMillis = 180),
        label = "sourceContent"
    )

    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = containerColor
    ) {
        Box(modifier = Modifier.padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = contentColor
            )
        }
    }
}
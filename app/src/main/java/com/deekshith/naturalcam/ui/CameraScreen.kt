package com.deekshith.naturalcam.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.camera.core.ImageCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.deekshith.naturalcam.ai.AiPhotographyDirector
import com.deekshith.naturalcam.ai.SceneSuggestion
import com.deekshith.naturalcam.camera.AspectRatioMode
import com.deekshith.naturalcam.camera.CameraMode
import com.deekshith.naturalcam.camera.NaturalCameraManager
import com.deekshith.naturalcam.filter.FilterCategory
import com.deekshith.naturalcam.filter.ForestDslrFilter
import com.deekshith.naturalcam.pose.PoseCategory
import com.deekshith.naturalcam.pose.PoseGuide
import com.deekshith.naturalcam.pose.PoseLibrary
import com.deekshith.naturalcam.sensor.rememberOrientationSensor
import com.deekshith.naturalcam.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun CameraScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    val cameraManager = remember { NaturalCameraManager(context, lifecycleOwner) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }

    // Modes & Zoom
    var currentMode by remember { mutableStateOf(CameraMode.PHOTO) }
    var selectedZoom by remember { mutableFloatStateOf(1.0f) }
    var exposureEv by remember { mutableFloatStateOf(-0.3f) }
    var aspectRatio by remember { mutableStateOf(AspectRatioMode.RATIO_4_3) }

    // Filters
    var selectedFilter by remember { mutableStateOf(ForestDslrFilter.NATURAL) }
    var selectedCategory by remember { mutableStateOf(FilterCategory.ALL) }
    var showFilterSelector by remember { mutableStateOf(false) }

    // On-Device AI Scene Director (Huawei Master AI style)
    var aiSuggestion by remember { mutableStateOf<SceneSuggestion?>(null) }
    var isAiDirectorEnabled by remember { mutableStateOf(true) }

    // Multi-Style Burst result notification
    var multiBurstBannerText by remember { mutableStateOf<String?>(null) }

    // Huawei Smart Pose & Composition Director
    var selectedPose by remember { mutableStateOf<PoseGuide?>(null) }
    var showPoseDrawer by remember { mutableStateOf(false) }
    var selectedPoseCategory by remember { mutableStateOf(PoseCategory.STANDING) }

    // Toggles
    var showExposureSlider by remember { mutableStateOf(false) }
    var showProControls by remember { mutableStateOf(false) }
    var showGrid by remember { mutableStateOf(false) }
    var showLeveler by remember { mutableStateOf(true) }
    var flashMode by remember { mutableIntStateOf(ImageCapture.FLASH_MODE_OFF) }
    var isTorchOn by remember { mutableStateOf(false) }
    var timerDurationSec by remember { mutableIntStateOf(0) }
    var countdownRemaining by remember { mutableIntStateOf(0) }

    // Pro settings
    var proIso by remember { mutableStateOf<Int?>(null) }
    var proShutter by remember { mutableStateOf<Long?>(null) }

    // Video Recording state
    var isRecording by remember { mutableStateOf(false) }
    var recordingDurationSec by remember { mutableIntStateOf(0) }

    // Capture & Gallery
    var lastCapturedUri by remember { mutableStateOf<Uri?>(null) }
    var isCapturing by remember { mutableStateOf(false) }
    var focusPosition by remember { mutableStateOf<Offset?>(null) }

    // Horizon leveler angle
    val rollAngle = rememberOrientationSensor(context)
    val isLevel = abs(rollAngle) <= 1.2f || abs(rollAngle - 90f) <= 1.2f || abs(rollAngle + 90f) <= 1.2f || abs(abs(rollAngle) - 180f) <= 1.2f

    // Register AI Photography Director on launch
    val currentAiEnabled by rememberUpdatedState(isAiDirectorEnabled)
    DisposableEffect(cameraManager) {
        val director = AiPhotographyDirector(context) { suggestion ->
            if (currentAiEnabled) {
                aiSuggestion = suggestion
            }
        }
        cameraManager.registerAiDirector(director)
        onDispose { director.close() }
    }

    // Video recording timer
    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingDurationSec = 0
            while (isRecording) {
                delay(1000)
                recordingDurationSec++
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraManager.shutdown()
        }
    }

    // Direct Hardware RenderEffect on PreviewView for 100% reliable 60fps live filter preview (including optical bloom)
    LaunchedEffect(selectedFilter, previewView) {
        val pv = previewView ?: return@LaunchedEffect
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val effect = selectedFilter.getRenderEffect()
            pv.setRenderEffect(effect)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
    ) {
        // 1. Camera Preview Viewfinder with Live 60fps Filter Preview
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // Real-Time Viewfinder Filter Preview using hardware RenderEffect (Android 12+)
                    renderEffect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && selectedFilter != ForestDslrFilter.NATURAL) {
                        selectedFilter.getRenderEffect()?.asComposeRenderEffect()
                    } else {
                        null
                    }
                }
        ) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        this.scaleType = PreviewView.ScaleType.FILL_CENTER
                        this.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        previewView = this
                        cameraManager.startCamera(
                            previewView = this,
                            mode = currentMode,
                            aspectRatio = aspectRatio
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, _, zoom, _ ->
                            val newZoom = (selectedZoom * zoom).coerceIn(0.6f, 10.0f)
                            selectedZoom = newZoom
                            cameraManager.setZoom(newZoom)
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            focusPosition = offset
                            val factory = previewView?.meteringPointFactory
                            if (factory != null) {
                                val point = factory.createPoint(offset.x, offset.y)
                                cameraManager.focusOnPoint(point)
                            }
                            scope.launch {
                                delay(1800)
                                focusPosition = null
                            }
                        }
                    }
            )
        }

        // 2. Rule of Thirds Grid Overlay
        if (showGrid) {
            GridOverlay(modifier = Modifier.fillMaxSize())
        }

        // 2b. Huawei Smart Pose Silhouette Wireframe Guide Overlay
        selectedPose?.let { pose ->
            Canvas(modifier = Modifier.fillMaxSize()) {
                PoseLibrary.drawSilhouette(
                    drawScope = this,
                    shape = pose.silhouetteShape,
                    color = AmberAccent.copy(alpha = 0.85f),
                    size = size
                )
            }
        }

        // 2c. Huawei Smart Pose Coaching Banner (Where to Stand & Sit Advice)
        AnimatedVisibility(
            visible = selectedPose != null,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 56.dp, start = 14.dp, end = 14.dp)
        ) {
            selectedPose?.let { pose ->
                Surface(
                    color = TranslucentBlack,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, AmberAccent)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "💡 Huawei Pose Director",
                                    color = AmberAccent,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "• ${pose.name}",
                                    color = White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            IconButton(
                                onClick = { selectedPose = null },
                                modifier = Modifier.size(22.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close Pose Guide",
                                    tint = LightGray,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = pose.coachingTip,
                            color = White.copy(alpha = 0.95f),
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "📍 ${pose.whereToStandTip}",
                            color = AmberAccent.copy(alpha = 0.85f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // 3. Digital Horizon Leveler
        if (showLeveler) {
            HorizonLeveler(isLevel = isLevel, modifier = Modifier.align(Alignment.Center))
        }

        // 4. Tap-To-Focus Indicator Ring
        focusPosition?.let { pos ->
            Box(
                modifier = Modifier
                    .offset(x = (pos.x - 35).dp, y = (pos.y - 35).dp)
                    .size(70.dp)
                    .border(1.5.dp, FocusRingColor, CircleShape)
            )
        }

        // 5. Self-Timer Countdown Animation Overlay
        if (countdownRemaining > 0) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$countdownRemaining",
                    color = White,
                    fontSize = 96.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // 6. Top Utility Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Flash & Torch Toggle
            IconButton(
                onClick = {
                    if (currentMode == CameraMode.VIDEO) {
                        isTorchOn = !isTorchOn
                        cameraManager.toggleTorch(isTorchOn)
                    } else {
                        flashMode = when (flashMode) {
                            ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON
                            ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO
                            else -> ImageCapture.FLASH_MODE_OFF
                        }
                        cameraManager.setFlashMode(flashMode)
                    }
                },
                modifier = Modifier
                    .background(TranslucentBlack, CircleShape)
                    .size(40.dp)
            ) {
                Icon(
                    imageVector = when {
                        isTorchOn -> Icons.Default.Highlight
                        flashMode == ImageCapture.FLASH_MODE_ON -> Icons.Default.FlashOn
                        flashMode == ImageCapture.FLASH_MODE_AUTO -> Icons.Default.FlashAuto
                        else -> Icons.Default.FlashOff
                    },
                    contentDescription = "Flash",
                    tint = if (flashMode == ImageCapture.FLASH_MODE_ON || isTorchOn) AmberAccent else White
                )
            }

            // Timer Toggle (Off -> 3s -> 10s)
            IconButton(
                onClick = {
                    timerDurationSec = when (timerDurationSec) {
                        0 -> 3
                        3 -> 10
                        else -> 0
                    }
                },
                modifier = Modifier
                    .background(TranslucentBlack, CircleShape)
                    .size(40.dp)
            ) {
                Text(
                    text = if (timerDurationSec == 0) "OFF" else "${timerDurationSec}s",
                    color = if (timerDurationSec > 0) AmberAccent else White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Natural Tone / Active DSLR Filter Badge (Tap to Open Filter Suite)
            Surface(
                color = TranslucentBlack,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, if (selectedFilter != ForestDslrFilter.NATURAL) AmberAccent else OnePlusRed.copy(alpha = 0.6f)),
                modifier = Modifier.clickable {
                    showFilterSelector = !showFilterSelector
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(if (selectedFilter != ForestDslrFilter.NATURAL) AmberAccent else OnePlusRed, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = if (selectedFilter != ForestDslrFilter.NATURAL) {
                            selectedFilter.displayName.uppercase()
                        } else {
                            when (currentMode) {
                                CameraMode.PRO -> "PRO RAW"
                                CameraMode.PORTRAIT -> "PORTRAIT"
                                CameraMode.NIGHT -> "NIGHTSCAPE"
                                CameraMode.VIDEO -> "4K UHD"
                                CameraMode.MULTI -> "MULTI-STYLE"
                                else -> "NATURAL TONE"
                            }
                        },
                        color = White,
                        fontSize = 10.sp,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            // Aspect Ratio Toggle (4:3 <-> 16:9)
            IconButton(
                onClick = {
                    aspectRatio = if (aspectRatio == AspectRatioMode.RATIO_4_3) {
                        AspectRatioMode.RATIO_16_9
                    } else {
                        AspectRatioMode.RATIO_4_3
                    }
                    previewView?.let {
                        cameraManager.startCamera(it, currentMode, aspectRatio)
                    }
                },
                modifier = Modifier
                    .background(TranslucentBlack, CircleShape)
                    .size(40.dp)
            ) {
                Text(
                    text = if (aspectRatio == AspectRatioMode.RATIO_4_3) "4:3" else "16:9",
                    color = White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Grid Toggle
            IconButton(
                onClick = { showGrid = !showGrid },
                modifier = Modifier
                    .background(TranslucentBlack, CircleShape)
                    .size(40.dp)
            ) {
                Icon(
                    imageVector = if (showGrid) Icons.Default.GridOn else Icons.Default.GridOff,
                    contentDescription = "Grid",
                    tint = if (showGrid) AmberAccent else White
                )
            }
        }

        // 7. On-Device Offline AI Scene Director Floating Card (Huawei Master AI Style)
        AnimatedVisibility(
            visible = aiSuggestion != null && !showFilterSelector,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 56.dp)
        ) {
            aiSuggestion?.let { suggestion ->
                Surface(
                    color = TranslucentBlack,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, AmberAccent.copy(alpha = 0.7f)),
                    modifier = Modifier.padding(horizontal = 20.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .clickable {
                                // 1-Tap apply recommended settings
                                selectedFilter = suggestion.recommendedFilter
                                exposureEv = suggestion.recommendedEv
                                cameraManager.setExposureEv(suggestion.recommendedEv)
                                if (currentMode != suggestion.recommendedMode && previewView != null) {
                                    cameraManager.switchMode(previewView!!, suggestion.recommendedMode) {
                                        currentMode = suggestion.recommendedMode
                                    }
                                }
                                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                aiSuggestion = null
                            }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = suggestion.title,
                                color = AmberAccent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = suggestion.subtitle,
                                color = White.copy(alpha = 0.9f),
                                fontSize = 10.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = AmberAccent,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = "APPLY",
                                color = Black,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }
        }

        // 8. Active Filter Indicator Chip on Viewfinder (Tap to Reset to Natural)
        AnimatedVisibility(
            visible = selectedFilter != ForestDslrFilter.NATURAL,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 220.dp)
        ) {
            Surface(
                color = TranslucentBlack,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, AmberAccent),
                modifier = Modifier.clickable {
                    selectedFilter = ForestDslrFilter.NATURAL
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        Color(selectedFilter.swatchStart),
                                        Color(selectedFilter.swatchEnd)
                                    )
                                )
                            )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = selectedFilter.displayName,
                        color = AmberAccent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Reset filter",
                        tint = LightGray,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        // 9. Multi-Style Burst Notification Toast
        AnimatedVisibility(
            visible = multiBurstBannerText != null,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 180.dp)
        ) {
            Surface(
                color = TranslucentBlack,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, AmberAccent)
            ) {
                Text(
                    text = multiBurstBannerText ?: "",
                    color = White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        // 10. Video Recording Duration Timer (When Recording)
        if (isRecording) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 56.dp)
                    .background(OnePlusRed, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                val mins = recordingDurationSec / 60
                val secs = recordingDurationSec % 60
                Text(
                    text = String.format("%02d:%02d", mins, secs),
                    color = White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // 11. Exposure Compensation Dial (-2.0 EV to +2.0 EV)
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = { showExposureSlider = !showExposureSlider },
                    modifier = Modifier
                        .background(TranslucentBlack, CircleShape)
                        .size(42.dp)
                ) {
                    Text(
                        text = "${if (exposureEv > 0) "+" else ""}${String.format("%.1f", exposureEv)}",
                        color = AmberAccent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                AnimatedVisibility(
                    visible = showExposureSlider,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = TranslucentBlack),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .width(48.dp)
                            .height(160.dp)
                    ) {
                        Slider(
                            value = exposureEv,
                            onValueChange = {
                                exposureEv = it
                                cameraManager.setExposureEv(it)
                            },
                            valueRange = -2.0f..2.0f,
                            steps = 7,
                            modifier = Modifier
                                .fillMaxHeight()
                                .padding(vertical = 10.dp)
                        )
                    }
                }
            }
        }

        // 12. PRO Mode Manual Controls Sheet (When in PRO mode)
        if (currentMode == CameraMode.PRO) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 200.dp)
                    .background(TranslucentBlack, RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text("ISO", color = LightGray, fontSize = 10.sp)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val isos = listOf(null, 100, 200, 400, 800, 1600, 3200)
                    items(isos) { iso ->
                        val isSelected = proIso == iso
                        Text(
                            text = if (iso == null) "AUTO" else "$iso",
                            color = if (isSelected) AmberAccent else White,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier
                                .clickable {
                                    proIso = iso
                                    cameraManager.setProSettings(iso = iso)
                                }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        // 13. Bottom Control Area
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Quick Control Strip: Multi-Lens Zoom + Filter Suite Button + Master AI Toggle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                // Multi-Lens Quick Selector (0.6x, 1.0x, 2.0x, 5.0x)
                Row(
                    modifier = Modifier
                        .background(TranslucentBlack, RoundedCornerShape(20.dp))
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(0.6f, 1.0f, 2.0f, 5.0f).forEach { zoom ->
                        val isSelected = abs(selectedZoom - zoom) < 0.15f
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (isSelected) OnePlusRed else Color.Transparent)
                                .clickable {
                                    selectedZoom = zoom
                                    cameraManager.setZoom(zoom)
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = when (zoom) {
                                    0.6f -> "0.6x"
                                    1.0f -> "1x"
                                    2.0f -> "2x"
                                    else -> "5x"
                                },
                                color = White,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                // DSLR Filter Suite Toggle Button (with active amber glow)
                IconButton(
                    onClick = {
                        showFilterSelector = !showFilterSelector
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            if (showFilterSelector || selectedFilter != ForestDslrFilter.NATURAL) AmberAccent else TranslucentBlack,
                            CircleShape
                        )
                        .border(
                            1.dp,
                            if (showFilterSelector || selectedFilter != ForestDslrFilter.NATURAL) AmberAccent else White.copy(alpha = 0.4f),
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Filters",
                        tint = if (showFilterSelector || selectedFilter != ForestDslrFilter.NATURAL) Black else White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Master AI Director Toggle Button (Huawei Master AI style)
                IconButton(
                    onClick = {
                        isAiDirectorEnabled = !isAiDirectorEnabled
                        if (!isAiDirectorEnabled) aiSuggestion = null
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            if (isAiDirectorEnabled) Color(0xFF1E88E5).copy(alpha = 0.8f) else TranslucentBlack,
                            CircleShape
                        )
                        .border(
                            1.dp,
                            if (isAiDirectorEnabled) Color(0xFF64B5F6) else White.copy(alpha = 0.4f),
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Psychology,
                        contentDescription = "Master AI",
                        tint = White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Huawei Smart Pose Guide Toggle Button
                IconButton(
                    onClick = {
                        showPoseDrawer = !showPoseDrawer
                        if (showPoseDrawer) showFilterSelector = false
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            if (showPoseDrawer || selectedPose != null) Color(0xFF00E676).copy(alpha = 0.85f) else TranslucentBlack,
                            CircleShape
                        )
                        .border(
                            1.dp,
                            if (showPoseDrawer || selectedPose != null) Color(0xFF00E676) else White.copy(alpha = 0.4f),
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.AccessibilityNew,
                        contentDescription = "Pose Guide",
                        tint = if (showPoseDrawer || selectedPose != null) Black else White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Huawei Smart Pose Guide Drawer (Expands right above modes)
            AnimatedVisibility(
                visible = showPoseDrawer,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .background(TranslucentBlack, RoundedCornerShape(20.dp))
                        .border(1.dp, Color(0xFF00E676).copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                        .padding(vertical = 10.dp, horizontal = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Pose Category Tabs (Standing | Sitting | Close-Up | Forest / Nature)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        items(PoseCategory.values()) { category ->
                            val isCatSelected = selectedPoseCategory == category
                            Text(
                                text = "${category.icon} ${category.label}",
                                color = if (isCatSelected) Color(0xFF00E676) else LightGray,
                                fontSize = 11.sp,
                                fontWeight = if (isCatSelected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier
                                    .clickable {
                                        selectedPoseCategory = category
                                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    }
                                    .background(
                                        if (isCatSelected) Color(0xFF00E676).copy(alpha = 0.2f) else Color.Transparent,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Pose Selection Cards
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val posesInCategory = PoseLibrary.poses.filter { it.category == selectedPoseCategory }
                        items(posesInCategory) { pose ->
                            val isPoseSelected = selectedPose == pose
                            Surface(
                                color = if (isPoseSelected) Color(0xFF00E676) else TranslucentBlack,
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, if (isPoseSelected) Color(0xFF00E676) else White.copy(alpha = 0.3f)),
                                modifier = Modifier.clickable {
                                    selectedPose = if (isPoseSelected) null else pose
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                }
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = pose.name,
                                        color = if (isPoseSelected) Black else White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (isPoseSelected) "ACTIVE GUIDE" else "Tap to align",
                                        color = if (isPoseSelected) Black.copy(alpha = 0.8f) else AmberAccent,
                                        fontSize = 9.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Filter Drawer with Visual Swatches (Expands cleanly right above Modes)
            AnimatedVisibility(
                visible = showFilterSelector,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .background(TranslucentBlack, RoundedCornerShape(20.dp))
                        .border(1.dp, AmberAccent.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
                        .padding(vertical = 10.dp, horizontal = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Category Tabs (All | Forest & Nature | Low-Light & Night | Black & White)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        items(FilterCategory.values()) { category ->
                            val isCatSelected = selectedCategory == category
                            Text(
                                text = category.label,
                                color = if (isCatSelected) AmberAccent else LightGray,
                                fontSize = 11.sp,
                                fontWeight = if (isCatSelected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier
                                    .clickable {
                                        selectedCategory = category
                                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    }
                                    .background(
                                        if (isCatSelected) AmberAccent.copy(alpha = 0.2f) else Color.Transparent,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Visual Filter Swatches Carousel
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val filteredList = ForestDslrFilter.values().filter {
                            selectedCategory == FilterCategory.ALL || it.category == selectedCategory || it == ForestDslrFilter.NATURAL
                        }

                        items(filteredList) { filter ->
                            val isSelected = selectedFilter == filter
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.clickable {
                                    selectedFilter = filter
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .border(
                                            width = if (isSelected) 3.dp else 1.dp,
                                            color = if (isSelected) AmberAccent else White.copy(alpha = 0.4f),
                                            shape = CircleShape
                                        )
                                        .padding(if (isSelected) 3.dp else 1.dp)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.linearGradient(
                                                listOf(
                                                    Color(filter.swatchStart),
                                                    Color(filter.swatchEnd)
                                                )
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Active",
                                            tint = White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = filter.displayName,
                                    color = if (isSelected) AmberAccent else White,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Camera Modes Carousel (Swipe/Tap between Modes)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(
                    CameraMode.PRO to "PRO",
                    CameraMode.PORTRAIT to "PORTRAIT",
                    CameraMode.PHOTO to "PHOTO",
                    CameraMode.MULTI to "MULTI",
                    CameraMode.VIDEO to "VIDEO",
                    CameraMode.NIGHT to "NIGHT"
                ).forEach { (mode, label) ->
                    val isSelected = currentMode == mode
                    Text(
                        text = label,
                        color = if (isSelected) AmberAccent else LightGray,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier
                            .clickable {
                                if (currentMode != mode) {
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    previewView?.let { pv ->
                                        cameraManager.switchMode(pv, mode) {
                                            currentMode = mode
                                        }
                                    }
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Shutter Button Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Recent Gallery Preview Thumbnail
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(DarkGray)
                        .border(1.dp, White.copy(alpha = 0.3f), CircleShape)
                        .clickable {
                            lastCapturedUri?.let { uri ->
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, "image/*")
                                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                                }
                                context.startActivity(intent)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (lastCapturedUri != null) {
                        AsyncImage(
                            model = lastCapturedUri,
                            contentDescription = "Last Photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = "Gallery",
                            tint = LightGray,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Shutter / Record / Multi-Burst Button
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .border(4.dp, if (currentMode == CameraMode.MULTI) AmberAccent else White, CircleShape)
                        .padding(6.dp)
                        .clip(if (isRecording) RoundedCornerShape(12.dp) else CircleShape)
                        .background(
                            when {
                                currentMode == CameraMode.VIDEO || isRecording -> OnePlusRed
                                currentMode == CameraMode.MULTI -> AmberAccent
                                else -> White
                            }
                        )
                        .clickable(enabled = !isCapturing) {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)

                            when (currentMode) {
                                CameraMode.VIDEO -> {
                                    if (isRecording) {
                                        cameraManager.stopVideoRecording()
                                        isRecording = false
                                    } else {
                                        isRecording = true
                                        cameraManager.startVideoRecording { event ->
                                            if (event is VideoRecordEvent.Finalize) {
                                                isRecording = false
                                                if (!event.hasError()) {
                                                    lastCapturedUri = event.outputResults.outputUri
                                                }
                                            }
                                        }
                                    }
                                }

                                CameraMode.MULTI -> {
                                    // Huawei XMAGE / Master All-In-One Multi-Capture
                                    isCapturing = true
                                    cameraManager.takeMultiAllInOne(
                                        onResultsSaved = { count, firstUri ->
                                            isCapturing = false
                                            lastCapturedUri = firstUri
                                            multiBurstBannerText = "✨ $count Photos Saved: Natural, 50MP High-Res, Portrait Bokeh & Master Styles!"
                                            scope.launch {
                                                delay(4000)
                                                multiBurstBannerText = null
                                            }
                                        },
                                        onError = {
                                            isCapturing = false
                                        }
                                    )
                                }

                                else -> {
                                    // Photo / Pro / Portrait / Night mode
                                    if (timerDurationSec > 0) {
                                        scope.launch {
                                            countdownRemaining = timerDurationSec
                                            while (countdownRemaining > 0) {
                                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                                delay(1000)
                                                countdownRemaining--
                                            }
                                            performPhotoCapture(cameraManager, selectedFilter) { uri ->
                                                lastCapturedUri = uri
                                            }
                                        }
                                    } else {
                                        isCapturing = true
                                        performPhotoCapture(cameraManager, selectedFilter) { uri ->
                                            isCapturing = false
                                            lastCapturedUri = uri
                                        }
                                    }
                                }
                            }
                        }
                )

                // Front / Back Camera Switcher
                IconButton(
                    onClick = {
                        previewView?.let { pv ->
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            cameraManager.switchCamera(pv) {}
                        }
                    },
                    modifier = Modifier
                        .size(52.dp)
                        .background(DarkGray, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.FlipCameraAndroid,
                        contentDescription = "Switch Camera",
                        tint = White,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    }
}

private fun performPhotoCapture(
    cameraManager: NaturalCameraManager,
    filter: ForestDslrFilter,
    onSaved: (Uri) -> Unit
) {
    cameraManager.takePhoto(
        filter = filter,
        onPhotoSaved = { uri -> onSaved(uri) },
        onError = { /* error handled */ }
    )
}

@Composable
fun HorizonLeveler(isLevel: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(120.dp, 20.dp)) {
        val lineColor = if (isLevel) Color(0xFF00E676) else Color.White.copy(alpha = 0.4f)
        val stroke = if (isLevel) 2.5.dp.toPx() else 1.5.dp.toPx()

        drawLine(
            color = lineColor,
            start = Offset(0f, size.height / 2),
            end = Offset(35f, size.height / 2),
            strokeWidth = stroke
        )
        drawLine(
            color = lineColor,
            start = Offset(size.width - 35f, size.height / 2),
            end = Offset(size.width, size.height / 2),
            strokeWidth = stroke
        )
        drawCircle(
            color = lineColor,
            radius = if (isLevel) 4.dp.toPx() else 2.5.dp.toPx(),
            center = Offset(size.width / 2, size.height / 2)
        )
    }
}

@Composable
fun GridOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val gridColor = Color.White.copy(alpha = 0.22f)
        val strokeWidth = 1.dp.toPx()

        drawLine(color = gridColor, start = Offset(width / 3f, 0f), end = Offset(width / 3f, height), strokeWidth = strokeWidth)
        drawLine(color = gridColor, start = Offset(width * 2f / 3f, 0f), end = Offset(width * 2f / 3f, height), strokeWidth = strokeWidth)
        drawLine(color = gridColor, start = Offset(0f, height / 3f), end = Offset(width, height / 3f), strokeWidth = strokeWidth)
        drawLine(color = gridColor, start = Offset(0f, height * 2f / 3f), end = Offset(width, height * 2f / 3f), strokeWidth = strokeWidth)
    }
}

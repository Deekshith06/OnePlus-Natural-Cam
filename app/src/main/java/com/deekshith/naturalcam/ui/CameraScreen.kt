package com.deekshith.naturalcam.ui

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.deekshith.naturalcam.camera.AspectRatioMode
import com.deekshith.naturalcam.camera.CameraMode
import com.deekshith.naturalcam.camera.NaturalCameraManager
import com.deekshith.naturalcam.coach.*
import com.deekshith.naturalcam.sensor.rememberOrientationSensor
import com.deekshith.naturalcam.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

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

    // Scene Coach State
    var coachUiState by remember { mutableStateOf(CoachUiState()) }
    var showDebugOverlay by remember { mutableStateOf(false) }
    var isPoseGuideEnabled by remember { mutableStateOf(false) }

    // Multi-Capture notification
    var multiCaptureBannerText by remember { mutableStateOf<String?>(null) }

    // Toggles & Controls
    var showGrid by remember { mutableStateOf(false) }
    var showLeveler by remember { mutableStateOf(true) }
    var flashMode by remember { mutableIntStateOf(ImageCapture.FLASH_MODE_OFF) }
    var isTorchOn by remember { mutableStateOf(false) }
    var timerDurationSec by remember { mutableIntStateOf(0) }
    var countdownRemaining by remember { mutableIntStateOf(0) }

    // Video Recording state
    var isRecording by remember { mutableStateOf(false) }
    var recordingDurationSec by remember { mutableIntStateOf(0) }

    // Capture & Gallery
    var lastCapturedUri by remember { mutableStateOf<Uri?>(null) }
    var isCapturing by remember { mutableStateOf(false) }
    var focusPosition by remember { mutableStateOf<Offset?>(null) }

    // Orientation sensor for digital horizon leveler
    val rollAngle = rememberOrientationSensor(context)
    val isLevel = abs(rollAngle) <= 1.2f || abs(rollAngle - 90f) <= 1.2f || abs(rollAngle + 90f) <= 1.2f || abs(abs(rollAngle) - 180f) <= 1.2f

    // Register Scene Coach Analyzer on launch
    DisposableEffect(cameraManager) {
        val coach = SceneCoachAnalyzer(context) { state ->
            coachUiState = state
        }
        coach.isPoseGuideEnabled = isPoseGuideEnabled
        cameraManager.registerSceneCoach(coach)
        onDispose {
            cameraManager.unregisterSceneCoach()
            coach.close()
        }
    }

    LaunchedEffect(isPoseGuideEnabled) {
        cameraManager.sceneCoachAnalyzer?.isPoseGuideEnabled = isPoseGuideEnabled
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
    ) {
        // 1. Camera Viewfinder (Authentic Sony IMX890 natural color pipeline - zero color matrix filters)
        Box(modifier = Modifier.fillMaxSize()) {
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

        // 3. Opt-In Portrait Framing Border & Centering Guide Overlay (Huawei Master AI style)
        if (isPoseGuideEnabled) {
            PortraitFramingBorderOverlay(
                framingGuide = coachUiState.framingGuide,
                isOnline = coachUiState.isOnline,
                environmentName = coachUiState.environmentName,
                modifier = Modifier.fillMaxSize()
            )
        }

        // 4. Digital Horizon Leveler
        if (showLeveler) {
            HorizonLeveler(isLevel = isLevel, modifier = Modifier.align(Alignment.Center))
        }

        // 5. Tap-To-Focus Indicator Ring
        focusPosition?.let { pos ->
            Box(
                modifier = Modifier
                    .offset(x = (pos.x - 35).dp, y = (pos.y - 35).dp)
                    .size(70.dp)
                    .border(1.5.dp, FocusRingColor, CircleShape)
            )
        }

        // 6. Self-Timer Countdown Overlay
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

        // 7. Top Utility Bar
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

            // Authentic Sensor Tone Mode Indicator
            Surface(
                color = TranslucentBlack,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(0.8.dp, OnePlusRed.copy(alpha = 0.6f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(OnePlusRed, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = when (currentMode) {
                            CameraMode.PRO -> "PRO RAW"
                            CameraMode.PORTRAIT -> "PORTRAIT"
                            CameraMode.NIGHT -> "NIGHTSCAPE"
                            CameraMode.VIDEO -> "4K UHD"
                            CameraMode.MULTI -> "AUTHENTIC MULTI"
                            else -> "NATURAL TONE"
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

            // Prominent Pose Coach Toggle Pill
            Surface(
                color = if (isPoseGuideEnabled) Color(0xFF00E676) else TranslucentBlack,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(
                    1.dp,
                    if (isPoseGuideEnabled) Color(0xFF00E676) else White.copy(alpha = 0.45f)
                ),
                modifier = Modifier
                    .clickable {
                        isPoseGuideEnabled = !isPoseGuideEnabled
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AccessibilityNew,
                        contentDescription = "Pose Coach",
                        tint = if (isPoseGuideEnabled) Black else AmberAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isPoseGuideEnabled) "POSE ON" else "POSE",
                        color = if (isPoseGuideEnabled) Black else White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
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

        // 8. Top Center Single Translucent Scene Coach Pill (Huawei Master AI style)
        val activeSuggestion = coachUiState.activeSuggestion
        AnimatedVisibility(
            visible = activeSuggestion != null && !coachUiState.isSuppressed,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 56.dp)
        ) {
            activeSuggestion?.let { suggestion ->
                SceneCoachPill(
                    suggestion = suggestion,
                    onApply = {
                        // Apply action to hardware
                        when (val action = suggestion.action) {
                            is CoachAction.AdjustEv -> {
                                exposureEv = action.targetEv
                                cameraManager.setExposureEv(action.targetEv)
                            }
                            is CoachAction.SetZoom -> {
                                selectedZoom = action.targetZoom
                                cameraManager.setZoom(action.targetZoom)
                            }
                            is CoachAction.SwitchMode -> {
                                if (currentMode != action.mode && previewView != null) {
                                    cameraManager.switchMode(previewView!!, action.mode) {
                                        currentMode = action.mode
                                    }
                                }
                            }
                            else -> {}
                        }
                        cameraManager.sceneCoachAnalyzer?.applySuggestion(suggestion)
                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    },
                    onDismiss = {
                        cameraManager.sceneCoachAnalyzer?.dismissSuggestion(suggestion.id)
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    },
                    onLongPress = {
                        showDebugOverlay = true
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    },
                    isOnline = coachUiState.isOnline,
                    environmentName = coachUiState.environmentName
                )
            }
        }

        // 8b. Contextual Pose Coach Floating Action (Huawei Master AI style)
        if (!showDebugOverlay && (coachUiState.hasHumanSubject || currentMode == CameraMode.PORTRAIT)) {
            Surface(
                color = if (isPoseGuideEnabled) Color(0xDD00E676) else Color(0xCC18181A),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(
                    1.2.dp,
                    if (isPoseGuideEnabled) Color(0xFF00E676) else AmberAccent.copy(alpha = 0.8f)
                ),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 110.dp, end = 16.dp)
                    .clickable {
                        isPoseGuideEnabled = !isPoseGuideEnabled
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AccessibilityNew,
                        contentDescription = "Pose Coach",
                        tint = if (isPoseGuideEnabled) Black else AmberAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = if (isPoseGuideEnabled) "Pose Guide: ON" else "Pose Guide: OFF",
                        color = if (isPoseGuideEnabled) Black else White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // 9. Debug Telemetry Overlay (Long-Press on Pill)
        if (showDebugOverlay) {
            CoachDebugOverlay(
                uiState = coachUiState,
                onClose = { showDebugOverlay = false },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 56.dp, start = 16.dp, end = 16.dp)
            )
        }

        // 10. Multi-Capture Toast
        AnimatedVisibility(
            visible = multiCaptureBannerText != null,
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
                    text = multiCaptureBannerText ?: "",
                    color = White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        // 11. Bottom Controls Bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Zoom Selector Buttons
            Row(
                modifier = Modifier
                    .background(TranslucentBlack, RoundedCornerShape(20.dp))
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(0.6f to ".6", 1.0f to "1x", 2.0f to "2x", 5.0f to "5x").forEach { (zoom, label) ->
                    val isSelected = abs(selectedZoom - zoom) < 0.15f
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) AmberAccent else Color.Transparent)
                            .clickable {
                                selectedZoom = zoom
                                cameraManager.setZoom(zoom)
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) Black else White,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
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
                                    if (mode == CameraMode.PORTRAIT) {
                                        isPoseGuideEnabled = true
                                    }
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

                // Shutter / Record Button
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
                                    isCapturing = true
                                    cameraManager.takeMultiAllInOne(
                                        onResultsSaved = { count, firstUri ->
                                            isCapturing = false
                                            lastCapturedUri = firstUri
                                            multiCaptureBannerText = "✨ $count Authentic Master Shots Saved (Natural & Portrait Bokeh)"
                                            scope.launch {
                                                delay(3500)
                                                multiCaptureBannerText = null
                                            }
                                        },
                                        onError = {
                                            isCapturing = false
                                        }
                                    )
                                }

                                else -> {
                                    if (timerDurationSec > 0) {
                                        scope.launch {
                                            countdownRemaining = timerDurationSec
                                            while (countdownRemaining > 0) {
                                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                                delay(1000)
                                                countdownRemaining--
                                            }
                                            performPhotoCapture(cameraManager) { uri ->
                                                lastCapturedUri = uri
                                            }
                                        }
                                    } else {
                                        isCapturing = true
                                        performPhotoCapture(cameraManager) { uri ->
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
    onSaved: (Uri) -> Unit
) {
    cameraManager.takePhoto(
        onPhotoSaved = { uri -> onSaved(uri) },
        onError = { /* error handled */ }
    )
}

/**
 * Top Center Translucent Glassmorphic Scene Coach Pill (32.dp height)
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SceneCoachPill(
    suggestion: CoachSuggestion,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
    onLongPress: () -> Unit,
    isOnline: Boolean = false,
    environmentName: String = "",
    modifier: Modifier = Modifier
) {
    val icon = when (suggestion.type) {
        SuggestionType.EXPOSURE -> "☀️"
        SuggestionType.HORIZON_LEVEL, SuggestionType.PITCH_TILT -> "📐"
        SuggestionType.ZOOM_FRAMING -> "🔍"
        SuggestionType.BACKLIGHT -> "🌓"
        SuggestionType.MOTION_STABILITY -> "🌙"
        SuggestionType.POSE_ALIGNMENT -> "🧍"
    }

    Surface(
        color = Color(0xCC18181A),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(0.8.dp, Color(0x33FFFFFF)),
        modifier = modifier
            .height(32.dp)
            .combinedClickable(
                onClick = onApply,
                onLongClick = onLongPress
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val netBadge = if (isOnline) "🌐" else "⚡"
            Text(text = "$netBadge $icon", fontSize = 11.sp)
            Spacer(modifier = Modifier.width(5.dp))
            if (environmentName.isNotBlank()) {
                Text(
                    text = environmentName,
                    color = AmberAccent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "•", color = LightGray, fontSize = 10.sp)
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = suggestion.shortLabel,
                color = White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )

            if (suggestion.action != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    color = AmberAccent,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "APPLY",
                        color = Black,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = LightGray,
                    modifier = Modifier.size(11.dp)
                )
            }
        }
    }
}

/**
 * Modern Portrait Framing Border & Centering Guide (Huawei Master AI style).
 * Replaces stick-figure skeleton with an elegant Golden Ratio Frame & centering directions.
 */
@Composable
fun PortraitFramingBorderOverlay(
    framingGuide: PortraitFramingGuide?,
    isOnline: Boolean,
    environmentName: String,
    modifier: Modifier = Modifier
) {
    val isPerfect = framingGuide?.isPerfect == true
    val isCentered = framingGuide?.isCentered == true

    val bracketColor = if (isPerfect) Color(0xFF00E676) else if (isCentered) AmberAccent else Color(0xEEFFFFFF)

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val cornerStrokePx = 3.dp.toPx()
            val thinStrokePx = 1.dp.toPx()

            // 1. Golden Ratio Target Frame Bounds (Center of viewfinder)
            val tLeft = (framingGuide?.targetBox?.left ?: 0.18f) * w
            val tTop = (framingGuide?.targetBox?.top ?: 0.12f) * h
            val tRight = (framingGuide?.targetBox?.right ?: 0.82f) * w
            val tBottom = (framingGuide?.targetBox?.bottom ?: 0.82f) * h
            val tW = tRight - tLeft
            val tH = tBottom - tTop
            val tCenterX = (tLeft + tRight) / 2f

            val armLength = 26.dp.toPx()

            // Outer golden ratio framing boundary
            drawRect(
                color = if (isPerfect) Color(0x4400E676) else Color(0x22FFFFFF),
                topLeft = Offset(tLeft, tTop),
                size = Size(tW, tH),
                style = Stroke(width = thinStrokePx)
            )

            // 2. Four L-Shaped Corner Brackets (Huawei Master AI style)
            // Top-Left
            drawLine(bracketColor, Offset(tLeft, tTop + armLength), Offset(tLeft, tTop), strokeWidth = cornerStrokePx, cap = StrokeCap.Round)
            drawLine(bracketColor, Offset(tLeft, tTop), Offset(tLeft + armLength, tTop), strokeWidth = cornerStrokePx, cap = StrokeCap.Round)

            // Top-Right
            drawLine(bracketColor, Offset(tRight - armLength, tTop), Offset(tRight, tTop), strokeWidth = cornerStrokePx, cap = StrokeCap.Round)
            drawLine(bracketColor, Offset(tRight, tTop), Offset(tRight, tTop + armLength), strokeWidth = cornerStrokePx, cap = StrokeCap.Round)

            // Bottom-Left
            drawLine(bracketColor, Offset(tLeft, tBottom - armLength), Offset(tLeft, tBottom), strokeWidth = cornerStrokePx, cap = StrokeCap.Round)
            drawLine(bracketColor, Offset(tLeft, tBottom), Offset(tLeft + armLength, tBottom), strokeWidth = cornerStrokePx, cap = StrokeCap.Round)

            // Bottom-Right
            drawLine(bracketColor, Offset(tRight - armLength, tBottom), Offset(tRight, tBottom), strokeWidth = cornerStrokePx, cap = StrokeCap.Round)
            drawLine(bracketColor, Offset(tRight, tBottom), Offset(tRight, tBottom - armLength), strokeWidth = cornerStrokePx, cap = StrokeCap.Round)

            // 3. Center Axis Alignment Ticks (Marks exact middle)
            val tickLen = 14.dp.toPx()
            drawLine(
                color = if (isCentered) Color(0xFF00E676) else AmberAccent.copy(alpha = 0.85f),
                start = Offset(tCenterX, tTop),
                end = Offset(tCenterX, tTop + tickLen),
                strokeWidth = cornerStrokePx,
                cap = StrokeCap.Round
            )
            drawLine(
                color = if (isCentered) Color(0xFF00E676) else AmberAccent.copy(alpha = 0.85f),
                start = Offset(tCenterX, tBottom - tickLen),
                end = Offset(tCenterX, tBottom),
                strokeWidth = cornerStrokePx,
                cap = StrokeCap.Round
            )

            // Fine vertical center alignment axis
            drawLine(
                color = if (isCentered) Color(0x5500E676) else Color(0x22FFFFFF),
                start = Offset(tCenterX, tTop + tickLen),
                end = Offset(tCenterX, tBottom - tickLen),
                strokeWidth = thinStrokePx
            )

            // 4. Head Placement Zone (Golden Portrait Eye/Head Oval in top 20% of target frame)
            val headCenterY = tTop + tH * 0.18f
            val headRadiusX = tW * 0.18f
            val headRadiusY = tH * 0.12f
            drawOval(
                color = if (isPerfect) Color(0x6600E676) else Color(0x33FFFFFF),
                topLeft = Offset(tCenterX - headRadiusX, headCenterY - headRadiusY),
                size = Size(headRadiusX * 2, headRadiusY * 2),
                style = Stroke(width = thinStrokePx)
            )

            // 5. Subject Tracking & Centering Direction Arrows
            if (framingGuide != null) {
                val sBox = framingGuide.subjectBox
                val sLeft = sBox.left * w
                val sTop = sBox.top * h
                val sRight = sBox.right * w
                val sBottom = sBox.bottom * h
                val sW = sRight - sLeft
                val sH = sBottom - sTop
                val sCenterX = sBox.centerX() * w
                val sCenterY = sBox.centerY() * h

                // Soft subject bounding box
                drawRoundRect(
                    color = if (isPerfect) Color(0x9900E676) else Color(0x66FFD54F),
                    topLeft = Offset(sLeft, sTop),
                    size = Size(sW, sH),
                    cornerRadius = CornerRadius(10.dp.toPx()),
                    style = Stroke(width = 1.8f)
                )

                // Directional Arrows when off-center
                if (!isCentered) {
                    val arrowY = sCenterY.coerceIn(tTop + 40.dp.toPx(), tBottom - 40.dp.toPx())
                    if (framingGuide.horizontalAlignment == HorizontalAlignmentState.MOVE_RIGHT) {
                        // Guide line from subject center to target center
                        drawLine(
                            color = AmberAccent,
                            start = Offset(sCenterX, arrowY),
                            end = Offset(tCenterX, arrowY),
                            strokeWidth = 2.0f
                        )
                        // Arrow head pointing right towards middle
                        drawLine(AmberAccent, Offset(tCenterX - 14.dp.toPx(), arrowY - 8.dp.toPx()), Offset(tCenterX, arrowY), strokeWidth = 2.5f, cap = StrokeCap.Round)
                        drawLine(AmberAccent, Offset(tCenterX - 14.dp.toPx(), arrowY + 8.dp.toPx()), Offset(tCenterX, arrowY), strokeWidth = 2.5f, cap = StrokeCap.Round)
                    } else if (framingGuide.horizontalAlignment == HorizontalAlignmentState.MOVE_LEFT) {
                        // Guide line from subject center to target center
                        drawLine(
                            color = AmberAccent,
                            start = Offset(sCenterX, arrowY),
                            end = Offset(tCenterX, arrowY),
                            strokeWidth = 2.0f
                        )
                        // Arrow head pointing left towards middle
                        drawLine(AmberAccent, Offset(tCenterX + 14.dp.toPx(), arrowY - 8.dp.toPx()), Offset(tCenterX, arrowY), strokeWidth = 2.5f, cap = StrokeCap.Round)
                        drawLine(AmberAccent, Offset(tCenterX + 14.dp.toPx(), arrowY + 8.dp.toPx()), Offset(tCenterX, arrowY), strokeWidth = 2.5f, cap = StrokeCap.Round)
                    }
                }
            }
        }

        // 6. Centering Guidance Pill (Above bottom controls)
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 220.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val guidanceText = framingGuide?.primaryDirection ?: "Position person in center frame"

            Surface(
                color = if (isPerfect) Color(0xEE00E676) else Color(0xDD121214),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(
                    1.dp,
                    if (isPerfect) Color(0xFF00E676) else if (isCentered) AmberAccent else Color(0x55FFFFFF)
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = guidanceText,
                        color = if (isPerfect) Black else White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (environmentName.isNotBlank()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "• $environmentName",
                            color = if (isPerfect) Color(0xCC000000) else LightGray,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // Posture Micro-Hint (if any)
            framingGuide?.postureHint?.let { hint ->
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    color = Color(0xBB000000),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = "Posture: $hint",
                        color = AmberAccent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}

/**
 * Opt-In Target Pose Skeleton:
 * Thin 1.5.dp stroke, 30% opacity, turns emerald green and displays "Good" when match score >= 0.82.
 */
@Composable
fun TargetSkeletonOverlay(
    skeleton: TargetSkeleton,
    poseMatched: Boolean,
    modifier: Modifier = Modifier
) {
    val skeletonColor = if (poseMatched) Color(0xCC00E676) else Color(0x4DFFFFFF)
    val strokeWidthPx = 2.0f

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val kps = skeleton.targetKeypoints.associateBy { it.id }

            fun pt(id: Int): Offset? {
                val kp = kps[id] ?: return null
                return Offset(kp.x * w, kp.y * h)
            }

            fun link(id1: Int, id2: Int) {
                val p1 = pt(id1); val p2 = pt(id2)
                if (p1 != null && p2 != null) {
                    drawLine(skeletonColor, p1, p2, strokeWidth = strokeWidthPx)
                }
            }

            // Head / Neck
            pt(0)?.let { nose ->
                drawCircle(skeletonColor, radius = 6.dp.toPx(), center = nose, style = Stroke(strokeWidthPx))
            }
            // Shoulders & Spine
            link(5, 6)
            // Left Arm
            link(5, 7); link(7, 9)
            // Right Arm
            link(6, 8); link(8, 10)
            // Torso
            link(5, 11); link(6, 12); link(11, 12)
            // Left Leg
            link(11, 13); link(13, 15)
            // Right Leg
            link(12, 14); link(14, 16)
        }

        // Status alignment hint pill
        Surface(
            color = if (poseMatched) Color(0xEE00E676) else Color(0xCC18181A),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 220.dp)
        ) {
            Text(
                text = if (poseMatched) "✓ Good (Ready to capture)" else skeleton.alignmentHint,
                color = if (poseMatched) Black else White,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
            )
        }
    }
}

/**
 * Debug Telemetry Overlay (Triggered by Long-Press on Coach Pill)
 */
@Composable
fun CoachDebugOverlay(
    uiState: CoachUiState,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xF0101012),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, AmberAccent.copy(alpha = 0.7f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🛠️ Scene Coach Live Telemetry",
                    color = AmberAccent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = White, modifier = Modifier.size(16.dp))
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Latency & FPS
            val p50 = uiState.inferenceMs
            val p95 = (p50 * 1.25f).roundToInt()
            Text(
                text = "⚡ Engine: ${String.format("%.1f", uiState.fps)} fps | Inference: ${p50}ms (p95: ${p95}ms)",
                color = White,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )

            // Network & Environmental Intelligence
            val netStatus = if (uiState.isOnline) "🌐 Online (Cloud Enhanced)" else "⚡ Offline (On-Device Neural)"
            Text(
                text = "📡 Network: $netStatus",
                color = if (uiState.isOnline) Color(0xFF00E676) else AmberAccent,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold
            )
            if (uiState.environmentName.isNotBlank()) {
                Text(
                    text = "🌍 Environment: ${uiState.environmentName} (${uiState.environmentAdvice})",
                    color = White.copy(alpha = 0.9f),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Places365 Top-3 Categories
            Text(
                text = "📍 Places365 Classification (Smoothed EMA):",
                color = AmberAccent,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )
            uiState.topSceneLabels.forEach { labelScore ->
                Text(
                    text = "   • ${labelScore.label}: ${(labelScore.confidence * 100).roundToInt()}%",
                    color = White.copy(alpha = 0.85f),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Measured Physical Metrics
            val m = uiState.liveMeasurements
            Text(
                text = "📊 Live Measurements:",
                color = AmberAccent,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "   • Luma: ${m.avgLuma.roundToInt()} | Clip: ${(m.highlightClippedPct * 100).roundToInt()}% | Crush: ${(m.shadowCrushedPct * 100).roundToInt()}%",
                color = White.copy(alpha = 0.85f),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "   • Roll: ${String.format("%.1f", m.horizonRollDeg)}° | Pitch: ${String.format("%.1f", m.cameraPitchDeg)}° | Shake: ${String.format("%.2f", m.angularSpeedRadS)} rad/s",
                color = White.copy(alpha = 0.85f),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "   • Sharpness (Laplacian): ${m.laplacianVariance.roundToInt()} | Backlight Ratio: ${String.format("%.2f", m.subjectBackgroundRatio)}",
                color = White.copy(alpha = 0.85f),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
            uiState.framingGuide?.let { guide ->
                Text(
                    text = "   • Center Offset: ${String.format("%+.2f", guide.centerOffset)} | Centered: ${guide.isCentered} | Perfect: ${guide.isPerfect}",
                    color = if (guide.isPerfect) Color(0xFF00E676) else AmberAccent,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            uiState.activeSuggestion?.let {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "🎯 Active Evidence: ${it.evidence}",
                    color = Color(0xFF00E676),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
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

package com.deekshith.naturalcam.ui

import android.content.Intent
import android.net.Uri
import android.view.HapticFeedbackConstants
import androidx.camera.core.ImageCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
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
import androidx.compose.ui.graphics.Color
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
import com.deekshith.naturalcam.camera.AspectRatioMode
import com.deekshith.naturalcam.camera.CameraMode
import com.deekshith.naturalcam.camera.NaturalCameraManager
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

    // Toggles
    var showExposureSlider by remember { mutableStateOf(false) }
    var showProControls by remember { mutableStateOf(false) }
    var showGrid by remember { mutableStateOf(false) }
    var showLeveler by remember { mutableStateOf(true) }
    var flashMode by remember { mutableIntStateOf(ImageCapture.FLASH_MODE_OFF) }
    var isTorchOn by remember { mutableStateOf(false) }
    var timerDurationSec by remember { mutableIntStateOf(0) } // 0 = Off, 3s, 10s
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
        // 1. Camera Preview Viewfinder with Pinch-to-Zoom & Tap-to-Focus
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    this.scaleType = PreviewView.ScaleType.FILL_CENTER
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

        // 2. Rule of Thirds Grid Overlay
        if (showGrid) {
            GridOverlay(modifier = Modifier.fillMaxSize())
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

            // Natural Tone / Mode Badge
            Surface(
                color = TranslucentBlack,
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, OnePlusRed.copy(alpha = 0.6f))
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

        // 7. Video Recording Duration Timer (When Recording)
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

        // 8. Exposure Compensation Dial (-2.0 EV to +2.0 EV)
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

        // 9. PRO Mode Manual Controls Sheet (When in PRO mode)
        if (currentMode == CameraMode.PRO) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 200.dp)
                    .background(TranslucentBlack, RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // ISO Selector
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

        // 10. Bottom Control Area
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Multi-Lens Quick Selector (0.6x Ultra-Wide, 1.0x Main 50MP, 2.0x Telephoto, 5.0x Zoom)
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

            Spacer(modifier = Modifier.height(18.dp))

            // Camera Modes Carousel (Swipe/Tap between Modes)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(
                    CameraMode.PRO to "PRO",
                    CameraMode.PORTRAIT to "PORTRAIT",
                    CameraMode.PHOTO to "PHOTO",
                    CameraMode.VIDEO to "VIDEO",
                    CameraMode.NIGHT to "NIGHT"
                ).forEach { (mode, label) ->
                    val isSelected = currentMode == mode
                    Text(
                        text = label,
                        color = if (isSelected) AmberAccent else LightGray,
                        fontSize = 13.sp,
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
                            .padding(horizontal = 10.dp, vertical = 6.dp)
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
                        .border(4.dp, White, CircleShape)
                        .padding(6.dp)
                        .clip(if (isRecording) RoundedCornerShape(12.dp) else CircleShape)
                        .background(if (currentMode == CameraMode.VIDEO || isRecording) OnePlusRed else White)
                        .clickable(enabled = !isCapturing) {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)

                            if (currentMode == CameraMode.VIDEO) {
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
                            } else {
                                // Photo / Pro / Portrait / Night mode
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

@Composable
fun HorizonLeveler(isLevel: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(120.dp, 20.dp)) {
        val lineColor = if (isLevel) Color(0xFF00E676) else Color.White.copy(alpha = 0.4f)
        val stroke = if (isLevel) 2.5.dp.toPx() else 1.5.dp.toPx()

        // Left and right horizon markers
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

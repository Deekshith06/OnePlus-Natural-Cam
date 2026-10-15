package com.deekshith.naturalcam.ui

import android.content.Intent
import android.net.Uri
import android.view.HapticFeedbackConstants
import androidx.camera.core.ImageCapture
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.deekshith.naturalcam.camera.NaturalCameraManager
import com.deekshith.naturalcam.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun CameraScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    val cameraManager = remember { NaturalCameraManager(context, lifecycleOwner) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }

    var selectedZoom by remember { mutableFloatStateOf(1.0f) }
    var exposureEv by remember { mutableFloatStateOf(-0.3f) }
    var showExposureSlider by remember { mutableStateOf(false) }
    var showGrid by remember { mutableStateOf(false) }
    var flashMode by remember { mutableIntStateOf(ImageCapture.FLASH_MODE_OFF) }

    var lastCapturedUri by remember { mutableStateOf<Uri?>(null) }
    var isCapturing by remember { mutableStateOf(false) }

    // Tap-to-focus indicator
    var focusPosition by remember { mutableStateOf<Offset?>(null) }

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
        // 1. Camera Preview Viewfinder
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    this.scaleType = PreviewView.ScaleType.FILL_CENTER
                    previewView = this
                    cameraManager.startCamera(
                        previewView = this,
                        onCameraReady = { /* ready */ },
                        onError = { /* handle error */ }
                    )
                }
            },
            modifier = Modifier
                .fillMaxSize()
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

        // 3. Focus Indicator
        focusPosition?.let { pos ->
            Box(
                modifier = Modifier
                    .offset(x = (pos.x - 35).dp, y = (pos.y - 35).dp)
                    .size(70.dp)
                    .border(1.5.dp, FocusRingColor, CircleShape)
            )
        }

        // 4. Top Controls Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Flash Toggle
            IconButton(
                onClick = {
                    flashMode = when (flashMode) {
                        ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON
                        ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO
                        else -> ImageCapture.FLASH_MODE_OFF
                    }
                    cameraManager.setFlashMode(flashMode)
                },
                modifier = Modifier
                    .background(TranslucentBlack, CircleShape)
                    .size(42.dp)
            ) {
                Icon(
                    imageVector = when (flashMode) {
                        ImageCapture.FLASH_MODE_ON -> Icons.Default.FlashOn
                        ImageCapture.FLASH_MODE_AUTO -> Icons.Default.FlashAuto
                        else -> Icons.Default.FlashOff
                    },
                    contentDescription = "Flash",
                    tint = if (flashMode == ImageCapture.FLASH_MODE_ON) AmberAccent else White
                )
            }

            // Natural Mode Badge
            Surface(
                color = TranslucentBlack,
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, OnePlusRed.copy(alpha = 0.6f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(OnePlusRed, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "NATURAL TONE",
                        color = White,
                        fontSize = 11.sp,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            // Grid Toggle
            IconButton(
                onClick = { showGrid = !showGrid },
                modifier = Modifier
                    .background(TranslucentBlack, CircleShape)
                    .size(42.dp)
            ) {
                Icon(
                    imageVector = if (showGrid) Icons.Default.GridOn else Icons.Default.GridOff,
                    contentDescription = "Grid",
                    tint = if (showGrid) AmberAccent else White
                )
            }
        }

        // 5. Exposure Offset Slider (Tap EV badge to toggle)
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = { showExposureSlider = !showExposureSlider },
                    modifier = Modifier
                        .background(TranslucentBlack, CircleShape)
                        .size(44.dp)
                ) {
                    Text(
                        text = "${if (exposureEv > 0) "+" else ""}${String.format("%.1f", exposureEv)}",
                        color = AmberAccent,
                        fontSize = 12.sp
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
                            .width(50.dp)
                            .height(180.dp)
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
                                .padding(vertical = 12.dp)
                        )
                    }
                }
            }
        }

        // 6. Bottom Control Area
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Lens Selector (0.6x Ultra-Wide, 1.0x Main 50MP, 2.0x Telephoto)
            Row(
                modifier = Modifier
                    .background(TranslucentBlack, RoundedCornerShape(20.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(0.6f, 1.0f, 2.0f).forEach { zoom ->
                    val isSelected = selectedZoom == zoom
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (isSelected) OnePlusRed else Color.Transparent)
                            .clickable {
                                selectedZoom = zoom
                                cameraManager.setZoom(zoom)
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (zoom == 0.6f) "0.6x" else if (zoom == 1.0f) "1x" else "2x",
                            color = White,
                            fontSize = 12.sp,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Shutter Button Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 36.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Recent Gallery Preview Thumbnail
                Box(
                    modifier = Modifier
                        .size(54.dp)
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

                // Shutter Button
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .border(4.dp, White, CircleShape)
                        .padding(6.dp)
                        .clip(CircleShape)
                        .background(if (isCapturing) AmberAccent else White)
                        .clickable(enabled = !isCapturing) {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            isCapturing = true
                            cameraManager.takePhoto(
                                onPhotoSaved = { uri ->
                                    isCapturing = false
                                    lastCapturedUri = uri
                                },
                                onError = {
                                    isCapturing = false
                                }
                            )
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
                        .size(54.dp)
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

@Composable
fun GridOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height

        val gridColor = Color.White.copy(alpha = 0.25f)
        val strokeWidth = 1.dp.toPx()

        // Vertical lines (1/3 and 2/3)
        drawLine(
            color = gridColor,
            start = Offset(width / 3f, 0f),
            end = Offset(width / 3f, height),
            strokeWidth = strokeWidth
        )
        drawLine(
            color = gridColor,
            start = Offset(width * 2f / 3f, 0f),
            end = Offset(width * 2f / 3f, height),
            strokeWidth = strokeWidth
        )

        // Horizontal lines (1/3 and 2/3)
        drawLine(
            color = gridColor,
            start = Offset(0f, height / 3f),
            end = Offset(width, height / 3f),
            strokeWidth = strokeWidth
        )
        drawLine(
            color = gridColor,
            start = Offset(0f, height * 2f / 3f),
            end = Offset(width, height * 2f / 3f),
            strokeWidth = strokeWidth
        )
    }
}

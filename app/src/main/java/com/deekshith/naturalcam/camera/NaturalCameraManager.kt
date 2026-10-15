package com.deekshith.naturalcam.camera

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.hardware.camera2.CaptureRequest
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.video.VideoCapture
import androidx.camera.view.PreviewView
import com.deekshith.naturalcam.filter.ForestDslrFilter
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import androidx.lifecycle.LifecycleOwner
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

enum class CameraMode {
    PHOTO,
    VIDEO,
    PRO,
    PORTRAIT,
    NIGHT,
    MULTI
}

enum class AspectRatioMode {
    RATIO_4_3,
    RATIO_16_9,
    RATIO_1_1,
    RATIO_FULL
}

class NaturalCameraManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner
) {
    private var cameraProvider: ProcessCameraProvider? = null
    var camera: Camera? = null
        private set

    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null

    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    var currentLensFacing: Int = CameraSelector.LENS_FACING_BACK
        private set

    var currentMode: CameraMode = CameraMode.PHOTO
        private set

    var currentFlashMode: Int = ImageCapture.FLASH_MODE_OFF
        private set

    var isTorchEnabled: Boolean = false
        private set

    // Pro settings
    var manualIso: Int? = null
        private set
    var manualShutterSpeedNanos: Long? = null
        private set
    var manualFocusDistance: Float? = null
        private set

    private var previewViewRef: PreviewView? = null

    @OptIn(ExperimentalCamera2Interop::class)
    fun startCamera(
        previewView: PreviewView,
        mode: CameraMode = currentMode,
        aspectRatio: AspectRatioMode = AspectRatioMode.RATIO_4_3,
        onCameraReady: (CameraInfo) -> Unit = {},
        onError: (Exception) -> Unit = {}
    ) {
        previewViewRef = previewView
        currentMode = mode
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()

                // Target aspect ratio for CameraX
                val targetAspectRatio = when (aspectRatio) {
                    AspectRatioMode.RATIO_16_9, AspectRatioMode.RATIO_FULL -> AspectRatio.RATIO_16_9
                    else -> AspectRatio.RATIO_4_3
                }

                // 1. Preview Use Case with Natural Tone Camera2 Interop (NO AI Bleaching)
                val previewBuilder = Preview.Builder()
                    .setTargetAspectRatio(targetAspectRatio)
                val previewExtender = Camera2Interop.Extender(previewBuilder)
                configureNaturalToneCapture(previewExtender, mode)

                preview = previewBuilder.build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                // 2. Photo Capture Use Case
                val captureBuilder = ImageCapture.Builder()
                    .setTargetAspectRatio(targetAspectRatio)
                    .setCaptureMode(
                        if (mode == CameraMode.NIGHT) ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
                        else ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
                    )
                    .setFlashMode(currentFlashMode)

                val captureExtender = Camera2Interop.Extender(captureBuilder)
                configureNaturalToneCapture(captureExtender, mode)
                imageCapture = captureBuilder.build()

                // 3. Video Capture Use Case (4K UHD / 1080p FHD Quality)
                val qualitySelector = QualitySelector.fromOrderedList(
                    listOf(Quality.UHD, Quality.FHD, Quality.HD),
                    FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
                )
                val recorder = Recorder.Builder()
                    .setQualitySelector(qualitySelector)
                    .setExecutor(cameraExecutor)
                    .build()
                videoCapture = VideoCapture.withOutput(recorder)

                // 4. On-Device Offline AI Scene Analysis Use Case
                val imageAnalysis = ImageAnalysis.Builder()
                    .setTargetAspectRatio(targetAspectRatio)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                aiDirector?.let { director ->
                    imageAnalysis.setAnalyzer(cameraExecutor, director)
                }

                // 5. Bind Use Cases to Lifecycle
                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(currentLensFacing)
                    .build()

                cameraProvider?.unbindAll()

                camera = if (mode == CameraMode.VIDEO) {
                    cameraProvider?.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        videoCapture
                    )
                } else {
                    if (aiDirector != null) {
                        cameraProvider?.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageCapture,
                            imageAnalysis
                        )
                    } else {
                        cameraProvider?.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageCapture
                        )
                    }
                }

                camera?.cameraInfo?.let { onCameraReady(it) }

                // Default -0.3 EV highlight bias
                applyInitialExposureBias()

            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
                onError(exc)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    @OptIn(ExperimentalCamera2Interop::class)
    private fun <T> configureNaturalToneCapture(extender: Camera2Interop.Extender<T>, mode: CameraMode) {
        // 1. Force Disable AI Scene Boost (Kills the whitening engine)
        extender.setCaptureRequestOption(
            CaptureRequest.CONTROL_SCENE_MODE,
            CaptureRequest.CONTROL_SCENE_MODE_DISABLED
        )

        // 2. Enforce Natural Edge rendering (No digital white sharpening halos)
        extender.setCaptureRequestOption(
            CaptureRequest.EDGE_MODE,
            CaptureRequest.EDGE_MODE_HIGH_QUALITY
        )

        // 3. Enforce Natural Tone mapping (Prevents lifting shadows into pale haze)
        extender.setCaptureRequestOption(
            CaptureRequest.TONEMAP_MODE,
            CaptureRequest.TONEMAP_MODE_HIGH_QUALITY
        )

        // 4. Natural Color Correction & High Quality Denoising
        extender.setCaptureRequestOption(
            CaptureRequest.COLOR_CORRECTION_MODE,
            CaptureRequest.COLOR_CORRECTION_MODE_HIGH_QUALITY
        )
        extender.setCaptureRequestOption(
            CaptureRequest.NOISE_REDUCTION_MODE,
            CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY
        )

        // 5. Mode-Specific Tuning
        if (mode == CameraMode.NIGHT) {
            // Nightscape: Long integration with natural contrast
            extender.setCaptureRequestOption(
                CaptureRequest.CONTROL_AE_ANTIBANDING_MODE,
                CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_AUTO
            )
        } else {
            extender.setCaptureRequestOption(
                CaptureRequest.CONTROL_MODE,
                CaptureRequest.CONTROL_MODE_AUTO
            )
        }
    }

    private fun applyInitialExposureBias() {
        val cameraInfo = camera?.cameraInfo ?: return
        val range = cameraInfo.exposureState.exposureCompensationRange
        val step = cameraInfo.exposureState.exposureCompensationStep.toFloat()

        if (step > 0f) {
            val targetIndex = (-0.33f / step).toInt().coerceIn(range.lower, range.upper)
            camera?.cameraControl?.setExposureCompensationIndex(targetIndex)
        }
    }

    fun setExposureEv(evOffset: Float) {
        val cameraInfo = camera?.cameraInfo ?: return
        val range = cameraInfo.exposureState.exposureCompensationRange
        val step = cameraInfo.exposureState.exposureCompensationStep.toFloat()

        if (step > 0f) {
            val targetIndex = (evOffset / step).toInt().coerceIn(range.lower, range.upper)
            camera?.cameraControl?.setExposureCompensationIndex(targetIndex)
        }
    }

    fun setZoom(zoomRatio: Float) {
        camera?.cameraControl?.setZoomRatio(zoomRatio.coerceIn(0.6f, 10.0f))
    }

    fun setFlashMode(mode: Int) {
        currentFlashMode = mode
        imageCapture?.flashMode = mode
    }

    fun toggleTorch(enabled: Boolean) {
        isTorchEnabled = enabled
        camera?.cameraControl?.enableTorch(enabled)
    }

    fun switchCamera(previewView: PreviewView, onComplete: () -> Unit = {}) {
        currentLensFacing = if (currentLensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
        startCamera(previewView, currentMode, onCameraReady = { onComplete() })
    }

    fun switchMode(previewView: PreviewView, mode: CameraMode, onComplete: () -> Unit = {}) {
        if (currentMode == mode) return
        currentMode = mode
        startCamera(previewView, mode, onCameraReady = { onComplete() })
    }

    fun focusOnPoint(meteringPoint: MeteringPoint) {
        val action = FocusMeteringAction.Builder(
            meteringPoint,
            FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
        )
            .setAutoCancelDuration(3, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        camera?.cameraControl?.startFocusAndMetering(action)
    }

    /**
     * Pro Mode Manual Controls using Camera2 Interop CaptureRequestOptions
     */
    @OptIn(ExperimentalCamera2Interop::class)
    fun setProSettings(
        iso: Int? = manualIso,
        shutterSpeedNanos: Long? = manualShutterSpeedNanos,
        focusDistance: Float? = manualFocusDistance
    ) {
        manualIso = iso
        manualShutterSpeedNanos = shutterSpeedNanos
        manualFocusDistance = focusDistance

        val cameraControl = camera?.cameraControl ?: return
        val builder = CaptureRequestOptions.Builder()

        // Manual ISO & Shutter Speed
        if (iso != null || shutterSpeedNanos != null) {
            builder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
            iso?.let { builder.setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, it) }
            shutterSpeedNanos?.let { builder.setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, it) }
        } else {
            builder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
        }

        // Manual Focus
        if (focusDistance != null) {
            builder.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
            builder.setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE, focusDistance)
        } else {
            builder.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
        }

        Camera2CameraControl.from(cameraControl).setCaptureRequestOptions(builder.build())
    }

    fun takePhoto(
        filter: com.deekshith.naturalcam.filter.ForestDslrFilter = com.deekshith.naturalcam.filter.ForestDslrFilter.NATURAL,
        onPhotoSaved: (Uri) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val imageCapture = this.imageCapture ?: return

        if (filter == com.deekshith.naturalcam.filter.ForestDslrFilter.NATURAL) {
            val name = "IMG_" + SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(System.currentTimeMillis())
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/NaturalCam")
                }
            }

            val outputOptions = ImageCapture.OutputFileOptions.Builder(
                context.contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            ).build()

            imageCapture.takePicture(
                outputOptions,
                cameraExecutor,
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        val savedUri = output.savedUri ?: Uri.EMPTY
                        Log.d(TAG, "Photo saved: $savedUri")
                        onPhotoSaved(savedUri)
                    }

                    override fun onError(exc: ImageCaptureException) {
                        Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                        onError(exc)
                    }
                }
            )
        } else {
            // Apply DSLR Forest Filter Matrix
            imageCapture.takePicture(
                cameraExecutor,
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        try {
                            val bitmap = image.toBitmap()
                            val filteredBitmap = filter.applyToBitmap(bitmap)
                            val savedUri = saveBitmapToGallery(filteredBitmap)
                            onPhotoSaved(savedUri)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error applying filter: ${e.message}", e)
                            onError(e)
                        } finally {
                            image.close()
                        }
                    }

                    override fun onError(exc: ImageCaptureException) {
                        Log.e(TAG, "Capture failed: ${exc.message}", exc)
                        onError(exc)
                    }
                }
            )
        }
    }

    var aiDirector: ImageAnalysis.Analyzer? = null

    fun registerAiDirector(director: ImageAnalysis.Analyzer) {
        aiDirector = director
    }

    /**
     * Huawei XMAGE / Master All-In-One Multi-Capture:
     * One single shutter press outputs 8 distinct master shots simultaneously:
     * 1) Authentic Natural (Sony IMX890, zero bleach)
     * 2) High-Resolution Full-Fidelity 50MP Ultra-Detail
     * 3) Optical Portrait Bokeh (f/1.4 background depth-of-field)
     * 4) Black Pro-Mist 1/4 (Optical highlight bloom with ZERO color shift)
     * 5) CPL Polarizer (Glare reduction & foliage contrast with 100% natural sensor colors)
     * 6) Emerald Pine Forest DSLR Grade
     * 7) Golden Canopy Autumn Sunbeam Grade
     * 8) Leica Noir Deep Monochrome Grade
     */
    fun takeMultiAllInOne(
        onResultsSaved: (count: Int, firstUri: Uri) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val imageCapture = this.imageCapture ?: return

        imageCapture.takePicture(
            cameraExecutor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    try {
                        val baseBitmap = image.toBitmap()
                        var firstUri: Uri? = null

                        // 1. Normal Natural Shot (Pure Sony IMX890 sensor)
                        val normalUri = saveBitmapToGallery(baseBitmap, prefix = "1_NATURAL")
                        firstUri = normalUri

                        // 2. High-Resolution Full-Fidelity 50MP Shot
                        val highResBmp = com.deekshith.naturalcam.filter.ForestDslrFilter.createHighResDetailMaster(baseBitmap)
                        saveBitmapToGallery(highResBmp, prefix = "2_HIGH_RES")
                        if (highResBmp != baseBitmap) highResBmp.recycle()

                        // 3. Optical Portrait Bokeh (Creamy f/1.4 Depth of Field)
                        val portraitBmp = com.deekshith.naturalcam.effect.PortraitBokehProcessor.createPortraitBokeh(baseBitmap)
                        saveBitmapToGallery(portraitBmp, prefix = "3_PORTRAIT_BOKEH")
                        portraitBmp.recycle()

                        // 4. Black Pro-Mist 1/4 (Optical bloom, zero color shift)
                        val proMistBmp = ForestDslrFilter.BLACK_PRO_MIST.applyToBitmap(baseBitmap)
                        saveBitmapToGallery(proMistBmp, prefix = "4_PRO_MIST_BLOOM")
                        if (proMistBmp != baseBitmap) proMistBmp.recycle()

                        // 5. CPL Polarizer (Anti-glare, untouched sensor color)
                        val cplBmp = ForestDslrFilter.CPL_POLARIZER.applyToBitmap(baseBitmap)
                        saveBitmapToGallery(cplBmp, prefix = "5_CPL_POLARIZER")
                        if (cplBmp != baseBitmap) cplBmp.recycle()

                        // 6. Emerald Pine Forest DSLR Edit
                        val emeraldBmp = ForestDslrFilter.EMERALD_FOREST.applyToBitmap(baseBitmap)
                        saveBitmapToGallery(emeraldBmp, prefix = "6_EMERALD_FOREST")
                        if (emeraldBmp != baseBitmap) emeraldBmp.recycle()

                        // 7. Golden Canopy Autumn Sunbeam Edit
                        val goldenBmp = ForestDslrFilter.GOLDEN_CANOPY.applyToBitmap(baseBitmap)
                        saveBitmapToGallery(goldenBmp, prefix = "7_GOLDEN_CANOPY")
                        if (goldenBmp != baseBitmap) goldenBmp.recycle()

                        // 8. Leica Noir Monochrome Edit
                        val leicaBmp = ForestDslrFilter.LEICA_NOIR.applyToBitmap(baseBitmap)
                        saveBitmapToGallery(leicaBmp, prefix = "8_LEICA_NOIR")
                        if (leicaBmp != baseBitmap) leicaBmp.recycle()

                        ContextCompat.getMainExecutor(context).execute {
                            onResultsSaved(8, firstUri ?: normalUri)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Multi all-in-one capture failed: ${e.message}", e)
                        ContextCompat.getMainExecutor(context).execute { onError(e) }
                    } finally {
                        image.close()
                    }
                }

                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Multi-style capture failed: ${exc.message}", exc)
                    ContextCompat.getMainExecutor(context).execute { onError(exc) }
                }
            }
        )
    }

    private fun saveBitmapToGallery(bitmap: android.graphics.Bitmap, prefix: String = "IMG_DSLR"): Uri {
        val name = "${prefix}_" + SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/NaturalCam")
            }
        }

        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            ?: throw IllegalStateException("Failed to create MediaStore entry")

        context.contentResolver.openOutputStream(uri)?.use { stream ->
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, stream)
        }
        return uri
    }

    @SuppressLint("MissingPermission")
    fun startVideoRecording(
        onVideoEvent: (VideoRecordEvent) -> Unit
    ) {
        val videoCapture = this.videoCapture ?: return
        if (activeRecording != null) return

        val name = "VID_" + SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "DCIM/NaturalCam")
            }
        }

        val mediaStoreOutput = MediaStoreOutputOptions.Builder(
            context.contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        )
            .setContentValues(contentValues)
            .build()

        activeRecording = videoCapture.output
            .prepareRecording(context, mediaStoreOutput)
            .withAudioEnabled()
            .start(ContextCompat.getMainExecutor(context), Consumer { recordEvent ->
                onVideoEvent(recordEvent)
                if (recordEvent is VideoRecordEvent.Finalize) {
                    activeRecording = null
                }
            })
    }

    fun stopVideoRecording() {
        activeRecording?.stop()
        activeRecording = null
    }

    fun isRecordingVideo(): Boolean = activeRecording != null

    fun shutdown() {
        activeRecording?.stop()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "NaturalCameraManager"
        private const val FILENAME_FORMAT = "yyyyMMdd_HHmmss"
    }
}

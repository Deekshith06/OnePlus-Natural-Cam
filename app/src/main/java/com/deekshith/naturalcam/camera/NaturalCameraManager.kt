package com.deekshith.naturalcam.camera

import android.content.ContentValues
import android.content.Context
import android.hardware.camera2.CaptureRequest
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class NaturalCameraManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner
) {
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    var currentLensFacing: Int = CameraSelector.LENS_FACING_BACK
        private set

    var currentFlashMode: Int = ImageCapture.FLASH_MODE_OFF
        private set

    private var exposureIndex: Int = 0

    @OptIn(ExperimentalCamera2Interop::class)
    fun startCamera(
        previewView: PreviewView,
        onCameraReady: (CameraInfo) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()

                // 1. Build Preview with Natural Tone Camera2 Interop (NO AI Bleaching)
                val previewBuilder = Preview.Builder()
                val previewExtender = Camera2Interop.Extender(previewBuilder)
                configureNaturalToneCapture(previewExtender)

                preview = previewBuilder.build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                // 2. Build ImageCapture with Zero-Lag & Clean Hardware Pipeline
                val captureBuilder = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setFlashMode(currentFlashMode)

                val captureExtender = Camera2Interop.Extender(captureBuilder)
                configureNaturalToneCapture(captureExtender)

                imageCapture = captureBuilder.build()

                // 3. Bind to Lifecycle
                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(currentLensFacing)
                    .build()

                cameraProvider?.unbindAll()
                camera = cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture
                )

                camera?.cameraInfo?.let { onCameraReady(it) }

                // Apply initial subtle negative EV bias (-0.3 to prevent blown highlights)
                applyInitialExposureBias()

            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
                onError(exc)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * Injects Camera2 hardware parameters to override vendor whitening and scene bleaching:
     * - Disables Scene Mode AI boosting
     * - Enforces natural high-quality tone curves
     * - Eliminates digital edge halos and oversharpening
     */
    @OptIn(ExperimentalCamera2Interop::class)
    private fun <T> configureNaturalToneCapture(extender: Camera2Interop.Extender<T>) {
        // Disable scene-mode AI enhancement (kills the whitening engine)
        extender.setCaptureRequestOption(
            CaptureRequest.CONTROL_SCENE_MODE,
            CaptureRequest.CONTROL_SCENE_MODE_DISABLED
        )

        // Enforce natural Edge Mode (prevents white halos around faces/edges)
        extender.setCaptureRequestOption(
            CaptureRequest.EDGE_MODE,
            CaptureRequest.EDGE_MODE_HIGH_QUALITY
        )

        // Enforce natural tone mapping (prevents extreme shadow lifting)
        extender.setCaptureRequestOption(
            CaptureRequest.TONEMAP_MODE,
            CaptureRequest.TONEMAP_MODE_HIGH_QUALITY
        )

        // Natural color correction
        extender.setCaptureRequestOption(
            CaptureRequest.COLOR_CORRECTION_MODE,
            CaptureRequest.COLOR_CORRECTION_MODE_HIGH_QUALITY
        )

        // Standard auto-exposure and natural auto-white-balance
        extender.setCaptureRequestOption(
            CaptureRequest.CONTROL_MODE,
            CaptureRequest.CONTROL_MODE_AUTO
        )
        extender.setCaptureRequestOption(
            CaptureRequest.CONTROL_AWB_MODE,
            CaptureRequest.CONTROL_AWB_MODE_AUTO
        )
    }

    private fun applyInitialExposureBias() {
        val cameraInfo = camera?.cameraInfo ?: return
        val range = cameraInfo.exposureState.exposureCompensationRange
        val step = cameraInfo.exposureState.exposureCompensationStep.toFloat()

        if (step > 0f) {
            // Target -0.3 EV to lock in deep contrast and natural skin tones
            val targetIndex = (-0.33f / step).toInt().coerceIn(range.lower, range.upper)
            exposureIndex = targetIndex
            camera?.cameraControl?.setExposureCompensationIndex(targetIndex)
        }
    }

    fun setExposureEv(evOffset: Float) {
        val cameraInfo = camera?.cameraInfo ?: return
        val range = cameraInfo.exposureState.exposureCompensationRange
        val step = cameraInfo.exposureState.exposureCompensationStep.toFloat()

        if (step > 0f) {
            val targetIndex = (evOffset / step).toInt().coerceIn(range.lower, range.upper)
            exposureIndex = targetIndex
            camera?.cameraControl?.setExposureCompensationIndex(targetIndex)
        }
    }

    fun setZoom(zoomRatio: Float) {
        camera?.cameraControl?.setZoomRatio(zoomRatio)
    }

    fun setFlashMode(mode: Int) {
        currentFlashMode = mode
        imageCapture?.flashMode = mode
    }

    fun switchCamera(previewView: PreviewView, onComplete: () -> Unit) {
        currentLensFacing = if (currentLensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
        startCamera(previewView, {}, {})
        onComplete()
    }

    fun focusOnPoint(meteringPoint: MeteringPoint) {
        val action = FocusMeteringAction.Builder(meteringPoint, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
            .setAutoCancelDuration(3, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        camera?.cameraControl?.startFocusAndMetering(action)
    }

    fun takePhoto(
        onPhotoSaved: (Uri) -> Unit,
        onError: (ImageCaptureException) -> Unit
    ) {
        val imageCapture = this.imageCapture ?: return

        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(System.currentTimeMillis())
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
                    Log.d(TAG, "Photo saved successfully: $savedUri")
                    onPhotoSaved(savedUri)
                }

                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                    onError(exc)
                }
            }
        )
    }

    fun shutdown() {
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "NaturalCameraManager"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
    }
}

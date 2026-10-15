package com.deekshith.naturalcam.coach

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Scene Coach Analyzer: Orchestrates real-time 4 fps offline intelligence.
 * Evaluates Photometry, Spatial Geometry, Places365, and MoveNet Pose kinematics
 * using deterministic rule functions with zero canned advice.
 */
class SceneCoachAnalyzer(
    context: Context,
    private val onUiStateUpdated: (CoachUiState) -> Unit
) : ImageAnalysis.Analyzer {

    private val placesClassifier = Places365Classifier(context)
    private val poseDetector = MoveNetPoseDetector(context)
    private val geometryAnalyzer = GeometryAnalyzer(context)
    val closedLoopTracker = ClosedLoopTracker()
    private val ruleEngine = RuleEngine(closedLoopTracker)

    private val mainHandler = Handler(Looper.getMainLooper())
    private val isAnalyzing = AtomicBoolean(false)
    private var lastAnalysisTimestamp = 0L

    // Camera Hardware Telemetry (updated from Camera2 CaptureResult)
    @Volatile var currentEv: Float = -0.3f
    @Volatile var currentZoom: Float = 1.0f
    @Volatile var currentIso: Int = 100
    @Volatile var currentExposureTimeNs: Long = 10_000_000L
    @Volatile var isPoseGuideEnabled: Boolean = false
        set(value) {
            field = value
            ruleEngine.isPoseGuideEnabled = value
        }

    // Metrics for Telemetry
    private var frameCount = 0
    private var lastFpsCalculationTime = System.currentTimeMillis()
    private var currentFps = 0f
    private var lastInferenceMs = 0L

    fun updateCameraHardwareState(ev: Float, zoom: Float, iso: Int, exposureNs: Long) {
        this.currentEv = ev
        this.currentZoom = zoom
        this.currentIso = iso
        this.currentExposureTimeNs = exposureNs
    }

    fun applySuggestion(suggestion: CoachSuggestion) {
        closedLoopTracker.onSuggestionApplied(suggestion)
    }

    fun dismissSuggestion(suggestionId: String) {
        closedLoopTracker.onSuggestionDismissed(suggestionId)
    }

    override fun analyze(image: ImageProxy) {
        val now = System.currentTimeMillis()
        // Rate limiter: ~4 fps (at least 240ms between inferences)
        if (now - lastAnalysisTimestamp < 240L || !isAnalyzing.compareAndSet(false, true)) {
            image.close()
            return
        }

        val startTime = System.currentTimeMillis()
        lastAnalysisTimestamp = startTime

        try {
            // 1. Measure Photometry & Luma (Y plane, < 1ms)
            val lightMetrics = LightAnalyzer.analyze(image)

            // 2. Measure Spatial Geometry & Optical Sharpness
            val rollDeg = geometryAnalyzer.currentRollDeg
            val pitchDeg = geometryAnalyzer.currentPitchDeg
            val angularSpeed = geometryAnalyzer.currentAngularSpeedRadS
            val laplacianVar = GeometryAnalyzer.computeLaplacianVariance(image)

            // 3. Extract Bitmap for Offline Neural Inference (Oriented Upright)
            var frameBitmap: Bitmap? = null
            try {
                val rawBitmap = image.toBitmap()
                val rotation = image.imageInfo.rotationDegrees
                frameBitmap = if (rotation != 0) {
                    val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                    Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true).also {
                        if (it != rawBitmap) rawBitmap.recycle()
                    }
                } else {
                    rawBitmap
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to convert ImageProxy to Bitmap: ${e.message}")
            }

            // 4. Offline Places365 Classification (Graceful fallback if unavailable)
            val placesResult = frameBitmap?.let { bmp ->
                try {
                    placesClassifier.classify(bmp)
                } catch (e: Exception) {
                    Log.e(TAG, "Places365 inference error: ${e.message}", e)
                    null
                }
            } ?: Pair(emptyList(), PlaceCategory.GENERAL)

            // 5. Offline MoveNet Pose Detection (Graceful fallback)
            val poseState = frameBitmap?.let { bmp ->
                try {
                    poseDetector.estimatePose(bmp)
                } catch (e: Exception) {
                    Log.e(TAG, "MoveNet inference error: ${e.message}", e)
                    null
                }
            }

            frameBitmap?.recycle()

            // 6. Assemble MeasuredFrameState
            val frameState = MeasuredFrameState(
                timestampMs = startTime,
                avgLuma = lightMetrics.avgLuma,
                highlightClippedPct = lightMetrics.highlightClippedPct,
                shadowCrushedPct = lightMetrics.shadowCrushedPct,
                subjectBackgroundRatio = lightMetrics.subjectBackgroundRatio,
                horizonRollDeg = rollDeg,
                cameraPitchDeg = pitchDeg,
                angularSpeedRadS = angularSpeed,
                laplacianVariance = laplacianVar,
                currentZoom = currentZoom,
                currentEv = currentEv,
                iso = currentIso,
                exposureTimeNs = currentExposureTimeNs,
                topPlaces = placesResult.first,
                activePlaceCategory = placesResult.second,
                poseState = poseState
            )

            // 7. Closed-Loop Telemetry Verification Update
            closedLoopTracker.update(frameState)

            // 8. Rule Engine Evaluation
            val (activeSuggestion, targetSkeleton) = ruleEngine.evaluate(frameState)
            val poseMatched = targetSkeleton != null && targetSkeleton.matchScore >= CoachThresholds.POSE_MATCH_SCORE_SUCCESS

            // 9. Telemetry & Latency Calculation
            val inferenceDuration = System.currentTimeMillis() - startTime
            lastInferenceMs = inferenceDuration
            frameCount++
            if (now - lastFpsCalculationTime >= 1000L) {
                currentFps = (frameCount * 1000f) / (now - lastFpsCalculationTime)
                frameCount = 0
                lastFpsCalculationTime = now
            }

            val uiState = CoachUiState(
                activeSuggestion = activeSuggestion,
                targetSkeleton = targetSkeleton,
                poseMatched = poseMatched,
                isSuppressed = activeSuggestion?.let { closedLoopTracker.isSuppressed(it.id) } ?: false,
                fps = currentFps,
                inferenceMs = lastInferenceMs,
                topSceneLabels = placesResult.first,
                liveMeasurements = frameState
            )

            mainHandler.post {
                onUiStateUpdated(uiState)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Fatal exception in SceneCoachAnalyzer: ${e.message}", e)
        } finally {
            image.close()
            isAnalyzing.set(false)
        }
    }

    fun close() {
        geometryAnalyzer.close()
        placesClassifier.close()
        poseDetector.close()
    }

    companion object {
        private const val TAG = "SceneCoachAnalyzer"
    }
}

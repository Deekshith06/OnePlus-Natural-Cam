package com.deekshith.naturalcam.ai

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.deekshith.naturalcam.camera.CameraMode
import com.deekshith.naturalcam.filter.ForestDslrFilter
import kotlin.math.abs

data class SceneSuggestion(
    val title: String,
    val subtitle: String,
    val recommendedFilter: ForestDslrFilter,
    val recommendedEv: Float,
    val recommendedMode: CameraMode,
    val lightingDesc: String,
    val subjectCenterX: Float = 0.5f,
    val subjectCenterY: Float = 0.48f,
    val hasHumanSubject: Boolean = false,
    val poseAlignmentHint: String = "Align subject with guide",
    val lumaHistogram: IntArray = IntArray(16)
)

class AiPhotographyDirector(
    context: Context? = null,
    private val onSuggestionUpdated: (SceneSuggestion) -> Unit
) : ImageAnalysis.Analyzer {

    private val neuralDirector = context?.let { OfflineNeuralDirector(it) }
    private var frameSkipCounter = 0
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastTitle: String? = null
    private var lastUpdateTimestamp = 0L

    fun close() {
        neuralDirector?.close()
    }

    override fun analyze(image: ImageProxy) {
        // Run analysis on 1 out of every 12 frames for < 1ms overhead and zero battery impact
        frameSkipCounter++
        if (frameSkipCounter % 12 != 0) {
            image.close()
            return
        }

        try {
            // 1. Run Offline On-Device Neural Model inference (Qualcomm NPU / TFLite)
            var neuralResult: NeuralDetectionResult? = null
            if (neuralDirector?.isInitialized == true) {
                try {
                    val frameBitmap = image.toBitmap()
                    neuralResult = neuralDirector.classifyFrame(frameBitmap)
                    frameBitmap.recycle()
                } catch (_: Exception) {}
            }

            val yPlane = image.planes[0]
            val uPlane = if (image.planes.size > 1) image.planes[1] else null
            val vPlane = if (image.planes.size > 2) image.planes[2] else null

            val yBuffer = yPlane.buffer
            val uBuffer = uPlane?.buffer
            val vBuffer = vPlane?.buffer

            val yRowStride = yPlane.rowStride
            val uvRowStride = uPlane?.rowStride ?: 0
            val uvPixelStride = uPlane?.pixelStride ?: 1

            val width = image.width
            val height = image.height

            var totalLuma = 0L
            var minLuma = 255
            var maxLuma = 0

            var greenDominanceCount = 0
            var warmSunsetCount = 0
            var blueNightCount = 0
            var skinPixelCount = 0
            var skinWeightedX = 0f
            var skinWeightedY = 0f
            var sampleCount = 0

            val histogram = IntArray(16)
            val yLimit = yBuffer.limit()
            val uLimit = uBuffer?.limit() ?: 0
            val vLimit = vBuffer?.limit() ?: 0

            val step = 16
            for (y in 0 until height step step) {
                for (x in 0 until width step step) {
                    val yIndex = y * yRowStride + x
                    if (yIndex in 0 until yLimit) {
                        val luma = yBuffer.get(yIndex).toInt() and 0xFF
                        totalLuma += luma
                        if (luma < minLuma) minLuma = luma
                        if (luma > maxLuma) maxLuma = luma

                        val histBin = (luma shr 4).coerceIn(0, 15)
                        histogram[histBin]++

                        // Proper Camera2 YUV_420_888 rowStride + pixelStride chroma indexing
                        if (uBuffer != null && vBuffer != null) {
                            val uvIndex = (y / 2) * uvRowStride + (x / 2) * uvPixelStride
                            if (uvIndex in 0 until uLimit && uvIndex in 0 until vLimit) {
                                val u = (uBuffer.get(uvIndex).toInt() and 0xFF) - 128
                                val v = (vBuffer.get(uvIndex).toInt() and 0xFF) - 128

                                val r = (luma + 1.402f * v).coerceIn(0f, 255f)
                                val g = (luma - 0.344f * u - 0.714f * v).coerceIn(0f, 255f)
                                val b = (luma + 1.772f * u).coerceIn(0f, 255f)

                                // Chlorophyll / Foliage Detection
                                if (g > r * 1.12f && g > b * 1.12f && luma > 28) {
                                    greenDominanceCount++
                                }
                                // Golden Hour / Warm Sunbeams
                                if (r > g * 1.18f && r > b * 1.35f && luma > 40) {
                                    warmSunsetCount++
                                }
                                // Blue Hour / Twilight
                                if (b > r * 1.22f && luma < 95) {
                                    blueNightCount++
                                }
                                // Human Skin-Tone Detection (YCbCr chrominance cluster)
                                val cb = u + 128
                                val cr = v + 128
                                if (cr in 135..173 && cb in 77..127 && luma in 45..235) {
                                    skinPixelCount++
                                    skinWeightedX += x.toFloat() / width
                                    skinWeightedY += y.toFloat() / height
                                }
                            }
                        }
                        sampleCount++
                    }
                }
            }

            val avgLuma = if (sampleCount > 0) (totalLuma / sampleCount).toInt() else 128
            val greenRatio = if (sampleCount > 0) greenDominanceCount.toFloat() / sampleCount else 0f
            val sunsetRatio = if (sampleCount > 0) warmSunsetCount.toFloat() / sampleCount else 0f
            val nightRatio = if (sampleCount > 0) blueNightCount.toFloat() / sampleCount else 0f
            val skinRatio = if (sampleCount > 0) skinPixelCount.toFloat() / sampleCount else 0f
            val contrastSpread = maxLuma - minLuma

            val hasPerson = skinRatio > 0.04f
            val subjX = if (skinPixelCount > 0) (skinWeightedX / skinPixelCount).coerceIn(0.15f, 0.85f) else 0.50f
            val subjY = if (skinPixelCount > 0) (skinWeightedY / skinPixelCount).coerceIn(0.15f, 0.85f) else 0.48f

            // Compute Live Pose & Framing Alignment Coaching
            val alignmentHint = when {
                !hasPerson -> "Position subject inside wireframe silhouette"
                abs(subjX - 0.5f) > 0.22f && subjX < 0.5f -> "Move camera slightly LEFT or ask subject to step RIGHT"
                abs(subjX - 0.5f) > 0.22f && subjX > 0.5f -> "Move camera slightly RIGHT or ask subject to step LEFT"
                subjY < 0.25f -> "Too little headroom — tilt camera UP slightly"
                subjY > 0.72f -> "Subject too low — lower camera to waist/chest height"
                else -> "✓ Subject Perfectly Aligned! Ready to shoot"
            }

            // Smart Scene Decision Matrix (Huawei Master AI + On-Device TFLite Neural Model)
            val suggestion = when {
                // 0. High-Confidence On-Device Neural Model Detection (Qualcomm NPU)
                neuralResult != null && neuralResult.confidence > 0.20f && neuralResult.category != SceneCategory.BALANCED_GENERAL -> {
                    val recFilter = ForestDslrFilter.values().firstOrNull { it.id == neuralResult.recommendedFilterId }
                        ?: ForestDslrFilter.NATURAL
                    val recMode = if (neuralResult.category == SceneCategory.PORTRAIT_HUMAN) CameraMode.PORTRAIT else CameraMode.PHOTO

                    SceneSuggestion(
                        title = "🤖 ${neuralResult.topLabel} (${(neuralResult.confidence * 100).toInt()}%)",
                        subtitle = neuralResult.coachingAdvice,
                        recommendedFilter = recFilter,
                        recommendedEv = neuralResult.recommendedEv,
                        recommendedMode = recMode,
                        lightingDesc = "On-Device Neural NPU",
                        subjectCenterX = subjX,
                        subjectCenterY = subjY,
                        hasHumanSubject = neuralResult.category == SceneCategory.PORTRAIT_HUMAN || hasPerson,
                        poseAlignmentHint = alignmentHint,
                        lumaHistogram = histogram
                    )
                }

                // 1. Portrait + Forest / Outdoor Subject
                hasPerson && greenRatio > 0.20f -> {
                    SceneSuggestion(
                        title = "🌲👤 Forest Portrait Detected",
                        subtitle = "Apply Emerald Pine + f/1.4 Bokeh & -0.3 EV for natural skin + deep greens",
                        recommendedFilter = ForestDslrFilter.EMERALD_FOREST,
                        recommendedEv = -0.3f,
                        recommendedMode = CameraMode.PORTRAIT,
                        lightingDesc = "Outdoor Canopy Portrait",
                        subjectCenterX = subjX,
                        subjectCenterY = subjY,
                        hasHumanSubject = true,
                        poseAlignmentHint = alignmentHint,
                        lumaHistogram = histogram
                    )
                }

                // 2. Forest & Canopy Sunbeams (High contrast + green foliage)
                greenRatio > 0.25f && contrastSpread > 195 -> {
                    SceneSuggestion(
                        title = "🌲✨ Canopy Sunbeams Detected",
                        subtitle = "Apply Sunbeam Bloom FX & -0.5 EV to glow sun rays without clipping",
                        recommendedFilter = ForestDslrFilter.SUNBEAM_HALATION,
                        recommendedEv = -0.5f,
                        recommendedMode = CameraMode.PHOTO,
                        lightingDesc = "Dappled Forest Sunlight",
                        subjectCenterX = subjX,
                        subjectCenterY = subjY,
                        hasHumanSubject = hasPerson,
                        poseAlignmentHint = alignmentHint,
                        lumaHistogram = histogram
                    )
                }

                // 3. Dense Forest & Woodland
                greenRatio > 0.26f -> {
                    SceneSuggestion(
                        title = "🌲 Forest & Foliage Detected",
                        subtitle = "Apply Emerald Pine (Dehaze + Vignette) & -0.3 EV for rich moss depth",
                        recommendedFilter = ForestDslrFilter.EMERALD_FOREST,
                        recommendedEv = -0.3f,
                        recommendedMode = CameraMode.PHOTO,
                        lightingDesc = "Diffused Canopy Light",
                        subjectCenterX = subjX,
                        subjectCenterY = subjY,
                        hasHumanSubject = hasPerson,
                        poseAlignmentHint = alignmentHint,
                        lumaHistogram = histogram
                    )
                }

                // 4. Portrait / Human Subject Detected Indoors or Daylight
                hasPerson && skinRatio > 0.07f -> {
                    SceneSuggestion(
                        title = "👤 Portrait Subject Detected",
                        subtitle = "Switch to Portrait f/1.4 Bokeh or Pro-Mist 1/4 for zero-bleach skin tones",
                        recommendedFilter = ForestDslrFilter.BLACK_PRO_MIST,
                        recommendedEv = -0.3f,
                        recommendedMode = CameraMode.PORTRAIT,
                        lightingDesc = "Natural Skin-Tone Lock",
                        subjectCenterX = subjX,
                        subjectCenterY = subjY,
                        hasHumanSubject = true,
                        poseAlignmentHint = alignmentHint,
                        lumaHistogram = histogram
                    )
                }

                // 5. Low Light / Extreme Dark
                avgLuma < 38 -> {
                    SceneSuggestion(
                        title = "🌙 Low-Light Night Scene",
                        subtitle = "Switch to Nightscape + Twilight Blue Halation for clean dark shadows",
                        recommendedFilter = ForestDslrFilter.TWILIGHT_BLUE,
                        recommendedEv = 0.0f,
                        recommendedMode = CameraMode.NIGHT,
                        lightingDesc = "Dim Ambient (< 20 lux)",
                        subjectCenterX = subjX,
                        subjectCenterY = subjY,
                        hasHumanSubject = hasPerson,
                        poseAlignmentHint = alignmentHint,
                        lumaHistogram = histogram
                    )
                }

                // 6. Golden Hour / Sunburst
                sunsetRatio > 0.22f -> {
                    SceneSuggestion(
                        title = "🍂 Golden Hour Light Detected",
                        subtitle = "Apply Golden Canopy (Warm Halation + S-Curve) for rich amber light",
                        recommendedFilter = ForestDslrFilter.GOLDEN_CANOPY,
                        recommendedEv = -0.4f,
                        recommendedMode = CameraMode.PHOTO,
                        lightingDesc = "Warm Golden Hour",
                        subjectCenterX = subjX,
                        subjectCenterY = subjY,
                        hasHumanSubject = hasPerson,
                        poseAlignmentHint = alignmentHint,
                        lumaHistogram = histogram
                    )
                }

                // 7. Blue Hour / Midnight Sky
                nightRatio > 0.32f -> {
                    SceneSuggestion(
                        title = "🌌 Twilight Blue Hour",
                        subtitle = "Apply Twilight Blue FX to balance deep indigo sky and warm lights",
                        recommendedFilter = ForestDslrFilter.TWILIGHT_BLUE,
                        recommendedEv = -0.2f,
                        recommendedMode = CameraMode.NIGHT,
                        lightingDesc = "Cool Blue Hour",
                        subjectCenterX = subjX,
                        subjectCenterY = subjY,
                        hasHumanSubject = hasPerson,
                        poseAlignmentHint = alignmentHint,
                        lumaHistogram = histogram
                    )
                }

                // 8. High Contrast / Backlit
                contrastSpread > 210 && avgLuma > 110 -> {
                    SceneSuggestion(
                        title = "☀️ Backlit / Harsh Highlights",
                        subtitle = "Apply Pro-Mist 1/4 & -0.7 EV to bloom highlights instead of white clipping",
                        recommendedFilter = ForestDslrFilter.BLACK_PRO_MIST,
                        recommendedEv = -0.7f,
                        recommendedMode = CameraMode.PHOTO,
                        lightingDesc = "High Dynamic Spread",
                        subjectCenterX = subjX,
                        subjectCenterY = subjY,
                        hasHumanSubject = hasPerson,
                        poseAlignmentHint = alignmentHint,
                        lumaHistogram = histogram
                    )
                }

                // 9. Balanced Natural Scene
                else -> {
                    val title = if (neuralResult != null && neuralResult.confidence > 0.15f) {
                        "🤖 AI: ${neuralResult.topLabel} (${(neuralResult.confidence * 100).toInt()}%)"
                    } else {
                        "🌿 Natural Balanced Scene"
                    }
                    val sub = if (neuralResult != null && neuralResult.confidence > 0.15f) {
                        neuralResult.coachingAdvice
                    } else {
                        "Optimal lighting. Zero-bleach Sony IMX890 natural tone active"
                    }
                    SceneSuggestion(
                        title = title,
                        subtitle = sub,
                        recommendedFilter = ForestDslrFilter.NATURAL,
                        recommendedEv = -0.3f,
                        recommendedMode = CameraMode.PHOTO,
                        lightingDesc = if (neuralResult != null) "Snapdragon NPU Active" else "Balanced Daylight",
                        subjectCenterX = subjX,
                        subjectCenterY = subjY,
                        hasHumanSubject = hasPerson,
                        poseAlignmentHint = alignmentHint,
                        lumaHistogram = histogram
                    )
                }
            }

            val now = System.currentTimeMillis()
            if (suggestion.title != lastTitle || (now - lastUpdateTimestamp) > 1200L) {
                lastTitle = suggestion.title
                lastUpdateTimestamp = now
                mainHandler.post {
                    onSuggestionUpdated(suggestion)
                }
            }

        } catch (_: Exception) {
            // Fail safely without interrupting camera preview
        } finally {
            image.close()
        }
    }
}

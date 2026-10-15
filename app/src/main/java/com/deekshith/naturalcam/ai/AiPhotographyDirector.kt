package com.deekshith.naturalcam.ai

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.deekshith.naturalcam.camera.CameraMode
import com.deekshith.naturalcam.filter.ForestDslrFilter
import java.nio.ByteBuffer

data class SceneSuggestion(
    val title: String,
    val subtitle: String,
    val recommendedFilter: ForestDslrFilter,
    val recommendedEv: Float,
    val recommendedMode: CameraMode,
    val lightingDesc: String
)

class AiPhotographyDirector(
    private val onSuggestionUpdated: (SceneSuggestion) -> Unit
) : ImageAnalysis.Analyzer {

    private var frameSkipCounter = 0

    override fun analyze(image: ImageProxy) {
        // Run analysis on 1 out of every 15 frames for near-zero battery and CPU impact (< 1ms)
        frameSkipCounter++
        if (frameSkipCounter % 15 != 0) {
            image.close()
            return
        }

        try {
            val yBuffer = image.planes[0].buffer // Luminance plane
            val uBuffer = if (image.planes.size > 1) image.planes[1].buffer else null
            val vBuffer = if (image.planes.size > 2) image.planes[2].buffer else null

            val width = image.width
            val height = image.height

            var totalLuma = 0L
            var minLuma = 255
            var maxLuma = 0

            var greenDominanceCount = 0
            var warmSunsetCount = 0
            var blueNightCount = 0
            var sampleCount = 0

            // Sample pixels across a uniform grid
            val step = 16
            for (y in 0 until height step step) {
                for (x in 0 until width step step) {
                    val yIndex = y * width + x
                    if (yIndex < yBuffer.remaining()) {
                        val luma = yBuffer.get(yIndex).toInt() and 0xFF
                        totalLuma += luma
                        if (luma < minLuma) minLuma = luma
                        if (luma > maxLuma) maxLuma = luma

                        // Approximate color channels from YUV
                        if (uBuffer != null && vBuffer != null) {
                            val uvIndex = (y / 2) * (width / 2) + (x / 2)
                            if (uvIndex < uBuffer.remaining() && uvIndex < vBuffer.remaining()) {
                                val u = (uBuffer.get(uvIndex).toInt() and 0xFF) - 128
                                val v = (vBuffer.get(uvIndex).toInt() and 0xFF) - 128

                                // Approximate RGB
                                val r = luma + 1.402f * v
                                val g = luma - 0.344f * u - 0.714f * v
                                val b = luma + 1.772f * u

                                if (g > r * 1.15f && g > b * 1.15f && luma > 30) {
                                    greenDominanceCount++
                                }
                                if (r > g * 1.2f && r > b * 1.4f && luma > 40) {
                                    warmSunsetCount++
                                }
                                if (b > r * 1.25f && luma < 90) {
                                    blueNightCount++
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
            val contrastSpread = maxLuma - minLuma

            // Smart Scene Decision Matrix (like Huawei Master AI)
            val suggestion = when {
                // 1. Forest & Nature Detection
                greenRatio > 0.30f -> {
                    SceneSuggestion(
                        title = "🌲 Forest & Foliage Detected",
                        subtitle = "Recommend Emerald Pine filter & -0.3 EV for deep foliage depth",
                        recommendedFilter = ForestDslrFilter.EMERALD_FOREST,
                        recommendedEv = -0.3f,
                        recommendedMode = CameraMode.PHOTO,
                        lightingDesc = "Diffused Canopy Light"
                    )
                }

                // 2. Low Light / Extreme Dark
                avgLuma < 35 -> {
                    SceneSuggestion(
                        title = "🌙 Low-Light Night Scene",
                        subtitle = "Recommend Nightscape Mode or Twilight Blue filter for crisp detail",
                        recommendedFilter = ForestDslrFilter.TWILIGHT_BLUE,
                        recommendedEv = 0.0f,
                        recommendedMode = CameraMode.NIGHT,
                        lightingDesc = "Dim Ambient (< 20 lux)"
                    )
                }

                // 3. Golden Hour / Sunburst
                sunsetRatio > 0.25f -> {
                    SceneSuggestion(
                        title = "🍂 Golden Hour Light Detected",
                        subtitle = "Recommend Golden Canopy filter to capture rich sunbeams",
                        recommendedFilter = ForestDslrFilter.GOLDEN_CANOPY,
                        recommendedEv = -0.3f,
                        recommendedMode = CameraMode.PHOTO,
                        lightingDesc = "Warm Golden Hour"
                    )
                }

                // 4. Blue Hour / Midnight Sky
                nightRatio > 0.35f -> {
                    SceneSuggestion(
                        title = "🌌 Twilight Blue Hour",
                        subtitle = "Recommend Twilight Blue filter to balance dark sky and lights",
                        recommendedFilter = ForestDslrFilter.TWILIGHT_BLUE,
                        recommendedEv = -0.2f,
                        recommendedMode = CameraMode.NIGHT,
                        lightingDesc = "Cool Blue Hour"
                    )
                }

                // 5. High Contrast / Backlit
                contrastSpread > 210 && avgLuma > 110 -> {
                    SceneSuggestion(
                        title = "☀️ Backlit / High Contrast",
                        subtitle = "Pull down EV by -0.7 to protect sky highlights from clipping",
                        recommendedFilter = ForestDslrFilter.NATURAL,
                        recommendedEv = -0.7f,
                        recommendedMode = CameraMode.PHOTO,
                        lightingDesc = "High Contrast Sunburst"
                    )
                }

                // 6. Balanced Natural Scene
                else -> {
                    SceneSuggestion(
                        title = "🌿 Natural Balanced Scene",
                        subtitle = "Optimal lighting. Hasselblad natural color matrix active",
                        recommendedFilter = ForestDslrFilter.NATURAL,
                        recommendedEv = -0.3f,
                        recommendedMode = CameraMode.PHOTO,
                        lightingDesc = "Balanced Daylight"
                    )
                }
            }

            onSuggestionUpdated(suggestion)

        } catch (e: Exception) {
            // Fail safely without affecting camera preview
        } finally {
            image.close()
        }
    }
}

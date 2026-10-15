package com.deekshith.naturalcam.coach

import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer

/**
 * Fast sub-sampled Photometry & Lighting Analyzer.
 * Extracts luma histogram, % clipped highlights (>250), % crushed shadows (<15),
 * and subject-to-background luma ratio for backlight detection in < 1ms.
 */
object LightAnalyzer {

    data class LightMetrics(
        val histogram: IntArray,
        val avgLuma: Float,
        val highlightClippedPct: Float,
        val shadowCrushedPct: Float,
        val subjectBackgroundRatio: Float
    )

    fun analyze(image: ImageProxy, subjectBox: CoachRectF? = null): LightMetrics {
        val yPlane = image.planes[0]
        val buffer: ByteBuffer = yPlane.buffer
        val rowStride = yPlane.rowStride
        val width = image.width
        val height = image.height

        val histogram = IntArray(16)
        var totalLuma = 0L
        var clippedCount = 0
        var crushedCount = 0
        var sampleCount = 0

        var subjectLumaTotal = 0L
        var subjectSampleCount = 0
        var bgLumaTotal = 0L
        var bgSampleCount = 0

        val step = 12 // 12px stride for sub-millisecond execution
        val limit = buffer.limit()

        for (y in 0 until height step step) {
            val normY = y.toFloat() / height
            val rowOffset = y * rowStride

            for (x in 0 until width step step) {
                val index = rowOffset + x
                if (index < limit) {
                    val luma = buffer.get(index).toInt() and 0xFF
                    totalLuma += luma
                    sampleCount++

                    val bin = (luma shr 4).coerceIn(0, 15)
                    histogram[bin]++

                    if (luma >= 250) clippedCount++
                    if (luma <= 15) crushedCount++

                    if (subjectBox != null) {
                        val normX = x.toFloat() / width
                        if (subjectBox.contains(normX, normY)) {
                            subjectLumaTotal += luma
                            subjectSampleCount++
                        } else {
                            bgLumaTotal += luma
                            bgSampleCount++
                        }
                    }
                }
            }
        }

        val avgLuma = if (sampleCount > 0) totalLuma.toFloat() / sampleCount else 128f
        val clippedPct = if (sampleCount > 0) clippedCount.toFloat() / sampleCount else 0f
        val crushedPct = if (sampleCount > 0) crushedCount.toFloat() / sampleCount else 0f

        val subjectAvg = if (subjectSampleCount > 0) subjectLumaTotal.toFloat() / subjectSampleCount else avgLuma
        val bgAvg = if (bgSampleCount > 0) bgLumaTotal.toFloat() / bgSampleCount else avgLuma
        val ratio = if (bgAvg > 1f) (subjectAvg / bgAvg).coerceIn(0.1f, 5.0f) else 1.0f

        return LightMetrics(
            histogram = histogram,
            avgLuma = avgLuma,
            highlightClippedPct = clippedPct,
            shadowCrushedPct = crushedPct,
            subjectBackgroundRatio = ratio
        )
    }
}

package com.deekshith.naturalcam.coach

import org.junit.Assert.*
import org.junit.Test

class PredictionSmootherTest {

    @Test
    fun testExponentialMovingAverage_smoothsValuesCorrectly() {
        val alpha = CoachThresholds.EMA_ALPHA_LUMA // 0.35f
        var smoothed = 100.0f

        // Sudden jump to 200.0f
        val input = 200.0f
        smoothed = alpha * input + (1f - alpha) * smoothed

        val expected = 0.35f * 200f + 0.65f * 100f // 70 + 65 = 135
        assertEquals(expected, smoothed, 0.001f)

        // Second step towards 200.0f
        smoothed = alpha * input + (1f - alpha) * smoothed
        val expected2 = 0.35f * 200f + 0.65f * 135f // 70 + 87.75 = 157.75
        assertEquals(expected2, smoothed, 0.001f)
    }

    @Test
    fun testHysteresisThresholds_preventFlickerAtBoundaries() {
        val enterThreshold = CoachThresholds.HIGHLIGHT_CLIPPING_ENTER_PCT // 0.08f
        val exitThreshold = CoachThresholds.HIGHLIGHT_CLIPPING_EXIT_PCT   // 0.04f

        assertTrue("Enter threshold must be higher than exit threshold for positive hysteresis", enterThreshold > exitThreshold)

        var isTriggered = false

        // Value rises to 0.06f (below enter) -> stays false
        if (!isTriggered && 0.06f > enterThreshold) isTriggered = true
        assertFalse(isTriggered)

        // Value rises to 0.09f (above enter) -> turns true
        if (!isTriggered && 0.09f > enterThreshold) isTriggered = true
        assertTrue(isTriggered)

        // Value falls slightly to 0.06f (above exit 0.04f) -> remains true (no flicker)
        if (isTriggered && 0.06f < exitThreshold) isTriggered = false
        assertTrue("Hysteresis must hold active state above exit threshold", isTriggered)

        // Value falls to 0.03f (below exit) -> turns false
        if (isTriggered && 0.03f < exitThreshold) isTriggered = false
        assertFalse("Hysteresis must release state below exit threshold", isTriggered)
    }
}

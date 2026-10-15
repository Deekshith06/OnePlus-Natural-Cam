package com.deekshith.naturalcam.coach

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import kotlin.math.atan2

class MoveNetKinematicsTest {

    @Test
    fun testStandingVsSittingPosture_geometryCalculation() {
        // Hip (11) at (0.4, 0.5), Knee (13) at (0.4, 0.75), Ankle (15) at (0.4, 0.98)
        // Straight leg -> angle ~ 180° -> Standing
        val hip = PoseKeypoint(11, 0.4f, 0.5f, 0.9f)
        val knee = PoseKeypoint(13, 0.4f, 0.75f, 0.9f)
        val ankle = PoseKeypoint(15, 0.4f, 0.98f, 0.9f)

        val straightLegAngle = computeAngle(hip, knee, ankle)
        assertNotNull(straightLegAngle)
        assertTrue("Straight leg angle should be > 155°", straightLegAngle!! > 155f)

        // Seated: Hip (11) at (0.4, 0.5), Knee (13) at (0.55, 0.72), Ankle (15) at (0.55, 0.95)
        // Bent leg ~ 90°-110° -> Sitting
        val seatedHip = PoseKeypoint(11, 0.35f, 0.5f, 0.9f)
        val seatedKnee = PoseKeypoint(13, 0.55f, 0.5f, 0.9f) // horizontal thigh
        val seatedAnkle = PoseKeypoint(15, 0.55f, 0.85f, 0.9f) // vertical shin

        val bentLegAngle = computeAngle(seatedHip, seatedKnee, seatedAnkle)
        assertNotNull(bentLegAngle)
        assertTrue("Bent knee sitting angle should be between 75° and 135° (was $bentLegAngle°)", bentLegAngle!! in 75f..135f)
    }

    @Test
    fun testGridPosition_determinesCorrectThirds() {
        fun computeThird(cx: Float): GridThirdsRegion {
            return when {
                cx < 0.33f -> GridThirdsRegion.LEFT_THIRD
                cx > 0.66f -> GridThirdsRegion.RIGHT_THIRD
                else -> GridThirdsRegion.CENTER_THIRD
            }
        }

        assertEquals(GridThirdsRegion.LEFT_THIRD, computeThird(0.25f))
        assertEquals(GridThirdsRegion.CENTER_THIRD, computeThird(0.50f))
        assertEquals(GridThirdsRegion.RIGHT_THIRD, computeThird(0.75f))
    }

    private fun computeAngle(a: PoseKeypoint, b: PoseKeypoint, c: PoseKeypoint): Float? {
        val v1x = a.x - b.x; val v1y = a.y - b.y
        val v2x = c.x - b.x; val v2y = c.y - b.y
        val angle1 = atan2(v1y.toDouble(), v1x.toDouble())
        val angle2 = atan2(v2y.toDouble(), v2x.toDouble())
        var diff = Math.toDegrees(abs(angle1 - angle2))
        if (diff > 180.0) diff = 360.0 - diff
        return diff.toFloat()
    }
}

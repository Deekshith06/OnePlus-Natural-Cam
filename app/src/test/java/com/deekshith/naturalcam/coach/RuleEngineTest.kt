package com.deekshith.naturalcam.coach

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.math.abs

class RuleEngineTest {

    private lateinit var closedLoopTracker: ClosedLoopTracker
    private lateinit var ruleEngine: RuleEngine

    @Before
    fun setUp() {
        closedLoopTracker = ClosedLoopTracker()
        ruleEngine = RuleEngine(closedLoopTracker)
    }

    @Test
    fun testHighlightClippingRule_triggersAccurateAdjustmentAndEvidence() {
        // 14% highlights clipped (> 8% enter threshold)
        val state = MeasuredFrameState(
            highlightClippedPct = 0.14f,
            currentEv = 0.0f
        )

        val (suggestion, _) = ruleEngine.evaluate(state)
        assertNotNull("Suggestion should be generated for 14% highlight clipping", suggestion)
        assertEquals("rule_highlight_clip", suggestion!!.id)
        assertEquals(SuggestionType.EXPOSURE, suggestion.type)
        assertEquals(1, suggestion.priority)
        assertTrue(suggestion.evidence.contains("14% highlights clipped"))
        assertTrue(suggestion.evidence.contains("EV -0.7") || suggestion.evidence.contains("EV -0.3"))
        assertNotNull(suggestion.action)
        assertTrue(suggestion.action is CoachAction.AdjustEv)
    }

    @Test
    fun testShadowCrushingRule_triggersPositiveEv() {
        // 25% shadow crushing (> 20% enter threshold)
        val state = MeasuredFrameState(
            shadowCrushedPct = 0.25f,
            currentEv = 0.0f,
            activePlaceCategory = PlaceCategory.GENERAL
        )

        val (suggestion, _) = ruleEngine.evaluate(state)
        assertNotNull(suggestion)
        assertEquals("rule_shadow_crush", suggestion!!.id)
        assertEquals(SuggestionType.EXPOSURE, suggestion.type)
        assertEquals("EV +0.3", suggestion.shortLabel)
        assertTrue(suggestion.evidence.contains("25% shadows crushed -> EV 0.3"))
    }

    @Test
    fun testHorizonRollRule_triggersLevelerWhenTilted() {
        // 8.0° roll tilt (> 1.5° threshold, < 35° Dutch angle threshold)
        val state = MeasuredFrameState(
            horizonRollDeg = 8.0f
        )

        val (suggestion, _) = ruleEngine.evaluate(state)
        assertNotNull(suggestion)
        assertEquals("rule_horizon_roll", suggestion!!.id)
        assertEquals(SuggestionType.HORIZON_LEVEL, suggestion.type)
        assertEquals("Level Horizon", suggestion.shortLabel)
        assertTrue(suggestion.evidence.contains("8° roll tilt -> Level device to 0°"))
    }

    @Test
    fun testDutchAngle_ignoredWhenTiltExceeds35Degrees() {
        // Intentional artistic 42° Dutch angle
        val state = MeasuredFrameState(
            horizonRollDeg = 42.0f
        )

        val (suggestion, _) = ruleEngine.evaluate(state)
        // Should NOT prompt to level horizon for extreme intentional angles
        if (suggestion != null) {
            assertNotEquals("rule_horizon_roll", suggestion.id)
        }
    }

    @Test
    fun testBacklightRule_triggersWhenSubjectInDeepShadow() {
        val dummyPose = HumanPoseState(
            boundingBox = CoachRectF(0.3f, 0.2f, 0.7f, 0.8f),
            keypoints = emptyList(),
            isStanding = true,
            isSitting = false,
            torsoLeanAngleDeg = 0f,
            headTiltAngleDeg = 0f,
            handsVisible = true,
            gridPosition = GridThirdsRegion.CENTER_THIRD
        )

        // Subject luma is only 35% of background luma (< 55% threshold)
        val state = MeasuredFrameState(
            subjectBackgroundRatio = 0.35f,
            poseState = dummyPose
        )

        val (suggestion, _) = ruleEngine.evaluate(state)
        assertNotNull(suggestion)
        assertEquals("rule_backlight", suggestion!!.id)
        assertEquals(SuggestionType.BACKLIGHT, suggestion.type)
        assertTrue(suggestion.evidence.contains("Subject in shadow (ratio 0.35)"))
    }

    @Test
    fun testLowLightShakeRule_triggersHoldSteady() {
        // Fast hand shake in low light
        val state = MeasuredFrameState(
            angularSpeedRadS = 0.65f,
            avgLuma = 45.0f
        )

        val (suggestion, _) = ruleEngine.evaluate(state)
        assertNotNull(suggestion)
        assertEquals("rule_shake_night", suggestion!!.id)
        assertEquals(SuggestionType.MOTION_STABILITY, suggestion.type)
        assertEquals("Hold Steady", suggestion.shortLabel)
        assertTrue(suggestion.evidence.contains("Hand shake (0.65 rad/s) in low light"))
    }

    @Test
    fun testPortraitFraming_stepBackWhenSubjectTooClose() {
        val closePose = HumanPoseState(
            boundingBox = CoachRectF(0.1f, 0.05f, 0.9f, 0.95f), // height = 0.90 (> 0.85)
            keypoints = emptyList(),
            isStanding = true,
            isSitting = false,
            torsoLeanAngleDeg = 0f,
            headTiltAngleDeg = 0f,
            handsVisible = true,
            gridPosition = GridThirdsRegion.CENTER_THIRD
        )

        val state = MeasuredFrameState(poseState = closePose)
        val (suggestion, _) = ruleEngine.evaluate(state)
        assertNotNull(suggestion)
        assertEquals("rule_portrait_close", suggestion!!.id)
        assertEquals("Step Back", suggestion.shortLabel)
        assertTrue(suggestion.evidence.contains("Subject occupying 90% frame -> Step back 1m"))
    }

    @Test
    fun testPortraitFraming_zoomInWhenSubjectTooFar() {
        val farPose = HumanPoseState(
            boundingBox = CoachRectF(0.4f, 0.3f, 0.6f, 0.65f), // height = 0.35 (< 0.45)
            keypoints = emptyList(),
            isStanding = true,
            isSitting = false,
            torsoLeanAngleDeg = 0f,
            headTiltAngleDeg = 0f,
            handsVisible = true,
            gridPosition = GridThirdsRegion.CENTER_THIRD
        )

        val state = MeasuredFrameState(poseState = farPose)
        val (suggestion, _) = ruleEngine.evaluate(state)
        assertNotNull(suggestion)
        assertEquals("rule_portrait_far", suggestion!!.id)
        assertEquals("Zoom 2x / Step In", suggestion.shortLabel)
        assertTrue(suggestion.evidence.contains("Subject occupying 35% frame -> Zoom 2x for portrait"))
    }

    @Test
    fun testPoseCoaching_detectsTorsoLeanAndGeneratesRelativeInstructions() {
        val box = CoachRectF(0.3f, 0.15f, 0.7f, 0.85f)
        val kps = listOf(
            PoseKeypoint(0, 0.5f, 0.2f, 0.9f),
            PoseKeypoint(5, 0.4f, 0.32f, 0.9f),
            PoseKeypoint(6, 0.6f, 0.32f, 0.9f),
            PoseKeypoint(11, 0.42f, 0.55f, 0.9f),
            PoseKeypoint(12, 0.58f, 0.55f, 0.9f)
        )
        val leaningPose = HumanPoseState(
            boundingBox = box,
            keypoints = kps,
            isStanding = true,
            isSitting = false,
            torsoLeanAngleDeg = 18.0f, // 18° lean
            headTiltAngleDeg = 2.0f,
            handsVisible = true,
            gridPosition = GridThirdsRegion.CENTER_THIRD
        )

        val state = MeasuredFrameState(poseState = leaningPose)
        val (_, skeleton) = ruleEngine.evaluate(state)
        assertNotNull(skeleton)
        assertTrue(skeleton!!.alignmentHint.contains("Straighten torso"))
        assertTrue(skeleton.alignmentHint.contains("18°"))
    }

    @Test
    fun testPoseCoaching_detectsAlignedPoseAndMarksGood() {
        val box = CoachRectF(0.3f, 0.15f, 0.7f, 0.85f)
        val cx = box.centerX(); val top = box.top; val h = box.height(); val w = box.width()
        val alignedKps = listOf(
            PoseKeypoint(0, cx, top + h * 0.08f, 0.95f),
            PoseKeypoint(5, cx - w * 0.32f, top + h * 0.20f, 0.95f),
            PoseKeypoint(6, cx + w * 0.32f, top + h * 0.20f, 0.95f),
            PoseKeypoint(11, cx - w * 0.20f, top + h * 0.52f, 0.95f),
            PoseKeypoint(12, cx + w * 0.20f, top + h * 0.52f, 0.95f)
        )
        val alignedPose = HumanPoseState(
            boundingBox = box,
            keypoints = alignedKps,
            isStanding = true,
            isSitting = false,
            torsoLeanAngleDeg = 0.0f,
            headTiltAngleDeg = 0.0f,
            handsVisible = true,
            gridPosition = GridThirdsRegion.CENTER_THIRD
        )

        val state = MeasuredFrameState(poseState = alignedPose)
        val (_, skeleton) = ruleEngine.evaluate(state)
        assertNotNull(skeleton)
        assertEquals("Pose Matched (Good)", skeleton!!.alignmentHint)
        assertTrue("Match score must be >= 0.82", skeleton.matchScore >= 0.82f)
    }

    @Test
    fun testPoseCoaching_whenPoseGuideEnabled_elevatesPriority() {
        val box = CoachRectF(0.3f, 0.15f, 0.7f, 0.85f)
        val kps = listOf(
            PoseKeypoint(0, 0.5f, 0.2f, 0.9f),
            PoseKeypoint(5, 0.4f, 0.32f, 0.9f),
            PoseKeypoint(6, 0.6f, 0.32f, 0.9f),
            PoseKeypoint(11, 0.42f, 0.55f, 0.9f),
            PoseKeypoint(12, 0.58f, 0.55f, 0.9f)
        )
        val leaningPose = HumanPoseState(
            boundingBox = box,
            keypoints = kps,
            isStanding = true,
            isSitting = false,
            torsoLeanAngleDeg = 18.0f,
            headTiltAngleDeg = 0.0f,
            handsVisible = true,
            gridPosition = GridThirdsRegion.CENTER_THIRD
        )

        // With pitch tilt present (-20° pitch tilt is priority 8)
        val state = MeasuredFrameState(
            cameraPitchDeg = -20.0f,
            poseState = leaningPose
        )

        ruleEngine.isPoseGuideEnabled = true
        val (suggestion, _) = ruleEngine.evaluate(state)
        assertNotNull(suggestion)
        assertEquals("rule_pose_coach", suggestion!!.id)
        assertEquals(2, suggestion.priority)
        assertEquals("Straighten Pose", suggestion.shortLabel)
    }
}

package com.deekshith.naturalcam.coach

import com.deekshith.naturalcam.camera.CameraMode
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Deterministic Rule Engine for Scene Coach.
 * ZERO canned advice: Evaluates rule functions strictly on measured physical telemetry.
 * Each suggestion computes its numerical evidence string (e.g. "14% highlights clipped -> EV -0.7").
 */
class RuleEngine(private val closedLoopTracker: ClosedLoopTracker) {

    private var currentActiveSuggestion: CoachSuggestion? = null
    private var lastSuggestionChangeTimestamp = 0L
    var isPoseGuideEnabled: Boolean = false

    /**
     * Evaluates all candidate suggestions from the measured frame state,
     * respects suppression, applies hysteresis and dwell, and returns the top priority suggestion.
     */
    fun evaluate(frameState: MeasuredFrameState): Pair<CoachSuggestion?, TargetSkeleton?> {
        val now = System.currentTimeMillis()
        val candidates = mutableListOf<CoachSuggestion>()

        // 1. Exposure Rule: Highlight Clipping
        val highlightThreshold = if (currentActiveSuggestion?.id == "rule_highlight_clip") {
            CoachThresholds.HIGHLIGHT_CLIPPING_EXIT_PCT
        } else {
            CoachThresholds.HIGHLIGHT_CLIPPING_ENTER_PCT
        }
        if (frameState.highlightClippedPct > highlightThreshold && !closedLoopTracker.isSuppressed("rule_highlight_clip")) {
            val clipPctInt = (frameState.highlightClippedPct * 100).roundToInt()
            val evDelta = when {
                frameState.highlightClippedPct > 0.20f -> -1.0f
                frameState.highlightClippedPct > 0.12f -> -0.7f
                else -> -0.3f
            }
            val targetEv = (frameState.currentEv + evDelta).coerceIn(-2.0f, 2.0f)
            candidates.add(
                CoachSuggestion(
                    id = "rule_highlight_clip",
                    type = SuggestionType.EXPOSURE,
                    priority = 1,
                    shortLabel = "EV ${if (evDelta > 0) "+$evDelta" else "$evDelta"}",
                    evidence = "$clipPctInt% highlights clipped -> EV ${String.format("%.1f", targetEv)}",
                    action = CoachAction.AdjustEv(targetEv),
                    trackedMetric = TrackedMetric.HIGHLIGHT_CLIPPING,
                    baselineValue = frameState.highlightClippedPct,
                    expectedTarget = 0.02f
                )
            )
        }

        // 2. Backlight Rule: Subject in Deep Shadow vs Bright Background
        if (frameState.poseState != null && frameState.subjectBackgroundRatio < CoachThresholds.BACKLIGHT_RATIO_ENTER &&
            !closedLoopTracker.isSuppressed("rule_backlight")
        ) {
            val ratioFmt = String.format("%.2f", frameState.subjectBackgroundRatio)
            candidates.add(
                CoachSuggestion(
                    id = "rule_backlight",
                    type = SuggestionType.BACKLIGHT,
                    priority = 2,
                    shortLabel = "Backlit: Shift Angle",
                    evidence = "Subject in shadow (ratio $ratioFmt) -> Move subject or use fill",
                    action = CoachAction.RepositionSubject,
                    trackedMetric = TrackedMetric.HIGHLIGHT_CLIPPING,
                    baselineValue = frameState.subjectBackgroundRatio,
                    expectedTarget = 0.75f
                )
            )
        }

        // 3. Motion & Low-Light Shake Rule
        if (frameState.angularSpeedRadS > CoachThresholds.SHAKE_ANGULAR_SPEED_THRESHOLD &&
            frameState.avgLuma < CoachThresholds.LOW_LIGHT_LUMA_THRESHOLD &&
            !closedLoopTracker.isSuppressed("rule_shake_night")
        ) {
            val speedFmt = String.format("%.2f", frameState.angularSpeedRadS)
            candidates.add(
                CoachSuggestion(
                    id = "rule_shake_night",
                    type = SuggestionType.MOTION_STABILITY,
                    priority = 3,
                    shortLabel = "Hold Steady",
                    evidence = "Hand shake ($speedFmt rad/s) in low light -> Hold steady",
                    action = CoachAction.SwitchMode(CameraMode.NIGHT),
                    trackedMetric = TrackedMetric.CAMERA_STABILITY,
                    baselineValue = frameState.angularSpeedRadS,
                    expectedTarget = 0.15f
                )
            )
        }

        // 4. Horizon Roll Leveling Rule
        val rollAbs = abs(frameState.horizonRollDeg)
        val rollThreshold = if (currentActiveSuggestion?.id == "rule_horizon_roll") {
            CoachThresholds.HORIZON_ROLL_EXIT_DEG
        } else {
            CoachThresholds.HORIZON_ROLL_ENTER_DEG
        }
        if (rollAbs > rollThreshold && rollAbs <= CoachThresholds.HORIZON_ROLL_MAX_CONSIDER_DEG &&
            !closedLoopTracker.isSuppressed("rule_horizon_roll")
        ) {
            val rollDegInt = rollAbs.roundToInt()
            candidates.add(
                CoachSuggestion(
                    id = "rule_horizon_roll",
                    type = SuggestionType.HORIZON_LEVEL,
                    priority = if (frameState.activePlaceCategory == PlaceCategory.HISTORIC_ARCHITECTURE ||
                        frameState.activePlaceCategory == PlaceCategory.BEACH_WATER) 2 else 4,
                    shortLabel = "Level Horizon",
                    evidence = "${rollDegInt}° roll tilt -> Level device to 0°",
                    action = CoachAction.LevelHorizon,
                    trackedMetric = TrackedMetric.HORIZON_ROLL,
                    baselineValue = rollAbs,
                    expectedTarget = 0.0f
                )
            )
        }

        // 5. Exposure Rule: Shadow Crushing
        val shadowThreshold = if (currentActiveSuggestion?.id == "rule_shadow_crush") {
            CoachThresholds.SHADOW_CRUSHING_EXIT_PCT
        } else {
            CoachThresholds.SHADOW_CRUSHING_ENTER_PCT
        }
        if (frameState.shadowCrushedPct > shadowThreshold &&
            frameState.activePlaceCategory != PlaceCategory.LOW_LIGHT_NIGHT &&
            !closedLoopTracker.isSuppressed("rule_shadow_crush")
        ) {
            val crushPctInt = (frameState.shadowCrushedPct * 100).roundToInt()
            val targetEv = (frameState.currentEv + 0.3f).coerceIn(-2.0f, 2.0f)
            candidates.add(
                CoachSuggestion(
                    id = "rule_shadow_crush",
                    type = SuggestionType.EXPOSURE,
                    priority = 5,
                    shortLabel = "EV +0.3",
                    evidence = "$crushPctInt% shadows crushed -> EV ${String.format("%.1f", targetEv)}",
                    action = CoachAction.AdjustEv(targetEv),
                    trackedMetric = TrackedMetric.SHADOW_CRUSHING,
                    baselineValue = frameState.shadowCrushedPct,
                    expectedTarget = 0.08f
                )
            )
        }

        // 6. Portrait Framing Rules (Subject Distance & Height)
        val pose = frameState.poseState
        if (pose != null) {
            val heightRatio = pose.boundingBox.height()
            if (heightRatio > CoachThresholds.PORTRAIT_SUBJECT_TOO_CLOSE_RATIO &&
                !closedLoopTracker.isSuppressed("rule_portrait_close")
            ) {
                val heightPct = (heightRatio * 100).roundToInt()
                candidates.add(
                    CoachSuggestion(
                        id = "rule_portrait_close",
                        type = SuggestionType.ZOOM_FRAMING,
                        priority = 6,
                        shortLabel = "Step Back",
                        evidence = "Subject occupying $heightPct% frame -> Step back 1m",
                        action = CoachAction.SetZoom(1.0f),
                        trackedMetric = TrackedMetric.SUBJECT_HEIGHT_RATIO,
                        baselineValue = heightRatio,
                        expectedTarget = 0.65f
                    )
                )
            } else if (heightRatio < CoachThresholds.PORTRAIT_SUBJECT_TOO_FAR_RATIO &&
                !closedLoopTracker.isSuppressed("rule_portrait_far")
            ) {
                val heightPct = (heightRatio * 100).roundToInt()
                candidates.add(
                    CoachSuggestion(
                        id = "rule_portrait_far",
                        type = SuggestionType.ZOOM_FRAMING,
                        priority = 7,
                        shortLabel = "Zoom 2x / Step In",
                        evidence = "Subject occupying $heightPct% frame -> Zoom 2x for portrait",
                        action = CoachAction.SetZoom(2.0f),
                        trackedMetric = TrackedMetric.SUBJECT_HEIGHT_RATIO,
                        baselineValue = heightRatio,
                        expectedTarget = 0.65f
                    )
                )
            }
        }

        // 7. Pitch Tilt Rule (Steep angles)
        val pitchAbs = abs(frameState.cameraPitchDeg)
        if (pitchAbs > CoachThresholds.CAMERA_PITCH_STEEP_DEG &&
            !closedLoopTracker.isSuppressed("rule_pitch_tilt")
        ) {
            val direction = if (frameState.cameraPitchDeg > 0) "down" else "up"
            val pitchInt = pitchAbs.roundToInt()
            candidates.add(
                CoachSuggestion(
                    id = "rule_pitch_tilt",
                    type = SuggestionType.PITCH_TILT,
                    priority = 8,
                    shortLabel = "Tilt $direction",
                    evidence = "${pitchInt}° pitch angle -> Level camera eye-line",
                    action = CoachAction.LevelHorizon,
                    trackedMetric = TrackedMetric.HORIZON_ROLL,
                    baselineValue = pitchAbs,
                    expectedTarget = 0.0f
                )
            )
        }

        // 8. Pose Kinematic Guidance & Target Skeleton
        var targetSkeleton: TargetSkeleton? = null
        if (pose != null) {
            val poseAnalysis = evaluatePoseGuidance(pose, frameState)
            targetSkeleton = poseAnalysis.second
            if (poseAnalysis.first != null && !closedLoopTracker.isSuppressed("rule_pose_coach")) {
                val suggestion = if (isPoseGuideEnabled) {
                    poseAnalysis.first!!.copy(priority = 2)
                } else {
                    poseAnalysis.first!!
                }
                candidates.add(suggestion)
            }
        }

        // Sort candidates by priority (1 is highest)
        candidates.sortBy { it.priority }
        val bestCandidate = candidates.firstOrNull()

        // Apply temporal dwell logic:
        // An active suggestion must persist for MIN_SUGGESTION_DWELL_MS unless condition is resolved
        val active = currentActiveSuggestion
        val finalSuggestion = if (active != null) {
            val stillValid = candidates.any { it.id == active.id }
            val dwellTimeMs = now - lastSuggestionChangeTimestamp
            if (stillValid && dwellTimeMs < CoachThresholds.MIN_SUGGESTION_DWELL_MS) {
                // Keep active suggestion during dwell
                candidates.firstOrNull { it.id == active.id } ?: active
            } else if (bestCandidate != null && (now - lastSuggestionChangeTimestamp) >= CoachThresholds.MAX_MUTATION_INTERVAL_MS) {
                // Switch to higher priority or newly surfaced suggestion
                if (bestCandidate.id != active.id) {
                    lastSuggestionChangeTimestamp = now
                }
                bestCandidate
            } else if (stillValid) {
                candidates.firstOrNull { it.id == active.id } ?: active
            } else {
                lastSuggestionChangeTimestamp = now
                bestCandidate
            }
        } else {
            if (bestCandidate != null) {
                lastSuggestionChangeTimestamp = now
            }
            bestCandidate
        }

        currentActiveSuggestion = finalSuggestion
        return Pair(finalSuggestion, targetSkeleton)
    }

    /**
     * Computes pose comparison against ideal composition for the current scene,
     * producing kinematic alignment instructions and target skeleton keypoints.
     */
    private fun evaluatePoseGuidance(
        pose: HumanPoseState,
        frameState: MeasuredFrameState
    ): Pair<CoachSuggestion?, TargetSkeleton?> {
        val box = pose.boundingBox
        val targetKps = createTargetKeypointsForBox(box, pose.isSitting)

        // Calculate similarity match score across visible keypoints
        var totalDist = 0f
        var countedKps = 0
        for (kp in pose.keypoints) {
            if (kp.confidence >= CoachThresholds.KEYPOINT_CONFIDENCE_MIN) {
                val target = targetKps.firstOrNull { it.id == kp.id }
                if (target != null) {
                    val dx = kp.x - target.x
                    val dy = kp.y - target.y
                    totalDist += (dx * dx + dy * dy)
                    countedKps++
                }
            }
        }

        val avgDist = if (countedKps > 0) kotlin.math.sqrt((totalDist / countedKps).toDouble()).toFloat() else 0.5f
        val anglePenalty = (abs(pose.torsoLeanAngleDeg) / 45.0f).coerceIn(0f, 0.5f) + (abs(pose.headTiltAngleDeg) / 45.0f).coerceIn(0f, 0.3f)
        val matchScore = ((1.0f - (avgDist / 0.35f)) * (1.0f - anglePenalty)).coerceIn(0.0f, 1.0f)

        // Generate kinematic relative instructions
        var hint = "Pose matched"
        var coachSuggestion: CoachSuggestion? = null

        when {
            abs(pose.torsoLeanAngleDeg) > 14.0f -> {
                val leanDir = if (pose.torsoLeanAngleDeg > 0) "right" else "left"
                val leanInt = abs(pose.torsoLeanAngleDeg).roundToInt()
                hint = "Straighten torso ($leanInt° $leanDir lean)"
                coachSuggestion = CoachSuggestion(
                    id = "rule_pose_coach",
                    type = SuggestionType.POSE_ALIGNMENT,
                    priority = 9,
                    shortLabel = "Straighten Pose",
                    evidence = "$leanInt° torso lean -> Align spine upright",
                    action = null,
                    trackedMetric = TrackedMetric.POSE_MATCH_SCORE,
                    baselineValue = matchScore,
                    expectedTarget = 0.85f
                )
            }
            abs(pose.headTiltAngleDeg) > 12.0f -> {
                val tiltInt = abs(pose.headTiltAngleDeg).roundToInt()
                hint = "Level chin ($tiltInt° head tilt)"
                coachSuggestion = CoachSuggestion(
                    id = "rule_pose_coach",
                    type = SuggestionType.POSE_ALIGNMENT,
                    priority = 9,
                    shortLabel = "Level Chin",
                    evidence = "$tiltInt° head tilt -> Level eye-line",
                    action = null,
                    trackedMetric = TrackedMetric.POSE_MATCH_SCORE,
                    baselineValue = matchScore,
                    expectedTarget = 0.85f
                )
            }
            matchScore >= CoachThresholds.POSE_MATCH_SCORE_SUCCESS -> {
                hint = "Pose Matched (Good)"
            }
            pose.gridPosition == GridThirdsRegion.CENTER_THIRD &&
                (frameState.activePlaceCategory == PlaceCategory.HISTORIC_ARCHITECTURE ||
                 frameState.activePlaceCategory == PlaceCategory.FOREST_NATURE) -> {
                hint = "Shift to left third to reveal scenery"
                coachSuggestion = CoachSuggestion(
                    id = "rule_pose_coach",
                    type = SuggestionType.POSE_ALIGNMENT,
                    priority = 9,
                    shortLabel = "Rule of Thirds",
                    evidence = "Centered subject obscures background -> Shift to thirds grid",
                    action = null,
                    trackedMetric = TrackedMetric.POSE_MATCH_SCORE,
                    baselineValue = matchScore,
                    expectedTarget = 0.85f
                )
            }
            else -> {
                val scorePct = (matchScore * 100).roundToInt()
                hint = "Align with guide ($scorePct%)"
            }
        }

        val skeleton = TargetSkeleton(
            id = "target_pose_${if (pose.isSitting) "seated" else "standing"}",
            name = if (pose.isSitting) "Seated Natural" else "Standing Natural",
            targetKeypoints = targetKps,
            alignmentHint = hint,
            matchScore = matchScore
        )

        return Pair(coachSuggestion, skeleton)
    }

    /**
     * Synthesizes target skeleton keypoints scaled and centered perfectly to the detected bounding box.
     */
    private fun createTargetKeypointsForBox(box: CoachRectF, isSitting: Boolean): List<PoseKeypoint> {
        val cx = box.centerX()
        val top = box.top
        val h = box.height()
        val w = box.width()

        val list = mutableListOf<PoseKeypoint>()
        // 0: Nose
        list.add(PoseKeypoint(0, cx, top + h * 0.08f, 1.0f))
        // 1: Left Eye, 2: Right Eye
        list.add(PoseKeypoint(1, cx - w * 0.08f, top + h * 0.06f, 1.0f))
        list.add(PoseKeypoint(2, cx + w * 0.08f, top + h * 0.06f, 1.0f))
        // 3: Left Ear, 4: Right Ear
        list.add(PoseKeypoint(3, cx - w * 0.16f, top + h * 0.08f, 1.0f))
        list.add(PoseKeypoint(4, cx + w * 0.16f, top + h * 0.08f, 1.0f))
        // 5: Left Shoulder, 6: Right Shoulder
        list.add(PoseKeypoint(5, cx - w * 0.32f, top + h * 0.20f, 1.0f))
        list.add(PoseKeypoint(6, cx + w * 0.32f, top + h * 0.20f, 1.0f))
        // 7: Left Elbow, 8: Right Elbow
        list.add(PoseKeypoint(7, cx - w * 0.38f, top + h * 0.38f, 1.0f))
        list.add(PoseKeypoint(8, cx + w * 0.38f, top + h * 0.38f, 1.0f))
        // 9: Left Wrist, 10: Right Wrist
        list.add(PoseKeypoint(9, cx - w * 0.30f, top + h * 0.52f, 1.0f))
        list.add(PoseKeypoint(10, cx + w * 0.30f, top + h * 0.52f, 1.0f))
        // 11: Left Hip, 12: Right Hip
        list.add(PoseKeypoint(11, cx - w * 0.20f, top + h * 0.52f, 1.0f))
        list.add(PoseKeypoint(12, cx + w * 0.20f, top + h * 0.52f, 1.0f))

        if (isSitting) {
            // Seated bent knees
            list.add(PoseKeypoint(13, cx - w * 0.30f, top + h * 0.72f, 1.0f))
            list.add(PoseKeypoint(14, cx + w * 0.30f, top + h * 0.72f, 1.0f))
            list.add(PoseKeypoint(15, cx - w * 0.25f, top + h * 0.94f, 1.0f))
            list.add(PoseKeypoint(16, cx + w * 0.25f, top + h * 0.94f, 1.0f))
        } else {
            // Standing upright legs
            list.add(PoseKeypoint(13, cx - w * 0.18f, top + h * 0.75f, 1.0f))
            list.add(PoseKeypoint(14, cx + w * 0.18f, top + h * 0.75f, 1.0f))
            list.add(PoseKeypoint(15, cx - w * 0.18f, top + h * 0.96f, 1.0f))
            list.add(PoseKeypoint(16, cx + w * 0.18f, top + h * 0.96f, 1.0f))
        }

        return list
    }
}

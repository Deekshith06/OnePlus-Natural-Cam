package com.deekshith.naturalcam.coach

import com.deekshith.naturalcam.camera.CameraMode

/**
 * All physical and mathematical thresholds for the Scene Coach rule engine.
 * ZERO canned advice: every rule is a deterministic function of measured values.
 */
object CoachThresholds {
    // Photometry & Light
    const val HIGHLIGHT_CLIPPING_ENTER_PCT = 0.08f   // 8% pixels clipped (>250)
    const val HIGHLIGHT_CLIPPING_EXIT_PCT = 0.04f    // Hysteresis exit
    const val SHADOW_CRUSHING_ENTER_PCT = 0.20f      // 20% pixels crushed (<15)
    const val SHADOW_CRUSHING_EXIT_PCT = 0.12f
    const val BACKLIGHT_RATIO_ENTER = 0.55f          // Subject luma < 55% of background luma
    const val BACKLIGHT_RATIO_EXIT = 0.70f
    
    // Spatial Geometry & Orientation
    const val HORIZON_ROLL_ENTER_DEG = 1.5f          // >1.5° tilt triggers leveler hint
    const val HORIZON_ROLL_MAX_CONSIDER_DEG = 35.0f  // Intentional Dutch angle ignored
    const val HORIZON_ROLL_EXIT_DEG = 0.8f           // Successfully leveled
    const val CAMERA_PITCH_STEEP_DEG = 18.0f         // Tilt up/down hint
    
    // Framing & Composition
    const val PORTRAIT_SUBJECT_TOO_FAR_RATIO = 0.45f // Subject occupying < 45% of vertical frame
    const val PORTRAIT_SUBJECT_TOO_CLOSE_RATIO = 0.85f // Subject occupying > 85% of vertical frame
    const val PORTRAIT_TARGET_HEIGHT_MIN = 0.55f     // Ideal subject height 55%
    const val PORTRAIT_TARGET_HEIGHT_MAX = 0.75f     // Ideal subject height 75%
    
    // Inertial & Sharpness
    const val SHAKE_ANGULAR_SPEED_THRESHOLD = 0.45f  // rad/s angular velocity
    const val LOW_LIGHT_LUMA_THRESHOLD = 75.0f       // Low-light scene threshold
    const val BLUR_LAPLACIAN_VARIANCE_MIN = 85.0f    // Below this indicates motion blur/out of focus
    
    // Pose Alignment
    const val POSE_MATCH_SCORE_SUCCESS = 0.82f       // 82% match score hides skeleton & shows "Good"
    const val KEYPOINT_CONFIDENCE_MIN = 0.35f
    
    // Temporal Dwell & Smoothing
    const val MIN_SUGGESTION_DWELL_MS = 2000L        // Minimum 2 seconds persistence
    const val MAX_MUTATION_INTERVAL_MS = 3000L       // At most 1 suggestion change per 3s in static scenes
    const val CLOSED_LOOP_TIMEOUT_MS = 3000L
    const val SUPPRESSION_AFTER_DISMISS_MS = 30000L  // 30 seconds suppression after dismiss
    const val CLOSED_LOOP_REQUIRED_IMPROVEMENT = 0.40f // Must improve by at least 40% to be effective
    
    // Smoothing EMA alphas
    const val EMA_ALPHA_LUMA = 0.35f
    const val EMA_ALPHA_GEOMETRY = 0.25f
    const val EMA_ALPHA_CLASSIFICATION = 0.20f
}

data class MeasuredFrameState(
    val timestampMs: Long = System.currentTimeMillis(),
    val avgLuma: Float = 128f,
    val highlightClippedPct: Float = 0f,
    val shadowCrushedPct: Float = 0f,
    val subjectBackgroundRatio: Float = 1f,
    val horizonRollDeg: Float = 0f,
    val cameraPitchDeg: Float = 0f,
    val angularSpeedRadS: Float = 0f,
    val laplacianVariance: Float = 150f,
    val currentZoom: Float = 1f,
    val currentEv: Float = 0f,
    val iso: Int = 100,
    val exposureTimeNs: Long = 10_000_000L,
    val topPlaces: List<SceneCategoryScore> = emptyList(),
    val activePlaceCategory: PlaceCategory = PlaceCategory.GENERAL,
    val poseState: HumanPoseState? = null
)

data class SceneCategoryScore(
    val label: String,
    val confidence: Float
)

enum class PlaceCategory(val displayName: String, val icon: String) {
    INDOOR_ROOM("Indoor Room", "🛋️"),
    CAFE_DINING("Cafe & Dining", "☕"),
    FOREST_NATURE("Forest & Trail", "🌲"),
    BEACH_WATER("Beach & Water", "🌊"),
    URBAN_STREET("City Street", "🏙️"),
    HISTORIC_ARCHITECTURE("Architecture", "🏛️"),
    BOTANICAL_GARDEN("Botanical Garden", "🌸"),
    LOW_LIGHT_NIGHT("Night Scene", "🌙"),
    PORTRAIT_STUDIO("Portrait Studio", "👤"),
    GENERAL("Natural Scene", "📷")
}

data class CoachRectF(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 0f,
    val bottom: Float = 0f
) {
    fun width(): Float = right - left
    fun height(): Float = bottom - top
    fun centerX(): Float = (left + right) / 2f
    fun centerY(): Float = (top + bottom) / 2f
    fun contains(x: Float, y: Float): Boolean = x in left..right && y in top..bottom
}

data class HumanPoseState(
    val boundingBox: CoachRectF,
    val keypoints: List<PoseKeypoint>,
    val isStanding: Boolean,
    val isSitting: Boolean,
    val torsoLeanAngleDeg: Float,
    val headTiltAngleDeg: Float,
    val handsVisible: Boolean,
    val gridPosition: GridThirdsRegion
)

enum class GridThirdsRegion {
    LEFT_THIRD,
    CENTER_THIRD,
    RIGHT_THIRD
}

data class PoseKeypoint(
    val id: Int,
    val x: Float, // normalized [0..1]
    val y: Float, // normalized [0..1]
    val confidence: Float
)

data class CoachSuggestion(
    val id: String,
    val type: SuggestionType,
    val priority: Int, // 1 (Highest) .. 10 (Lowest)
    val shortLabel: String,
    val evidence: String,
    val action: CoachAction?,
    val trackedMetric: TrackedMetric,
    val baselineValue: Float,
    val expectedTarget: Float
)

enum class SuggestionType {
    EXPOSURE,
    ZOOM_FRAMING,
    HORIZON_LEVEL,
    PITCH_TILT,
    MOTION_STABILITY,
    BACKLIGHT,
    POSE_ALIGNMENT
}

enum class TrackedMetric {
    HIGHLIGHT_CLIPPING,
    SHADOW_CRUSHING,
    HORIZON_ROLL,
    SUBJECT_HEIGHT_RATIO,
    POSE_MATCH_SCORE,
    CAMERA_STABILITY
}

sealed class CoachAction {
    data class AdjustEv(val targetEv: Float) : CoachAction()
    data class SetZoom(val targetZoom: Float) : CoachAction()
    data class SwitchMode(val mode: CameraMode) : CoachAction()
    object LevelHorizon : CoachAction()
    object RepositionSubject : CoachAction()
}

data class CoachUiState(
    val activeSuggestion: CoachSuggestion? = null,
    val targetSkeleton: TargetSkeleton? = null,
    val poseMatched: Boolean = false,
    val isSuppressed: Boolean = false,
    // Live Debug Telemetry
    val fps: Float = 0f,
    val inferenceMs: Long = 0L,
    val topSceneLabels: List<SceneCategoryScore> = emptyList(),
    val liveMeasurements: MeasuredFrameState = MeasuredFrameState()
)

data class TargetSkeleton(
    val id: String,
    val name: String,
    val targetKeypoints: List<PoseKeypoint>,
    val alignmentHint: String,
    val matchScore: Float
)

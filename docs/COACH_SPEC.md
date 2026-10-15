# Scene Coach Specification: Architecture, Data Contracts & Thresholds

## 1. System Overview & Philosophy
The **Scene Coach** is an offline, real-time photographic intelligence engine modeled after professional cinematography directors and Huawei Master AI. It continuously observes the camera viewfinder at ~4 fps, measures physical scene attributes (photometry, scene classification, pose kinematics, spatial geometry, and sensor shake), and evaluates deterministic rule functions to produce actionable camera and pose suggestions.

### Core Tenets
1. **Zero Canned Advice:** No static lookup table mapping label -> static text. Every recommendation is computed from real-time measured physical quantities with transparent mathematical evidence.
2. **Natural Pipeline Integrity:** All artificial color-shade matrices, color filters, and RenderEffects are completely excised. The imaging pipeline preserves raw Hasselblad / Sony IMX890 authentic colors.
3. **Closed-Loop Feedback:** Every user-applied adjustment is tracked. If the corresponding metric does not improve over subsequent frames, the suggestion is flagged as ineffective and suppressed.
4. **Zero Battery & Preview Impact:** Runs on a dedicated single-thread executor at ~4 fps with `STRATEGY_KEEP_ONLY_LATEST`. Target p95 analysis latency < 60ms. Fails safe under all conditions.

---

## 2. Module Boundaries & Architecture

```
                    ┌─────────────────────────┐
                    │ CameraX ImageAnalysis   │ (YUV_420_888 @ 4 fps)
                    └───────────┬─────────────┘
                                │
                    ┌───────────▼─────────────┐
                    │  SceneCoachAnalyzer     │ (Single-thread Executor)
                    └───────────┬─────────────┘
                                │
        ┌───────────────────────┼──────────────────────────┬────────────────────────┐
        ▼                       ▼                          ▼                        ▼
┌──────────────┐      ┌──────────────────┐       ┌─────────────────┐      ┌──────────────────┐
│  Light       │      │  Places365       │       │  MoveNet Pose   │      │  Geometry &      │
│  Analyzer    │      │  Classifier      │       │  Estimator      │      │  Sensor Reader   │
│ (Luma/Clip)  │      │ (ResNet18 TFLite)│       │ (17 Keypoints)  │      │ (Pitch/Roll/IMU) │
└───────┬──────┘      └─────────┬────────┘       └────────┬────────┘      └─────────┬────────┘
        │                       │                         │                         │
        └───────────────────────┼─────────────────────────┴─────────────────────────┘
                                │
                    ┌───────────▼─────────────┐
                    │   MeasuredFrameState    │ (Immutable Measured Physical Metrics)
                    └───────────┬─────────────┘
                                │
                    ┌───────────▼─────────────┐
                    │   PredictionSmoother    │ (EMA + Hysteresis + 2.0s Dwell)
                    └───────────┬─────────────┘
                                │
                    ┌───────────▼─────────────┐
                    │   RuleEngine            │ (Deterministic Rule Functions)
                    └───────────┬─────────────┘
                                │
                    ┌───────────▼─────────────┐
                    │   ClosedLoopTracker     │ (Pre/Post Apply Verification)
                    └───────────┬─────────────┘
                                │
                    ┌───────────▼─────────────┐
                    │   SceneCoachUiState     │ (Single Pill + Opt-in Skeleton)
                    └─────────────────────────┘
```

---

## 3. Data Contracts

### 3.1 `MeasuredFrameState`
```kotlin
data class MeasuredFrameState(
    val timestampMs: Long,
    // Light & Photometry
    val lumaHistogram: IntArray,       // 16 bins (0..255)
    val avgLuma: Float,                // 0..255
    val highlightClippedPct: Float,    // % pixels > 250
    val shadowCrushedPct: Float,       // % pixels < 15
    val subjectBackgroundRatio: Float, // subject luma / background luma (backlight detection)
    
    // Geometry & Inertial
    val horizonRollDeg: Float,         // device roll (-180..180°)
    val cameraPitchDeg: Float,        // device pitch (-90..90°)
    val angularSpeedRadS: Float,      // gyro magnitude (shake)
    val laplacianVariance: Float,      // sharpness / edge energy
    
    // Camera Hardware State (from CaptureResult)
    val currentZoom: Float,
    val currentEv: Float,
    val iso: Int,
    val exposureTimeNs: Long,
    
    // Place Classification (Places365 ResNet18)
    val topPlaces: List<SceneCategoryScore>, // top-3 labels and probabilities
    val activePlaceCategory: PlaceCategory,
    
    // Human Pose Kinematics (MoveNet Lightning)
    val poseState: HumanPoseState?
)

data class SceneCategoryScore(
    val label: String,
    val confidence: Float
)

data class HumanPoseState(
    val boundingBox: RectF,            // normalized [0..1]
    val keypoints: List<PoseKeypoint>, // 17 COCO keypoints
    val isStanding: Boolean,           // hip-knee-ankle angle > 155°
    val isSitting: Boolean,            // hip-knee-ankle angle in 75..135°
    val torsoLeanAngleDeg: Float,      // angle of shoulder-to-hip vector vs vertical
    val headTiltAngleDeg: Float,       // angle of eye-line vs horizontal
    val handsVisible: Boolean,         // wrists confidence > 0.45
    val gridPosition: GridThirdsRegion // LEFT_THIRD, CENTER_THIRD, RIGHT_THIRD
)

data class PoseKeypoint(
    val id: Int,                       // 0: nose, 1: left_eye, 2: right_eye, ... 16: right_ankle
    val x: Float,                      // normalized 0..1
    val y: Float,                      // normalized 0..1
    val confidence: Float
)
```

### 3.2 `CoachSuggestion` & Evidence
```kotlin
data class CoachSuggestion(
    val id: String,
    val category: SuggestionType,      // CAMERA_SETTING, FRAMING_GEOMETRY, POSE_GUIDE
    val priority: Int,                 // 1 (Critical) .. 10 (Subtle)
    val shortLabel: String,            // E.g. "EV -0.7", "Level Horizon", "Step Back"
    val evidence: String,              // E.g. "14% highlights clipped -> EV -0.7"
    val action: CoachAction?,          // Executable on-tap adjustment
    val trackedMetric: TrackedMetric,  // Metric to verify in closed-loop
    val expectedDelta: Float,          // Target change required to prove efficacy
    val baselineValue: Float           // Measured value at suggestion generation
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

sealed class CoachAction {
    data class AdjustEv(val targetEv: Float) : CoachAction()
    data class SetZoom(val targetZoom: Float) : CoachAction()
    data class SetMode(val mode: CameraMode) : CoachAction()
    object LevelDevice : CoachAction()
    object RepositionSubject : CoachAction()
}
```

---

## 4. Thresholds Configuration (`CoachThresholds.kt`)

All physical thresholds are centralized in a single configuration file without magic numbers:

| Metric | Threshold | Suggested Adjustment | Evidence String Format |
| :--- | :--- | :--- | :--- |
| **Highlight Clipping** | `highlightClippedPct > 0.08` (8%) | EV delta `-0.3f` to `-0.7f` | `"${(pct * 100).toInt()}% highlights clipped -> EV ${ev}"` |
| **Shadow Crushing** | `shadowCrushedPct > 0.20` (20%) | EV delta `+0.3f` | `"${(pct * 100).toInt()}% shadows crushed -> EV +0.3"` |
| **Backlight Ratio** | `subjectBackgroundRatio < 0.55` | Face exposure / HDR or fill | `"Subject in shadow (luma ratio ${ratio}) -> Fill light / Move out of backlight"` |
| **Horizon Roll** | `abs(roll) in 1.5° .. 35.0°` | Level horizon to 0° | `"${abs(roll).toInt()}° roll tilt -> Level device"` |
| **Camera Pitch** | `pitch > 18°` (low angle) / `< -18°` (high angle) | Tilt camera upright | `"${pitch.toInt()}° pitch angle -> Eye-level framing"` |
| **Portrait Framing** | `subjectHeight / frameHeight < 0.45` | Zoom 2x or Step Forward | `"Subject occupying ${(heightRatio * 100).toInt()}% frame -> Zoom 2x for portrait"` |
| **Subject Crowding** | `subjectHeight / frameHeight > 0.85` | Zoom 1x / 0.6x or Step Back | `"Subject too close (${(heightRatio * 100).toInt()}%) -> Step back 1m"` |
| **Sensor Shake** | `angularSpeed > 0.45 rad/s && luma < 80` | Night Mode / Hold Steady | `"Hand shake (${speed} rad/s) in low light -> Hold steady"` |
| **Match Score** | `poseMatchScore >= 0.82` (82%) | Hide target skeleton & show "Good" | `"Pose matched (85%) -> Ready to capture"` |

---

## 5. Temporal Smoothing & Dwell Logic
To prevent UI flickering and cognitive overload in static scenes:
1. **Exponential Moving Average (EMA):**
   $$\hat{x}_t = \alpha \cdot x_t + (1 - \alpha) \cdot \hat{x}_{t-1}$$
   - Luma and clipping metrics: $\alpha = 0.35$ (fast photometry tracking).
   - Gyro roll/pitch: $\alpha = 0.25$ (suppress hand tremor).
   - Scene classification logits: $\alpha = 0.20$ (smooth classification).
2. **Enter/Exit Hysteresis:**
   - Enter threshold: $T_{\text{enter}} = 1.0 \times T_{\text{base}}$
   - Exit threshold: $T_{\text{exit}} = 0.65 \times T_{\text{base}}$
3. **Minimum Dwell:**
   - Active suggestions must persist for a minimum of **2.0 seconds** unless the measured condition completely resolves or the user applies the action.
   - Max suggestion mutation rate: **at most 1 change per 3.0 seconds** in static conditions.

---

## 6. Closed-Loop Verification Protocol
1. User taps **Apply** on the Coach pill.
2. The engine snapshots `preApplyMetric = baselineValue` and timestamps the action.
3. Over the next 4 to 8 analysis cycles (1.0 to 2.0 seconds):
   - Engine measures `postApplyMetric`.
   - Efficacy check:
     $$\Delta_{\text{observed}} = | \text{target} - \text{postApplyMetric} |$$
     $$\text{IsEffective} = \Delta_{\text{observed}} < \Delta_{\text{baseline}} \times 0.60$$
4. If `IsEffective == false`:
   - Mark rule ID as `INEFFECTIVE_FOR_SCENE`.
   - Suppress suggestion for **30 seconds**.
   - Log verification event with pre/post metrics to telemetry.

---

## 7. User Interface Specification
- **Top Center Pill:**
  - Height: `32.dp`.
  - Shape: `RoundedCornerShape(16.dp)`.
  - Background: Glassmorphic translucent dark `Color(0xCC18181A)` with 0.8.dp border.
  - Layout: `[ Icon (e.g. ☀️ / 📐 / 🧍) | Short Label | Action Badge / Dismiss (✕) ]`.
  - Auto-hide after 4.0 seconds. 30 seconds suppression if user taps `✕`.
- **Opt-In Target Pose Skeleton:**
  - Delicate 1.5.dp stroke width at ~30% alpha (`Color(0x4DFFFFFF)`).
  - Aligned strictly within subject bounding box.
  - Switches to emerald green (`Color(0xCC00E676)`) and shows a compact "Good" chip when match score exceeds 82%, then fades out.
- **Debug Telemetry Overlay:**
  - Triggered via long-press on the Coach Pill.
  - Displays:
    - FPS and analysis p50 / p95 latency.
    - Top-3 Places365 categories and confidence scores.
    - Live measured values: % clipped, % crushed, roll, pitch, angular speed, laplacian sharpness.
    - Active rule state, closed-loop success rate.

---

## 8. Failure Safety & Graceful Degradation
1. **Model Load Failure:** If Places365 or MoveNet fails to load or compile on NNAPI, catch safely and immediately fall back to CPU threads. If TFLite is unavailable, fall back completely to pure deterministic photometric and geometric rules (light histogram + gyro). The camera preview MUST NEVER crash.
2. **Buffer Safety:** Every `ImageProxy` received in `analyze()` is wrapped in a `try-finally` block ensuring `image.close()` is invoked 100% of the time, even under unhandled exceptions.
3. **No Network Access:** The application retains zero network permissions (`android.permission.INTERNET` is strictly prohibited).

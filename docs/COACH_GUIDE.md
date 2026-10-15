# Scene Coach User Guide

The **Scene Coach** is an offline, real-time photographic intelligence feature in OnePlus Natural Cam, inspired by Huawei Master AI and professional cinematography direction. It continuously analyzes physical scene conditions and provides deterministic, evidence-backed advice to achieve optimal natural photographs without artificial digital processing.

---

## 1. How It Works

As you point the camera, the Scene Coach measures live physical properties from the viewfinder at ~4 fps:
- **Photometry:** Live luma histogram, highlight clipping percentage (>250), shadow crushing (<15), and subject-vs-background backlight ratio.
- **Orientation & Sharpness:** Device roll tilt (leveler), camera pitch angle, gyro angular speed (hand shake), and Laplacian optical sharpness.
- **Scene Context:** 365 environment categories via bundled Places365 ResNet18 (forest, beach, architecture, cafe, street, etc.).
- **Human Posture:** 17-keypoint body kinematics via MoveNet Lightning (standing vs sitting stance, torso lean, head tilt, and grid alignment).

Every recommendation is calculated from numerical measurements using strict rule thresholds. **There is zero canned text or fixed lookup tables.**

---

## 2. Using the Scene Coach Pill

The Coach interface is designed to keep your viewfinder completely uncluttered:
- **Single Frosted Pill:** Appears discreetly at the top center of the screen with an icon and concise instruction (e.g., `☀️ EV -0.7`, `📐 Level Horizon`, `🔍 Step Back`, `🌙 Hold Steady`).
- **Tap "APPLY":** Automatically applies the hardware adjustment (adjusts exposure compensation, sets zoom, or switches camera modes).
- **Dismiss (✕):** If you don't wish to follow a recommendation, tap the small `✕` on the pill. The suggestion will be suppressed for **30 seconds**.
- **Auto-Hide:** Pills auto-hide after 4 seconds of inactivity.

---

## 3. Closed-Loop Efficacy Tracking

When you tap **Apply**, the Scene Coach re-measures the scene over subsequent frames:
- If the metric improves by at least 40% (e.g., clipped highlights drop from 14% to under 2%), the coach confirms resolution.
- If the metric fails to improve (for example, moving the EV down did not reduce a blown-out light bulb reflection), the coach marks the suggestion as ineffective for this scene and suppresses it for **30 seconds**.

---

## 4. Opt-In Pose Guide Skeleton

Body coaching is completely opt-in so it never gets in the way of casual shooting:
1. Tap the **Pose Guide** icon (🧍) in the top utility bar.
2. When a person is detected in frame, a delicate, thin target wireframe (1.5.dp stroke, 30% opacity) appears aligned to the subject's bounding box.
3. The coach provides real-time kinematic adjustments (e.g., *"Straighten torso (18° lean)"*, *"Level chin by 8°"*).
4. Once the subject matches the composition target (match score $\ge$ 82%), the skeleton turns **emerald green**, displays **"✓ Good"**, and fades away so you can capture the shot.

---

## 5. Live Debug Telemetry Overlay

Curious to see what the camera is measuring right now?
- **Long-press on the Coach Pill** to reveal the live telemetry card.
- Displays:
  - Real-time engine FPS and inference latency (p50 / p95 in ms).
  - Top-3 Places365 environment classifications and confidence scores.
  - Live photometry numbers (average luma, highlight clipping %, shadow crush %).
  - Spatial orientation (device roll °, pitch °, angular velocity rad/s).
  - Optical sharpness (Laplacian variance) and backlight ratio.
  - Active mathematical evidence string.
- Tap `✕` on the top right to close the overlay.

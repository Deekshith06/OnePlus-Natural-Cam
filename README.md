# 📸 OnePlus Natural Cam: Scene Coach

[![Android Build](https://github.com/Deekshith06/OnePlus-Natural-Cam/actions/workflows/build-apk.yml/badge.svg)](https://github.com/Deekshith06/OnePlus-Natural-Cam/actions)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Privacy](https://img.shields.io/badge/Privacy-0%20Internet%20Permissions-brightgreen.svg)](#privacy--bloat-free)
[![Device](https://img.shields.io/badge/Optimized%20for-OnePlus%2011%20%2F%20OxygenOS-red.svg)](#the-problem)

A lightweight, **100% Free & Open-Source (FOSS)** camera app built for OnePlus devices running OxygenOS / ColorOS. Specifically engineered to **eliminate aggressive AI skin-whitening and highlight blowout**, while delivering an intelligent, **100% offline Scene Coach** inspired by Huawei Master AI and professional cinematography direction.

---

## 🎯 Scene Coach (Huawei Master AI Style)

The Scene Coach is a live on-device intelligence engine that continuously analyzes the viewfinder at ~4 fps to tell you (1) how to adjust the camera and (2) how the subject should pose or stand.

* **🚫 Zero Canned Advice:** Every recommendation is calculated strictly from measured physical sensor metrics with transparent evidence strings (e.g. `14% highlights clipped -> EV -0.7`). No static lookup tables.
* **📍 Places365 Environment Classification:** Uses a bundled ResNet18 model trained on Places365 (MIT License) to identify the scene environment (forest path, cafe, beach, urban street, etc.) with EMA smoothing and 2.0s dwell hysteresis.
* **🧍 MoveNet Pose Kinematics:** 17-keypoint body tracking (Apache 2.0) detects standing vs sitting stance, torso lean, head tilt, and grid position.
* **💡 Photometry & Geometry:** Measures sub-sampled luma histograms, highlight clipping (>250), shadow crushing (<15), backlight ratios, device roll tilt, pitch angle, and gyro angular shake.
* **🔄 Closed-Loop Verification:** Re-measures metrics after you tap "Apply". If the metric does not improve by at least 40%, it marks the suggestion ineffective and suppresses it for 30 seconds.
* **✨ Minimal Uncluttered UI:** A single frosted translucent pill at top center (tap to apply, auto-hides after 4s, dismiss ✕ suppresses for 30s). Long-pressing the pill opens a live debug telemetry overlay.
* **📐 Opt-In Wireframe Skeleton:** A delicate 30% opacity target skeleton aligned to the subject box. Turns emerald green and displays "Good" when pose match score $\ge$ 82%.

---

## 🌿 100% Authentic Natural Color Pipeline

All artificial color-shade matrices, color filters, and RenderEffects have been **completely excised**. The imaging pipeline preserves 100% authentic Sony IMX890 / Hasselblad sensor color reproduction without artificial neon hues or digital bleaching.

---

## 🌟 Flagship Camera Features

* **🚫 Zero AI Bleaching:** Forces `CaptureRequest.CONTROL_SCENE_MODE = DISABLED` and strips vendor beauty masks.
* **🎥 4K UHD Video Recording:** Record crisp 4K / 1080p video with high-bitrate audio and zero AI smoothing.
* **🎛️ PRO / Manual Mode:** Full manual control over ISO, Shutter Speed, Focus, and EV compensation.
* **🌙 Nightscape Mode:** Long-exposure night photography with natural shadow retention.
* **👤 Portrait Mode:** Natural background bokeh simulation with authentic human skin tones.
* **🔍 Multi-Lens Switching:** Instant hardware buttons for **0.6x (Ultra-Wide)**, **1.0x (Main 50MP Sony IMX890)**, **2.0x (Telephoto)**, and **5.0x (Zoom)**.
* **📐 Digital Horizon Leveler:** Real-time accelerometer leveler that turns green when the phone is held level.
* **⏱️ Self-Timer & Grid:** 3s / 10s countdown with visual animation and Rule-of-Thirds composition grid.
* **🛡️ 100% Bloat-Free & Offline:** **Zero `android.permission.INTERNET`**. All models bundled locally. No analytics, no ads, no cloud sync.

---

## 📚 Technical Documentation

- [Scene Coach Architecture & Thresholds Specification](docs/COACH_SPEC.md)
- [Scene Coach User Guide](docs/COACH_GUIDE.md)
- [Bundled Models & Licenses Documentation](docs/MODELS.md)
- [QA Verification & Test Report](docs/TEST_REPORT.md)

---

## 📄 License

Licensed under the [Apache License, Version 2.0](LICENSE).

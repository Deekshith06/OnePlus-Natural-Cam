# 📸 OnePlus Natural Cam

[![Android Build](https://github.com/Deekshith06/OnePlus-Natural-Cam/actions/workflows/build-apk.yml/badge.svg)](https://github.com/Deekshith06/OnePlus-Natural-Cam/actions)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Privacy](https://img.shields.io/badge/Privacy-0%20Internet%20Permissions-brightgreen.svg)](#privacy--bloat-free)
[![Device](https://img.shields.io/badge/Optimized%20for-OnePlus%2011%20%2F%20OxygenOS-red.svg)](#the-problem)

A lightweight, **100% Free & Open-Source (FOSS)** camera app built for OnePlus devices running OxygenOS / ColorOS. Specifically engineered to **eliminate the aggressive AI skin-whitening, highlight blowout, and milky pale wash** produced by stock OnePlus camera post-processing.

---

## 🛑 The Problem with the Stock OnePlus Camera

On recent OxygenOS versions (especially on flagship devices like the **OnePlus 11** with Sony IMX890 sensors), the native camera software runs an aggressive AI post-processing stack:
1. **Skin Bleaching:** Automatic face detection applies a beauty and whitening filter that strips skin color and depth.
2. **Artificial Shadow-Lifting:** The camera merges frames into a flat, milky-white haze with blown-out highlights.
3. **AI Detail Boost Halos:** Digital sharpening creates unnatural glowing white edges around subjects.
4. **Hardcoded Firmware Logic:** Even if you turn off "AI" and "HDR" toggles in the stock camera, the underlying JPEG tone-mapping curve still forcibly bleaches photos.

---

## ✨ The Solution: OnePlus Natural Cam

**OnePlus Natural Cam** bypasses proprietary vendor whitening algorithms by interacting directly with the Android **CameraX & Camera2** hardware pipeline:

* **🚫 Zero AI Bleaching:** Forces `CaptureRequest.CONTROL_SCENE_MODE = DISABLED` and completely shuts off vendor facial retouch extensions.
* **🎨 Authentic Skin Tones & Contrast:** Restores true-to-life skin tones, deep organic shadows, and rich contrast.
* **⚡ Zero Shutter Lag (ZSL):** Instantaneous capture—no background processing freeze, no waiting wheel.
* **☀️ Highlight-Protection Tuning:** Features a default -0.3 EV highlight-protection bias with a live on-screen exposure slider (-2.0 EV to +2.0 EV) to prevent clipped whites.
* **🔍 Multi-Lens Switching:** Seamless switching between **0.6x (Ultra-Wide)**, **1.0x (Main 50MP)**, and **2.0x (Telephoto)**.
* **🛡️ 100% Bloat-Free & Private:** **Zero `android.permission.INTERNET`**. Does not make a single network connection. No analytics, no telemetry, no ads, under 6 MB.
* **📱 No Root Required:** Installs as a standalone APK on any OnePlus phone running Android 8.0+. Your stock camera remains completely untouched.

---

## 📥 Download & Installation

1. Go to the [Releases](https://github.com/Deekshith06/OnePlus-Natural-Cam/releases) tab (or download the latest APK from [GitHub Actions Artifacts](https://github.com/Deekshith06/OnePlus-Natural-Cam/actions)).
2. Tap the downloaded `.apk` file on your OnePlus phone.
3. Tap **Install** and grant Camera/Microphone permissions.
4. Enjoy natural, contrast-rich photos without pale AI skin whitening!

---

## 🛠️ Building from Source

### Prerequisites:
* Android Studio (Koala / Ladybug or newer)
* JDK 17
* Android SDK 35

### Command Line Build:
```bash
git clone https://github.com/Deekshith06/OnePlus-Natural-Cam.git
cd OnePlus-Natural-Cam
./gradlew assembleDebug
```
The compiled APK will be output to:
`app/build/outputs/apk/debug/app-debug.apk`

---

## 📋 Technical Architecture

* **UI Layer:** Jetpack Compose (Material 3 Dark Theme with OnePlus Red accents)
* **Camera Engine:** Android CameraX (`1.3.4`)
* **Hardware Interop:** Camera2Interop parameter injection:
  * `CaptureRequest.CONTROL_SCENE_MODE_DISABLED`
  * `CaptureRequest.EDGE_MODE_HIGH_QUALITY`
  * `CaptureRequest.TONEMAP_MODE_HIGH_QUALITY`
  * `CaptureRequest.COLOR_CORRECTION_MODE_HIGH_QUALITY`
* **Storage:** Native MediaStore API saving directly to `DCIM/NaturalCam` with EXIF orientation metadata.

---

## 🤝 Contributing

Contributions, bug reports, and device-specific test reports from other OnePlus models (OnePlus 12, 10 Pro, 9 Pro, Nord series) are welcome! Feel free to open an Issue or submit a Pull Request.

---

## 📄 License

Licensed under the [Apache License, Version 2.0](LICENSE).

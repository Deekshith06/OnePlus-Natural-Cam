# Bundled Offline Machine Learning Models

OnePlus Natural Cam bundles 100% offline machine learning models directly within `app/src/main/assets/models/`. The application requires **zero internet access** (`android.permission.INTERNET` is strictly omitted). All inferences execute locally on the Snapdragon 8 Gen 2 Qualcomm Hexagon NPU / GPU via TensorFlow Lite with automatic 4-thread CPU fallback.

---

## 1. Scene & Place Recognition: Places365 ResNet18

- **Task:** Photographic environment and place category classification.
- **Model Architecture:** ResNet18 trained on the Places365-Standard dataset.
- **File Asset:** `app/src/main/assets/models/places365.tflite` (22.8 MB)
- **Labels Asset:** `app/src/main/assets/models/categories_places365.txt` (365 categories)
- **License:** **MIT License** (CSAIL Vision / Places365 Project).
- **Source:** [Places365: An Image Database for Deep Scene Understanding](https://github.com/CSAILVision/places365) (Zhou et al., TPAMI 2017).
- **Input Tensor Contract:**
  - Data format: `[1, 3, 224, 224]` Float32 (NCHW).
  - Normalization: ImageNet standard channel mean `[0.485, 0.456, 0.406]` and std `[0.229, 0.224, 0.225]`.
- **Output Tensor Contract:**
  - Logits: `[1, 365]` Float32.
  - Post-processing: Numerically stable Softmax, followed by Exponential Moving Average (EMA, $\alpha = 0.20$) smoothing and a 2.0s dwell hysteresis.
- **Why Places365 vs ImageNet?**
  ImageNet classifies discrete objects (e.g., "coffee mug", "laptop", "tabby cat"), leading to inappropriate suggestions for scene composition. Places365 classifies holistic environments (e.g., "forest path", "botanical garden", "coffee shop", "beach", "urban street", "courtyard"), providing environment context for rule prioritization.

---

## 2. Human Pose Kinematics: MoveNet Lightning

- **Task:** 17-keypoint single-person pose estimation, posture kinematics (standing vs sitting angles), torso lean, head tilt, and grid position.
- **Model Architecture:** MoveNet Lightning (quantized float16/int8 backbone).
- **File Asset:** `app/src/main/assets/models/movenet_lightning.tflite` (4.5 MB)
- **License:** **Apache License 2.0** (Google MediaPipe / TensorFlow Authors).
- **Source:** [TensorFlow Hub MoveNet SinglePose Lightning](https://tfhub.dev/google/movenet/singlepose/lightning/4).
- **Input Tensor Contract:**
  - Data format: `[1, 192, 192, 3]` Float32 (NHWC).
  - Value range: `[0.0, 255.0]`.
- **Output Tensor Contract:**
  - Shape: `[1, 1, 17, 3]` Float32 where each keypoint contains `(y, x, confidence)`.
- **Keypoints Tracked:**
  - 0: Nose
  - 1: Left Eye, 2: Right Eye
  - 3: Left Ear, 4: Right Ear
  - 5: Left Shoulder, 6: Right Shoulder
  - 7: Left Elbow, 8: Right Elbow
  - 9: Left Wrist, 10: Right Wrist
  - 11: Left Hip, 12: Right Hip
  - 13: Left Knee, 14: Right Knee
  - 15: Left Ankle, 16: Right Ankle

---

## 3. Failure Safety & Graceful Degradation

Both models are loaded during analyzer initialization. If NPU hardware acceleration fails or is unsupported on a given ROM/device, the engine catches the exception and falls back to a 4-thread CPU interpreter. If model loading fails entirely:
- The camera preview and capture pipeline **NEVER crash**.
- The Scene Coach gracefully falls back to pure deterministic photometric rules (histogram, clipped highlights, crushed shadows) and spatial geometry rules (device roll, pitch, angular gyro shake).

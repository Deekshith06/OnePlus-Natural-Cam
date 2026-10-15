package com.deekshith.naturalcam.coach

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min

/**
 * Offline Pose Kinematics and Landmarking powered by MoveNet Lightning (Apache 2.0).
 * Detects 17 keypoints with confidence, estimates standing vs sitting postures,
 * torso lean angle, head tilt, grid position, and bounding box.
 */
class MoveNetPoseDetector(private val context: Context) {
    private var interpreter: Interpreter? = null
    private var inputBuffer: ByteBuffer? = null
    private var inputDataType: DataType = DataType.UINT8
    var isInitialized = false
        private set

    init {
        loadModel()
    }

    private fun loadModel() {
        try {
            val afd = context.assets.openFd("models/movenet_lightning.tflite")
            val inputStream = FileInputStream(afd.fileDescriptor)
            val fileChannel = inputStream.channel
            val modelBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.declaredLength)

            // Use 4-thread CPU with XNNPACK - stable, high throughput, avoids vendor NNAPI driver segfaults on Android 16
            val options = Interpreter.Options().apply {
                setNumThreads(4)
            }
            interpreter = Interpreter(modelBuffer, options)
            isInitialized = true
            initBuffer()
            Log.i(TAG, "MoveNet Lightning loaded with 4-thread CPU")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize MoveNet: ${e.message}", e)
        }
    }

    private fun initBuffer() {
        val tflite = interpreter ?: return
        val tensor = tflite.getInputTensor(0)
        inputDataType = tensor.dataType()
        val bytesPerChannel = when (inputDataType) {
            DataType.UINT8, DataType.INT8 -> 1
            DataType.INT32, DataType.FLOAT32 -> 4
            else -> 1
        }
        val totalBytes = 1 * 192 * 192 * 3 * bytesPerChannel
        inputBuffer = ByteBuffer.allocateDirect(totalBytes).apply {
            order(ByteOrder.nativeOrder())
        }
        Log.i(TAG, "MoveNet initialized: input shape=${tensor.shape().contentToString()} type=$inputDataType bufferBytes=$totalBytes")
    }

    fun estimatePose(bitmap: Bitmap): HumanPoseState? {
        val tflite = interpreter ?: return null
        val buf = inputBuffer ?: return null
        if (!isInitialized) return null

        try {
            val scaled = Bitmap.createScaledBitmap(bitmap, 192, 192, true)
            buf.rewind()

            val intValues = IntArray(192 * 192)
            scaled.getPixels(intValues, 0, 192, 0, 0, 192, 192)

            when (inputDataType) {
                DataType.UINT8 -> {
                    for (pixel in intValues) {
                        buf.put(((pixel shr 16) and 0xFF).toByte())
                        buf.put(((pixel shr 8) and 0xFF).toByte())
                        buf.put((pixel and 0xFF).toByte())
                    }
                }
                DataType.INT32 -> {
                    for (pixel in intValues) {
                        buf.putInt((pixel shr 16) and 0xFF)
                        buf.putInt((pixel shr 8) and 0xFF)
                        buf.putInt(pixel and 0xFF)
                    }
                }
                DataType.INT8 -> {
                    for (pixel in intValues) {
                        buf.put((((pixel shr 16) and 0xFF) - 128).toByte())
                        buf.put((((pixel shr 8) and 0xFF) - 128).toByte())
                        buf.put(((pixel and 0xFF) - 128).toByte())
                    }
                }
                else -> { // FLOAT32
                    for (pixel in intValues) {
                        buf.putFloat(((pixel shr 16) and 0xFF).toFloat())
                        buf.putFloat(((pixel shr 8) and 0xFF).toFloat())
                        buf.putFloat((pixel and 0xFF).toFloat())
                    }
                }
            }

            if (scaled != bitmap) scaled.recycle()

            // Output tensor [1, 1, 17, 3]: [y, x, score]
            val outputTensor = Array(1) { Array(1) { Array(17) { FloatArray(3) } } }
            tflite.run(buf, outputTensor)

            val rawKeypoints = outputTensor[0][0]
            val keypoints = mutableListOf<PoseKeypoint>()

            var minX = 1.0f; var maxX = 0.0f
            var minY = 1.0f; var maxY = 0.0f
            var confidentPointsCount = 0

            for (i in 0 until 17) {
                val y = rawKeypoints[i][0].coerceIn(0f, 1f)
                val x = rawKeypoints[i][1].coerceIn(0f, 1f)
                val score = rawKeypoints[i][2].coerceIn(0f, 1f)

                keypoints.add(PoseKeypoint(i, x, y, score))

                if (score >= CoachThresholds.KEYPOINT_CONFIDENCE_MIN) {
                    minX = min(minX, x)
                    maxX = max(maxX, x)
                    minY = min(minY, y)
                    maxY = max(maxY, y)
                    confidentPointsCount++
                }
            }

            // Need at least 4 confident keypoints to consider a person detected
            if (confidentPointsCount < 4 || maxX <= minX || maxY <= minY) {
                return null
            }

            val boundingBox = CoachRectF(minX, minY, maxX, maxY)

            // Posture Kinematics
            // Keypoint mapping:
            // 5: l_shoulder, 6: r_shoulder
            // 11: l_hip, 12: r_hip
            // 13: l_knee, 14: r_knee
            // 15: l_ankle, 16: r_ankle
            val lHip = keypoints[11]; val rHip = keypoints[12]
            val lKnee = keypoints[13]; val rKnee = keypoints[14]
            val lAnkle = keypoints[15]; val rAnkle = keypoints[16]
            val lShoulder = keypoints[5]; val rShoulder = keypoints[6]
            val lEye = keypoints[1]; val rEye = keypoints[2]
            val lWrist = keypoints[9]; val rWrist = keypoints[10]

            // Calculate leg angles (hip - knee - ankle)
            val lLegAngle = computeAngle(lHip, lKnee, lAnkle)
            val rLegAngle = computeAngle(rHip, rKnee, rAnkle)

            val isStanding = when {
                lLegAngle != null && rLegAngle != null -> lLegAngle > 150f || rLegAngle > 150f
                lLegAngle != null -> lLegAngle > 150f
                rLegAngle != null -> rLegAngle > 150f
                else -> (maxY - minY) > 0.50f // Upper body + lower body visible vertically
            }

            val isSitting = when {
                lLegAngle != null && rLegAngle != null -> lLegAngle in 75f..135f || rLegAngle in 75f..135f
                lLegAngle != null -> lLegAngle in 75f..135f
                rLegAngle != null -> rLegAngle in 75f..135f
                else -> !isStanding && (maxY - minY) < 0.55f && (maxX - minX) > 0.30f
            }

            // Torso lean angle vs vertical
            val midShoulderX = (lShoulder.x + rShoulder.x) / 2f
            val midShoulderY = (lShoulder.y + rShoulder.y) / 2f
            val midHipX = (lHip.x + rHip.x) / 2f
            val midHipY = (lHip.y + rHip.y) / 2f
            val torsoLeanAngleDeg = Math.toDegrees(atan2((midShoulderX - midHipX).toDouble(), (midHipY - midShoulderY).toDouble())).toFloat()

            // Head tilt vs horizontal
            val headTiltAngleDeg = if (lEye.confidence > 0.3f && rEye.confidence > 0.3f) {
                Math.toDegrees(atan2((rEye.y - lEye.y).toDouble(), (rEye.x - lEye.x).toDouble())).toFloat()
            } else 0f

            val handsVisible = lWrist.confidence > 0.40f || rWrist.confidence > 0.40f

            val centerX = (minX + maxX) / 2f
            val gridPosition = when {
                centerX < 0.40f -> GridThirdsRegion.LEFT_THIRD
                centerX > 0.60f -> GridThirdsRegion.RIGHT_THIRD
                else -> GridThirdsRegion.CENTER_THIRD
            }

            return HumanPoseState(
                boundingBox = boundingBox,
                keypoints = keypoints,
                isStanding = isStanding,
                isSitting = isSitting,
                torsoLeanAngleDeg = torsoLeanAngleDeg,
                headTiltAngleDeg = headTiltAngleDeg,
                handsVisible = handsVisible,
                gridPosition = gridPosition
            )
        } catch (e: Exception) {
            Log.e(TAG, "Pose estimation failed: ${e.message}", e)
            return null
        }
    }

    private fun computeAngle(a: PoseKeypoint, b: PoseKeypoint, c: PoseKeypoint): Float? {
        if (a.confidence < 0.25f || b.confidence < 0.25f || c.confidence < 0.25f) return null
        val v1x = a.x - b.x; val v1y = a.y - b.y
        val v2x = c.x - b.x; val v2y = c.y - b.y
        val angle1 = atan2(v1y.toDouble(), v1x.toDouble())
        val angle2 = atan2(v2y.toDouble(), v2x.toDouble())
        var diff = Math.toDegrees(abs(angle1 - angle2))
        if (diff > 180.0) diff = 360.0 - diff
        return diff.toFloat()
    }

    fun close() {
        interpreter?.close()
        interpreter = null
        isInitialized = false
    }

    companion object {
        private const val TAG = "MoveNetPoseDetector"
    }
}

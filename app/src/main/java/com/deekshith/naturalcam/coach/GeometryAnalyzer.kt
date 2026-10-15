package com.deekshith.naturalcam.coach

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer
import kotlin.math.sqrt

/**
 * Spatial Geometry, Horizon, Shake, and Optical Sharpness Analyzer.
 * Measures device roll, pitch, gyro angular velocity, and Laplacian edge sharpness.
 */
class GeometryAnalyzer(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    var currentRollDeg: Float = 0f
        private set
    var currentPitchDeg: Float = 0f
        private set
    var currentAngularSpeedRadS: Float = 0f
        private set

    init {
        rotationSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        gyroSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                val rotationMatrix = FloatArray(9)
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                val orientation = FloatArray(3)
                SensorManager.getOrientation(rotationMatrix, orientation)

                // roll is orientation[2], pitch is orientation[1] in radians
                val rawPitch = Math.toDegrees(orientation[1].toDouble()).toFloat()
                val rawRoll = Math.toDegrees(orientation[2].toDouble()).toFloat()

                // EMA smoothing (alpha = 0.25)
                val alpha = CoachThresholds.EMA_ALPHA_GEOMETRY
                currentPitchDeg = alpha * rawPitch + (1f - alpha) * currentPitchDeg
                currentRollDeg = alpha * rawRoll + (1f - alpha) * currentRollDeg
            }
            Sensor.TYPE_GYROSCOPE -> {
                val wx = event.values[0]
                val wy = event.values[1]
                val wz = event.values[2]
                val speed = sqrt(wx * wx + wy * wy + wz * wz)
                val alpha = 0.30f
                currentAngularSpeedRadS = alpha * speed + (1f - alpha) * currentAngularSpeedRadS
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    fun close() {
        sensorManager.unregisterListener(this)
    }

    companion object {
        /**
         * Computes Laplacian variance on the central 50% region of the luma plane.
         * Measures focus sharpness / motion blur.
         */
        fun computeLaplacianVariance(image: ImageProxy): Float {
            val yPlane = image.planes[0]
            val buffer: ByteBuffer = yPlane.buffer
            val rowStride = yPlane.rowStride
            val width = image.width
            val height = image.height

            val startX = (width * 0.25f).toInt()
            val endX = (width * 0.75f).toInt()
            val startY = (height * 0.25f).toInt()
            val endY = (height * 0.75f).toInt()

            var sum = 0.0
            var sumSq = 0.0
            var count = 0
            val step = 8 // stride
            val limit = buffer.limit()

            for (y in startY + 1 until endY - 1 step step) {
                val rowOffset = y * rowStride
                val rowAbove = (y - 1) * rowStride
                val rowBelow = (y + 1) * rowStride

                for (x in startX + 1 until endX - 1 step step) {
                    val idx = rowOffset + x
                    if (idx < limit && rowAbove + x < limit && rowBelow + x < limit) {
                        val center = buffer.get(idx).toInt() and 0xFF
                        val top = buffer.get(rowAbove + x).toInt() and 0xFF
                        val bottom = buffer.get(rowBelow + x).toInt() and 0xFF
                        val left = buffer.get(rowOffset + x - 1).toInt() and 0xFF
                        val right = buffer.get(rowOffset + x + 1).toInt() and 0xFF

                        // 3x3 Laplacian: top + bottom + left + right - 4 * center
                        val laplacian = (top + bottom + left + right - 4 * center).toDouble()
                        sum += laplacian
                        sumSq += laplacian * laplacian
                        count++
                    }
                }
            }

            if (count < 2) return 100.0f
            val mean = sum / count
            val variance = (sumSq / count) - (mean * mean)
            return variance.coerceAtLeast(0.0).toFloat()
        }
    }
}

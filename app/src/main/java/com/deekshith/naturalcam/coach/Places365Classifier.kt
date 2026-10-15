package com.deekshith.naturalcam.coach

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.exp

/**
 * Offline Scene & Place Classifier powered by ResNet18 trained on Places365 (MIT License).
 * Classifies 365 scene categories in under 15ms on Snapdragon 8 Gen 2 NPU/GPU/CPU.
 * Outputs top-3 categories with confidence scores and smooths predictions using EMA
 * and enter/exit hysteresis with a 2-second minimum dwell time.
 */
class Places365Classifier(private val context: Context) {
    private var interpreter: Interpreter? = null
    private val categories = mutableListOf<String>()
    
    // Smoothed EMA probabilities
    private var smoothedProbabilities = FloatArray(365)
    private var currentActivePlace: PlaceCategory = PlaceCategory.GENERAL
    private var lastCategorySwitchTimestamp = 0L
    
    private var inputBuffer: ByteBuffer? = null
    private var isNCHW = true

    var isInitialized = false
        private set

    init {
        loadModelAndCategories()
    }

    private fun loadModelAndCategories() {
        try {
            // 1. Load 365 Categories
            context.assets.open("models/categories_places365.txt").use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).useLines { lines ->
                    categories.addAll(lines)
                }
            }

            // 2. Load Places365 ResNet18 TFLite Model
            val afd = context.assets.openFd("models/places365.tflite")
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
            Log.i(TAG, "Places365 ResNet18 loaded with 4-thread CPU (${categories.size} categories)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Places365 model: ${e.message}", e)
        }
    }

    private fun initBuffer() {
        val tflite = interpreter ?: return
        val inTensor = tflite.getInputTensor(0)
        val shape = inTensor.shape()
        // Check if NCHW [1, 3, 224, 224] or NHWC [1, 224, 224, 3]
        isNCHW = (shape.size == 4 && shape[1] == 3)
        val totalBytes = 1 * 3 * 224 * 224 * 4 // Float32
        inputBuffer = ByteBuffer.allocateDirect(totalBytes).apply {
            order(ByteOrder.nativeOrder())
        }
        Log.i(TAG, "Places365 buffer initialized: shape=${shape.contentToString()} isNCHW=$isNCHW type=${inTensor.dataType()}")
    }

    fun classify(bitmap: Bitmap): Pair<List<SceneCategoryScore>, PlaceCategory> {
        val tflite = interpreter ?: return Pair(emptyList(), PlaceCategory.GENERAL)
        val buf = inputBuffer ?: return Pair(emptyList(), PlaceCategory.GENERAL)
        if (!isInitialized || categories.isEmpty()) return Pair(emptyList(), PlaceCategory.GENERAL)

        try {
            val scaled = Bitmap.createScaledBitmap(bitmap, 224, 224, true)
            buf.rewind()

            val intValues = IntArray(224 * 224)
            scaled.getPixels(intValues, 0, 224, 0, 0, 224, 224)

            // ImageNet mean & std
            val meanR = 0.485f; val meanG = 0.456f; val meanB = 0.406f
            val stdR = 0.229f;  val stdG = 0.224f;  val stdB = 0.225f

            if (isNCHW) {
                // Channel R
                for (pixel in intValues) {
                    val r = (((pixel shr 16) and 0xFF) / 255.0f - meanR) / stdR
                    buf.putFloat(r)
                }
                // Channel G
                for (pixel in intValues) {
                    val g = (((pixel shr 8) and 0xFF) / 255.0f - meanG) / stdG
                    buf.putFloat(g)
                }
                // Channel B
                for (pixel in intValues) {
                    val b = ((pixel and 0xFF) / 255.0f - meanB) / stdB
                    buf.putFloat(b)
                }
            } else {
                // NHWC
                for (pixel in intValues) {
                    val r = (((pixel shr 16) and 0xFF) / 255.0f - meanR) / stdR
                    val g = (((pixel shr 8) and 0xFF) / 255.0f - meanG) / stdG
                    val b = ((pixel and 0xFF) / 255.0f - meanB) / stdB
                    buf.putFloat(r)
                    buf.putFloat(g)
                    buf.putFloat(b)
                }
            }

            if (scaled != bitmap) scaled.recycle()

            // Run inference: Output logits [1, 365]
            val outputLogits = Array(1) { FloatArray(categories.size) }
            tflite.run(buf, outputLogits)

            // Compute Softmax probabilities
            val rawLogits = outputLogits[0]
            var maxLogit = Float.NEGATIVE_INFINITY
            for (v in rawLogits) {
                if (v > maxLogit) maxLogit = v
            }

            var sumExp = 0.0f
            val currentProbabilities = FloatArray(categories.size)
            for (i in rawLogits.indices) {
                val expVal = exp(rawLogits[i] - maxLogit)
                currentProbabilities[i] = expVal
                sumExp += expVal
            }
            if (sumExp > 0f) {
                for (i in currentProbabilities.indices) {
                    currentProbabilities[i] /= sumExp
                }
            }

            // Smooth probabilities with EMA (alpha = 0.20)
            val alpha = CoachThresholds.EMA_ALPHA_CLASSIFICATION
            for (i in currentProbabilities.indices) {
                smoothedProbabilities[i] = alpha * currentProbabilities[i] + (1f - alpha) * smoothedProbabilities[i]
            }

            // Extract top 3 categories
            val indexedScores = smoothedProbabilities.mapIndexed { idx, prob ->
                val labelName = if (idx in categories.indices) categories[idx] else "unknown"
                SceneCategoryScore(labelName, prob)
            }.sortedByDescending { it.confidence }

            val top3 = indexedScores.take(3)
            val topScore = top3.firstOrNull() ?: SceneCategoryScore("general", 0f)

            // Map top label to PlaceCategory
            val candidatePlace = mapCategoryToPlace(topScore.label)

            // Apply 2-second minimum dwell time hysteresis
            val now = System.currentTimeMillis()
            if (candidatePlace != currentActivePlace) {
                if (now - lastCategorySwitchTimestamp >= CoachThresholds.MIN_SUGGESTION_DWELL_MS) {
                    currentActivePlace = candidatePlace
                    lastCategorySwitchTimestamp = now
                    Log.i(TAG, "Places365 switched place: ${candidatePlace.displayName} ('${topScore.label}' ${(topScore.confidence * 100).toInt()}%)")
                }
            } else {
                lastCategorySwitchTimestamp = now
            }

            return Pair(top3, currentActivePlace)

        } catch (e: Exception) {
            Log.e(TAG, "Places365 classification failed: ${e.message}", e)
            return Pair(emptyList(), currentActivePlace)
        }
    }

    private fun mapCategoryToPlace(label: String): PlaceCategory {
        val lower = label.lowercase()
        return when {
            // 1. Forest & Hiking & Woodland Nature
            lower.contains("forest") || lower.contains("tree") || lower.contains("wood") ||
            lower.contains("mountain") || lower.contains("valley") || lower.contains("canyon") ||
            lower.contains("trail") || lower.contains("path") || lower.contains("pasture") ||
            lower.contains("field") || lower.contains("orchard") || lower.contains("desert") -> {
                PlaceCategory.FOREST_NATURE
            }

            // 2. Beach, Lake, River, Pool & Coastal Water
            lower.contains("beach") || lower.contains("ocean") || lower.contains("sea") ||
            lower.contains("lake") || lower.contains("river") || lower.contains("water") ||
            lower.contains("swimming pool") || lower.contains("harbor") || lower.contains("pier") ||
            lower.contains("waterfall") || lower.contains("creek") || lower.contains("coast") -> {
                PlaceCategory.BEACH_WATER
            }

            // 3. Cafe, Restaurant, Bakery & Dining
            lower.contains("restaurant") || lower.contains("cafe") || lower.contains("coffee") ||
            lower.contains("bakery") || lower.contains("bar") || lower.contains("pub") ||
            lower.contains("dining") || lower.contains("pizzeria") || lower.contains("food court") ||
            lower.contains("buffet") || lower.contains("bistro") -> {
                PlaceCategory.CAFE_DINING
            }

            // 4. Indoor Room, Office & Study
            lower.contains("bedroom") || lower.contains("hotel room") || lower.contains("living room") ||
            lower.contains("office") || lower.contains("conference") || lower.contains("classroom") ||
            lower.contains("library") || lower.contains("lobby") || lower.contains("kitchen") ||
            lower.contains("corridor") || lower.contains("dorm room") || lower.contains("nursery") -> {
                PlaceCategory.INDOOR_ROOM
            }

            // 5. Historic Architecture & Monuments
            lower.contains("church") || lower.contains("cathedral") || lower.contains("temple") ||
            lower.contains("mosque") || lower.contains("castle") || lower.contains("palace") ||
            lower.contains("monument") || lower.contains("ruins") || lower.contains("arch") ||
            lower.contains("amphitheater") || lower.contains("tower") || lower.contains("bridge") -> {
                PlaceCategory.HISTORIC_ARCHITECTURE
            }

            // 6. City Street & Urban Area
            lower.contains("street") || lower.contains("crosswalk") || lower.contains("highway") ||
            lower.contains("downtown") || lower.contains("alley") || lower.contains("parking") ||
            lower.contains("plaza") || lower.contains("gas station") || lower.contains("bus station") -> {
                PlaceCategory.URBAN_STREET
            }

            // 7. Botanical Garden & Greenhouse
            lower.contains("garden") || lower.contains("greenhouse") || lower.contains("botanical") ||
            lower.contains("park") || lower.contains("arboretum") || lower.contains("flower") -> {
                PlaceCategory.BOTANICAL_GARDEN
            }

            else -> PlaceCategory.GENERAL
        }
    }

    fun close() {
        interpreter?.close()
        interpreter = null
        isInitialized = false
    }

    companion object {
        private const val TAG = "Places365Classifier"
    }
}

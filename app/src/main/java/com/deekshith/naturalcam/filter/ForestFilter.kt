package com.deekshith.naturalcam.filter

import android.graphics.*

enum class ForestDslrFilter(
    val id: String,
    val displayName: String,
    val description: String,
    val matrix: FloatArray
) {
    NATURAL(
        id = "natural",
        displayName = "Natural",
        description = "Pure Sony IMX890 sensor output without alteration",
        matrix = floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
    ),

    EMERALD_FOREST(
        id = "emerald",
        displayName = "Emerald Pine",
        description = "Deep lush evergreen foliage with enriched moss and suppressed neon yellows",
        matrix = floatArrayOf(
            0.92f, 0.05f, 0.00f, 0f, -5f,
            0.00f, 1.18f, 0.02f, 0f, 8f,
            0.02f, 0.05f, 0.95f, 0f, -2f,
            0.00f, 0.00f, 0.00f, 1f, 0f
        )
    ),

    MOODY_WOODS(
        id = "moody",
        displayName = "Moody Woods",
        description = "Nordic misty woodland look with muted greens and cool teal shadow tones",
        matrix = floatArrayOf(
            0.85f, 0.10f, 0.00f, 0f, -10f,
            0.05f, 0.90f, 0.05f, 0f, -4f,
            0.00f, 0.12f, 1.05f, 0f, 12f,
            0.00f, 0.00f, 0.00f, 1f, 0f
        )
    ),

    GOLDEN_CANOPY(
        id = "golden",
        displayName = "Golden Canopy",
        description = "Warm autumn sun rays breaking through tree branches with amber highlights",
        matrix = floatArrayOf(
            1.15f, 0.05f, 0.00f, 0f, 15f,
            0.05f, 1.08f, 0.00f, 0f, 10f,
            0.00f, 0.00f, 0.85f, 0f, -15f,
            0.00f, 0.00f, 0.00f, 1f, 0f
        )
    ),

    FUJI_VELVIA(
        id = "velvia",
        displayName = "Velvia Landscape",
        description = "Iconic Fuji landscape slide film: deep contrast, rich bark browns, and vivid leaves",
        matrix = floatArrayOf(
            1.20f, -0.05f, -0.05f, 0f, -10f,
            -0.05f, 1.25f, -0.05f, 0f, -10f,
            -0.05f, -0.05f, 1.15f, 0f, -8f,
            0.00f, 0.00f, 0.00f, 1f, 0f
        )
    ),

    ANSEL_MONO(
        id = "ansel",
        displayName = "Ansel Woods B&W",
        description = "Ansel Adams monochrome with red spectral filter to darken skies and pop foliage",
        matrix = floatArrayOf(
            0.50f, 0.40f, 0.10f, 0f, -15f,
            0.50f, 0.40f, 0.10f, 0f, -15f,
            0.50f, 0.40f, 0.10f, 0f, -15f,
            0.00f, 0.00f, 0.00f, 1f, 0f
        )
    );

    fun applyToBitmap(source: Bitmap): Bitmap {
        if (this == NATURAL) return source

        val output = Bitmap.createBitmap(source.width, source.height, source.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix(matrix))
        }
        canvas.drawBitmap(source, 0f, 0f, paint)
        return output
    }
}

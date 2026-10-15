package com.deekshith.naturalcam.filter

import android.graphics.*

enum class FilterCategory(val label: String) {
    ALL("All"),
    FOREST("Forest & Nature"),
    LOW_LIGHT("Low-Light & Night"),
    MONOCHROME("Black & White")
}

enum class ForestDslrFilter(
    val id: String,
    val displayName: String,
    val description: String,
    val category: FilterCategory,
    val matrix: FloatArray
) {
    NATURAL(
        id = "natural",
        displayName = "Natural",
        description = "Pure Sony IMX890 sensor output with authentic Hasselblad contrast",
        category = FilterCategory.ALL,
        matrix = floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
    ),

    // --- Forest & Nature Suite ---
    EMERALD_FOREST(
        id = "emerald",
        displayName = "Emerald Pine",
        description = "Deep lush evergreen foliage with enriched moss and suppressed neon yellows",
        category = FilterCategory.FOREST,
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
        category = FilterCategory.FOREST,
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
        category = FilterCategory.FOREST,
        matrix = floatArrayOf(
            1.15f, 0.05f, 0.00f, 0f, 15f,
            0.05f, 1.08f, 0.00f, 0f, 10f,
            0.00f, 0.00f, 0.85f, 0f, -15f,
            0.00f, 0.00f, 0.00f, 1f, 0f
        )
    ),

    FUJI_VELVIA(
        id = "velvia",
        displayName = "Velvia Slide",
        description = "Classic landscape slide film: deep contrast, rich bark browns, and punchy greens",
        category = FilterCategory.FOREST,
        matrix = floatArrayOf(
            1.20f, -0.05f, -0.05f, 0f, -10f,
            -0.05f, 1.25f, -0.05f, 0f, -10f,
            -0.05f, -0.05f, 1.15f, 0f, -8f,
            0.00f, 0.00f, 0.00f, 1f, 0f
        )
    ),

    MOSS_STREAM(
        id = "moss_stream",
        displayName = "Moss & River",
        description = "Crystal clear water reflections with enriched mineral greens and damp stone contrast",
        category = FilterCategory.FOREST,
        matrix = floatArrayOf(
            0.90f, 0.08f, 0.00f, 0f, -8f,
            0.02f, 1.12f, 0.08f, 0f, 6f,
            0.00f, 0.05f, 1.15f, 0f, 10f,
            0.00f, 0.00f, 0.00f, 1f, 0f
        )
    ),

    // --- Low Light & Nightscape Suite ---
    TWILIGHT_BLUE(
        id = "twilight",
        displayName = "Twilight Blue",
        description = "Deep indigo blue-hour night sky with preserved golden lantern and fire glow",
        category = FilterCategory.LOW_LIGHT,
        matrix = floatArrayOf(
            0.88f, 0.02f, 0.10f, 0f, -12f,
            0.02f, 0.92f, 0.10f, 0f, -8f,
            0.00f, 0.08f, 1.25f, 0f, 16f,
            0.00f, 0.00f, 0.00f, 1f, 0f
        )
    ),

    CYBER_NIGHT(
        id = "cyber_night",
        displayName = "Cyber Night",
        description = "Teal-tinted deep shadows with boosted magenta and neon highlights for dark environments",
        category = FilterCategory.LOW_LIGHT,
        matrix = floatArrayOf(
            1.10f, -0.05f, 0.08f, 0f, 10f,
            -0.02f, 0.95f, 0.10f, 0f, -6f,
            0.05f, 0.10f, 1.20f, 0f, 18f,
            0.00f, 0.00f, 0.00f, 1f, 0f
        )
    ),

    CAMPFIRE_GLOW(
        id = "campfire",
        displayName = "Campfire Amber",
        description = "Warm tungsten and candlelight glow that removes harsh blue noise from night scenes",
        category = FilterCategory.LOW_LIGHT,
        matrix = floatArrayOf(
            1.22f, 0.08f, 0.00f, 0f, 18f,
            0.02f, 1.05f, 0.00f, 0f, 8f,
            0.00f, 0.00f, 0.80f, 0f, -20f,
            0.00f, 0.00f, 0.00f, 1f, 0f
        )
    ),

    // --- Monochrome Suite ---
    LEICA_NOIR(
        id = "leica_noir",
        displayName = "Leica Noir",
        description = "Deep inky blacks, silky tonal midtones, and crisp highlights reminiscent of Leica Monochrom",
        category = FilterCategory.MONOCHROME,
        matrix = floatArrayOf(
            0.60f, 0.35f, 0.05f, 0f, -20f,
            0.60f, 0.35f, 0.05f, 0f, -20f,
            0.60f, 0.35f, 0.05f, 0f, -20f,
            0.00f, 0.00f, 0.00f, 1f, 0f
        )
    ),

    ANSEL_MONO(
        id = "ansel",
        displayName = "Ansel Woods B&W",
        description = "Ansel Adams monochrome with red spectral filter to darken skies and pop foliage",
        category = FilterCategory.MONOCHROME,
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

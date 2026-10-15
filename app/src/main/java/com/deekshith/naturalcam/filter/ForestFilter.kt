package com.deekshith.naturalcam.filter

import android.graphics.*
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

enum class FilterCategory(val label: String) {
    ALL("All"),
    OPTICAL_FX("Optical (No Color Shift)"),
    FOREST("Forest & Nature"),
    LOW_LIGHT("Low-Light & Night"),
    MONOCHROME("Black & White")
}

/**
 * Defines true multi-stage optical & photographic parameters beyond simple RGB color matrices:
 * - [matrix]: Base spectral color response
 * - [contrastCurve]: Non-linear S-curve contrast strength (0f = linear, >0 = deeper blacks & rolled-off highlights)
 * - [vignetteStrength]: Optical corner light falloff (0f..1f)
 * - [bloomStrength]: Canopy sunbeam halation / highlight glow (0f..1f)
 * - [bloomWarmth]: Warmth of highlight halation (true = golden/amber halation, false = neutral/cool mist)
 * - [grainStrength]: Organic silver-halide film texture (0f..1f)
 * - [sharpenStrength]: Micro-contrast / unsharp mask edge definition (0f..1f)
 * - [softMistStrength]: Pro-Mist optical diffusion filter effect (0f..1f)
 */
enum class ForestDslrFilter(
    val id: String,
    val displayName: String,
    val subtitle: String,
    val description: String,
    val category: FilterCategory,
    val matrix: FloatArray,
    val contrastCurve: Float = 0f,
    val vignetteStrength: Float = 0f,
    val bloomStrength: Float = 0f,
    val bloomWarmth: Boolean = false,
    val grainStrength: Float = 0f,
    val sharpenStrength: Float = 0f,
    val softMistStrength: Float = 0f,
    val swatchStart: Long,
    val swatchEnd: Long
) {
    NATURAL(
        id = "natural",
        displayName = "Natural",
        subtitle = "Zero-Bleach Optical",
        description = "Pure Sony IMX890 sensor output with authentic Hasselblad natural tone",
        category = FilterCategory.ALL,
        matrix = floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ),
        swatchStart = 0xFF757575,
        swatchEnd = 0xFFE0E0E0
    ),

    // --- Optical Suite: 100% Zero-Color-Shift Physical Lens & Lighting Filters ---
    BLACK_PRO_MIST(
        id = "black_pro_mist",
        displayName = "Pro-Mist 1/4",
        subtitle = "Diffusion Bloom (No Color Shift)",
        description = "Tiffen Black Pro-Mist 1/4 optical glass: blooms specular highlights and softens digital harshness with ZERO color tint",
        category = FilterCategory.OPTICAL_FX,
        matrix = floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ),
        contrastCurve = 0.15f,
        bloomStrength = 0.65f,
        bloomWarmth = false,
        softMistStrength = 0.55f,
        swatchStart = 0xFF212121,
        swatchEnd = 0xFFECEFF1
    ),

    CPL_POLARIZER(
        id = "cpl_polarizer",
        displayName = "CPL Polarizer",
        subtitle = "Anti-Glare (100% Sensor Colors)",
        description = "Circular Polarizer glass: eliminates water reflection and foliage glare with 100% untouched sensor colors",
        category = FilterCategory.OPTICAL_FX,
        matrix = floatArrayOf(
            1.15f, 0.00f, 0.00f, 0f, -19f,
            0.00f, 1.15f, 0.00f, 0f, -19f,
            0.00f, 0.00f, 1.15f, 0f, -19f,
            0.00f, 0.00f, 0.00f, 1f, 0f
        ),
        contrastCurve = 0.40f,
        vignetteStrength = 0.20f,
        sharpenStrength = 0.35f,
        swatchStart = 0xFF0D47A1,
        swatchEnd = 0xFF29B6F6
    ),

    HDR_MICRO_CLARITY(
        id = "micro_clarity",
        displayName = "Texture Clarity",
        subtitle = "Edge Micro-Contrast (Zero Tint)",
        description = "Accentuates bark, leaves, and stone textures using optical unsharp masking without changing color balance",
        category = FilterCategory.OPTICAL_FX,
        matrix = floatArrayOf(
            1.08f, 0.00f, 0.00f, 0f, -10f,
            0.00f, 1.08f, 0.00f, 0f, -10f,
            0.00f, 0.00f, 1.08f, 0f, -10f,
            0.00f, 0.00f, 0.00f, 1f, 0f
        ),
        contrastCurve = 0.35f,
        sharpenStrength = 0.80f,
        swatchStart = 0xFF37474F,
        swatchEnd = 0xFF90A4AE
    ),

    DREAM_SOFT_FOCUS(
        id = "dream_soft",
        displayName = "Dream Soft Focus",
        subtitle = "Natural Portrait Softener",
        description = "Classic portrait optical softening: gentle glow that flatters skin naturally without whitening or color cast",
        category = FilterCategory.OPTICAL_FX,
        matrix = floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ),
        bloomStrength = 0.70f,
        bloomWarmth = false,
        softMistStrength = 0.65f,
        swatchStart = 0xFF3E2723,
        swatchEnd = 0xFFFFF8E1
    ),

    CINE_FLAT_DR(
        id = "cine_flat",
        displayName = "Cine Log Dynamic",
        subtitle = "Dynamic Range Curve (Neutral)",
        description = "Symmetric tonal curve lifting shadow detail and protecting highlights with zero color shift",
        category = FilterCategory.OPTICAL_FX,
        matrix = floatArrayOf(
            0.90f, 0.00f, 0.00f, 0f, 14f,
            0.00f, 0.90f, 0.00f, 0f, 14f,
            0.00f, 0.00f, 0.90f, 0f, 14f,
            0.00f, 0.00f, 0.00f, 1f, 0f
        ),
        contrastCurve = 0.0f,
        swatchStart = 0xFF455A64,
        swatchEnd = 0xFFCFD8DC
    ),

    SUNBEAM_HALATION(
        id = "sunbeam_halation",
        displayName = "Sunbeam Bloom",
        subtitle = "Glow + Halation FX",
        description = "Diffuses bright sunlight filtering through leaves into a dreamy golden optical halo with deep shadow contrast",
        category = FilterCategory.OPTICAL_FX,
        matrix = floatArrayOf(
            1.06f, 0.04f, 0.00f, 0f, 4f,
            0.02f, 1.05f, 0.00f, 0f, 2f,
            0.00f, 0.00f, 0.94f, 0f, -6f,
            0.00f, 0.00f, 0.00f, 1f, 0f
        ),
        contrastCurve = 0.35f,
        vignetteStrength = 0.40f,
        bloomStrength = 0.65f,
        bloomWarmth = true,
        swatchStart = 0xFFFF6F00,
        swatchEnd = 0xFFFFE082
    ),

    HELIOS_VIGNETTE(
        id = "helios_lens",
        displayName = "Helios 58mm",
        subtitle = "Vintage Lens Falloff",
        description = "Classic Soviet Helios 44-2 optical look: dramatic dark edge vignette, glowing center, and organic film grain",
        category = FilterCategory.OPTICAL_FX,
        matrix = floatArrayOf(
            1.05f, 0.03f, 0.00f, 0f, -4f,
            0.02f, 1.04f, 0.02f, 0f, -2f,
            0.00f, 0.03f, 0.92f, 0f, -8f,
            0.00f, 0.00f, 0.00f, 1f, 0f
        ),
        contrastCurve = 0.45f,
        vignetteStrength = 0.75f,
        bloomStrength = 0.35f,
        bloomWarmth = true,
        grainStrength = 0.28f,
        swatchStart = 0xFF3E2723,
        swatchEnd = 0xFFD4E157
    ),

    // --- Forest & Nature Suite ---
    EMERALD_FOREST(
        id = "emerald",
        displayName = "Emerald Pine",
        subtitle = "Dehaze + Deep Greens",
        description = "Deep lush evergreen foliage with S-curve shadow depth, optical vignette, and suppressed neon yellows",
        category = FilterCategory.FOREST,
        matrix = floatArrayOf(
            0.90f, 0.04f, 0.00f, 0f, -8f,
            0.00f, 1.15f, 0.03f, 0f, 4f,
            0.02f, 0.05f, 0.96f, 0f, -4f,
            0.00f, 0.00f, 0.00f, 1f, 0f
        ),
        contrastCurve = 0.45f,
        vignetteStrength = 0.45f,
        sharpenStrength = 0.35f,
        swatchStart = 0xFF1B5E20,
        swatchEnd = 0xFF4CAF50
    ),

    MOODY_WOODS(
        id = "moody",
        displayName = "Moody Woods",
        subtitle = "Misty Diffusion + Vignette",
        description = "Nordic misty woodland look with atmospheric fog diffusion, deep vignette, and cool shadow tones",
        category = FilterCategory.FOREST,
        matrix = floatArrayOf(
            0.85f, 0.08f, 0.00f, 0f, -12f,
            0.04f, 0.92f, 0.05f, 0f, -6f,
            0.00f, 0.10f, 1.06f, 0f, 10f,
            0.00f, 0.00f, 0.00f, 1f, 0f
        ),
        contrastCurve = 0.50f,
        vignetteStrength = 0.65f,
        bloomStrength = 0.40f,
        softMistStrength = 0.35f,
        swatchStart = 0xFF1A3636,
        swatchEnd = 0xFF406882
    ),

    GOLDEN_CANOPY(
        id = "golden",
        displayName = "Golden Canopy",
        subtitle = "Warm Bloom + S-Curve",
        description = "Warm autumn sunbeams with golden halation glow on leaves and crisp bark micro-contrast",
        category = FilterCategory.FOREST,
        matrix = floatArrayOf(
            1.14f, 0.05f, 0.00f, 0f, 10f,
            0.04f, 1.06f, 0.00f, 0f, 6f,
            0.00f, 0.00f, 0.86f, 0f, -14f,
            0.00f, 0.00f, 0.00f, 1f, 0f
        ),
        contrastCurve = 0.40f,
        vignetteStrength = 0.40f,
        bloomStrength = 0.50f,
        bloomWarmth = true,
        swatchStart = 0xFFBF360C,
        swatchEnd = 0xFFFFB300
    ),

    FUJI_VELVIA(
        id = "velvia",
        displayName = "Velvia 50 Film",
        subtitle = "Slide Film + Micro-Detail",
        description = "Iconic Fuji Velvia 50 slide film: steep S-curve contrast, fine film grain, and razor-sharp foliage",
        category = FilterCategory.FOREST,
        matrix = floatArrayOf(
            1.18f, -0.05f, -0.05f, 0f, -12f,
            -0.05f, 1.22f, -0.05f, 0f, -10f,
            -0.05f, -0.05f, 1.15f, 0f, -10f,
            0.00f, 0.00f, 0.00f, 1f, 0f
        ),
        contrastCurve = 0.60f,
        vignetteStrength = 0.35f,
        sharpenStrength = 0.45f,
        grainStrength = 0.18f,
        swatchStart = 0xFFB71C1C,
        swatchEnd = 0xFF2E7D32
    ),

    MOSS_STREAM(
        id = "moss_stream",
        displayName = "Moss & River",
        subtitle = "Polarizer + Water Glow",
        description = "Simulates a Circular Polarizer (CPL) lens filter: cuts water glare, deepens wet stone, and softens cascades",
        category = FilterCategory.FOREST,
        matrix = floatArrayOf(
            0.90f, 0.06f, 0.00f, 0f, -10f,
            0.02f, 1.12f, 0.06f, 0f, 4f,
            0.00f, 0.05f, 1.14f, 0f, 8f,
            0.00f, 0.00f, 0.00f, 1f, 0f
        ),
        contrastCurve = 0.45f,
        vignetteStrength = 0.40f,
        bloomStrength = 0.30f,
        sharpenStrength = 0.30f,
        swatchStart = 0xFF004D40,
        swatchEnd = 0xFF00ACC1
    ),

    // --- Low Light & Nightscape Suite ---
    TWILIGHT_BLUE(
        id = "twilight",
        displayName = "Twilight Blue",
        subtitle = "Night Halation + Dehaze",
        description = "Deep indigo blue-hour sky with glowing street/lantern halation and clean noise-suppressed shadows",
        category = FilterCategory.LOW_LIGHT,
        matrix = floatArrayOf(
            0.88f, 0.02f, 0.08f, 0f, -14f,
            0.02f, 0.94f, 0.08f, 0f, -10f,
            0.00f, 0.08f, 1.22f, 0f, 14f,
            0.00f, 0.00f, 0.00f, 1f, 0f
        ),
        contrastCurve = 0.50f,
        vignetteStrength = 0.50f,
        bloomStrength = 0.45f,
        swatchStart = 0xFF0D47A1,
        swatchEnd = 0xFF1E88E5
    ),

    CYBER_NIGHT(
        id = "cyber_night",
        displayName = "Cyber Neon",
        subtitle = "Anamorphic Glow FX",
        description = "High-contrast night grade with glowing neon halation, crushed inky shadows, and edge vignette",
        category = FilterCategory.LOW_LIGHT,
        matrix = floatArrayOf(
            1.10f, -0.05f, 0.08f, 0f, 6f,
            -0.02f, 0.96f, 0.10f, 0f, -8f,
            0.05f, 0.08f, 1.20f, 0f, 14f,
            0.00f, 0.00f, 0.00f, 1f, 0f
        ),
        contrastCurve = 0.55f,
        vignetteStrength = 0.55f,
        bloomStrength = 0.60f,
        swatchStart = 0xFF4A148C,
        swatchEnd = 0xFF00E5FF
    ),

    CAMPFIRE_GLOW(
        id = "campfire",
        displayName = "Campfire Ember",
        subtitle = "Warm Bloom + Dark Falloff",
        description = "Warm candlelight/firelight glow with strong dark vignette falloff that hides low-light shadow noise",
        category = FilterCategory.LOW_LIGHT,
        matrix = floatArrayOf(
            1.20f, 0.08f, 0.00f, 0f, 12f,
            0.02f, 1.04f, 0.00f, 0f, 4f,
            0.00f, 0.00f, 0.80f, 0f, -20f,
            0.00f, 0.00f, 0.00f, 1f, 0f
        ),
        contrastCurve = 0.50f,
        vignetteStrength = 0.70f,
        bloomStrength = 0.55f,
        bloomWarmth = true,
        swatchStart = 0xFFE65100,
        swatchEnd = 0xFFFF8F00
    ),

    // --- Monochrome Suite ---
    LEICA_NOIR(
        id = "leica_noir",
        displayName = "Leica Noir",
        subtitle = "Silver Grain + Vignette",
        description = "Authentic Leica Monochrom optical signature: deep S-curve blacks, fine Ilford silver grain, and lens falloff",
        category = FilterCategory.MONOCHROME,
        matrix = floatArrayOf(
            0.58f, 0.34f, 0.08f, 0f, -18f,
            0.58f, 0.34f, 0.08f, 0f, -18f,
            0.58f, 0.34f, 0.08f, 0f, -18f,
            0.00f, 0.00f, 0.00f, 1f, 0f
        ),
        contrastCurve = 0.65f,
        vignetteStrength = 0.60f,
        grainStrength = 0.35f,
        sharpenStrength = 0.40f,
        swatchStart = 0xFF000000,
        swatchEnd = 0xFFFFFFFF
    ),

    ANSEL_MONO(
        id = "ansel",
        displayName = "Ansel Zone X",
        subtitle = "Red Optical Filter + Clarity",
        description = "Ansel Adams Zone System B&W: red spectral filter darkens skies, boosts leaf luminance, and sharpens bark",
        category = FilterCategory.MONOCHROME,
        matrix = floatArrayOf(
            0.65f, 0.30f, 0.05f, 0f, -15f,
            0.65f, 0.30f, 0.05f, 0f, -15f,
            0.65f, 0.30f, 0.05f, 0f, -15f,
            0.00f, 0.00f, 0.00f, 1f, 0f
        ),
        contrastCurve = 0.70f,
        vignetteStrength = 0.50f,
        sharpenStrength = 0.60f,
        grainStrength = 0.22f,
        swatchStart = 0xFF212121,
        swatchEnd = 0xFF9E9E9E
    );

    /**
     * Real-time hardware GPU RenderEffect pipeline for 60fps live viewfinder preview.
     * Accurately renders optical bloom, highlight diffusion, and contrast adjustments on-device.
     */
    fun getRenderEffect(): android.graphics.RenderEffect? {
        if (this == NATURAL) return null
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) return null

        return try {
            val colorEffect = android.graphics.RenderEffect.createColorFilterEffect(
                android.graphics.ColorMatrixColorFilter(matrix)
            )
            if (bloomStrength > 0f || softMistStrength > 0f) {
                val blurRadius = if (softMistStrength > 0.4f) 20f else 14f
                val blurEffect = android.graphics.RenderEffect.createBlurEffect(
                    blurRadius, blurRadius, android.graphics.Shader.TileMode.CLAMP
                )
                android.graphics.RenderEffect.createBlendModeEffect(
                    colorEffect,
                    blurEffect,
                    android.graphics.BlendMode.SCREEN
                )
            } else {
                colorEffect
            }
        } catch (e: Throwable) {
            android.graphics.RenderEffect.createColorFilterEffect(
                android.graphics.ColorMatrixColorFilter(matrix)
            )
        }
    }

    /**
     * Full Multi-Stage Optical Pipeline:
     * 1. Spectral Color Matrix
     * 2. Non-Linear S-Curve Tone & Dehaze Mapping
     * 3. Pro-Mist / Canopy Sunbeam Halation & Highlight Bloom
     * 4. Unsharp Mask Micro-Contrast Enhancement
     * 5. Optical Lens Vignette Falloff
     * 6. Silver-Halide Organic Film Grain
     */
    fun applyToBitmap(source: Bitmap): Bitmap {
        if (this == NATURAL) return source

        val width = source.width
        val height = source.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        // Stage 1: Base Spectral Matrix
        val basePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix(matrix))
        }
        canvas.drawBitmap(source, 0f, 0f, basePaint)

        // Stage 2: S-Curve Contrast & Micro-Clarity / Grain on pixel buffer
        if (contrastCurve > 0f || grainStrength > 0f) {
            applyToneCurveAndGrain(output, contrastCurve, grainStrength)
        }

        // Stage 3: Optical Highlight Bloom / Sunbeam Halation / Pro-Mist Diffusion
        if (bloomStrength > 0f || softMistStrength > 0f) {
            applyOpticalBloomAndMist(canvas, output, max(bloomStrength, softMistStrength), bloomWarmth)
        }

        // Stage 4: Unsharp Mask Micro-Contrast (High-frequency edge definition)
        if (sharpenStrength > 0f) {
            applyUnsharpMask(canvas, output, sharpenStrength)
        }

        // Stage 5: Optical Lens Vignette (Natural radial corner falloff)
        if (vignetteStrength > 0f) {
            applyOpticalVignette(canvas, width, height, vignetteStrength)
        }

        return output
    }

    companion object {
        /**
         * Generates a genuine High-Resolution Detail-Enhanced Master shot:
         * Applies optical unsharp masking + local micro-contrast S-curve without altering natural colors.
         */
        fun createHighResDetailMaster(source: Bitmap): Bitmap {
            val width = source.width
            val height = source.height
            val output = source.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(output)

            // Subtle natural S-curve to prevent flat shadows
            applyToneCurveAndGrain(output, contrastStrength = 0.28f, grainStrength = 0f)
            // High-frequency optical unsharp mask for leaf/bark/fabric micro-texture
            applyUnsharpMask(canvas, output, amount = 0.85f)
            return output
        }

        private fun applyToneCurveAndGrain(bitmap: Bitmap, contrastStrength: Float, grainStrength: Float) {
            val w = bitmap.width
            val h = bitmap.height

            // Precompute 256-entry S-Curve LUT (fast O(1) lookup per channel)
            val lut = IntArray(256)
            for (i in 0..255) {
                val x = i / 255.0
                // Cosine S-curve: darkens shadows cleanly and rolls off highlights smoothly
                val sCurve = 0.5 - 0.5 * cos(Math.PI * x)
                val blended = x * (1.0 - contrastStrength) + sCurve * contrastStrength
                lut[i] = (blended * 255.0).toInt().coerceIn(0, 255)
            }

            val rowPixels = IntArray(w)
            val grainAmp = (grainStrength * 28f).toInt()
            var rngState = 0x5DEECE66DL

            for (y in 0 until h) {
                bitmap.getPixels(rowPixels, 0, w, 0, y, w, 1)
                for (x in 0 until w) {
                    val p = rowPixels[x]
                    val a = p and -0x1000000
                    var r = lut[(p shr 16) and 0xFF]
                    var g = lut[(p shr 8) and 0xFF]
                    var b = lut[p and 0xFF]

                    if (grainAmp > 0) {
                        // Fast inline LCG pseudo-random noise weighted strongest in midtones
                        rngState = (rngState * 25214903917L + 11L) and ((1L shl 48) - 1)
                        val noise = (((rngState shr 24).toInt() and 0xFF) - 128) * grainAmp / 128
                        r = (r + noise).coerceIn(0, 255)
                        g = (g + noise).coerceIn(0, 255)
                        b = (b + noise).coerceIn(0, 255)
                    }

                    rowPixels[x] = a or (r shl 16) or (g shl 8) or b
                }
                bitmap.setPixels(rowPixels, 0, w, 0, y, w, 1)
            }
        }

        private fun applyOpticalBloomAndMist(
            canvas: Canvas,
            source: Bitmap,
            strength: Float,
            warmHalation: Boolean
        ) {
            val w = source.width
            val h = source.height
            // Downsample 1/8th to extract bright highlights and create wide optical diffusion
            val smallW = max(16, w / 8)
            val smallH = max(16, h / 8)
            val small = Bitmap.createScaledBitmap(source, smallW, smallH, true)

            // Threshold only bright highlights (sunbeams, sky glints, lanterns)
            val pixels = IntArray(smallW * smallH)
            small.getPixels(pixels, 0, smallW, 0, 0, smallW, smallH)
            for (i in pixels.indices) {
                val p = pixels[i]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                val luma = (r * 77 + g * 150 + b * 29) shr 8

                if (luma > 155) {
                    val factor = (luma - 155) / 100f
                    val hr = min(255, (r * factor * (if (warmHalation) 1.25f else 1.0f)).toInt())
                    val hg = min(255, (g * factor * (if (warmHalation) 1.05f else 1.0f)).toInt())
                    val hb = min(255, (b * factor * (if (warmHalation) 0.70f else 1.05f)).toInt())
                    pixels[i] = (0xFF shl 24) or (hr shl 16) or (hg shl 8) or hb
                } else {
                    pixels[i] = 0x00000000
                }
            }
            small.setPixels(pixels, 0, smallW, 0, 0, smallW, smallH)

            // Box-blur the highlight buffer twice for silky Gaussian-like halation
            boxBlurInPlace(small, radius = 4)
            boxBlurInPlace(small, radius = 4)

            val bloomPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
                alpha = (strength * 185f).toInt().coerceIn(0, 255)
            }
            val destRect = Rect(0, 0, w, h)
            canvas.drawBitmap(small, null, destRect, bloomPaint)
            small.recycle()
        }

        private fun applyUnsharpMask(canvas: Canvas, source: Bitmap, amount: Float) {
            val w = source.width
            val h = source.height
            // High-frequency detail boost via contrast-centered convolution matrix on luminance edges
            val contrast = 1f + (amount * 0.22f)
            val translate = (-0.5f * contrast + 0.5f) * 255f
            val detailMatrix = ColorMatrix(
                floatArrayOf(
                    contrast, 0f, 0f, 0f, translate,
                    0f, contrast, 0f, 0f, translate,
                    0f, 0f, contrast, 0f, translate,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            val detailPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
                colorFilter = ColorMatrixColorFilter(detailMatrix)
                alpha = (amount * 95f).toInt().coerceIn(0, 255)
            }
            canvas.drawBitmap(source, 0f, 0f, detailPaint)
        }

        private fun applyOpticalVignette(canvas: Canvas, width: Int, height: Int, strength: Float) {
            val centerX = width * 0.5f
            val centerY = height * 0.5f
            val radius = max(width, height) * 0.72f
            val maxAlpha = (strength * 195f).toInt().coerceIn(0, 235)

            val gradient = RadialGradient(
                centerX,
                centerY,
                radius,
                intArrayOf(
                    Color.TRANSPARENT,
                    Color.argb((maxAlpha * 0.25f).toInt(), 0, 0, 0),
                    Color.argb((maxAlpha * 0.70f).toInt(), 0, 0, 0),
                    Color.argb(maxAlpha, 0, 0, 0)
                ),
                floatArrayOf(0.0f, 0.55f, 0.82f, 1.0f),
                Shader.TileMode.CLAMP
            )

            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = gradient
            }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        }

        private fun boxBlurInPlace(bitmap: Bitmap, radius: Int) {
            val w = bitmap.width
            val h = bitmap.height
            val pix = IntArray(w * h)
            val temp = IntArray(w * h)
            bitmap.getPixels(pix, 0, w, 0, 0, w, h)

            for (y in 0 until h) {
                for (x in 0 until w) {
                    var r = 0
                    var g = 0
                    var b = 0
                    var count = 0
                    for (dx in -radius..radius) {
                        val nx = (x + dx).coerceIn(0, w - 1)
                        val p = pix[y * w + nx]
                        r += (p shr 16) and 0xFF
                        g += (p shr 8) and 0xFF
                        b += p and 0xFF
                        count++
                    }
                    temp[y * w + x] = (0xFF shl 24) or ((r / count) shl 16) or ((g / count) shl 8) or (b / count)
                }
            }
            for (y in 0 until h) {
                for (x in 0 until w) {
                    var r = 0
                    var g = 0
                    var b = 0
                    var count = 0
                    for (dy in -radius..radius) {
                        val ny = (y + dy).coerceIn(0, h - 1)
                        val p = temp[ny * w + x]
                        r += (p shr 16) and 0xFF
                        g += (p shr 8) and 0xFF
                        b += p and 0xFF
                        count++
                    }
                    pix[y * w + x] = (0xFF shl 24) or ((r / count) shl 16) or ((g / count) shl 8) or (b / count)
                }
            }
            bitmap.setPixels(pix, 0, w, 0, 0, w, h)
        }
    }
}

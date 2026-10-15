package com.deekshith.naturalcam.effect

import android.graphics.*
import kotlin.math.max

object PortraitBokehProcessor {

    /**
     * Generates a DSLR optical portrait bokeh depth effect from a single sensor capture.
     * Keeps the subject sharp while applying optical lens blur and vignette falloff to the background.
     */
    fun createPortraitBokeh(source: Bitmap, blurRadius: Int = 22): Bitmap {
        val width = source.width
        val height = source.height

        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        // 1. Generate Creamy Background Bokeh
        // Downscale slightly for smooth Gaussian bokeh quality and speed
        val scale = 0.35f
        val smallW = max(1, (width * scale).toInt())
        val smallH = max(1, (height * scale).toInt())
        val smallBmp = Bitmap.createScaledBitmap(source, smallW, smallH, true)
        val blurredSmall = fastBlur(smallBmp, blurRadius)
        val blurredFull = Bitmap.createScaledBitmap(blurredSmall, width, height, true)

        // 2. Draw Blurred Background as Base Layer
        canvas.drawBitmap(blurredFull, 0f, 0f, null)

        // 3. Create Subject Sharp Focus Mask (Soft Radial Oval Depth Mask)
        val maskBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val maskCanvas = Canvas(maskBitmap)

        val centerX = width * 0.50f
        val centerY = height * 0.48f
        val radiusX = width * 0.32f
        val radiusY = height * 0.40f

        val gradient = RadialGradient(
            centerX,
            centerY,
            max(radiusX, radiusY),
            intArrayOf(
                Color.WHITE,
                Color.WHITE,
                Color.argb(200, 255, 255, 255),
                Color.TRANSPARENT
            ),
            floatArrayOf(0.0f, 0.55f, 0.80f, 1.0f),
            Shader.TileMode.CLAMP
        )

        val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = gradient
        }

        // Draw oval mask
        val ovalRect = RectF(
            centerX - radiusX,
            centerY - radiusY,
            centerX + radiusX,
            centerY + radiusY
        )
        maskCanvas.drawOval(ovalRect, maskPaint)

        // 4. Blend Sharp Foreground Subject using Mask
        val sharpSubject = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val sharpCanvas = Canvas(sharpSubject)
        sharpCanvas.drawBitmap(source, 0f, 0f, null)

        val blendPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
        sharpCanvas.drawBitmap(maskBitmap, 0f, 0f, blendPaint)

        // 5. Composite Sharp Subject over Blurred Background
        canvas.drawBitmap(sharpSubject, 0f, 0f, null)

        // Clean up intermediate bitmaps to preserve RAM
        smallBmp.recycle()
        blurredSmall.recycle()
        blurredFull.recycle()
        maskBitmap.recycle()
        sharpSubject.recycle()

        return output
    }

    /**
     * Fast, high-quality stack blur algorithm for optical bokeh
     */
    private fun fastBlur(sentBitmap: Bitmap, radius: Int): Bitmap {
        val bitmap = sentBitmap.copy(sentBitmap.config ?: Bitmap.Config.ARGB_8888, true)
        if (radius < 1) return bitmap

        val w = bitmap.width
        val h = bitmap.height
        val pix = IntArray(w * h)
        bitmap.getPixels(pix, 0, w, 0, 0, w, h)

        val wm = w - 1
        val hm = h - 1
        val wh = w * h
        val div = radius + radius + 1

        val r = IntArray(wh)
        val g = IntArray(wh)
        val b = IntArray(wh)
        var rsum: Int
        var gsum: Int
        var bsum: Int
        var x: Int
        var y: Int
        var i: Int
        var p: Int
        var yp: Int
        var yi: Int
        val vmin = IntArray(max(w, h))

        var divsum = (div + 1) shr 1
        divsum *= divsum
        val dv = IntArray(256 * divsum)
        for (idx in 0 until 256 * divsum) {
            dv[idx] = idx / divsum
        }

        yi = 0
        var yw = 0
        val stack = Array(div) { IntArray(3) }
        var stackpointer: Int
        var stackstart: Int
        var sir: IntArray
        var rbs: Int
        val r1 = radius + 1
        var routsum: Int
        var goutsum: Int
        var boutsum: Int
        var rinsum: Int
        var ginsum: Int
        var binsum: Int

        for (curY in 0 until h) {
            rinsum = 0
            ginsum = 0
            binsum = 0
            routsum = 0
            goutsum = 0
            boutsum = 0
            rsum = 0
            gsum = 0
            bsum = 0
            for (currI in -radius..radius) {
                p = pix[yi + max(0, currI).coerceAtMost(wm)]
                sir = stack[currI + radius]
                sir[0] = (p and 0xff0000) shr 16
                sir[1] = (p and 0x00ff00) shr 8
                sir[2] = p and 0x0000ff
                rbs = r1 - kotlin.math.abs(currI)
                rsum += sir[0] * rbs
                gsum += sir[1] * rbs
                bsum += sir[2] * rbs
                if (currI > 0) {
                    rinsum += sir[0]
                    ginsum += sir[1]
                    binsum += sir[2]
                } else {
                    routsum += sir[0]
                    goutsum += sir[1]
                    boutsum += sir[2]
                }
            }
            stackpointer = radius

            for (curX in 0 until w) {
                r[yi] = dv[rsum]
                g[yi] = dv[gsum]
                b[yi] = dv[bsum]

                rsum -= routsum
                gsum -= goutsum
                bsum -= boutsum

                stackstart = stackpointer - radius + div
                sir = stack[stackstart % div]

                routsum -= sir[0]
                goutsum -= sir[1]
                boutsum -= sir[2]

                if (curY == 0) {
                    vmin[curX] = (curX + radius + 1).coerceAtMost(wm)
                }
                p = pix[yw + vmin[curX]]

                sir[0] = (p and 0xff0000) shr 16
                sir[1] = (p and 0x00ff00) shr 8
                sir[2] = p and 0x0000ff

                rinsum += sir[0]
                ginsum += sir[1]
                binsum += sir[2]

                rsum += rinsum
                gsum += ginsum
                bsum += binsum

                stackpointer = (stackpointer + 1) % div
                sir = stack[stackpointer % div]

                routsum += sir[0]
                goutsum += sir[1]
                boutsum += sir[2]

                rinsum -= sir[0]
                ginsum -= sir[1]
                binsum -= sir[2]

                yi++
            }
            yw += w
        }

        for (curX in 0 until w) {
            rinsum = 0
            ginsum = 0
            binsum = 0
            routsum = 0
            goutsum = 0
            boutsum = 0
            rsum = 0
            gsum = 0
            bsum = 0
            yp = -radius * w
            for (currI in -radius..radius) {
                yi = max(0, yp) + curX
                sir = stack[currI + radius]
                sir[0] = r[yi]
                sir[1] = g[yi]
                sir[2] = b[yi]

                rbs = r1 - kotlin.math.abs(currI)
                rsum += r[yi] * rbs
                gsum += g[yi] * rbs
                bsum += b[yi] * rbs

                if (currI > 0) {
                    rinsum += sir[0]
                    ginsum += sir[1]
                    binsum += sir[2]
                } else {
                    routsum += sir[0]
                    goutsum += sir[1]
                    boutsum += sir[2]
                }

                if (currI < hm) {
                    yp += w
                }
            }
            yi = curX
            stackpointer = radius
            for (curY in 0 until h) {
                pix[yi] = (-0x1000000 and pix[yi]) or (dv[rsum] shl 16) or (dv[gsum] shl 8) or dv[bsum]

                rsum -= routsum
                gsum -= goutsum
                bsum -= boutsum

                stackstart = stackpointer - radius + div
                sir = stack[stackstart % div]

                routsum -= sir[0]
                goutsum -= sir[1]
                boutsum -= sir[2]

                if (curX == 0) {
                    vmin[curY] = (curY + r1).coerceAtMost(hm) * w
                }
                p = curX + vmin[curY]

                sir[0] = r[p]
                sir[1] = g[p]
                sir[2] = b[p]

                rinsum += sir[0]
                ginsum += sir[1]
                binsum += sir[2]

                rsum += rinsum
                gsum += ginsum
                bsum += binsum

                stackpointer = (stackpointer + 1) % div
                sir = stack[stackpointer]

                routsum += sir[0]
                goutsum += sir[1]
                boutsum += sir[2]

                rinsum -= sir[0]
                ginsum -= sir[1]
                binsum -= sir[2]

                yi += w
            }
        }

        bitmap.setPixels(pix, 0, w, 0, 0, w, h)
        return bitmap
    }
}

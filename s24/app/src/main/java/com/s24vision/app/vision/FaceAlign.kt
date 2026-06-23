package com.s24vision.app.vision

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint

/** Выравнивание лица по 5 landmarks (шаблон ArcFace 112×112). */
internal object FaceAlign {

    private val ARCFACE_DST = floatArrayOf(
        38.2946f, 51.6963f,
        73.5318f, 51.5014f,
        56.0252f, 71.7366f,
        41.5493f, 92.3655f,
        70.7299f, 92.2041f,
    )

    /** Аффинное преобразование source → [imageSize]×[imageSize] по 5 точкам. */
    fun warp(bitmap: Bitmap, landmarks: FloatArray, imageSize: Int = 112): Bitmap {
        val m = Matrix()
        m.setValues(estimateMatrix(landmarks, imageSize))
        val out = Bitmap.createBitmap(imageSize, imageSize, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(bitmap, m, Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }

    /** Matrix.setValues: [a,b,tx, c,d,ty, 0,0,1] — source → template. */
    private fun estimateMatrix(landmarks: FloatArray, imageSize: Int): FloatArray {
        val ratio = imageSize / 112f
        val dst = FloatArray(10) { i -> ARCFACE_DST[i] * ratio }
        val t = umeyamaSimilarity(landmarks, dst, 5)
        return floatArrayOf(t[0], t[1], t[2], t[3], t[4], t[5], 0f, 0f, 1f)
    }

    /**
     * 2D similarity (scale + rotation + translation), Procrustes / Umeyama.
     * @return [a,b,tx, c,d,ty]
     */
    private fun umeyamaSimilarity(src: FloatArray, dst: FloatArray, n: Int): FloatArray {
        var srcMx = 0f
        var srcMy = 0f
        var dstMx = 0f
        var dstMy = 0f
        for (i in 0 until n) {
            srcMx += src[i * 2]
            srcMy += src[i * 2 + 1]
            dstMx += dst[i * 2]
            dstMy += dst[i * 2 + 1]
        }
        srcMx /= n
        srcMy /= n
        dstMx /= n
        dstMy /= n

        var a = 0f
        var b = 0f
        var srcVar = 0f
        for (i in 0 until n) {
            val sx = src[i * 2] - srcMx
            val sy = src[i * 2 + 1] - srcMy
            val dx = dst[i * 2] - dstMx
            val dy = dst[i * 2 + 1] - dstMy
            a += sx * dx + sy * dy
            b += sx * dy - sy * dx
            srcVar += sx * sx + sy * sy
        }
        if (srcVar < 1e-6f) {
            return floatArrayOf(1f, 0f, dstMx - srcMx, 0f, 1f, dstMy - srcMy)
        }
        val invVar = 1f / srcVar
        val m00 = a * invVar
        val m01 = -b * invVar
        val m10 = b * invVar
        val m11 = a * invVar
        val tx = dstMx - (m00 * srcMx + m01 * srcMy)
        val ty = dstMy - (m10 * srcMx + m11 * srcMy)
        return floatArrayOf(m00, m01, tx, m10, m11, ty)
    }
}

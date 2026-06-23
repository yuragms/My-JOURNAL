package com.s24vision.app.vision

import android.graphics.Bitmap
import com.s24vision.app.core.onnx.OnnxModel
import kotlin.math.max
import kotlin.math.min

/**
 * Постобработка SCRFD det_500m (buffalo_sc): 3 FPN-уровня, stride 8/16/32,
 * 2 якоря на ячейку, 5 landmarks. Совместимо с insightface.model_zoo.scrfd.
 */
internal class ScrfdDetector(
    private val model: OnnxModel,
    private val detThresh: Float = 0.5f,
    private val nmsThresh: Float = 0.4f,
    private val minFaceSize: Int = 20,
) {
    private val strides = intArrayOf(8, 16, 32)
    private val numAnchors = 2
    private val inputMean = 127.5f
    private val inputStd = 128f

    data class Face(
        val x1: Float,
        val y1: Float,
        val x2: Float,
        val y2: Float,
        val score: Float,
        /** 10 floats: x0,y0,…,x4,y4 в координатах исходного кропа. */
        val kps: FloatArray,
    )

    /** Крупнейшее лицо в кропе или null. */
    fun detectLargest(crop: Bitmap): Face? =
        detectAll(crop).maxByOrNull { (it.x2 - it.x1) * (it.y2 - it.y1) }
            ?.takeIf { min(it.x2 - it.x1, it.y2 - it.y1) >= minFaceSize }

    /** Все лица на кадре (после NMS). */
    fun detectAll(crop: Bitmap): List<Face> {
        val detSize = pickDetSize(crop.width, crop.height)
        val prep = prepareInput(crop, detSize, detSize)
        val outs = model.runAll(prep.blob, longArrayOf(1, 3, detSize.toLong(), detSize.toLong()))
        val scores = outs.filter { it.second.getOrElse(1) { 0 } == 1L }
            .sortedByDescending { it.second[0] }
        val bboxes = outs.filter { it.second.getOrElse(1) { 0 } == 4L }
            .sortedByDescending { it.second[0] }
        val kps = outs.filter { it.second.getOrElse(1) { 0 } == 10L }
            .sortedByDescending { it.second[0] }
        if (scores.size < 3 || bboxes.size < 3 || kps.size < 3) return emptyList()

        val candidates = ArrayList<Face>()
        for (idx in strides.indices) {
            decodeLevel(
                scores = scores[idx].first,
                bboxPreds = bboxes[idx].first,
                kpsPreds = kps[idx].first,
                stride = strides[idx],
                inputW = detSize,
                inputH = detSize,
                detScale = prep.detScale,
                out = candidates,
            )
        }
        if (candidates.isEmpty()) return emptyList()
        return nms(candidates).filter { min(it.x2 - it.x1, it.y2 - it.y1) >= minFaceSize }
    }

    private fun pickDetSize(w: Int, h: Int): Int {
        val m = max(w, h)
        return when {
            m <= 160 -> 128
            m <= 320 -> 192
            else -> 320
        }
    }

    private data class Prep(
        val blob: FloatArray,
        val detScale: Float,
        val padW: Int,
        val padH: Int,
    )

    private fun prepareInput(crop: Bitmap, inputW: Int, inputH: Int): Prep {
        val w = crop.width
        val h = crop.height
        val imRatio = h.toFloat() / w
        val modelRatio = inputH.toFloat() / inputW
        val newW: Int
        val newH: Int
        if (imRatio > modelRatio) {
            newH = inputH
            newW = (newH / imRatio).toInt().coerceAtLeast(1)
        } else {
            newW = inputW
            newH = (newW * imRatio).toInt().coerceAtLeast(1)
        }
        val detScale = newH.toFloat() / h
        val resized = Bitmap.createScaledBitmap(crop, newW, newH, true)
        val px = IntArray(inputW * inputH)
        val plane = inputW * inputH
        val blob = FloatArray(3 * plane)
        val tmp = IntArray(newW)
        for (y in 0 until newH) {
            resized.getPixels(tmp, 0, newW, 0, y, newW, 1)
            for (x in 0 until newW) {
                val p = tmp[x]
                val r = (p shr 16 and 0xFF) / inputStd - inputMean / inputStd
                val g = (p shr 8 and 0xFF) / inputStd - inputMean / inputStd
                val b = (p and 0xFF) / inputStd - inputMean / inputStd
                val i = y * inputW + x
                blob[i] = r
                blob[plane + i] = g
                blob[2 * plane + i] = b
            }
        }
        if (resized !== crop) resized.recycle()
        return Prep(blob, detScale, inputW, inputH)
    }

    private fun decodeLevel(
        scores: FloatArray,
        bboxPreds: FloatArray,
        kpsPreds: FloatArray,
        stride: Int,
        inputW: Int,
        inputH: Int,
        detScale: Float,
        out: MutableList<Face>,
    ) {
        val height = inputH / stride
        val width = inputW / stride
        val grid = height * width
        val anchors = grid * numAnchors
        if (scores.size < anchors) return

        val centers = FloatArray(anchors * 2)
        var a = 0
        for (gy in 0 until height) {
            for (gx in 0 until width) {
                val cx = gx * stride.toFloat()
                val cy = gy * stride.toFloat()
                repeat(numAnchors) {
                    centers[a * 2] = cx
                    centers[a * 2 + 1] = cy
                    a++
                }
            }
        }

        for (i in 0 until anchors) {
            val score = scores[i]
            if (score < detThresh) continue
            val o = i * 4
            val ko = i * 10
            val cx = centers[i * 2]
            val cy = centers[i * 2 + 1]
            val l = bboxPreds[o] * stride
            val t = bboxPreds[o + 1] * stride
            val r = bboxPreds[o + 2] * stride
            val b = bboxPreds[o + 3] * stride
            val x1 = (cx - l) / detScale
            val y1 = (cy - t) / detScale
            val x2 = (cx + r) / detScale
            val y2 = (cy + b) / detScale
            val kps = FloatArray(10)
            for (k in 0 until 5) {
                kps[k * 2] = (cx + kpsPreds[ko + k * 2] * stride) / detScale
                kps[k * 2 + 1] = (cy + kpsPreds[ko + k * 2 + 1] * stride) / detScale
            }
            out.add(Face(x1, y1, x2, y2, score, kps))
        }
    }

    private fun nms(faces: List<Face>): List<Face> {
        val sorted = faces.sortedByDescending { it.score }
        val kept = ArrayList<Face>()
        val used = BooleanArray(sorted.size)
        for (i in sorted.indices) {
            if (used[i]) continue
            kept.add(sorted[i])
            for (j in i + 1 until sorted.size) {
                if (used[j]) continue
                if (iou(sorted[i], sorted[j]) > nmsThresh) used[j] = true
            }
        }
        return kept
    }

    private fun iou(a: Face, b: Face): Float {
        val xx1 = max(a.x1, b.x1)
        val yy1 = max(a.y1, b.y1)
        val xx2 = min(a.x2, b.x2)
        val yy2 = min(a.y2, b.y2)
        val w = max(0f, xx2 - xx1 + 1)
        val h = max(0f, yy2 - yy1 + 1)
        val inter = w * h
        val areaA = (a.x2 - a.x1 + 1) * (a.y2 - a.y1 + 1)
        val areaB = (b.x2 - b.x1 + 1) * (b.y2 - b.y1 + 1)
        return inter / (areaA + areaB - inter)
    }
}

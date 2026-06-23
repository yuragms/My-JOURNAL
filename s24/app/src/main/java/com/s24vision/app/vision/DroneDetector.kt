package com.s24vision.app.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import com.s24vision.app.core.onnx.OnnxModelHolder
import com.s24vision.app.core.settings.RecognitionSettings

/**
 * Специализированный детектор дронов (YOLO11s, 1 класс).
 * Веса: sapoepsilon/yolov11s-drone-detector (HuggingFace).
 * ONNX: [1, 5, 8400] → cx, cy, w, h, conf в пикселях входа 640.
 * Старый формат [1, 300, 6] (end2end) тоже поддерживается.
 */
class DroneDetector(
    context: Context,
    settings: RecognitionSettings,
    private val inputSize: Int = 640,
    private val confTh: Float = 0.25f,
) {
    private val model = OnnxModelHolder(context, "drone_det.onnx", settings)
    private val end2EndStride = 6
    private val label = "drone"

    fun detect(bitmap: Bitmap): List<Detection> {
        val lb = YoloLetterbox.fit(bitmap, inputSize)
        val input = bitmapToNchw(lb.bitmap)
        if (lb.bitmap !== bitmap) lb.bitmap.recycle()
        val (out, shape) = model.get().run(
            input,
            longArrayOf(1, 3, inputSize.toLong(), inputSize.toLong()),
        )
        val raw = decode(out, shape, lb, bitmap.width, bitmap.height)
        return DetectionFilters.filterDrone(DetectionFilters.nms(raw, 0.4f), bitmap.width, bitmap.height)
    }

    private fun bitmapToNchw(bmp: Bitmap): FloatArray {
        val w = bmp.width
        val h = bmp.height
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        val out = FloatArray(3 * w * h)
        val plane = w * h
        for (i in 0 until plane) {
            val p = px[i]
            out[i] = (p shr 16 and 0xFF) / 255f
            out[plane + i] = (p shr 8 and 0xFF) / 255f
            out[2 * plane + i] = (p and 0xFF) / 255f
        }
        return out
    }

    private fun decode(
        out: FloatArray,
        shape: LongArray,
        lb: LetterboxResult,
        ow: Int,
        oh: Int,
    ): List<Detection> = when {
        shape.size == 3 && shape[2] == end2EndStride.toLong() ->
            decodeEnd2End(out, shape[1].toInt(), lb, ow, oh)
        shape.size == 3 && shape[1] in 4L..8L ->
            decodeRawYolo(out, shape[1].toInt(), shape[2].toInt(), lb, ow, oh)
        else -> decodeEnd2End(out, out.size / end2EndStride, lb, ow, oh)
    }

    /** Ultralytics end2end: [1, 300, 6] → xyxy, conf, cls. */
    private fun decodeEnd2End(
        out: FloatArray,
        n: Int,
        lb: LetterboxResult,
        ow: Int,
        oh: Int,
    ): List<Detection> {
        val res = ArrayList<Detection>()
        for (i in 0 until n) {
            val o = i * end2EndStride
            val score = out[o + 4]
            if (score < confTh) continue
            res.add(detectionFromRect(out[o], out[o + 1], out[o + 2], out[o + 3], score, lb, ow, oh))
        }
        return res
    }

    /** Стандартный YOLOv8/11: [1, 4+nc, anchors] — cx, cy, w, h, class scores. */
    private fun decodeRawYolo(
        out: FloatArray,
        channels: Int,
        anchors: Int,
        lb: LetterboxResult,
        ow: Int,
        oh: Int,
    ): List<Detection> {
        val res = ArrayList<Detection>()
        val scoreChannel = 4
        for (i in 0 until anchors) {
            val score = out[scoreChannel * anchors + i]
            if (score < confTh) continue
            val cx = out[0 * anchors + i]
            val cy = out[1 * anchors + i]
            val w = out[2 * anchors + i]
            val h = out[3 * anchors + i]
            res.add(detectionFromRect(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f, score, lb, ow, oh))
        }
        return res
    }

    private fun detectionFromRect(
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        score: Float,
        lb: LetterboxResult,
        ow: Int,
        oh: Int,
    ): Detection {
        val rect = YoloLetterbox.mapRectToFrame(x1, y1, x2, y2, lb)
        rect.right = rect.right.coerceIn(rect.left + 1f, ow.toFloat())
        rect.bottom = rect.bottom.coerceIn(rect.top + 1f, oh.toFloat())
        rect.left = rect.left.coerceIn(0f, ow - 1f)
        rect.top = rect.top.coerceIn(0f, oh - 1f)
        return Detection(rect = rect, label = label, score = score, isPerson = false)
    }

    fun close() = model.close()
}

package com.s24vision.app.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import com.s24vision.app.core.onnx.OnnxModel

/**
 * Детектор на prompt-free YOLOE-26.
 *
 * ВНИМАНИЕ: точная форма выхода и раскладка (x1,y1,x2,y2,score,cls) фиксируются по
 * netron после экспорта (tools/export_models.py). Если экспорт даёт иной формат
 * (например [1, N, 6] или транспонированный), скорректируйте [decode].
 */
class YoloeDetector(
    context: Context,
    private val inputSize: Int = 640,
) {
    private val model = OnnxModel(context, "yoloe26s.onnx")

    fun detect(bitmap: Bitmap, confTh: Float): List<Detection> {
        val scaled = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        val input = bitmapToNchw(scaled)
        val (out, shape) = model.run(
            input,
            longArrayOf(1, 3, inputSize.toLong(), inputSize.toLong()),
        )
        return decode(out, shape, bitmap.width, bitmap.height, confTh)
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

    /**
     * Каркас под формат [n, 6] = (x1, y1, x2, y2, score, cls) в координатах входа
     * [inputSize x inputSize]. Боксы масштабируются обратно к исходному кадру.
     */
    private fun decode(
        out: FloatArray,
        @Suppress("UNUSED_PARAMETER") shape: LongArray,
        ow: Int,
        oh: Int,
        confTh: Float,
    ): List<Detection> {
        val res = ArrayList<Detection>()
        val stride = 6
        val n = out.size / stride
        val sx = ow.toFloat() / inputSize
        val sy = oh.toFloat() / inputSize
        for (i in 0 until n) {
            val o = i * stride
            val score = out[o + 4]
            if (score < confTh) continue
            val cls = out[o + 5].toInt()
            val label = CocoLabels.nameOf(cls)
            res.add(
                Detection(
                    rect = RectF(out[o] * sx, out[o + 1] * sy, out[o + 2] * sx, out[o + 3] * sy),
                    label = label,
                    score = score,
                    isPerson = label == "person",
                ),
            )
        }
        return res
    }

    fun close() = model.close()
}

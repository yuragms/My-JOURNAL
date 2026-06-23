package com.s24vision.app.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import com.s24vision.app.core.onnx.OnnxModelHolder
import com.s24vision.app.core.settings.RecognitionSettings

/**
 * Детектор на prompt-free YOLOE-26 (seg).
 *
 * Откалибровано по реальному экспорту: вход `images [1,3,640,640]`,
 * выход `output0 [1,300,38]`, где на детекцию идёт раскладка
 * `[x1, y1, x2, y2, conf, cls, +32 коэф. масок]`; координаты — в пикселях
 * входа 640; cls — id в словаре RAM++ (4585 классов), см. ClassNames.
 * `output1` (прототипы масок) не используется.
 */
class YoloeDetector(
    context: Context,
    settings: RecognitionSettings,
    private val inputSize: Int = 640,
) {
    private val model = OnnxModelHolder(context, "yoloe26s.onnx", settings)
    private val classNames = ClassNames(context)
    private val stride = 38

    fun detect(bitmap: Bitmap, confTh: Float): List<Detection> {
        val scaled = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        val input = bitmapToNchw(scaled)
        val (out, shape) = model.get().run(
            input,
            longArrayOf(1, 3, inputSize.toLong(), inputSize.toLong()),
        )
        val raw = decode(out, shape, bitmap.width, bitmap.height, confTh)
        return DetectionFilters.filter(DetectionFilters.nms(raw), bitmap.width, bitmap.height)
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
     * Формат строки: `[x1, y1, x2, y2, conf, cls, ...масочные коэф.]`,
     * координаты в пикселях входа [inputSize]. Боксы масштабируются к кадру.
     */
    private fun decode(
        out: FloatArray,
        @Suppress("UNUSED_PARAMETER") shape: LongArray,
        ow: Int,
        oh: Int,
        confTh: Float,
    ): List<Detection> {
        val res = ArrayList<Detection>()
        val n = out.size / stride
        val sx = ow.toFloat() / inputSize
        val sy = oh.toFloat() / inputSize
        for (i in 0 until n) {
            val o = i * stride
            val score = out[o + 4]
            if (score < confTh) continue
            val cls = out[o + 5].toInt()
            res.add(
                Detection(
                    rect = RectF(out[o] * sx, out[o + 1] * sy, out[o + 2] * sx, out[o + 3] * sy),
                    label = classNames.nameOf(cls),
                    score = score,
                    isPerson = classNames.isPerson(cls),
                ),
            )
        }
        return res
    }

    fun close() = model.close()
}

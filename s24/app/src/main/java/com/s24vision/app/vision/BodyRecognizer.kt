package com.s24vision.app.vision

import android.content.Context
import android.graphics.Bitmap
import com.s24vision.app.core.math.Embeddings
import com.s24vision.app.core.onnx.OnnxModel

/** OSNet ReID: эмбеддинг тела по кропу человека (вход 256x128, ImageNet-нормализация). */
class BodyRecognizer(context: Context) {

    private val model = OnnxModel(context, "body_reid.onnx")
    private val w = 128
    private val h = 256
    private val mean = floatArrayOf(0.485f, 0.456f, 0.406f)
    private val std = floatArrayOf(0.229f, 0.224f, 0.225f)

    fun embed(crop: Bitmap): FloatArray {
        val b = Bitmap.createScaledBitmap(crop, w, h, true)
        val px = IntArray(w * h)
        b.getPixels(px, 0, w, 0, 0, w, h)
        val input = FloatArray(3 * w * h)
        val plane = w * h
        for (i in 0 until plane) {
            val p = px[i]
            input[i] = (((p shr 16 and 0xFF) / 255f) - mean[0]) / std[0]
            input[plane + i] = (((p shr 8 and 0xFF) / 255f) - mean[1]) / std[1]
            input[2 * plane + i] = (((p and 0xFF) / 255f) - mean[2]) / std[2]
        }
        val (out, _) = model.run(input, longArrayOf(1, 3, h.toLong(), w.toLong()))
        return Embeddings.l2normalize(out)
    }

    fun close() = model.close()
}

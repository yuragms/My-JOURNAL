package com.s24vision.app.vision

import android.content.Context
import android.graphics.Bitmap
import com.s24vision.app.core.math.Embeddings
import com.s24vision.app.core.onnx.OnnxModel
import com.s24vision.app.core.profiles.Profile

/** MobileCLIP image encoder: эмбеддинг кропа объекта и матч с профилями объектов. */
class ObjectRecognizer(
    context: Context,
    private val size: Int = 256,
) {
    private val model = OnnxModel(context, "object_encoder.onnx")

    fun embed(crop: Bitmap): FloatArray {
        val b = Bitmap.createScaledBitmap(crop, size, size, true)
        val px = IntArray(size * size)
        b.getPixels(px, 0, size, 0, 0, size, size)
        val input = FloatArray(3 * size * size)
        val plane = size * size
        for (i in 0 until plane) {
            val p = px[i]
            input[i] = (p shr 16 and 0xFF) / 255f
            input[plane + i] = (p shr 8 and 0xFF) / 255f
            input[2 * plane + i] = (p and 0xFF) / 255f
        }
        val (out, _) = model.run(input, longArrayOf(1, 3, size.toLong(), size.toLong()))
        return Embeddings.l2normalize(out)
    }

    /** @return (имя профиля, косинус) или null, если ниже порога / нет профилей. */
    fun match(crop: Bitmap, profiles: List<Profile>, th: Float): Pair<String, Float>? {
        if (profiles.isEmpty()) return null
        val q = embed(crop)
        var best: Pair<String, Float>? = null
        for (p in profiles) {
            val (_, s) = Embeddings.bestMatch(q, p.embeddings)
            if (s >= th && (best == null || s > best!!.second)) best = p.name to s
        }
        return best
    }

    fun close() = model.close()
}

package com.s24vision.app.vision

import android.content.Context
import android.graphics.Bitmap
import com.s24vision.app.core.math.Embeddings
import com.s24vision.app.core.onnx.OnnxModel

/**
 * Распознавание лица: SCRFD-детекция лица внутри кропа человека, выравнивание по
 * 5 точкам, ArcFace-эмбеддинг (вход 112x112, нормализация (x-0.5)/0.5).
 *
 * ВНИМАНИЕ: [detectAndAlign] требует доводки по реальному выходу face_det.onnx
 * (формат боксов/ландмарков SCRFD). До отладки на устройстве используется
 * временный центр-ресайз 112x112, дающий грубый эмбеддинг — это позволяет собрать
 * и протестировать остальной пайплайн.
 */
class FaceRecognizer(context: Context) {

    private val det = OnnxModel(context, "face_det.onnx")
    private val rec = OnnxModel(context, "face_rec.onnx")
    private val faceSize = 112

    /** @return эмбеддинг лица или null, если лицо не найдено. */
    fun embed(personCrop: Bitmap): FloatArray? {
        val aligned = detectAndAlign(personCrop) ?: return null
        val px = IntArray(faceSize * faceSize)
        aligned.getPixels(px, 0, faceSize, 0, 0, faceSize, faceSize)
        val input = FloatArray(3 * faceSize * faceSize)
        val plane = faceSize * faceSize
        for (i in 0 until plane) {
            val p = px[i]
            input[i] = (((p shr 16 and 0xFF) / 255f) - 0.5f) / 0.5f
            input[plane + i] = (((p shr 8 and 0xFF) / 255f) - 0.5f) / 0.5f
            input[2 * plane + i] = (((p and 0xFF) / 255f) - 0.5f) / 0.5f
        }
        val (out, _) = rec.run(input, longArrayOf(1, 3, faceSize.toLong(), faceSize.toLong()))
        return Embeddings.l2normalize(out)
    }

    /**
     * Запуск SCRFD, выбор крупнейшего лица, аффинное выравнивание по 5 точкам.
     * Точная декодировка боксов/ландмарков заполняется по выходу face_det.onnx.
     * Временная реализация: ресайз кропа к 112x112.
     */
    private fun detectAndAlign(crop: Bitmap): Bitmap? {
        return Bitmap.createScaledBitmap(crop, faceSize, faceSize, true)
    }

    fun close() {
        det.close()
        rec.close()
    }
}

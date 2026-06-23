package com.s24vision.app.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import com.s24vision.app.core.math.Embeddings
import com.s24vision.app.core.onnx.OnnxModelHolder
import com.s24vision.app.core.settings.RecognitionSettings

/**
 * Распознавание лица: SCRFD-детекция лица внутри кропа человека, выравнивание по
 * 5 точкам, ArcFace-эмбеддинг (вход 112×112, нормализация (x-0.5)/0.5).
 */
class FaceRecognizer(
    context: Context,
    settings: RecognitionSettings,
) {

    private val det = OnnxModelHolder(context, "face_det.onnx", settings)
    private val rec = OnnxModelHolder(context, "face_rec.onnx", settings)
    private val faceSize = 112

    private fun scrfd() = ScrfdDetector(det.get())

    /** SCRFD на всём кадре → примерные боксы person (без YOLOE). */
    fun detectPersonBoxes(frame: Bitmap): List<Detection> {
        val faces = scrfd().detectAll(frame)
        if (faces.isEmpty()) return emptyList()
        return faces.map { face ->
            Detection(
                rect = faceToPersonRect(face, frame.width, frame.height),
                label = "person",
                score = face.score,
                isPerson = true,
            )
        }
    }

    private fun faceToPersonRect(face: ScrfdDetector.Face, frameW: Int, frameH: Int): RectF {
        val fw = face.x2 - face.x1
        val fh = face.y2 - face.y1
        val cx = (face.x1 + face.x2) * 0.5f
        val top = face.y1 - fh * 0.35f
        val pw = fw * 2.6f
        val ph = fh * 5.2f
        val left = (cx - pw * 0.5f).coerceIn(0f, frameW.toFloat())
        val right = (cx + pw * 0.5f).coerceIn(0f, frameW.toFloat())
        val t = top.coerceIn(0f, frameH.toFloat())
        val b = (top + ph).coerceIn(0f, frameH.toFloat())
        return RectF(left, t, right, b)
    }

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
        if (aligned !== personCrop) aligned.recycle()
        val (out, _) = rec.get().run(input, longArrayOf(1, 3, faceSize.toLong(), faceSize.toLong()))
        return Embeddings.l2normalize(out)
    }

    /** SCRFD → крупнейшее лицо → выравнивание 112×112; без лица — null. */
    private fun detectAndAlign(crop: Bitmap): Bitmap? {
        val face = scrfd().detectLargest(crop) ?: return null
        return FaceAlign.warp(crop, face.kps, faceSize)
    }

    fun close() {
        det.close()
        rec.close()
    }
}

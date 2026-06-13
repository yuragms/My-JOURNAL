package com.s24vision.app.enroll

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import com.s24vision.app.core.profiles.EnrollResult
import com.s24vision.app.core.profiles.ProfileStore
import com.s24vision.app.core.profiles.ProfileType
import com.s24vision.app.vision.BodyRecognizer
import com.s24vision.app.vision.Detection
import com.s24vision.app.vision.FaceRecognizer
import com.s24vision.app.vision.ObjectRecognizer
import com.s24vision.app.vision.YoloeDetector

/** Обучение/дообучение профилей по видеоролику (сбор эмбеддингов). */
class Enroller(
    private val detector: YoloeDetector,
    private val face: FaceRecognizer,
    private val body: BodyRecognizer,
    private val obj: ObjectRecognizer,
    private val store: ProfileStore,
    private val confTh: Float = 0.4f,
    private val sampleEveryMs: Long = 300,
    private val maxSamples: Int = 50,
) {
    data class Outcome(
        val face: EnrollResult?,
        val body: EnrollResult?,
        val obj: EnrollResult?,
    )

    /** isPerson=true → лицо+тело; false → объект (крупнейший бокс на кадре). */
    fun enroll(videoPath: String, name: String, isPerson: Boolean): Outcome {
        val frames = sampleFrames(videoPath)
        val faceEmbs = ArrayList<FloatArray>()
        val bodyEmbs = ArrayList<FloatArray>()
        val objEmbs = ArrayList<FloatArray>()

        for (f in frames) {
            val dets = detector.detect(f, confTh)
            if (isPerson) {
                val person = dets.filter { it.isPerson }
                    .maxByOrNull { it.rect.width() * it.rect.height() } ?: continue
                val crop = cropOf(f, person)
                face.embed(crop)?.let { faceEmbs.add(it) }
                bodyEmbs.add(body.embed(crop))
            } else {
                val o = dets.filter { !it.isPerson }
                    .maxByOrNull { it.rect.width() * it.rect.height() } ?: continue
                objEmbs.add(obj.embed(cropOf(f, o)))
            }
        }

        return if (isPerson) {
            Outcome(
                face = store.addEmbeddings(ProfileType.FACE, name, faceEmbs),
                body = store.addEmbeddings(ProfileType.BODY, name, bodyEmbs),
                obj = null,
            )
        } else {
            Outcome(
                face = null,
                body = null,
                obj = store.addEmbeddings(ProfileType.OBJECT, name, objEmbs),
            )
        }
    }

    private fun cropOf(bmp: Bitmap, d: Detection): Bitmap {
        val l = d.rect.left.toInt().coerceIn(0, bmp.width - 1)
        val t = d.rect.top.toInt().coerceIn(0, bmp.height - 1)
        val r = d.rect.right.toInt().coerceIn(l + 1, bmp.width)
        val b = d.rect.bottom.toInt().coerceIn(t + 1, bmp.height)
        return Bitmap.createBitmap(bmp, l, t, r - l, b - t)
    }

    private fun sampleFrames(path: String): List<Bitmap> {
        val r = MediaMetadataRetriever()
        r.setDataSource(path)
        val durMs = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0
        val out = ArrayList<Bitmap>()
        var t = 0L
        while (t < durMs && out.size < maxSamples) {
            r.getFrameAtTime(t * 1000, MediaMetadataRetriever.OPTION_CLOSEST)?.let { out.add(it) }
            t += sampleEveryMs
        }
        r.release()
        return out
    }
}

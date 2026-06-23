package com.s24vision.app.enroll

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.s24vision.app.core.monitor.SystemLoadLabel
import com.s24vision.app.core.profiles.EnrollResult
import com.s24vision.app.core.profiles.ProfileStore
import com.s24vision.app.core.settings.RecognitionSettings
import com.s24vision.app.core.profiles.ProfileType
import com.s24vision.app.vision.BodyRecognizer
import com.s24vision.app.vision.BoxTracker
import com.s24vision.app.vision.CompositeDetector
import com.s24vision.app.vision.DetectionIndex
import com.s24vision.app.vision.FaceRecognizer
import com.s24vision.app.vision.ObjectRecognizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Обучение/дообучение профилей по видеоролику (сбор эмбеддингов). */
class Enroller(
    private val context: Context,
    private val detector: CompositeDetector,
    private val face: FaceRecognizer,
    private val body: BodyRecognizer,
    private val obj: ObjectRecognizer,
    private val store: ProfileStore,
    private val settings: RecognitionSettings,
    private val enrollConfTh: Float = 0.32f,
    private val sampleEveryMs: Long = 200,
    private val maxSamples: Int = 60,
) {
    data class Outcome(
        val face: EnrollResult?,
        val body: EnrollResult?,
        val obj: EnrollResult?,
    )

    data class Progress(
        val percent: Int,
        val label: String,
        val systemLoad: String = "",
    )

    enum class Mode { CREATE, IMPROVE }

    /**
     * [boxIndex] — номер бокса (#N) с экрана камеры/записи.
     */
    suspend fun enroll(
        videoUri: String,
        name: String,
        isPerson: Boolean,
        mode: Mode,
        boxIndex: Int = 1,
        onProgress: suspend (Progress) -> Unit = {},
    ): Outcome = withContext(Dispatchers.Default) {
        suspend fun report(percent: Int, label: String) {
            val p = Progress(percent, label, SystemLoadLabel.format("enroll", settings))
            Log.i(TAG, "${p.percent}% — ${p.label} | ${p.systemLoad}")
            onProgress(p)
        }
        report(2, "Чтение ролика…")
        val frames = sampleFrames(videoUri) { extracted, total ->
            val pct = 2 + extracted * 13 / total.coerceAtLeast(1)
            report(pct, "Извлечение кадров: $extracted/$total")
        }
        if (frames.isEmpty()) {
            report(100, "Готово")
            return@withContext emptyOutcome(isPerson)
        }

        val idx = boxIndex.coerceAtLeast(1)
        val personTracker = BoxTracker.enrollPersons()
        val objectTracker = BoxTracker.enrollObjects()
        val faceEmbs = ArrayList<FloatArray>()
        val bodyEmbs = ArrayList<FloatArray>()
        val objEmbsByTrack = LinkedHashMap<Int, ArrayList<FloatArray>>()
        val trackHits = HashMap<Int, Int>()
        val total = frames.size

        frames.forEachIndexed { i, f ->
            // Тот же detect(), что на камере при записи (уважает YOLOE вкл/выкл).
            val dets = detector.detect(f, enrollConfTh)
            if (isPerson) {
                val tracked = personTracker.update(dets.filter { it.isPerson })
                val picked = DetectionIndex.pickByTrackId(tracked, idx)
                    ?: if (tracked.size == 1) tracked.first().detection else null
                if (picked != null) {
                    trackHits[idx] = (trackHits[idx] ?: 0) + 1
                    val crop = cropOf(f, picked)
                    face.embed(crop)?.let { faceEmbs.add(it) }
                    bodyEmbs.add(body.embed(crop))
                    if (crop !== f) crop.recycle()
                }
            } else {
                val objects = dets.filter { !it.isPerson }
                val tracked = objectTracker.update(objects)
                val pickedTrack = DetectionIndex.pickTrackedForEnroll(tracked, idx, objects)
                if (pickedTrack != null) {
                    val tid = pickedTrack.trackId
                    trackHits[tid] = (trackHits[tid] ?: 0) + 1
                    val crop = cropOf(f, pickedTrack.detection)
                    objEmbsByTrack.getOrPut(tid) { ArrayList() }.add(obj.embed(crop))
                    if (crop !== f) crop.recycle()
                }
            }
            f.recycle()
            val pct = 15 + (i + 1) * 78 / total
            report(pct, "Бокс #$idx · кадр ${i + 1}/$total")
        }

        val objEmbs = resolveObjectEmbeddings(objEmbsByTrack, trackHits, idx)

        Log.i(
            TAG,
            "enroll name=$name isPerson=$isPerson face=${faceEmbs.size} body=${bodyEmbs.size} " +
                "obj=${objEmbs.size} trackHits=$trackHits",
        )

        report(96, "Сохранение профиля…")
        val save = when (mode) {
            Mode.CREATE -> store::createEmbeddings
            Mode.IMPROVE -> store::improveEmbeddings
        }

        val outcome = if (isPerson) {
            if (faceEmbs.isEmpty() && bodyEmbs.isEmpty()) {
                noBoxOutcome(isPerson = true, boxIndex = idx, trackHits = trackHits)
            } else {
                Outcome(
                    face = save(ProfileType.FACE, name, faceEmbs),
                    body = save(ProfileType.BODY, name, bodyEmbs),
                    obj = null,
                )
            }
        } else {
            if (objEmbs.isEmpty()) {
                noBoxOutcome(isPerson = false, boxIndex = idx, trackHits = trackHits)
            } else {
                Outcome(
                    face = null,
                    body = null,
                    obj = save(ProfileType.OBJECT, name, objEmbs),
                )
            }
        }
        report(100, "Готово")
        outcome
    }

    private fun resolveObjectEmbeddings(
        byTrack: Map<Int, List<FloatArray>>,
        hits: Map<Int, Int>,
        preferred: Int,
    ): List<FloatArray> {
        byTrack[preferred]?.takeIf { it.isNotEmpty() }?.let { return it }
        val dominant = DetectionIndex.dominantTrackId(hits, preferred)
        byTrack[dominant]?.takeIf { it.isNotEmpty() }?.let { return it }
        if (byTrack.size == 1) return byTrack.values.first()
        return byTrack.maxByOrNull { it.value.size }?.value ?: emptyList()
    }

    companion object {
        private const val TAG = "S24Enroll"
    }

    private fun emptyOutcome(isPerson: Boolean) = if (isPerson) {
        Outcome(
            face = EnrollResult.Empty("нет кадров"),
            body = EnrollResult.Empty("нет кадров"),
            obj = null,
        )
    } else {
        Outcome(face = null, body = null, obj = EnrollResult.Empty("нет кадров"))
    }

    private fun noBoxOutcome(
        isPerson: Boolean,
        boxIndex: Int,
        trackHits: Map<Int, Int>,
    ) = if (isPerson) {
        Outcome(
            face = EnrollResult.Empty(personMissHint(boxIndex, trackHits)),
            body = EnrollResult.Empty(personMissHint(boxIndex, trackHits)),
            obj = null,
        )
    } else {
        Outcome(
            face = null,
            body = null,
            obj = EnrollResult.Empty(objectMissHint(boxIndex, trackHits)),
        )
    }

    private fun personMissHint(boxIndex: Int, trackHits: Map<Int, Int>) =
        "бокс #$boxIndex (person) не найден в кадрах${tracksHint(trackHits)} — проверьте номер или «Это человек?»"

    private fun objectMissHint(boxIndex: Int, trackHits: Map<Int, Int>) =
        "бокс #$boxIndex (объект) не найден${tracksHint(trackHits)} — укажите #N с рамки на записи; «Это человек?» выкл."

    private fun tracksHint(trackHits: Map<Int, Int>): String {
        if (trackHits.isEmpty()) return ""
        val found = trackHits.entries.sortedBy { it.key }.joinToString { "#${it.key}×${it.value}" }
        return " (в ролике: $found)"
    }

    private fun cropOf(bmp: Bitmap, d: com.s24vision.app.vision.Detection): Bitmap {
        val l = d.rect.left.toInt().coerceIn(0, bmp.width - 1)
        val t = d.rect.top.toInt().coerceIn(0, bmp.height - 1)
        val r = d.rect.right.toInt().coerceIn(l + 1, bmp.width)
        val b = d.rect.bottom.toInt().coerceIn(t + 1, bmp.height)
        return Bitmap.createBitmap(bmp, l, t, r - l, b - t)
    }

    private suspend fun sampleFrames(
        videoUri: String,
        onExtracted: suspend (extracted: Int, totalEstimate: Int) -> Unit = { _, _ -> },
    ): List<Bitmap> {
        val r = MediaMetadataRetriever()
        r.setDataSource(context, Uri.parse(videoUri))
        val durMs = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0
        val totalEstimate = if (durMs > 0) {
            ((durMs / sampleEveryMs) + 1).toInt().coerceAtMost(maxSamples)
        } else {
            maxSamples
        }
        val out = ArrayList<Bitmap>()
        var t = 0L
        while (t < durMs && out.size < maxSamples) {
            r.getFrameAtTime(t * 1000, MediaMetadataRetriever.OPTION_CLOSEST)?.let { out.add(it) }
            onExtracted(out.size, totalEstimate)
            t += sampleEveryMs
        }
        r.release()
        return out
    }
}

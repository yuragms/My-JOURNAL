package com.s24vision.app.vision

import android.graphics.RectF
import kotlin.math.hypot
import kotlin.math.max

/** Результат трекинга: детекция + стабильный номер на протяжении видео/сессии. */
data class TrackedDetection(
    val detection: Detection,
    val trackId: Int,
)

/**
 * IoU + близость центров: номер держится на быстрых/мелких объектах (дрон).
 * [smoothAlpha] сглаживает джиттер рамки (0 = выкл).
 */
class BoxTracker(
    private val maxMissed: Int = 12,
    private val iouMatchTh: Float = 0.3f,
    private val smoothAlpha: Float = 0f,
) {
    private data class Track(
        val id: Int,
        var rect: RectF,
        var missed: Int,
        var label: String? = null,
    )

    private var nextId = 1
    private val tracks = ArrayList<Track>()

    fun reset() {
        tracks.clear()
        nextId = 1
    }

    fun update(detections: List<Detection>): List<TrackedDetection> {
        if (detections.isEmpty()) {
            tracks.forEach { it.missed++ }
            tracks.removeAll { it.missed > maxMissed }
            return emptyList()
        }

        tracks.forEach { it.missed++ }

        val pairs = ArrayList<Triple<Int, Int, Float>>()
        for (ti in tracks.indices) {
            for (di in detections.indices) {
                val score = matchScore(tracks[ti], detections[di])
                if (score >= matchMinScore) pairs.add(Triple(ti, di, score))
            }
        }
        pairs.sortByDescending { it.third }

        val usedTrack = BooleanArray(tracks.size)
        val usedDet = BooleanArray(detections.size)
        val out = ArrayList<TrackedDetection>()

        for ((ti, di, _) in pairs) {
            if (usedTrack[ti] || usedDet[di]) continue
            usedTrack[ti] = true
            usedDet[di] = true
            val t = tracks[ti]
            val det = detections[di]
            t.rect = smoothRect(t.rect, det.rect)
            t.label = det.label
            t.missed = 0
            out.add(TrackedDetection(det.copy(rect = RectF(t.rect)), t.id))
        }

        for (di in detections.indices) {
            if (usedDet[di]) continue
            val det = detections[di]
            val id = nextId++
            tracks.add(Track(id, RectF(det.rect), missed = 0, label = det.label))
            out.add(TrackedDetection(det, id))
        }

        tracks.removeAll { it.missed > maxMissed }
        return out.sortedBy { it.trackId }
    }

    private fun matchScore(track: Track, det: Detection): Float {
        val iou = DetectionFilters.iou(track.rect, det.rect)
        var score = iou
        if (track.label != null && track.label.equals(det.label, ignoreCase = true)) {
            score += 0.08f
        }
        if (iou >= iouMatchTh) return score + 1f

        val acx = (track.rect.left + track.rect.right) * 0.5f
        val acy = (track.rect.top + track.rect.bottom) * 0.5f
        val bcx = (det.rect.left + det.rect.right) * 0.5f
        val bcy = (det.rect.top + det.rect.bottom) * 0.5f
        val dist = hypot(acx - bcx, acy - bcy)
        val span = max(
            max(track.rect.width(), track.rect.height()),
            max(det.rect.width(), det.rect.height()),
        ).coerceAtLeast(12f)
        if (dist < span * 1.5f) return score.coerceAtLeast(0.32f)
        return score
    }

    private fun smoothRect(prev: RectF, raw: RectF): RectF {
        if (smoothAlpha <= 0f) return RectF(raw)
        val a = smoothAlpha.coerceIn(0f, 1f)
        val inv = 1f - a
        return RectF(
            prev.left * inv + raw.left * a,
            prev.top * inv + raw.top * a,
            prev.right * inv + raw.right * a,
            prev.bottom * inv + raw.bottom * a,
        )
    }

    companion object {
        /** Люди — крупные, медленные. */
        fun persons() = BoxTracker(maxMissed = 14, iouMatchTh = 0.28f, smoothAlpha = 0.25f)

        /** Дроны/объекты — мелкие, быстрые, джиттер детектора. */
        fun objects() = BoxTracker(maxMissed = 48, iouMatchTh = 0.18f, smoothAlpha = 0.42f)

        /** Обучение по ролику — редкие кадры, длинная память трека. */
        fun enrollObjects() = BoxTracker(maxMissed = 24, iouMatchTh = 0.15f, smoothAlpha = 0.35f)

        fun enrollPersons() = BoxTracker(maxMissed = 6, iouMatchTh = 0.25f, smoothAlpha = 0.3f)

        private const val matchMinScore = 0.28f
    }
}

package com.s24vision.app.vision

import android.graphics.RectF

/**
 * Отсекает мелкие и слабые детекции (трава, шум фона), особенно «неопознанный объект».
 */
object DetectionFilters {

    /** Мин. доля площади кадра для человека. */
    private const val MIN_PERSON_AREA_RATIO = 0.006f

    /** Мин. доля площади кадра для прочих объектов. */
    private const val MIN_OBJECT_AREA_RATIO = 0.022f

    private const val MIN_PERSON_SIDE_PX = 36f
    private const val MIN_OBJECT_SIDE_PX = 56f

    /** Базовый порог уверенности для не-людей (поверх confTh детектора). */
    private const val OBJECT_SCORE_MIN = 0.52f

    /** Для класса object / неопознанный — ещё строже. */
    private const val GENERIC_SCORE_MIN = 0.68f

    /** Мелкие «шумные» классы RAM++ (растительность, фон) — только если крупный бокс. */
    private const val NOISY_LARGE_AREA_RATIO = 0.06f

    private val NOISY_OUTDOOR_LABELS = setOf(
        "grass", "plant", "leaf", "weed", "flower", "ground", "field",
        "vegetation", "foliage", "branch", "twig", "shrub", "bush", "meadow",
        "lawn", "straw", "hay", "crop", "sapling", "herb", "moss",
    )

    fun filter(detections: List<Detection>, frameW: Int, frameH: Int): List<Detection> {
        if (detections.isEmpty()) return detections
        val frameArea = frameW * frameH.toFloat()
        return detections.filter { d -> keep(d, frameArea) }
    }

    private fun keep(d: Detection, frameArea: Float): Boolean {
        val w = d.rect.width().coerceAtLeast(0f)
        val h = d.rect.height().coerceAtLeast(0f)
        val areaRatio = (w * h) / frameArea
        val minSide = minOf(w, h)

        if (d.isPerson) {
            return areaRatio >= MIN_PERSON_AREA_RATIO && minSide >= MIN_PERSON_SIDE_PX
        }

        if (d.score < OBJECT_SCORE_MIN) return false
        if (d.label == ClassNames.GENERIC && d.score < GENERIC_SCORE_MIN) return false
        if (d.label.lowercase() in NOISY_OUTDOOR_LABELS && areaRatio < NOISY_LARGE_AREA_RATIO) {
            return false
        }
        return areaRatio >= MIN_OBJECT_AREA_RATIO && minSide >= MIN_OBJECT_SIDE_PX
    }

    /** Мелкие дроны в небе — ниже порог площади, чем у обычных объектов. */
    fun filterDrone(detections: List<Detection>, frameW: Int, frameH: Int): List<Detection> {
        if (detections.isEmpty()) return detections
        val frameArea = frameW * frameH.toFloat()
        return detections.filter { d ->
            val w = d.rect.width().coerceAtLeast(0f)
            val h = d.rect.height().coerceAtLeast(0f)
            val areaRatio = (w * h) / frameArea
            val minSide = minOf(w, h)
            d.score >= 0.32f && areaRatio >= 0.0008f && minSide >= 12f
        }
    }

    /** Non-maximum suppression по IoU (убирает дублирующиеся боксы). */
    fun nms(detections: List<Detection>, iouTh: Float = 0.45f): List<Detection> {
        if (detections.size <= 1) return detections
        val sorted = detections.sortedByDescending { it.score }
        val kept = ArrayList<Detection>()
        val suppressed = BooleanArray(sorted.size)
        for (i in sorted.indices) {
            if (suppressed[i]) continue
            kept.add(sorted[i])
            for (j in i + 1 until sorted.size) {
                if (!suppressed[j] && iou(sorted[i].rect, sorted[j].rect) > iouTh) {
                    suppressed[j] = true
                }
            }
        }
        return kept
    }

    fun iou(a: RectF, b: RectF): Float {
        val interL = maxOf(a.left, b.left)
        val interT = maxOf(a.top, b.top)
        val interR = minOf(a.right, b.right)
        val interB = minOf(a.bottom, b.bottom)
        val interW = (interR - interL).coerceAtLeast(0f)
        val interH = (interB - interT).coerceAtLeast(0f)
        val inter = interW * interH
        if (inter <= 0f) return 0f
        val union = a.width() * a.height() + b.width() * b.height() - inter
        return if (union > 0f) inter / union else 0f
    }
}

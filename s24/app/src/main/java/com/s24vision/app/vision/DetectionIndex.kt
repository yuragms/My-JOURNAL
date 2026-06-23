package com.s24vision.app.vision

/** Выбор бокса по стабильному trackId (номер на всём видео/сессии). */
object DetectionIndex {

    fun pickByTrackId(tracked: List<TrackedDetection>, trackId: Int): Detection? =
        tracked.firstOrNull { it.trackId == trackId }?.detection

    /** Крупнейший drone или объект с макс. уверенностью — запасной вариант для обучения. */
    fun fallbackObject(detections: List<Detection>, preferDrone: Boolean = true): Detection? {
        if (detections.isEmpty()) return null
        val pool = if (preferDrone) {
            detections.filter { it.label.equals("drone", ignoreCase = true) }.ifEmpty { detections }
        } else {
            detections
        }
        return pool.maxByOrNull { it.rect.width() * it.rect.height() * it.score }
    }

    /**
     * Выбор трека для обучения: сначала точный #N, иначе единственный объект,
     * иначе ближайший к крупнейшему drone.
     */
    fun pickTrackedForEnroll(
        tracked: List<TrackedDetection>,
        trackId: Int,
        objectDetections: List<Detection>,
    ): TrackedDetection? {
        tracked.firstOrNull { it.trackId == trackId }?.let { return it }
        if (tracked.size == 1) return tracked.first()
        val fallback = fallbackObject(objectDetections, preferDrone = true) ?: return null
        return tracked.maxByOrNull { DetectionFilters.iou(it.detection.rect, fallback.rect) }
            ?.takeIf { DetectionFilters.iou(it.detection.rect, fallback.rect) >= 0.18f }
    }

    /** Какой trackId дал больше всего попаданий — если пользовательский #N пуст. */
    fun dominantTrackId(hits: Map<Int, Int>, preferred: Int): Int {
        if ((hits[preferred] ?: 0) > 0) return preferred
        return hits.maxByOrNull { it.value }?.key ?: preferred
    }
}

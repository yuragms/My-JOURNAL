package com.s24vision.app.core.profiles

/** Метаданные обученного профиля для экрана «Профили». */
object ProfileMetrics {

    fun bytesToMb(bytes: Long): Float = bytes / (1024f * 1024f)

    fun formatSizeMb(mb: Float): String =
        if (mb < 0.1f) "<0.1 MB" else if (mb < 10f) "%.1f MB".format(mb) else "%.0f MB".format(mb)

    fun formatAccuracy(score: Float): String = "%.2f".format(score)

    /**
     * Оценка качества по числу эталонных эмбеддингов (чем больше обучение — тем выше).
     * Не заменяет реальный benchmark, только подсказка в UI.
     */
    fun estimateAccuracy(type: ProfileType, sampleCount: Int): Float {
        val base = when (type) {
            ProfileType.FACE -> 0.50f
            ProfileType.BODY -> 0.46f
            ProfileType.OBJECT -> 0.42f
        }
        return (base + sampleCount * 0.007f).coerceIn(0.35f, 0.90f)
    }
}

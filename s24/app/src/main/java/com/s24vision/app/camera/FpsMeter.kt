package com.s24vision.app.camera

/** Скользящее окно ~1 с для оценки fps. */
class FpsMeter(private val windowNs: Long = 1_000_000_000L) {

    private val stamps = ArrayDeque<Long>(64)

    fun reset() {
        stamps.clear()
    }

    fun tick(): Float {
        val now = System.nanoTime()
        stamps.addLast(now)
        val cutoff = now - windowNs
        while (stamps.isNotEmpty() && stamps.first() < cutoff) {
            stamps.removeFirst()
        }
        if (stamps.size < 2) return 0f
        val span = stamps.last() - stamps.first()
        if (span <= 0L) return 0f
        return (stamps.size - 1) * 1_000_000_000f / span
    }
}

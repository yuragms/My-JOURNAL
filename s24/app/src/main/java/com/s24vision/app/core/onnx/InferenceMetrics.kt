package com.s24vision.app.core.onnx

/** Время последнего кадра pipeline (детекция + распознавание), мс. */
object InferenceMetrics {
    @Volatile
    var lastMs: Float = 0f
}

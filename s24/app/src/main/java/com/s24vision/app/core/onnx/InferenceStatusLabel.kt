package com.s24vision.app.core.onnx

import com.s24vision.app.core.settings.RecognitionSettings

object InferenceStatusLabel {

    fun format(settings: RecognitionSettings): String {
        val mode = settings.inferenceBackend().titleRu
        val accel = OnnxRuntimeStatus.activeSummary()
        val infer = InferenceMetrics.lastMs
            .takeIf { it > 0f }
            ?.let { " · infer ${"%.0f".format(it)}ms" }
            .orEmpty()
        return "ORT: $mode ($accel)$infer"
    }
}

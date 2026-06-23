package com.s24vision.app.core.onnx

/** Куда ONNX Runtime отдаёт inference (для A/B-тестов на устройстве). */
enum class InferenceBackend(
    val prefKey: String,
    val titleRu: String,
    val hintRu: String,
) {
    CPU(
        prefKey = "cpu",
        titleRu = "CPU",
        hintRu = "XNNPACK на больших ядрах — эталон стабильности",
    ),
    NNAPI(
        prefKey = "nnapi",
        titleRu = "NNAPI",
        hintRu = "FP16, NPU/GPU через драйвер; при ошибке — откат на CPU",
    ),
    NNAPI_NO_CPU(
        prefKey = "nnapi_nc",
        titleRu = "NNAPI строгий",
        hintRu = "Без CPU-fallback в NNAPI; при сбое — мягкий откат",
    ),
    ;

    companion object {
        fun fromPref(key: String): InferenceBackend =
            entries.find { it.prefKey == key } ?: NNAPI
    }
}

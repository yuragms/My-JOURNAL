package com.s24vision.app.core.onnx

data class OnnxSessionReport(
    val assetName: String,
    val requested: InferenceBackend,
    /** CPU, NNAPI или NNAPI* (строгий режим). */
    val active: String,
    val note: String? = null,
)

package com.s24vision.app.core.onnx

import android.content.Context
import com.s24vision.app.core.settings.RecognitionSettings

/** Ленивая сессия ONNX; пересоздаётся при смене [InferenceBackend] в настройках. */
class OnnxModelHolder(
    private val context: Context,
    private val assetName: String,
    private val settings: RecognitionSettings,
) {
    @Volatile
    private var cached: OnnxModel? = null

    @Volatile
    private var cachedBackend: InferenceBackend? = null

    @Synchronized
    fun get(): OnnxModel {
        val backend = settings.inferenceBackend()
        val model = cached
        if (model != null && cachedBackend == backend) return model
        model?.close()
        val next = OnnxModel(context, assetName, backend)
        cached = next
        cachedBackend = backend
        return next
    }

    @Synchronized
    fun close() {
        cached?.close()
        cached = null
        cachedBackend = null
    }
}

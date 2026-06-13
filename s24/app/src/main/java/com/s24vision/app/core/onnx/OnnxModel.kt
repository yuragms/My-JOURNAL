package com.s24vision.app.core.onnx

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.providers.NNAPIFlags
import android.content.Context
import java.nio.FloatBuffer
import java.util.EnumSet

/** Обёртка над OrtSession: загрузка модели из assets и прогон одного входа. */
class OnnxModel(
    context: Context,
    assetName: String,
    useNnapi: Boolean = true,
) {
    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession

    init {
        val bytes = context.assets.open("models/$assetName").readBytes()
        val opts = OrtSession.SessionOptions().apply {
            if (useNnapi) runCatching { addNnapi(EnumSet.of(NNAPIFlags.USE_FP16)) }
        }
        session = env.createSession(bytes, opts)
    }

    val inputName: String get() = session.inputNames.iterator().next()

    /** Прогон одного входа. Возвращает первый выход как FloatArray + его форму. */
    fun run(input: FloatArray, shape: LongArray): Pair<FloatArray, LongArray> {
        OnnxTensor.createTensor(env, FloatBuffer.wrap(input), shape).use { t ->
            session.run(mapOf(inputName to t)).use { res ->
                val out = res[0] as OnnxTensor
                val info = out.info.shape
                val buf = out.floatBuffer
                val arr = FloatArray(buf.remaining())
                buf.get(arr)
                return arr to info
            }
        }
    }

    fun close() {
        session.close()
    }
}

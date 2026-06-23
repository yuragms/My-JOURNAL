package com.s24vision.app.core.onnx

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.providers.NNAPIFlags
import android.content.Context
import android.util.Log
import java.nio.FloatBuffer
import java.util.EnumSet

/** Обёртка над OrtSession: загрузка модели из assets и прогон одного входа. */
class OnnxModel(
    context: Context,
    private val assetName: String,
    private val backend: InferenceBackend = InferenceBackend.NNAPI,
) {
    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession
    val report: OnnxSessionReport

    init {
        val bytes = context.assets.open("models/$assetName").readBytes()
        val created = createSession(bytes, backend)
        session = created.first
        report = created.second
        OnnxRuntimeStatus.register(report)
        Log.i(
            TAG,
            "$assetName: запрошен ${backend.titleRu} → ${report.active}" +
                (report.note?.let { " ($it)" } ?: ""),
        )
    }

    private fun createSession(
        bytes: ByteArray,
        backend: InferenceBackend,
    ): Pair<OrtSession, OnnxSessionReport> {
        when (backend) {
            InferenceBackend.CPU -> {
                val s = env.createSession(bytes, OrtSession.SessionOptions())
                return s to OnnxSessionReport(assetName, backend, "CPU", null)
            }
            InferenceBackend.NNAPI -> {
                runCatching {
                    val opts = OrtSession.SessionOptions().apply {
                        addNnapi(EnumSet.of(NNAPIFlags.USE_FP16))
                    }
                    val s = env.createSession(bytes, opts)
                    return s to OnnxSessionReport(assetName, backend, "NNAPI", null)
                }.onFailure { e ->
                    Log.w(TAG, "$assetName: NNAPI недоступен, CPU fallback", e)
                }
                val s = env.createSession(bytes, OrtSession.SessionOptions())
                return s to OnnxSessionReport(assetName, backend, "CPU", "NNAPI fallback")
            }
            InferenceBackend.NNAPI_NO_CPU -> {
                runCatching {
                    val opts = OrtSession.SessionOptions().apply {
                        addNnapi(EnumSet.of(NNAPIFlags.USE_FP16, NNAPIFlags.CPU_DISABLED))
                    }
                    val s = env.createSession(bytes, opts)
                    return s to OnnxSessionReport(assetName, backend, "NNAPI*", null)
                }.onFailure { e ->
                    Log.w(TAG, "$assetName: строгий NNAPI недоступен", e)
                }
                runCatching {
                    val opts = OrtSession.SessionOptions().apply {
                        addNnapi(EnumSet.of(NNAPIFlags.USE_FP16))
                    }
                    val s = env.createSession(bytes, opts)
                    return s to OnnxSessionReport(assetName, backend, "NNAPI", "строгий режим недоступен")
                }.onFailure { e ->
                    Log.w(TAG, "$assetName: NNAPI недоступен, CPU fallback", e)
                }
                val s = env.createSession(bytes, OrtSession.SessionOptions())
                return s to OnnxSessionReport(assetName, backend, "CPU", "NNAPI fail")
            }
        }
    }

    val inputName: String get() = session.inputNames.iterator().next()

    fun run(input: FloatArray, shape: LongArray): Pair<FloatArray, LongArray> {
        val (arr, info) = runAll(input, shape).first()
        return arr to info
    }

    fun runAll(input: FloatArray, shape: LongArray): List<Pair<FloatArray, LongArray>> {
        OnnxTensor.createTensor(env, FloatBuffer.wrap(input), shape).use { t ->
            session.run(mapOf(inputName to t)).use { res ->
                return (0 until res.size()).map { i ->
                    val out = res.get(i) as OnnxTensor
                    val buf = out.floatBuffer
                    val arr = FloatArray(buf.remaining())
                    buf.get(arr)
                    arr to out.info.shape
                }
            }
        }
    }

    fun close() {
        session.close()
    }

    companion object {
        private const val TAG = "OnnxModel"
    }
}

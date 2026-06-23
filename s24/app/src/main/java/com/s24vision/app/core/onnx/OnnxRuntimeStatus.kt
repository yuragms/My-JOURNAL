package com.s24vision.app.core.onnx

import java.util.concurrent.ConcurrentHashMap

/** Актуальные сессии ONNX после последней загрузки моделей. */
object OnnxRuntimeStatus {

    private val reports = ConcurrentHashMap<String, OnnxSessionReport>()

    fun register(report: OnnxSessionReport) {
        reports[report.assetName] = report
    }

    fun clear() = reports.clear()

    fun all(): List<OnnxSessionReport> =
        reports.values.sortedBy { it.assetName }

    /** Кратко для оверлея камеры: «NNAPI: 4/6 на ускорителе». */
    fun activeSummary(): String {
        if (reports.isEmpty()) return "модели не загружены"
        val accel = reports.values.count { it.active.startsWith("NNAPI") }
        val total = reports.size
        return "$accel/$total на ускорителе"
    }

    /** Подробно для экрана «Профили». */
    fun detailLines(): List<String> = all().map { r ->
        val note = r.note?.let { " ($it)" }.orEmpty()
        "${r.assetName}: ${r.active}$note"
    }
}

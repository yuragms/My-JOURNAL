package com.s24vision.app.core.monitor

import java.io.File

/**
 * Best-effort GPU-нагрузка для Adreno через vendor sysfs.
 * Per-app GPU% Android без root не предоставляет; при недоступности — `n/a`.
 */
object GpuMonitor {

    private val candidates = listOf(
        "/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage",
        "/sys/class/kgsl/kgsl-3d0/gpubusy",
    )

    fun percent(): Float? {
        for (p in candidates) {
            val f = File(p)
            if (!f.canRead()) continue
            val raw = runCatching { f.readText().trim() }.getOrNull() ?: continue
            val nums = Regex("\\d+").findAll(raw).map { it.value.toLong() }.toList()
            // gpu_busy_percentage -> "37 %"
            if (raw.contains("%") && nums.isNotEmpty()) {
                return nums[0].toFloat().coerceIn(0f, 100f)
            }
            // gpubusy -> "busy total"
            if (nums.size >= 2 && nums[1] > 0) {
                return (nums[0].toFloat() / nums[1] * 100f).coerceIn(0f, 100f)
            }
        }
        return null
    }

    fun label(): String = percent()?.let { "GPU: ${"%.0f".format(it)}%" } ?: "GPU: n/a"
}

package com.s24vision.app.core.monitor

import android.os.SystemClock
import com.s24vision.app.core.onnx.InferenceStatusLabel
import com.s24vision.app.core.settings.RecognitionSettings

/** Строка нагрузки CPU/GPU и имени потока для UI. */
object SystemLoadLabel {

    @Volatile
    private var prevCpu: CpuSample? = CpuMonitor.readSystem()

    @Volatile
    private var prevProc: ProcessCpuSample? = CpuMonitor.readProcess()

    @Volatile
    private var prevCpuidle: CpuidleSample? = CpuMonitor.readCpuidle()

    @Volatile
    private var prevWallMs: Long = SystemClock.elapsedRealtime()

    fun format(threadLabel: String? = Thread.currentThread().name, settings: RecognitionSettings? = null): String {
        val curWallMs = SystemClock.elapsedRealtime()
        val cur = CpuMonitor.readSystem()
        val curProc = CpuMonitor.readProcess()
        val curCpuidle = if (cur == null) CpuMonitor.readCpuidle() else null

        val appPct = when {
            prevProc != null && curProc != null && prevCpu != null && cur != null ->
                CpuMonitor.processPercent(prevProc!!, curProc, prevCpu!!, cur)
            prevProc != null && curProc != null && prevWallMs > 0 ->
                CpuMonitor.appPercentWall(prevProc!!, curProc, prevWallMs, curWallMs)
            else -> null
        }

        val sysPct = when {
            prevCpu != null && cur != null ->
                CpuMonitor.systemPercent(prevCpu!!, cur)
            prevCpuidle != null && curCpuidle != null ->
                CpuMonitor.systemPercentCpuidle(prevCpuidle!!, curCpuidle)
            else -> null
        }

        if (cur != null) prevCpu = cur
        if (curProc != null) prevProc = curProc
        if (curCpuidle != null) prevCpuidle = curCpuidle
        prevWallMs = curWallMs

        val app = appPct?.let { "%.0f%%".format(it) } ?: "n/a"
        val sys = sysPct?.let { "%.0f%%".format(it) } ?: "n/a"
        val cpu = "CPU прилож.: $app  система: $sys"
        val gpu = GpuMonitor.label()
        val ort = settings?.let { InferenceStatusLabel.format(it) }
        val thread = threadLabel?.let { "поток: $it" } ?: ""
        return listOfNotNull(cpu, gpu, ort?.takeIf { it.isNotBlank() }, thread.takeIf { it.isNotBlank() })
            .joinToString("  ")
    }
}

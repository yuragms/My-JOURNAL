package com.s24vision.app.core.monitor

import java.io.File

data class CpuSample(val idle: Long, val total: Long)

object CpuMonitor {

    /** Парсит строку `cpu  ...` из /proc/stat. idle = idle + iowait. */
    fun parseProcStat(line: String): CpuSample {
        val parts = line.trim().split(Regex("\\s+")).drop(1).map { it.toLong() }
        val idle = parts.getOrElse(3) { 0 } + parts.getOrElse(4) { 0 }
        val total = parts.sum()
        return CpuSample(idle, total)
    }

    fun systemPercent(prev: CpuSample, cur: CpuSample): Float {
        val dTotal = (cur.total - prev.total).toFloat()
        if (dTotal <= 0f) return 0f
        val dIdle = (cur.idle - prev.idle).toFloat()
        return ((dTotal - dIdle) / dTotal * 100f).coerceIn(0f, 100f)
    }

    fun readSystem(): CpuSample =
        parseProcStat(File("/proc/stat").bufferedReader().use { it.readLine() })
}

package com.s24vision.app.core.monitor

import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import java.io.File

data class CpuSample(val idle: Long, val total: Long)

data class ProcessCpuSample(val jiffies: Long)

/** Сумма cpuidle time (мкс) по ядрам + метка wall-clock для fallback без /proc/stat. */
data class CpuidleSample(val perCoreIdleUs: LongArray, val wallMs: Long) {
    override fun equals(other: Any?) = other is CpuidleSample &&
        wallMs == other.wallMs && perCoreIdleUs.contentEquals(other.perCoreIdleUs)

    override fun hashCode() = 31 * wallMs.hashCode() + perCoreIdleUs.contentHashCode()
}

object CpuMonitor {

    private val clkTck: Float by lazy {
        Os.sysconf(OsConstants._SC_CLK_TCK).toFloat().coerceAtLeast(1f)
    }

    /** Парсит строку `cpu  ...` из /proc/stat. idle = idle + iowait. */
    fun parseProcStat(line: String): CpuSample {
        val parts = line.trim().split(Regex("\\s+")).drop(1).map { it.toLong() }
        val idle = parts.getOrElse(3) { 0 } + parts.getOrElse(4) { 0 }
        val total = parts.sum()
        return CpuSample(idle, total)
    }

    fun parseOnlineCpus(raw: String): List<Int> {
        val result = mutableListOf<Int>()
        for (part in raw.split(',')) {
            val p = part.trim()
            if (p.isEmpty()) continue
            if ('-' in p) {
                val bounds = p.split('-', limit = 2)
                val from = bounds[0].toInt()
                val to = bounds.getOrElse(1) { bounds[0] }.toInt()
                for (i in from..to) result.add(i)
            } else {
                result.add(p.toInt())
            }
        }
        return result
    }

    fun systemPercent(prev: CpuSample, cur: CpuSample): Float {
        val dTotal = (cur.total - prev.total).toFloat()
        if (dTotal <= 0f) return 0f
        val dIdle = (cur.idle - prev.idle).toFloat()
        return ((dTotal - dIdle) / dTotal * 100f).coerceIn(0f, 100f)
    }

    /** utime + stime из строки `/proc/[pid]/stat` (поля 14–15). */
    fun parseProcessStat(line: String): Long {
        val after = line.substringAfterLast(')')
        val fields = after.trim().split(Regex("\\s+"))
        val utime = fields.getOrElse(11) { "0" }.toLong()
        val stime = fields.getOrElse(12) { "0" }.toLong()
        return utime + stime
    }

    /** Доля CPU процесса от суммарной ёмкости системы (0–100%, мультиядро учтено в /proc/stat). */
    fun processPercent(
        prevProc: ProcessCpuSample,
        curProc: ProcessCpuSample,
        prevSys: CpuSample,
        curSys: CpuSample,
    ): Float {
        val dTotal = (curSys.total - prevSys.total).toFloat()
        if (dTotal <= 0f) return 0f
        val dProc = (curProc.jiffies - prevProc.jiffies).toFloat().coerceAtLeast(0f)
        return (dProc / dTotal * 100f).coerceIn(0f, 100f)
    }

    /** CPU приложения без /proc/stat: jiffies процесса / (wall × ядра × CLK_TCK). */
    fun appPercentWall(
        prevProc: ProcessCpuSample,
        curProc: ProcessCpuSample,
        prevWallMs: Long,
        curWallMs: Long,
    ): Float {
        val dProc = (curProc.jiffies - prevProc.jiffies).toFloat().coerceAtLeast(0f)
        val dMs = curWallMs - prevWallMs
        if (dMs <= 0) return 0f
        val capacity = dMs / 1000f * clkTck * Runtime.getRuntime().availableProcessors()
        if (capacity <= 0f) return 0f
        return (dProc / capacity * 100f).coerceIn(0f, 100f)
    }

    /**
     * Средняя загрузка CPU по ядрам через cpuidle (мкс).
     * На Samsung /proc/stat часто недоступен, cpuidle sysfs — доступен.
     */
    fun systemPercentCpuidle(prev: CpuidleSample, cur: CpuidleSample): Float {
        val wallUs = (cur.wallMs - prev.wallMs) * 1000L
        if (wallUs <= 0) return 0f
        val cores = minOf(prev.perCoreIdleUs.size, cur.perCoreIdleUs.size)
        if (cores == 0) return 0f
        var sum = 0f
        for (i in 0 until cores) {
            val idleDelta = (cur.perCoreIdleUs[i] - prev.perCoreIdleUs[i]).coerceAtLeast(0L)
            val idleClamped = idleDelta.coerceAtMost(wallUs)
            sum += (wallUs - idleClamped) * 100f / wallUs
        }
        return (sum / cores).coerceIn(0f, 100f)
    }

    /**
     * Читает /proc/stat. На современных Android доступ к нему запрещён политикой
     * SELinux (EACCES), поэтому возвращаем null — UI перейдёт на cpuidle fallback.
     */
    fun readSystem(): CpuSample? = runCatching {
        parseProcStat(File("/proc/stat").bufferedReader().use { it.readLine() })
    }.getOrNull()

    fun readProcess(): ProcessCpuSample? = runCatching {
        ProcessCpuSample(parseProcessStat(File("/proc/self/stat").readText()))
    }.getOrNull()

    fun readCpuidle(): CpuidleSample? = runCatching {
        val online = parseOnlineCpus(File("/sys/devices/system/cpu/online").readText().trim())
        if (online.isEmpty()) return@runCatching null
        val idle = LongArray(online.size)
        for (i in online.indices) {
            idle[i] = readCoreIdleUs(online[i]) ?: return@runCatching null
        }
        CpuidleSample(idle, SystemClock.elapsedRealtime())
    }.getOrNull()

    private fun readCoreIdleUs(cpu: Int): Long? {
        val dir = File("/sys/devices/system/cpu/cpu$cpu/cpuidle")
        if (!dir.isDirectory) return null
        var sum = 0L
        val states = dir.listFiles()?.filter { it.isDirectory && it.name.startsWith("state") } ?: return null
        if (states.isEmpty()) return null
        for (state in states) {
            val raw = File(state, "time").takeIf { it.canRead() }?.readText()?.trim() ?: continue
            sum += raw.toULongOrNull()?.toLong() ?: raw.toLongOrNull() ?: continue
        }
        return sum
    }
}

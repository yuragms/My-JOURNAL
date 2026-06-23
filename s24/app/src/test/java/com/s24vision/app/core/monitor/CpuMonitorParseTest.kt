package com.s24vision.app.core.monitor

import org.junit.Assert.assertEquals
import org.junit.Test

class CpuMonitorParseTest {

    @Test
    fun systemPercentFromTwoSamples() {
        val prev = CpuSample(idle = 100, total = 200)
        val cur = CpuSample(idle = 150, total = 300)
        assertEquals(50f, CpuMonitor.systemPercent(prev, cur), 1e-3f)
    }

    @Test
    fun parseProcStatLine() {
        // busy = 100+0+200 = 300, idle = 700+0, total = 1000
        val line = "cpu  100 0 200 700 0 0 0 0 0 0"
        val s = CpuMonitor.parseProcStat(line)
        assertEquals(700L, s.idle)
        assertEquals(1000L, s.total)
    }

    @Test
    fun processPercentFromSamples() {
        val prevProc = ProcessCpuSample(100)
        val curProc = ProcessCpuSample(150)
        val prevSys = CpuSample(idle = 100, total = 200)
        val curSys = CpuSample(idle = 150, total = 300)
        assertEquals(50f, CpuMonitor.processPercent(prevProc, curProc, prevSys, curSys), 1e-3f)
    }

    @Test
    fun systemPercentCpuidleFromSamples() {
        val prev = CpuidleSample(longArrayOf(0L, 0L), wallMs = 0L)
        val cur = CpuidleSample(longArrayOf(500_000L, 900_000L), wallMs = 1000L)
        // 2 ядра: 50% и 10% busy → avg 30%
        assertEquals(30f, CpuMonitor.systemPercentCpuidle(prev, cur), 1e-3f)
    }

    @Test
    fun parseOnlineCpusRange() {
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 6, 7), CpuMonitor.parseOnlineCpus("0-7"))
        assertEquals(listOf(0, 1, 5, 6, 7), CpuMonitor.parseOnlineCpus("0-1,5-7"))
    }

    @Test
    fun parseProcessStatLine() {
        val line = "12345 (s24vision) R 1 2 3 4 5 6 7 8 9 10 100 50"
        assertEquals(150L, CpuMonitor.parseProcessStat(line))
    }
}

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
}

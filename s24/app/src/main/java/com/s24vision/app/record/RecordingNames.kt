package com.s24vision.app.record

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Имена роликов: `140626-12.30.mp4` (ддММгг-ЧЧ.мм). */
object RecordingNames {
    private val fmt = SimpleDateFormat("ddMMyy-HH.mm", Locale.US)

    fun now(): String = "${fmt.format(Date())}.mp4"
}

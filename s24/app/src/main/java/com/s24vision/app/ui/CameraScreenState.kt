package com.s24vision.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.camera.core.ImageProxy
import androidx.camera.view.PreviewView
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.LifecycleOwner
import com.s24vision.app.camera.CameraController
import com.s24vision.app.camera.toBitmapCompat
import com.s24vision.app.core.monitor.CpuMonitor
import com.s24vision.app.core.monitor.GpuMonitor
import com.s24vision.app.record.FrameRecorder
import com.s24vision.app.vision.Annotation
import com.s24vision.app.vision.FramePipeline

/** Состояние и логика главного экрана камеры: живой пайплайн, мониторинг, запись. */
class CameraScreenState(
    private val owner: LifecycleOwner,
    private val controller: CameraController,
    private val pipeline: FramePipeline,
) {
    private val _annotations = mutableStateOf<List<Annotation>>(emptyList())
    val annotations: State<List<Annotation>> get() = _annotations

    private val _statsLabel = mutableStateOf("")
    val statsLabel: State<String> get() = _statsLabel

    private val _recording = mutableStateOf(false)
    val recording: State<Boolean> get() = _recording

    private val _frameWidth = mutableStateOf(0)
    val frameWidth: State<Int> get() = _frameWidth

    private val _frameHeight = mutableStateOf(0)
    val frameHeight: State<Int> get() = _frameHeight

    private var preview: PreviewView? = null
    private var prevCpu = CpuMonitor.readSystem()
    private var frameCount = 0
    private var recorder: FrameRecorder? = null

    fun attachPreview(view: PreviewView) {
        preview = view
        controller.start(owner, view, ::onFrame)
    }

    fun toggleLens() {
        preview?.let { controller.toggleLens(owner, it, ::onFrame) }
    }

    fun toggleRecord() {
        _recording.value = !_recording.value
        if (!_recording.value) {
            recorder?.stop()
            recorder = null
        }
    }

    private fun onFrame(proxy: ImageProxy) {
        try {
            frameCount++
            if (frameCount % 3 != 0) return // каждый 3-й кадр — тяжёлый пайплайн
            val bmp = proxy.toBitmapCompat()
            _frameWidth.value = bmp.width
            _frameHeight.value = bmp.height
            val annos = pipeline.process(bmp)
            _annotations.value = annos

            if (_recording.value && annos.isNotEmpty()) {
                val r = recorder ?: FrameRecorder(bmp.width, bmp.height).also {
                    it.start(AppDeps.recordingsDir())
                    recorder = it
                }
                r.encode(drawAnnotations(bmp, annos))
            }

            val cur = CpuMonitor.readSystem()
            val cpu = CpuMonitor.systemPercent(prevCpu, cur)
            prevCpu = cur
            _statsLabel.value = "CPU: %.0f%%  %s".format(cpu, GpuMonitor.label())
        } finally {
            proxy.close()
        }
    }

    private fun drawAnnotations(src: Bitmap, annos: List<Annotation>): Bitmap {
        val bmp = src.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(bmp)
        val box = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 4f }
        val text = Paint().apply { color = Color.YELLOW; textSize = 36f }
        annos.forEach { a ->
            box.color = if (a.det.isPerson) Color.CYAN else Color.GREEN
            canvas.drawRect(a.det.rect, box)
            canvas.drawText(a.displayLabel, a.det.rect.left, (a.det.rect.top - 8f).coerceAtLeast(20f), text)
        }
        return bmp
    }
}

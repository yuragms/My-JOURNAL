package com.s24vision.app.ui

import android.graphics.Bitmap
import androidx.camera.core.ImageProxy
import androidx.camera.view.PreviewView
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.LifecycleOwner
import com.s24vision.app.camera.CameraController
import com.s24vision.app.camera.FpsMeter
import com.s24vision.app.camera.toBitmapCompat
import com.s24vision.app.core.monitor.SystemLoadLabel
import com.s24vision.app.core.settings.RecognitionSettings
import com.s24vision.app.record.RecordBitmapScale
import com.s24vision.app.record.RecordSink
import com.s24vision.app.vision.Annotation
import com.s24vision.app.vision.AnnotationRenderer
import com.s24vision.app.vision.FramePipeline
import com.s24vision.app.vision.StreamLabelRenderer

/** Состояние и логика главного экрана камеры: живой пайплайн, мониторинг, запись. */
class CameraScreenState(
    private val owner: LifecycleOwner,
    private val controller: CameraController,
    private val pipeline: FramePipeline,
    private val settings: RecognitionSettings,
) {
    companion object {
        /** Каждый кадр анализа в обычном режиме. */
        private const val ANALYSIS_STEP = 1

        /** При REC нейросеть реже — запись идёт на каждом кадре камеры. */
        private const val ANALYSIS_STEP_RECORDING = 2
    }

    private var cachedAnnos: List<Annotation> = emptyList()

    private val _annotations = mutableStateOf<List<Annotation>>(emptyList())
    val annotations: State<List<Annotation>> get() = _annotations

    private val _statsLabel = mutableStateOf("")
    val statsLabel: State<String> get() = _statsLabel

    private val _detectStreamLabel = mutableStateOf("")
    val detectStreamLabel: State<String> get() = _detectStreamLabel

    private val _recordStreamLabel = mutableStateOf("")
    val recordStreamLabel: State<String> get() = _recordStreamLabel

    private val detectFps = FpsMeter()
    private val recordFps = FpsMeter()

    private val _recording = mutableStateOf(false)
    val recording: State<Boolean> get() = _recording

    private val _recordSeconds = mutableStateOf(0)
    val recordSeconds: State<Int> get() = _recordSeconds

    private var recordStartedAtMs = 0L

    private val _frameWidth = mutableStateOf(0)
    val frameWidth: State<Int> get() = _frameWidth

    private val _frameHeight = mutableStateOf(0)
    val frameHeight: State<Int> get() = _frameHeight

    private var preview: PreviewView? = null
    private var frameCount = 0
    private var recordSink: RecordSink? = null

    fun attachPreview(view: PreviewView) {
        preview = view
        recordSink = RecordSink(view.context.applicationContext)
        controller.start(owner, view, ::onFrame)
    }

    fun toggleLens() {
        preview?.let {
            pipeline.resetTrackers()
            detectFps.reset()
            recordFps.reset()
            controller.toggleLens(owner, it, ::onFrame)
        }
    }

    fun toggleRecord() {
        _recording.value = !_recording.value
        if (_recording.value) {
            recordStartedAtMs = System.currentTimeMillis()
            _recordSeconds.value = 0
            cachedAnnos = _annotations.value
            recordFps.reset()
            _recordStreamLabel.value = ""
        } else {
            recordStartedAtMs = 0L
            _recordSeconds.value = 0
            recordFps.reset()
            _recordStreamLabel.value = ""
            recordSink?.stop()
        }
    }

    fun currentZoomRatio(): Float = controller.currentZoomRatio()

    fun setZoomRatio(ratio: Float) = controller.setZoomRatio(ratio)

    private fun onFrame(proxy: ImageProxy) {
        try {
            frameCount++
            val recording = _recording.value
            val analyzeThisFrame = !recording || frameCount % ANALYSIS_STEP_RECORDING == 0
            if (!recording && frameCount % ANALYSIS_STEP != 0) return

            val bmp = proxy.toBitmapCompat()
            _frameWidth.value = bmp.width
            _frameHeight.value = bmp.height

            val annos = if (analyzeThisFrame) {
                val fps = detectFps.tick()
                _detectStreamLabel.value = StreamLabelRenderer.format("Детекция", bmp.width, bmp.height, fps)
                pipeline.process(bmp).also { cachedAnnos = it }
            } else {
                cachedAnnos
            }
            _annotations.value = annos

            if (recording) {
                _recordSeconds.value = ((System.currentTimeMillis() - recordStartedAtMs) / 1000L)
                    .toInt()
                    .coerceAtLeast(0)
                val overlay = if (cachedAnnos.isNotEmpty()) {
                    AnnotationRenderer.drawOnBitmap(bmp, cachedAnnos)
                } else {
                    bmp.copy(bmp.config ?: Bitmap.Config.ARGB_8888, false)
                }
                if (overlay !== bmp) bmp.recycle()
                val (rw, rh) = RecordBitmapScale.outputSize(overlay.width, overlay.height)
                val fps = recordFps.tick()
                val recordLbl = StreamLabelRenderer.format("Запись", rw, rh, fps)
                _recordStreamLabel.value = recordLbl
                val streamLabels = buildList {
                    val detectLbl = _detectStreamLabel.value
                    if (detectLbl.isNotBlank()) add(detectLbl)
                    add(recordLbl)
                }
                recordSink?.submit(overlay, overlay.width, overlay.height, streamLabels)
            } else {
                bmp.recycle()
            }

            _statsLabel.value = SystemLoadLabel.format("camera-analysis", settings)
        } finally {
            proxy.close()
        }
    }

}

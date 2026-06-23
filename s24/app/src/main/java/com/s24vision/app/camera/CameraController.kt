package com.s24vision.app.camera

import android.content.Context
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.Executors

/** Управление CameraX: превью + покадровый анализ, переключение селфи/основная, зум. */
class CameraController(private val context: Context) {

    private var useFront = false
    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private var provider: ProcessCameraProvider? = null
    private var camera: Camera? = null

    fun start(owner: LifecycleOwner, previewView: PreviewView, onFrame: (ImageProxy) -> Unit) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            provider = future.get()
            bind(owner, previewView, onFrame)
        }, ContextCompat.getMainExecutor(context))
    }

    fun toggleLens(owner: LifecycleOwner, previewView: PreviewView, onFrame: (ImageProxy) -> Unit) {
        useFront = !useFront
        bind(owner, previewView, onFrame)
    }

    fun currentZoomRatio(): Float =
        camera?.cameraInfo?.zoomState?.value?.zoomRatio ?: 1f

    fun setZoomRatio(ratio: Float) {
        val cam = camera ?: return
        val zoom = cam.cameraInfo.zoomState.value ?: return
        val clamped = ratio.coerceIn(zoom.minZoomRatio, zoom.maxZoomRatio)
        cam.cameraControl.setZoomRatio(clamped)
    }

    private fun bind(owner: LifecycleOwner, previewView: PreviewView, onFrame: (ImageProxy) -> Unit) {
        val p = provider ?: return
        p.unbindAll()
        val selector =
            if (useFront) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { it.setAnalyzer(analysisExecutor) { proxy -> onFrame(proxy) } }
        camera = p.bindToLifecycle(owner, selector, preview, analysis)
    }
}

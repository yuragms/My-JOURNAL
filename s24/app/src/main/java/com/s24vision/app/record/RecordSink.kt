package com.s24vision.app.record

import android.content.Context
import android.graphics.Bitmap
import com.s24vision.app.vision.StreamLabelRenderer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Асинхронная запись кадров в отдельном потоке.
 * Если кодер не успевает — сохраняется только последний кадр (без накопления очереди).
 */
class RecordSink(private val appContext: Context) {

    private val executor = Executors.newSingleThreadExecutor()
    private var recorder: FrameRecorder? = null
    private val pending = AtomicReference<Bitmap?>(null)
    private val pumping = AtomicBoolean(false)

    fun submit(frame: Bitmap, width: Int, height: Int, streamLabels: List<String> = emptyList()) {
        val scaled = RecordBitmapScale.downscale(frame)
        if (scaled !== frame) frame.recycle()
        val labeled = StreamLabelRenderer.drawOnBitmap(scaled, streamLabels)
        if (labeled !== scaled) scaled.recycle()
        val w = labeled.width
        val h = labeled.height
        pending.getAndSet(labeled)?.recycle()
        if (pumping.compareAndSet(false, true)) {
            executor.execute { pump(w, h) }
        }
    }

    private fun pump(width: Int, height: Int) {
        try {
            while (true) {
                val frame = pending.getAndSet(null) ?: break
                val rec = recorder ?: FrameRecorder(appContext, width, height).also {
                    it.start()
                    recorder = it
                }
                rec.encode(frame)
                frame.recycle()
            }
        } finally {
            pumping.set(false)
            if (pending.get() != null && pumping.compareAndSet(false, true)) {
                executor.execute { pump(width, height) }
            }
        }
    }

    fun stop() {
        executor.execute {
            recorder?.stop()
            recorder = null
            pending.getAndSet(null)?.recycle()
        }
    }
}

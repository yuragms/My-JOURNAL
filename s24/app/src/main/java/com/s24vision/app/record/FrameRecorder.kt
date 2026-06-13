package com.s24vision.app.record

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.view.Surface
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Кодирует последовательность Bitmap (уже с нарисованными боксами) в H.264 mp4.
 * Рисуем кадр на input-Surface кодера и дренируем выход в MediaMuxer.
 *
 * Параметры (битрейт/частота кадров) можно подстроить после проверки на устройстве.
 */
class FrameRecorder(
    private val width: Int,
    private val height: Int,
    private val fps: Int = 15,
) {
    private lateinit var codec: MediaCodec
    private lateinit var muxer: MediaMuxer
    private lateinit var surface: Surface
    private var trackIndex = -1
    private var muxerStarted = false
    private var frameIndex = 0L
    private val bufferInfo = MediaCodec.BufferInfo()

    lateinit var outputFile: File
        private set

    fun start(dir: File) {
        dir.mkdirs()
        val ts = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        outputFile = File(dir, "$ts.mp4")

        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface,
            )
            setInteger(MediaFormat.KEY_BIT_RATE, 6_000_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        surface = codec.createInputSurface()
        codec.start()
        muxer = MediaMuxer(outputFile.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    }

    fun encode(frame: Bitmap) {
        val canvas = surface.lockCanvas(null)
        try {
            canvas.drawBitmap(frame, null, Rect(0, 0, width, height), null)
        } finally {
            surface.unlockCanvasAndPost(canvas)
        }
        drain(endOfStream = false)
        frameIndex++
    }

    private fun drain(endOfStream: Boolean) {
        if (endOfStream) codec.signalEndOfInputStream()
        while (true) {
            val outIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
            when {
                outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> if (!endOfStream) return
                outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    trackIndex = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                    muxerStarted = true
                }
                outIndex >= 0 -> {
                    val encoded = codec.getOutputBuffer(outIndex) ?: continue
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        bufferInfo.size = 0
                    }
                    if (bufferInfo.size > 0 && muxerStarted) {
                        bufferInfo.presentationTimeUs = frameIndex * 1_000_000L / fps
                        muxer.writeSampleData(trackIndex, encoded, bufferInfo)
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
            }
        }
    }

    fun stop() {
        runCatching { drain(endOfStream = true) }
        runCatching { codec.stop() }
        runCatching { codec.release() }
        if (muxerStarted) runCatching { muxer.stop() }
        runCatching { muxer.release() }
        runCatching { surface.release() }
    }
}

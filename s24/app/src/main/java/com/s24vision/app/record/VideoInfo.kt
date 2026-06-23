package com.s24vision.app.record

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlin.math.roundToInt

data class VideoInfo(
    val fps: Float,
    val frameCount: Int,
    val durationMs: Long,
) {
    fun summary(sizeBytes: Long): String {
        val fpsStr = if (fps == fps.roundToInt().toFloat()) fps.roundToInt().toString() else "%.1f".format(fps)
        return "$frameCount кадр · $fpsStr fps · ${formatBytes(sizeBytes)}"
    }

    companion object {
        fun probe(context: Context, uri: Uri): VideoInfo? = runCatching {
            val r = MediaMetadataRetriever()
            r.setDataSource(context, uri)
            val duration = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val fps = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toFloatOrNull()
                ?: 15f
            r.release()
            val frames = if (duration > 0) {
                (duration / 1000.0 * fps).roundToInt().coerceAtLeast(1)
            } else {
                0
            }
            VideoInfo(fps, frames, duration)
        }.getOrNull()
    }
}

fun formatBytes(sizeBytes: Long): String = when {
    sizeBytes >= 1_000_000 -> "%.1f МБ".format(sizeBytes / 1_000_000.0)
    sizeBytes >= 1_000 -> "%.0f КБ".format(sizeBytes / 1_000.0)
    else -> "$sizeBytes Б"
}

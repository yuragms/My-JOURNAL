package com.s24vision.app.vision

import android.graphics.Bitmap
import android.util.Log
import android.graphics.RectF
import com.s24vision.app.core.profiles.Profile
import com.s24vision.app.core.profiles.ProfileStore
import com.s24vision.app.core.profiles.ProfileType
import com.s24vision.app.core.settings.BuiltinModels
import com.s24vision.app.core.settings.RecognitionSettings

/**
 * YOLOE (общая сцена) + специализированные детекторы.
 * При выключенном YOLOE — только drone_det и (при необходимости) YOLOE под профили объектов.
 * SCRFD-«person» без YOLOE не запускается — иначе на записи лишние рамки.
 */
class CompositeDetector(
    private val yoloe: YoloeDetector,
    private val drone: DroneDetector,
    private val face: FaceRecognizer,
    private val obj: ObjectRecognizer,
    private val store: ProfileStore,
    private val settings: RecognitionSettings,
    private val profileDetectTh: Float = 0.50f,
) {
    /** Живой поток — с учётом галочек в «Профили». */
    fun detect(bitmap: Bitmap, confTh: Float): List<Detection> {
        val drones = detectDrones(bitmap)
        val others = ArrayList<Detection>(16)

        if (settings.isYoloeEnabled()) {
            val main = yoloe.detect(bitmap, confTh)
            if (settings.isPersonDetectionEnabled()) {
                others.addAll(main.filter { it.isPerson })
            }
            others.addAll(mergeYoloeObjects(main.filter { !it.isPerson }, drones))
        } else if (settings.needsYoloeForObjectProfiles(store)) {
            // Профили объектов (не дрон) — точечный проход YOLOE только под совпадение.
            others.addAll(profileMatchedObjects(bitmap, confTh, drones))
        }
        return drones + DetectionFilters.nms(others, 0.45f)
    }

    /** Обучение по видео — всегда полный детектор. */
    fun detectForEnroll(bitmap: Bitmap, confTh: Float): List<Detection> {
        val main = yoloe.detect(bitmap, confTh)
        val drones = runCatching { drone.detect(bitmap) }.getOrElse { emptyList() }
        val others = ArrayList<Detection>(main.size)
        others.addAll(main.filter { it.isPerson })
        others.addAll(mergeYoloeObjects(main.filter { !it.isPerson }, drones))
        return drones + DetectionFilters.nms(others, 0.45f)
    }

    private fun detectDrones(bitmap: Bitmap): List<Detection> {
        if (!settings.isBuiltinEnabled(BuiltinModels.DRONE_DET)) return emptyList()
        return runCatching { drone.detect(bitmap) }
            .onFailure { Log.w(TAG, "drone_det failed", it) }
            .getOrElse { emptyList() }
    }

    companion object {
        private const val TAG = "S24Drone"
    }

    /** YOLOE выкл.: ищем только боксы, совпадающие с включёнными профилями объектов. */
    private fun profileMatchedObjects(
        bitmap: Bitmap,
        confTh: Float,
        drones: List<Detection>,
    ): List<Detection> {
        val profiles = store.list(ProfileType.OBJECT)
            .filter { settings.isProfileEnabled(ProfileType.OBJECT, it) }
            .mapNotNull { store.load(ProfileType.OBJECT, it) }
        if (profiles.isEmpty()) return emptyList()

        val out = ArrayList<Detection>()
        for (d in yoloe.detect(bitmap, confTh)) {
            if (d.isPerson) continue
            if (d.label.equals("drone", ignoreCase = true)) continue
            if (drones.any { DetectionFilters.iou(d.rect, it.rect) > 0.35f }) continue
            if (matchesProfile(bitmap, d, profiles)) out.add(d)
        }
        return out
    }

    private fun matchesProfile(bitmap: Bitmap, d: Detection, profiles: List<Profile>): Boolean {
        val crop = crop(bitmap, d.rect)
        val match = obj.bestMatch(crop, profiles) ?: return false
        if (crop !== bitmap) crop.recycle()
        return match.second >= profileDetectTh
    }

    private fun mergeYoloeObjects(objects: List<Detection>, drones: List<Detection>): List<Detection> {
        if (drones.isEmpty()) return objects
        val kept = ArrayList<Detection>(objects.size)
        for (d in objects) {
            if (d.label.equals("drone", ignoreCase = true)) continue
            if (DroneConfusion.isConfusable(d.label) &&
                drones.any { DetectionFilters.iou(d.rect, it.rect) > 0.3f }
            ) {
                continue
            }
            if (drones.any { DetectionFilters.iou(d.rect, it.rect) > 0.4f }) continue
            kept.add(d)
        }
        return kept
    }

    private fun crop(bitmap: Bitmap, rect: RectF): Bitmap {
        val l = rect.left.toInt().coerceIn(0, bitmap.width - 1)
        val t = rect.top.toInt().coerceIn(0, bitmap.height - 1)
        val r = rect.right.toInt().coerceIn(l + 1, bitmap.width)
        val b = rect.bottom.toInt().coerceIn(t + 1, bitmap.height)
        return Bitmap.createBitmap(bitmap, l, t, r - l, b - t)
    }

    fun close() {
        yoloe.close()
        drone.close()
    }
}

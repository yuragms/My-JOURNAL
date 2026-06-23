package com.s24vision.app.vision

import android.graphics.Bitmap
import com.s24vision.app.core.math.Embeddings
import com.s24vision.app.core.onnx.InferenceMetrics
import com.s24vision.app.core.profiles.Profile
import com.s24vision.app.core.profiles.ProfileStore
import com.s24vision.app.core.profiles.ProfileType
import com.s24vision.app.core.settings.BuiltinModels
import com.s24vision.app.core.settings.RecognitionSettings

data class Annotation(
    val det: Detection,
    val displayLabel: String,
    /** Стабильный trackId — один номер на протяжении видео/сессии для того же объекта. */
    val boxIndex: Int,
)

/** Связывает детектор и распознаватели: кадр → список аннотаций с подписями. */
class FramePipeline(
    private val detector: CompositeDetector,
    private val face: FaceRecognizer,
    private val body: BodyRecognizer,
    private val obj: ObjectRecognizer,
    private val store: ProfileStore,
    private val settings: RecognitionSettings,
    private val fusion: IdentityFusion,
    private val confTh: Float = 0.45f,
    private val faceTh: Float = 0.45f,
    private val bodyTh: Float = 0.65f,
    /** Порог для generic / drone-детектора. */
    private val objTh: Float = 0.50f,
    /** Переименовать конкретный класс YOLOE (стул, чашка…) только при очень похожем кропе. */
    private val objRelabelTh: Float = 0.76f,
) {
    private val personTracker = BoxTracker.persons()
    private val objectTracker = BoxTracker.objects()

    fun resetTrackers() {
        personTracker.reset()
        objectTracker.reset()
    }

    fun process(bmp: Bitmap): List<Annotation> {
        val t0 = System.nanoTime()
        try {
            val faces = enabledProfiles(ProfileType.FACE)
            val bodies = enabledProfiles(ProfileType.BODY)
            val objs = enabledProfiles(ProfileType.OBJECT)
            val dets = detector.detect(bmp, confTh)
            val out = ArrayList<Annotation>()
            personTracker.update(dets.filter { it.isPerson }).forEach { t ->
                val d = t.detection
                out.add(Annotation(d, personLabel(crop(bmp, d), faces, bodies), t.trackId))
            }
            objectTracker.update(dets.filter { !it.isPerson }).forEach { t ->
                val d = t.detection
                out.add(Annotation(d, objectLabel(crop(bmp, d), d, objs), t.trackId))
            }
            return out
        } finally {
            InferenceMetrics.lastMs = (System.nanoTime() - t0) / 1_000_000f
        }
    }

    private fun enabledProfiles(type: ProfileType): List<Profile> =
        store.list(type)
            .filter { settings.isProfileEnabled(type, it) }
            .mapNotNull { store.load(type, it) }

    private fun crop(bmp: Bitmap, d: Detection): Bitmap {
        val l = d.rect.left.toInt().coerceIn(0, bmp.width - 1)
        val t = d.rect.top.toInt().coerceIn(0, bmp.height - 1)
        val r = d.rect.right.toInt().coerceIn(l + 1, bmp.width)
        val b = d.rect.bottom.toInt().coerceIn(t + 1, bmp.height)
        return Bitmap.createBitmap(bmp, l, t, r - l, b - t)
    }

    private fun bestPerName(q: FloatArray?, profiles: List<Profile>): Pair<String, Float>? {
        if (q == null) return null
        var best: Pair<String, Float>? = null
        for (p in profiles) {
            val (_, s) = Embeddings.bestMatch(q, p.embeddings)
            if (best == null || s > best!!.second) best = p.name to s
        }
        return best
    }

    private fun personLabel(c: Bitmap, faces: List<Profile>, bodies: List<Profile>): String {
        val useFace = settings.isBuiltinEnabled(BuiltinModels.FACE) && faces.isNotEmpty()
        val useBody = settings.isBuiltinEnabled(BuiltinModels.BODY) && bodies.isNotEmpty()
        if (!useFace && !useBody) return "person"
        val fe = if (useFace) face.embed(c) else null
        val be = if (useBody) body.embed(c) else null
        val fm = bestPerName(fe, faces)
        val bm = bestPerName(be, bodies)
        val name = fm?.takeIf { it.second >= faceTh }?.first
            ?: bm?.takeIf { it.second >= bodyTh }?.first
            ?: return "person"
        val faceS = if (fm?.first == name) fm.second else 0f
        val bodyS = if (bm?.first == name) bm.second else 0f
        val total = fusion.total(faceS, bodyS)
        return "%s face:%.2f body:%.2f total:%.2f".format(name, faceS, bodyS, total)
    }

    private fun objectLabel(c: Bitmap, d: Detection, objs: List<Profile>): String {
        val catalogLabel = if (d.label == ClassNames.GENERIC) "неопознанный объект" else d.label
        if (!settings.isBuiltinEnabled(BuiltinModels.OBJECT_ENCODER) || objs.isEmpty()) {
            return catalogLabel
        }
        val m = obj.bestMatch(c, objs) ?: return catalogLabel
        if (m.second < requiredObjScore(d)) return catalogLabel
        return if (d.label.equals("drone", ignoreCase = true)) "drone" else m.first
    }

    private fun requiredObjScore(d: Detection): Float = when {
        d.label.equals("drone", ignoreCase = true) -> objTh
        d.label == ClassNames.GENERIC -> objTh
        DroneConfusion.isConfusable(d.label) -> objTh
        else -> objRelabelTh
    }
}

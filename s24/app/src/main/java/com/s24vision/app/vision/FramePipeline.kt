package com.s24vision.app.vision

import android.graphics.Bitmap
import com.s24vision.app.core.math.Embeddings
import com.s24vision.app.core.profiles.Profile
import com.s24vision.app.core.profiles.ProfileStore
import com.s24vision.app.core.profiles.ProfileType

data class Annotation(val det: Detection, val displayLabel: String)

/** Связывает детектор и распознаватели: кадр → список аннотаций с подписями. */
class FramePipeline(
    private val detector: YoloeDetector,
    private val face: FaceRecognizer,
    private val body: BodyRecognizer,
    private val obj: ObjectRecognizer,
    private val store: ProfileStore,
    private val fusion: IdentityFusion,
    private val confTh: Float = 0.4f,
    private val faceTh: Float = 0.45f,
    private val bodyTh: Float = 0.65f,
    private val objTh: Float = 0.5f,
) {
    fun process(bmp: Bitmap): List<Annotation> {
        val faces = store.list(ProfileType.FACE).mapNotNull { store.load(ProfileType.FACE, it) }
        val bodies = store.list(ProfileType.BODY).mapNotNull { store.load(ProfileType.BODY, it) }
        val objs = store.list(ProfileType.OBJECT).mapNotNull { store.load(ProfileType.OBJECT, it) }
        return detector.detect(bmp, confTh).map { d ->
            val label = if (d.isPerson) {
                personLabel(crop(bmp, d), faces, bodies)
            } else {
                objectLabel(crop(bmp, d), d, objs)
            }
            Annotation(d, label)
        }
    }

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
        val fe = face.embed(c)
        val be = body.embed(c)
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
        val m = obj.match(c, objs, objTh)
        if (m != null) return m.first
        return if (d.label == CocoLabels.GENERIC) "неопознанный объект" else d.label
    }
}

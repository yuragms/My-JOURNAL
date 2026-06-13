package com.s24vision.app.ui

import android.content.Context
import com.s24vision.app.core.profiles.ProfileStore
import com.s24vision.app.core.profiles.ProfileType
import com.s24vision.app.enroll.Enroller
import com.s24vision.app.vision.BodyRecognizer
import com.s24vision.app.vision.FaceRecognizer
import com.s24vision.app.vision.FramePipeline
import com.s24vision.app.vision.IdentityFusion
import com.s24vision.app.vision.ObjectRecognizer
import com.s24vision.app.vision.YoloeDetector
import java.io.File

/** Простой holder зависимостей. Инициализируется один раз в MainActivity. */
object AppDeps {

    private lateinit var appCtx: Context
    private lateinit var storeRef: ProfileStore
    private lateinit var detectorRef: YoloeDetector
    private lateinit var faceRef: FaceRecognizer
    private lateinit var bodyRef: BodyRecognizer
    private lateinit var objectRef: ObjectRecognizer
    private lateinit var pipelineRef: FramePipeline
    private lateinit var enrollerRef: Enroller

    private const val FACE_TH = 0.45f
    private const val BODY_TH = 0.65f

    @Volatile
    private var initialized = false

    @Synchronized
    fun init(ctx: Context) {
        if (initialized) return
        appCtx = ctx.applicationContext
        storeRef = ProfileStore(appCtx.filesDir)
        detectorRef = YoloeDetector(appCtx)
        faceRef = FaceRecognizer(appCtx)
        bodyRef = BodyRecognizer(appCtx)
        objectRef = ObjectRecognizer(appCtx)
        val fusion = IdentityFusion(faceTh = FACE_TH, bodyTh = BODY_TH)
        pipelineRef = FramePipeline(
            detector = detectorRef,
            face = faceRef,
            body = bodyRef,
            obj = objectRef,
            store = storeRef,
            fusion = fusion,
            faceTh = FACE_TH,
            bodyTh = BODY_TH,
        )
        enrollerRef = Enroller(detectorRef, faceRef, bodyRef, objectRef, storeRef)
        initialized = true
    }

    fun store() = storeRef
    fun pipeline() = pipelineRef
    fun enroller() = enrollerRef
    fun recordingsDir() = File(appCtx.filesDir, "recordings")

    fun profileExists(name: String, isPerson: Boolean): Boolean =
        if (isPerson) {
            storeRef.exists(ProfileType.FACE, name) || storeRef.exists(ProfileType.BODY, name)
        } else {
            storeRef.exists(ProfileType.OBJECT, name)
        }
}

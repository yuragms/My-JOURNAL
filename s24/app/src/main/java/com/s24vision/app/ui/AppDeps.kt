package com.s24vision.app.ui

import android.content.Context
import com.s24vision.app.core.profiles.ProfileStore
import com.s24vision.app.core.profiles.ProfileType
import com.s24vision.app.core.settings.RecognitionSettings
import com.s24vision.app.enroll.Enroller
import com.s24vision.app.record.TrainingLog
import com.s24vision.app.vision.BodyRecognizer
import com.s24vision.app.vision.CompositeDetector
import com.s24vision.app.vision.DroneDetector
import com.s24vision.app.vision.FaceRecognizer
import com.s24vision.app.vision.FramePipeline
import com.s24vision.app.vision.IdentityFusion
import com.s24vision.app.vision.ObjectRecognizer
import com.s24vision.app.vision.YoloeDetector

/** Простой holder зависимостей. Инициализируется один раз в MainActivity. */
object AppDeps {

    private lateinit var appCtx: Context
    private lateinit var storeRef: ProfileStore
    private lateinit var settingsRef: RecognitionSettings
    private lateinit var detectorRef: CompositeDetector
    private lateinit var faceRef: FaceRecognizer
    private lateinit var bodyRef: BodyRecognizer
    private lateinit var objectRef: ObjectRecognizer
    private lateinit var pipelineRef: FramePipeline
    private lateinit var enrollerRef: Enroller
    private lateinit var trainingLogRef: TrainingLog

    private const val FACE_TH = 0.45f
    private const val BODY_TH = 0.65f
    private const val OBJ_TH = 0.50f
    private const val OBJ_RELABEL_TH = 0.76f

    @Volatile
    private var initialized = false

    @Synchronized
    fun init(ctx: Context) {
        if (initialized) return
        appCtx = ctx.applicationContext
        storeRef = ProfileStore(appCtx.filesDir)
        settingsRef = RecognitionSettings(appCtx)
        faceRef = FaceRecognizer(appCtx, settingsRef)
        bodyRef = BodyRecognizer(appCtx, settingsRef)
        objectRef = ObjectRecognizer(appCtx, settingsRef)
        detectorRef = CompositeDetector(
            YoloeDetector(appCtx, settingsRef),
            DroneDetector(appCtx, settingsRef),
            faceRef,
            objectRef,
            storeRef,
            settingsRef,
        )
        val fusion = IdentityFusion(faceTh = FACE_TH, bodyTh = BODY_TH)
        pipelineRef = FramePipeline(
            detector = detectorRef,
            face = faceRef,
            body = bodyRef,
            obj = objectRef,
            store = storeRef,
            settings = settingsRef,
            fusion = fusion,
            faceTh = FACE_TH,
            bodyTh = BODY_TH,
            objTh = OBJ_TH,
            objRelabelTh = OBJ_RELABEL_TH,
        )
        enrollerRef = Enroller(appCtx, detectorRef, faceRef, bodyRef, objectRef, storeRef, settingsRef)
        trainingLogRef = TrainingLog(appCtx)
        initialized = true
    }

    fun store() = storeRef
    fun recognitionSettings() = settingsRef
    fun pipeline() = pipelineRef
    fun enroller() = enrollerRef
    fun trainingLog() = trainingLogRef
    fun appContext(): Context = appCtx

    fun profileExists(name: String, isPerson: Boolean): Boolean =
        if (isPerson) {
            storeRef.exists(ProfileType.FACE, name) || storeRef.exists(ProfileType.BODY, name)
        } else {
            storeRef.exists(ProfileType.OBJECT, name)
        }
}

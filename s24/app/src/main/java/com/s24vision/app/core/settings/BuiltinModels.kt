package com.s24vision.app.core.settings

/** Встроенные ONNX-модели из assets/models. */
object BuiltinModels {

    const val YOLOE = "yoloe"
    const val DRONE_DET = "drone_det"
    const val FACE = "face"
    const val BODY = "body"
    const val OBJECT_ENCODER = "object_encoder"

    data class Info(
        val id: String,
        val assets: String,
        val titleRu: String,
        val descriptionRu: String,
        /** Размер ONNX в APK, МБ. */
        val sizeMb: Float,
        /** Ориентировочный коэффициент качества (mAP / match-rate). */
        val accuracyHint: Float,
    )

    val all: List<Info> = listOf(
        Info(
            id = YOLOE,
            assets = "yoloe26s.onnx",
            titleRu = "YOLOE-26s",
            descriptionRu = "Детекция объектов (RAM++, 4585 классов)",
            sizeMb = 46.7f,
            accuracyHint = 0.44f,
        ),
        Info(
            id = DRONE_DET,
            assets = "drone_det_{n,s,m,l}.onnx",
            titleRu = "Детектор дронов",
            descriptionRu = "YOLO11 n–l, активный размер — в блоке выше (~220 MB все варианты в APK)",
            sizeMb = 220f,
            accuracyHint = 0.72f,
        ),
        Info(
            id = FACE,
            assets = "face_det.onnx + face_rec.onnx",
            titleRu = "Лицо",
            descriptionRu = "SCRFD + ArcFace — распознавание людей по лицу",
            sizeMb = 15.4f,
            accuracyHint = 0.72f,
        ),
        Info(
            id = BODY,
            assets = "body_reid.onnx",
            titleRu = "Тело",
            descriptionRu = "OSNet ReID — распознавание людей по силуэту",
            sizeMb = 0.9f,
            accuracyHint = 0.65f,
        ),
        Info(
            id = OBJECT_ENCODER,
            assets = "object_encoder.onnx",
            titleRu = "Энкодер объектов",
            descriptionRu = "Сопоставление с обученными профилями объектов",
            sizeMb = 3.6f,
            accuracyHint = 0.56f,
        ),
    )

    fun formatSizeMb(mb: Float): String =
        if (mb < 10f) "%.1f MB".format(mb) else "%.0f MB".format(mb)

    fun formatAccuracy(score: Float): String = "%.2f".format(score)
}

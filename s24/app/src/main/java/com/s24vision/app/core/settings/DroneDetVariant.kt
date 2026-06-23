package com.s24vision.app.core.settings

/** Вариант YOLO11 drone_det в assets/models. */
enum class DroneDetVariant(
    val prefKey: String,
    val assetFile: String,
    val titleRu: String,
    val sizeMbHint: Float,
    val sourceHint: String,
) {
    N(
        prefKey = "n",
        assetFile = "drone_det_n.onnx",
        titleRu = "YOLO11n",
        sizeMbHint = 10f,
        sourceHint = "marie-kjelberg/drone-detector",
    ),
    S(
        prefKey = "s",
        assetFile = "drone_det_s.onnx",
        titleRu = "YOLO11s",
        sizeMbHint = 37f,
        sourceHint = "sapoepsilon/yolov11s-drone-detector (эталон)",
    ),
    M(
        prefKey = "m",
        assetFile = "drone_det_m.onnx",
        titleRu = "YOLO11m",
        sizeMbHint = 77f,
        sourceHint = "дообучение Seraphim mini",
    ),
    L(
        prefKey = "l",
        assetFile = "drone_det_l.onnx",
        titleRu = "YOLO11l",
        sizeMbHint = 97f,
        sourceHint = "дообучение Seraphim mini",
    ),
    ;

    companion object {
        fun fromPref(key: String): DroneDetVariant =
            when (key) {
                "x" -> S // x убран — сброс старой настройки
                else -> entries.find { it.prefKey == key } ?: S
            }
    }
}

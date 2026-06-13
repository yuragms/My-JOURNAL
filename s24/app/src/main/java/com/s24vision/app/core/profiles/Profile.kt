package com.s24vision.app.core.profiles

enum class ProfileType(val dir: String) {
    FACE("faces"),
    BODY("bodies"),
    OBJECT("objects"),
}

data class Profile(
    val type: ProfileType,
    val name: String,
    val embeddings: MutableList<FloatArray>,
)

/** Результат обучения/дообучения — основа для сообщений пользователю. */
sealed interface EnrollResult {
    data class Created(val total: Int) : EnrollResult
    data class Improved(val before: Int, val after: Int) : EnrollResult
    data class Empty(val reason: String) : EnrollResult
}

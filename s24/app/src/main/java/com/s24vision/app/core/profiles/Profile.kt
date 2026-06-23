package com.s24vision.app.core.profiles

enum class ProfileType(val dir: String, val displayRu: String) {
    FACE("faces", "Лицо"),
    BODY("bodies", "Тело"),
    OBJECT("objects", "Объект"),
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
    /** Профиль с таким именем уже есть (кнопка «Обучить»). */
    data class AlreadyExists(val name: String) : EnrollResult
    /** Профиля ещё нет (кнопка «Дообучить»). */
    data class NotFound(val name: String) : EnrollResult
}

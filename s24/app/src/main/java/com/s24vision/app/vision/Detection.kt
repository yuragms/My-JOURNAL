package com.s24vision.app.vision

import android.graphics.RectF

data class Detection(
    val rect: RectF,
    val label: String,
    val score: Float,
    val isPerson: Boolean,
)

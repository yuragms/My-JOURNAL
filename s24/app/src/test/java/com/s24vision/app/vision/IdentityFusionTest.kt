package com.s24vision.app.vision

import org.junit.Assert.assertEquals
import org.junit.Test

class IdentityFusionTest {

    private val f = IdentityFusion(faceTh = 0.45f, bodyTh = 0.65f)

    @Test
    fun bothChannelsAgree() {
        // 0.6*0.5 + 0.4*0.7 + 0.2*0.5 = 0.3 + 0.28 + 0.1 = 0.68
        assertEquals(0.68f, f.total(face = 0.5f, body = 0.7f), 1e-3f)
    }

    @Test
    fun weakFaceStrongBody() {
        // один канал: max + 0.25*min = 0.72 + 0.25*0.31 = 0.7975
        assertEquals(0.7975f, f.total(face = 0.31f, body = 0.72f), 1e-3f)
    }
}

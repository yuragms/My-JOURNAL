package com.s24vision.app.core.math

import org.junit.Assert.assertEquals
import org.junit.Test

class EmbeddingsTest {

    @Test
    fun cosineOfIdenticalVectorsIsOne() {
        val a = floatArrayOf(1f, 2f, 2f)
        val n = Embeddings.l2normalize(a)
        assertEquals(1.0f, Embeddings.cosine(n, n), 1e-5f)
    }

    @Test
    fun cosineOfOrthogonalIsZero() {
        val a = Embeddings.l2normalize(floatArrayOf(1f, 0f))
        val b = Embeddings.l2normalize(floatArrayOf(0f, 1f))
        assertEquals(0.0f, Embeddings.cosine(a, b), 1e-5f)
    }

    @Test
    fun bestMatchReturnsMaxCosine() {
        val q = Embeddings.l2normalize(floatArrayOf(1f, 0f))
        val gallery = listOf(
            Embeddings.l2normalize(floatArrayOf(0f, 1f)),
            Embeddings.l2normalize(floatArrayOf(1f, 0.1f)),
        )
        assertEquals(1, Embeddings.bestMatch(q, gallery).first)
    }
}

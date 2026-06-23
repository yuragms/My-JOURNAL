package com.s24vision.app.core.profiles

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProfileStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun enrollNewCreatesProfileAndReportsCreated() {
        val store = ProfileStore(tmp.root)
        val r = store.createEmbeddings(
            ProfileType.OBJECT, "дрон",
            listOf(floatArrayOf(1f, 0f), floatArrayOf(0.9f, 0.1f)),
        )
        assertTrue(r is EnrollResult.Created)
        assertEquals(2, (r as EnrollResult.Created).total)
        assertTrue(store.exists(ProfileType.OBJECT, "дрон"))
    }

    @Test
    fun enrollExistingMergesAndReportsImproved() {
        val store = ProfileStore(tmp.root)
        store.createEmbeddings(ProfileType.OBJECT, "дрон", listOf(floatArrayOf(1f, 0f)))
        val r = store.improveEmbeddings(ProfileType.OBJECT, "дрон", listOf(floatArrayOf(0f, 1f)))
        assertTrue(r is EnrollResult.Improved)
        r as EnrollResult.Improved
        assertEquals(1, r.before)
        assertEquals(2, r.after)
    }

    @Test
    fun loadReturnsPersistedEmbeddings() {
        val store = ProfileStore(tmp.root)
        store.createEmbeddings(ProfileType.FACE, "yura", listOf(floatArrayOf(1f, 2f, 3f)))
        val store2 = ProfileStore(tmp.root)
        val p = store2.load(ProfileType.FACE, "yura")!!
        assertEquals(1, p.embeddings.size)
        assertArrayEquals(floatArrayOf(1f, 2f, 3f), p.embeddings[0], 1e-6f)
    }
}

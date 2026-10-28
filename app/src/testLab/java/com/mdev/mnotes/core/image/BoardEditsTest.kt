package com.mdev.mnotes.core.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** Gumka w panelu kredy po zmianie narożników (2c-B5): pętle w ułamkach wyniku nie przeżywają zmiany geometrii. */
class BoardEditsTest {
    private val loop = floatArrayOf(0.1f, 0.1f, 0.3f, 0.1f, 0.2f, 0.3f)
    private val corners = floatArrayOf(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f)

    @Test
    fun changingCornersClearsTheEraser() {
        val e = BoardEdits(straighten = true, crop = true).withLoop(loop).withLoop(loop)
        assertEquals(2, e.erase.size)
        val moved = e.withGeometry(corners)
        assertEquals(0, moved.erase.size)
        // „Narożniki automatycznie” to też zmiana geometrii
        assertEquals(0, moved.withLoop(loop).withGeometry(null).erase.size)
    }

    @Test
    fun theSameCornersKeepTheEraser() {
        val e = BoardEdits(true, true).withGeometry(corners).withLoop(loop)
        assertSame(e, e.withGeometry(corners.copyOf()))
        assertEquals(1, e.withGeometry(corners.copyOf()).erase.size)
    }

    @Test
    fun turningStraighteningOrCropOffClearsTheEraser() {
        val e = BoardEdits(true, true).withLoop(loop)
        assertEquals(0, e.withGeometry(null, straighten = false).erase.size)
        assertEquals(0, e.withGeometry(null, crop = false).erase.size)
    }

    @Test
    fun cornersAreCopiedSoLaterChangesToTheArrayDoNotLeak() {
        val c = corners.copyOf()
        val e = BoardEdits(true, true).withGeometry(c).withLoop(loop)
        c[0] = 0.2f
        assertEquals(0f, e.corners!![0])
        assertEquals(0, e.withGeometry(c).erase.size)
    }
}

package com.mdev.mnotes.core

import com.mdev.mnotes.core.layout.BottomStack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A2-8: kapsuła trybu wykładu, pasek szukania i dymek nie nachodzą na siebie. */
class BottomStackTest {
    private val capTop = BottomStack.CAPSULE_BOTTOM + BottomStack.CAPSULE_H

    @Test fun nothingOpenKeepsOldPlaces() {
        val b = BottomStack.bottoms(capsule = false, search = false)
        assertEquals(BottomStack.SEARCH_BOTTOM, b[0], 0f)
        assertEquals(BottomStack.TOAST_BOTTOM, b[1], 0f)
    }

    @Test fun capsulePushesSearchAndToastUp() {
        val b = BottomStack.bottoms(capsule = true, search = false)
        assertTrue(b[0] >= capTop)
        assertTrue("dymek nad kapsułą", b[1] >= capTop)
    }

    @Test fun allThreeStackWithoutOverlap() {
        val b = BottomStack.bottoms(capsule = true, search = true)
        assertTrue(b[0] >= capTop)
        assertTrue("dymek nad paskiem szukania", b[1] >= b[0] + BottomStack.SEARCH_H)
        // Sam pasek szukania: dymek też nad nim (wcześniej dymek 28 dp leżał na pasku 14–64 dp).
        val s = BottomStack.bottoms(capsule = false, search = true)
        assertTrue(s[1] >= s[0] + BottomStack.SEARCH_H)
    }
}

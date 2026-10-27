package com.mdev.mnotes.core

import com.mdev.mnotes.core.layout.PageShift
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PageShiftTest {

    private val before = mapOf("a" to floatArrayOf(0f, 0f), "b" to floatArrayOf(0f, 900f), "c" to floatArrayOf(0f, 1800f))

    /** Strona b trzymana w miejscu, c przeskoczyła na górę: jedzie tylko c (o 1800 px). */
    @Test fun onlyMovedNeighboursSlide() {
        val now = mapOf("a" to floatArrayOf(0f, 0f), "b" to floatArrayOf(0f, 900f), "c" to floatArrayOf(0f, 900f - 900f))
        val s = PageShift(before, 300L)
        assertTrue(s.active(0L))
        s.begin(1000L) { now[it] }
        assertNull(s.offset("a", 1000L))
        assertNull(s.offset("b", 1000L))
        val o = s.offset("c", 1000L)!!
        assertEquals(1800f, o[1], 1e-3f)
        assertTrue(s.offset("c", 1150L)!![1] < 1800f * 0.2f)   // ease-out: w połowie czasu już ponad 80 % drogi
        assertNull(s.offset("c", 1300L))
        assertFalse(s.active(1300L))
    }

    @Test fun removedPageIsIgnoredAndZeroDurationIsOff() {
        val s = PageShift(before, 0L)
        s.begin(0L) { if (it == "b") null else floatArrayOf(0f, 5f) }
        assertNull(s.offset("a", 0L))
        assertNull(s.offset("b", 0L))
    }

    @Test fun easeEnds() {
        assertEquals(0f, PageShift.ease(0f), 0f)
        assertEquals(1f, PageShift.ease(1f), 0f)
    }
}

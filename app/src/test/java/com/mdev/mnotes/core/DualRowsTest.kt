package com.mdev.mnotes.core

import com.mdev.mnotes.core.layout.DualRows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** F4: dwa zeszyty w jednym obszarze — rzędy, rozdzielacz, wspólne powiększenie. */
class DualRowsTest {

    @Test fun heightsSplitFreeSpace() {
        val h = DualRows.heights(1000f, 0.5f, 20f)
        assertEquals(490f, h[0], 1e-3f); assertEquals(490f, h[1], 1e-3f)
        val skew = DualRows.heights(1000f, 0.95f, 20f)
        assertEquals(980f * DualRows.MAX_FRACTION, skew[0], 1e-3f)
        assertEquals(DualRows.DEFAULT_FRACTION, DualRows.clamp(Float.NaN), 0f)
    }

    @Test fun dividerDragMovesSplitAndStopsAtLimits() {
        assertEquals(0.6f, DualRows.dragged(0.5f, 98f, 1000f, 20f), 1e-4f)
        assertEquals(DualRows.MIN_FRACTION, DualRows.dragged(0.5f, -5000f, 1000f, 20f), 0f)
        assertEquals(DualRows.MAX_FRACTION, DualRows.dragged(0.5f, 5000f, 1000f, 20f), 0f)
    }

    @Test fun sharedZoomFollowsWithinLimitsWithoutLoops() {
        assertEquals(2.4f, DualRows.follow(2.4f, 1.2f, 0.5f, 10f)!!, 0f)
        assertEquals(10f, DualRows.follow(30f, 1.2f, 0.5f, 10f)!!, 0f)
        // Ta sama skala — nic do zrobienia (drugi widok nie odpowiada echem).
        assertNull(DualRows.follow(1.2f, 1.2f, 0.5f, 10f))
    }

    @Test fun candidatesSkipCurrentAndSortByRecent() {
        data class Nb(val id: String, val opened: Long)
        val list = listOf(Nb("a", 1), Nb("b", 5), Nb("c", 3))
        assertEquals(listOf("b", "c"), DualRows.candidates(list, { it.id }, { it.opened }, setOf("a")).map { it.id })
    }
}

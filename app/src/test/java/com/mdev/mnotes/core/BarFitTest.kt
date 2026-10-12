package com.mdev.mnotes.core

import com.mdev.mnotes.core.layout.BarEntry
import com.mdev.mnotes.core.layout.BarFit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BarFitTest {
    // Trzy grupy jak na pasku: 2 + 3 + 4, ważność maleje z indeksem.
    private val items = listOf(0, 0, 1, 1, 1, 2, 2, 2, 2).mapIndexed { i, g -> BarEntry(g, i) }
    private val btn = 40f

    @Test fun everythingFitsWhenThereIsRoom() {
        val full = BarFit.length(items.size, 3, btn, false)
        val r = BarFit.fit(items, full, btn)
        assertTrue(r.hidden.isEmpty()); assertFalse(r.tight)
    }

    @Test fun tightLayoutIsTriedBeforeHiding() {
        val full = BarFit.length(items.size, 3, btn, false)
        val r = BarFit.fit(items, full - 1f, btn)
        assertTrue(r.hidden.isEmpty()); assertTrue(r.tight)
    }

    @Test fun leastImportantItemsGoUnderMore() {
        val tight = BarFit.length(items.size, 3, btn, true)
        val r = BarFit.fit(items, tight - 1f, btn)
        assertFalse(r.tight)
        // Chowane od końca listy ważności, a to, co zostaje, mieści się razem z przyciskiem „⋯”.
        assertEquals((items.size - r.hidden.size until items.size).toSet(), r.hidden)
        val vis = items.indices - r.hidden
        assertTrue(BarFit.length(vis.size + 1, vis.map { items[it].group }.distinct().size, btn, false) <= tight - 1f)
        assertTrue(r.hidden.size >= 2)
    }

    @Test fun pinnedItemStaysVisible() {
        val r = BarFit.fit(items, 200f, btn, pinned = 8)
        assertFalse(8 in r.hidden)
        assertTrue(7 in r.hidden)
        assertEquals(BarFit.fit(items, 200f, btn).hidden.size, r.hidden.size)
    }

    @Test fun unboundedSpaceHidesNothing() {
        assertTrue(BarFit.fit(items, Float.POSITIVE_INFINITY, btn).hidden.isEmpty())
    }

    @Test fun rankDecidesNotPosition() {
        val mixed = listOf(BarEntry(0, 5), BarEntry(0, 0), BarEntry(0, 3), BarEntry(0, 1))
        val r = BarFit.fit(mixed, BarFit.length(3, 1, btn, false), btn)
        assertEquals(setOf(0, 2), r.hidden)
    }
}

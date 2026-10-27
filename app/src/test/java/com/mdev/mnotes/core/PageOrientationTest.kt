package com.mdev.mnotes.core

import com.mdev.mnotes.core.layout.DocLayout
import com.mdev.mnotes.core.layout.LayoutMode
import com.mdev.mnotes.core.model.PageFormat
import com.mdev.mnotes.core.model.PageSpec
import com.mdev.mnotes.core.model.Template
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** F5: strony pionowe i poziome oraz różne formaty w jednym zeszycie. */
class PageOrientationTest {

    @Test fun formatIsRecognizedInBothOrientations() {
        assertEquals(PageFormat.A4, PageSpec.of(PageFormat.A4).format)
        assertEquals(PageFormat.A4, PageSpec.of(PageFormat.A4, landscape = true).format)
        assertEquals(PageFormat.A5, PageSpec(420.4f, 595f).format)
        assertNull(PageSpec(500f, 700f).format)
    }

    @Test fun orientationSwapsSidesKeepingStyle() {
        val a5 = PageSpec.of(PageFormat.A5, template = Template.GRID)
        val land = a5.oriented(true)
        assertTrue(land.landscape)
        assertEquals(595f, land.w, 0f); assertEquals(420f, land.h, 0f)
        assertEquals(Template.GRID, land.template)
        assertEquals(land, land.oriented(true))
        assertFalse(land.oriented(false).landscape)
        val sq = PageSpec.of(PageFormat.SQUARE)
        assertEquals(sq, sq.oriented(true))
    }

    @Test fun formatKeepsEachPageOrientation() {
        val land = PageSpec.of(PageFormat.A5, landscape = true)
        val a4 = land.withFormat(PageFormat.A4)
        assertTrue(a4.landscape)
        assertEquals(842f, a4.w, 0f)
        assertFalse(PageSpec.of(PageFormat.A5).withFormat(PageFormat.A4).landscape)
    }

    @Test fun mixedPagesLayOutCentered() {
        // Rysunek z zeszytu: A5 pion, A4 poziom, A5 pion w jednym ciągu.
        val specs = listOf(PageSpec.of(PageFormat.A5), PageSpec.of(PageFormat.A4, landscape = true), PageSpec.of(PageFormat.A5))
        val l = DocLayout.of(specs, LayoutMode.HORIZONTAL, gap = 10f, pad = 0f)
        assertEquals(0f, l.left(0), 0f)
        assertEquals(430f, l.left(1), 0f)
        assertEquals((595f - 595f) / 2f, l.top(0), 0f)
        assertEquals((595f - 595f) / 2f + (595f - 595f), l.top(1), 0f)
        assertEquals(1, l.pageAt(600f, 300f))
        val v = DocLayout.of(specs, LayoutMode.VERTICAL, gap = 10f, pad = 0f)
        assertEquals((842f - 420f) / 2f, v.left(0), 0f)
        assertEquals(0f, v.left(1), 0f)
    }
}

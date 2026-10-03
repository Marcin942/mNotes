package com.mdev.mnotes.core

import com.mdev.mnotes.core.geom.Bounds
import com.mdev.mnotes.core.layout.DocLayout
import com.mdev.mnotes.core.layout.LayoutMode
import com.mdev.mnotes.core.model.PageFormat
import com.mdev.mnotes.core.model.PageSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DocLayoutTest {

    private val a4 = PageSpec.of(PageFormat.A4)
    private val a5l = PageSpec.of(PageFormat.A5, landscape = true)

    @Test
    fun verticalStackCentersNarrowerPages() {
        val l = DocLayout.of(listOf(a4, a5l, a4), LayoutMode.VERTICAL, gap = 10f, pad = 20f)
        assertEquals(20f, l.top(0), 0f)
        assertEquals(20f + 842f + 10f, l.top(1), 0f)
        assertEquals(l.top(1) + 420f + 10f, l.top(2), 0f)
        assertEquals(20f, l.left(0), 0f)
        assertEquals(20f + (595f - 595f) / 2f, l.left(1), 0f)
        assertEquals(595f + 40f, l.docWidth, 0f)
        assertEquals(l.top(2) + 842f + 20f, l.docHeight, 0f)
    }

    @Test
    fun pageAtAndNearest() {
        val l = DocLayout.of(listOf(a4, a4, a4), LayoutMode.VERTICAL, gap = 20f, pad = 0f)
        assertEquals(0, l.pageAt(100f, 100f))
        assertEquals(-1, l.pageAt(100f, 842f + 10f))
        assertEquals(1, l.pageAt(100f, 862f + 5f))
        assertEquals(0, l.nearest(100f, 842f + 4f))
        assertEquals(1, l.nearest(100f, 842f + 18f))
        assertEquals(2, l.nearest(100f, 99999f))
        assertEquals(0, l.nearest(100f, -500f))
    }

    @Test
    fun visibleRange() {
        val l = DocLayout.of(List(10) { a4 }, LayoutMode.VERTICAL, gap = 20f, pad = 0f)
        assertEquals(0..0, l.visibleRange(Bounds(0f, 100f, 500f, 700f)))
        assertEquals(0..1, l.visibleRange(Bounds(0f, 700f, 500f, 900f)))
        assertEquals(3..5, l.visibleRange(Bounds(0f, 3 * 862f + 10f, 500f, 5 * 862f + 10f)))
        assertTrue(l.visibleRange(Bounds(0f, -100f, 500f, -10f)).isEmpty())
        assertTrue(l.visibleRange(Bounds(0f, 1e6f, 500f, 2e6f)).isEmpty())
        // Przerwa między stronami nie należy do żadnej z nich.
        assertTrue(l.visibleRange(Bounds(0f, 850f, 500f, 855f)).isEmpty())
    }

    @Test
    fun horizontalLayout() {
        val l = DocLayout.of(listOf(a4, a5l), LayoutMode.HORIZONTAL, gap = 10f, pad = 0f)
        assertEquals(0f, l.left(0), 0f)
        assertEquals(605f, l.left(1), 0f)
        assertEquals((842f - 420f) / 2f, l.top(1), 0f)
        assertEquals(1, l.pageAt(700f, 400f))
        assertEquals(605f + 595f, l.docWidth, 0f)
        assertEquals(842f, l.docHeight, 0f)
    }

    @Test
    fun emptyLayout() {
        val l = DocLayout.of(emptyList(), LayoutMode.VERTICAL)
        assertEquals(-1, l.nearest(0f, 0f))
        assertTrue(l.visibleRange(Bounds(0f, 0f, 10f, 10f)).isEmpty())
    }
}

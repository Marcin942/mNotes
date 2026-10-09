package com.mdev.mnotes.core

import com.mdev.mnotes.core.geom.Affine
import com.mdev.mnotes.core.geom.Bounds
import com.mdev.mnotes.core.layout.DocLayout
import com.mdev.mnotes.core.layout.LayoutMode
import com.mdev.mnotes.core.layout.PageDrop
import com.mdev.mnotes.core.model.PageFormat
import com.mdev.mnotes.core.model.PageSpec
import org.junit.Assert.assertEquals
import org.junit.Test

class PageDropTest {

    private val a4 = PageSpec.of(PageFormat.A4)
    private val a5l = PageSpec.of(PageFormat.A5, landscape = true)
    private val vertical = DocLayout.of(listOf(a4, a5l, a4), LayoutMode.VERTICAL, gap = 10f, pad = 20f)
    private val horizontal = DocLayout.of(listOf(a4, a4), LayoutMode.HORIZONTAL, gap = 10f, pad = 20f)

    @Test
    fun staysOnSourceWhenCenterIsInsideIt() {
        assertEquals(0, PageDrop.target(vertical, 0, Bounds(100f, 100f, 200f, 200f)))
    }

    @Test
    fun movesToNextPageWhenCenterCrossesIt() {
        // środek 100 pt pod dolną krawędzią strony 0 (wysokość 842 + przerwa 10) leży już na stronie 1
        val moved = Bounds(100f, 900f, 200f, 960f)
        assertEquals(1, PageDrop.target(vertical, 0, moved))
    }

    @Test
    fun movesToPreviousPage() {
        val moved = Bounds(100f, -400f, 200f, -340f)
        assertEquals(0, PageDrop.target(vertical, 1, moved))
    }

    @Test
    fun skipsOverIntermediatePages() {
        val moved = Bounds(100f, 842f + 420f + 100f, 200f, 842f + 420f + 160f)
        assertEquals(2, PageDrop.target(vertical, 0, moved))
    }

    @Test
    fun centerInGapKeepsSourcePage() {
        // przerwa między stronami 0 i 1: y strony 0 od 842 do 852
        val moved = Bounds(100f, 842f + 2f, 200f, 842f + 8f)
        assertEquals(0, PageDrop.target(vertical, 0, moved))
    }

    @Test
    fun centerOutsideAnyPageKeepsSourcePage() {
        assertEquals(1, PageDrop.target(vertical, 1, Bounds(-900f, 0f, -800f, 40f)))
    }

    @Test
    fun horizontalLayoutUsesTheXAxis() {
        assertEquals(1, PageDrop.target(horizontal, 0, Bounds(600f, 100f, 660f, 160f)))
        assertEquals(0, PageDrop.target(horizontal, 0, Bounds(300f, 100f, 360f, 160f)))
    }

    @Test
    fun emptyBoundsOrBadIndexStay() {
        assertEquals(0, PageDrop.target(vertical, 0, Bounds.EMPTY))
        assertEquals(7, PageDrop.target(vertical, 7, Bounds(0f, 0f, 10f, 10f)))
    }

    @Test
    fun transformIsExpressedInTargetCoordinates() {
        // punkt (100, 900) strony 0 po przesunięciu o (0, 0) leży w dokumencie na y = 20 + 900 = 920,
        // a na stronie 1 (top = 20 + 842 + 10 = 872) na y = 48
        val m = PageDrop.inTarget(vertical, 0, 1, Affine.IDENTITY)
        assertEquals(48f, m.mapY(100f, 900f), 1e-3f)
        assertEquals(100f + (vertical.left(0) - vertical.left(1)), m.mapX(100f, 900f), 1e-3f)
    }

    @Test
    fun sameTargetReturnsTransformUnchanged() {
        val m = Affine.translation(5f, 6f)
        assertEquals(m, PageDrop.inTarget(vertical, 1, 1, m))
    }

    @Test
    fun transformKeepsTheMovePart() {
        val m = PageDrop.inTarget(vertical, 0, 2, Affine.translation(30f, 1000f))
        val docY = vertical.top(0) + 700f + 1000f
        assertEquals(docY - vertical.top(2), m.mapY(0f, 700f), 1e-3f)
        assertEquals(vertical.left(0) + 10f + 30f - vertical.left(2), m.mapX(10f, 700f), 1e-3f)
    }
}

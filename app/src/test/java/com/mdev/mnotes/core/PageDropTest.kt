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

    /** 3c-A2: przeniesione na inną stronę wystawało poza nią i strona je przycinała — „obiekt pod stroną”. */
    @Test
    fun movedToAnotherPageLandsWhollyOnIt() {
        // Ramka 200×120 z dołu strony 0, przesunięta tak, że środek jest już na stronie 1, a góra jeszcze w przerwie/na 0.
        val b = Bounds(100f, 700f, 300f, 820f)
        // Środek (y = 760) trafia 50 pt pod górny brzeg strony 1, góra ramki 10 pt nad nim.
        val dy = vertical.top(1) - vertical.top(0) - 710f
        val m = Affine.translation(0f, dy)
        assertEquals(1, PageDrop.target(vertical, 0, m.mapBounds(b)))
        val plain = PageDrop.inTarget(vertical, 0, 1, m).mapBounds(b)
        assertEquals(true, plain.t < 0f)
        val r = PageDrop.intoTarget(vertical, 0, 1, m, b).mapBounds(b)
        assertEquals(0f, r.t, 1e-3f)
        assertEquals(120f, r.h, 1e-3f)
        assertEquals(plain.l, r.l, 1e-3f)
        // Wystające za prawy brzeg dosuwa do niego; większe od strony przylega do lewego brzegu.
        val wide = PageDrop.intoTarget(vertical, 0, 1, Affine.translation(vertical.width(1) - 50f, dy + 100f), b).mapBounds(b)
        assertEquals(vertical.width(1), wide.r, 1e-3f)
        val huge = Bounds(0f, 0f, 2000f, 50f)
        assertEquals(0f, PageDrop.intoTarget(vertical, 0, 1, Affine.translation(-300f, dy + 100f), huge).mapBounds(huge).l, 1e-3f)
    }

    @Test
    fun withinThePageNothingIsPushed() {
        // Na tej samej stronie wolno wysunąć obiekt za brzeg (jak dotąd).
        val b = Bounds(500f, 100f, 700f, 200f)
        val m = Affine.translation(50f, 0f)
        assertEquals(m.mapBounds(b).r, PageDrop.intoTarget(vertical, 0, 0, m, b).mapBounds(b).r, 1e-3f)
    }
}

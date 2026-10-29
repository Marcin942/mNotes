package com.mdev.mnotes.core

import com.mdev.mnotes.core.edit.SnipCut
import com.mdev.mnotes.core.geom.Bounds
import com.mdev.mnotes.core.layout.PageDrop
import com.mdev.mnotes.core.model.DocEdit
import com.mdev.mnotes.core.model.ElementsEdit
import com.mdev.mnotes.core.model.ImageEl
import com.mdev.mnotes.core.model.TextBox
import com.mdev.mnotes.core.model.UndoStack
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 3c-B4: narzędzie „Wycinek” — maska lassa/prostokąta, plan renderu, przeliczenie na piksele, dopasowanie do strony. */
class SnipCutTest {
    private fun a(m: ByteArray, w: Int, x: Int, y: Int) = m[y * w + x].toInt() and 255

    @Test
    fun rectangleFromAnyTwoCorners() {
        assertArrayEquals(floatArrayOf(10f, 20f, 50f, 20f, 50f, 60f, 10f, 60f), SnipCut.rect(50f, 60f, 10f, 20f), 0f)
        assertArrayEquals(SnipCut.rect(10f, 60f, 50f, 20f), SnipCut.rect(50f, 20f, 10f, 60f), 0f)
    }

    @Test
    fun planUsesTwiceTheZoomWithinLimitsAndStaysOnThePage() {
        val poly = SnipCut.rect(100f, 100f, 300f, 200f)
        assertEquals(3f, SnipCut.plan(poly, 595f, 842f, zoom = 1f)!!.scale)       // co najmniej 3 px/pt
        assertEquals(5f, SnipCut.plan(poly, 595f, 842f, zoom = 2.5f)!!.scale)     // 2× powiększenia
        assertEquals(6f, SnipCut.plan(poly, 595f, 842f, zoom = 10f)!!.scale)      // najwyżej 6
        val p = SnipCut.plan(poly, 595f, 842f, 1f)!!
        assertEquals(600, p.wPx); assertEquals(300, p.hPx)
        // wystające poza kartkę przycięte do strony
        val edge = SnipCut.plan(SnipCut.rect(-50f, -50f, 100f, 100f), 595f, 842f, 1f)!!
        assertEquals(0f, edge.rect.l); assertEquals(100f, edge.rect.w)
        // cała duża tablica: dłuższy bok najwyżej MAX_PX
        val big = SnipCut.plan(SnipCut.rect(0f, 0f, 3000f, 2000f), 4000f, 4000f, 3f)!!
        assertTrue(big.wPx <= SnipCut.MAX_PX && big.hPx <= SnipCut.MAX_PX)
    }

    @Test
    fun tinyOrOffPageCutIsNoCut() {
        assertNull(SnipCut.plan(SnipCut.rect(10f, 10f, 13f, 80f), 595f, 842f, 1f))
        assertNull(SnipCut.plan(SnipCut.rect(700f, 10f, 800f, 80f), 595f, 842f, 1f))
        assertNull(SnipCut.plan(floatArrayOf(1f, 1f, 50f, 50f), 595f, 842f, 1f))
    }

    @Test
    fun pixelsFollowThePlan() {
        val poly = floatArrayOf(110f, 120f, 150f, 120f, 130f, 160f)
        val plan = SnipCut.plan(poly, 595f, 842f, 1f)!!
        assertArrayEquals(floatArrayOf(0f, 0f, 120f, 0f, 60f, 120f), SnipCut.toPixels(poly, plan), 1e-3f)
    }

    @Test
    fun lassoMaskIsTransparentOutsideAndSmoothOnTheEdge() {
        // trójkąt 0,0 – 40,0 – 0,40 w bitmapie 40×40
        val m = SnipCut.coverage(floatArrayOf(0f, 0f, 40f, 0f, 0f, 40f), 40, 40)
        assertEquals(255, a(m, 40, 2, 2))      // w środku
        assertEquals(0, a(m, 40, 38, 38))      // poza
        val edge = a(m, 40, 20, 19)            // na przekątnej
        assertTrue("brzeg wygładzony: $edge", edge in 40..215)
        // pole zgadza się z polem trójkąta (800 px²)
        val sum = m.sumOf { (it.toInt() and 255) / 255.0 }
        assertEquals(800.0, sum, 8.0)
    }

    @Test
    fun rectangleMaskIsFullInsideAndCrossedLoopUsesEvenOdd() {
        val m = SnipCut.coverage(floatArrayOf(0f, 0f, 10f, 0f, 10f, 10f, 0f, 10f), 10, 10)
        assertTrue(m.all { (it.toInt() and 255) == 255 })
        // ósemka (pętla przecina się w środku): oba trójkąty — lewy i prawy — w masce, górny i dolny klin poza nią
        val eight = SnipCut.coverage(floatArrayOf(0f, 0f, 20f, 20f, 20f, 0f, 0f, 20f), 20, 20)
        assertEquals(255, a(eight, 20, 2, 10))
        assertEquals(255, a(eight, 20, 17, 10))
        assertEquals(0, a(eight, 20, 10, 2))
        assertEquals(0, a(eight, 20, 10, 17))
    }

    @Test
    fun insertedSizeKeepsScaleUnlessTooBig() {
        assertArrayEquals(floatArrayOf(200f, 100f), SnipCut.fit(200f, 100f, 595f, 842f), 1e-3f)
        val big = SnipCut.fit(1000f, 500f, 595f, 842f)
        assertEquals(595f * 0.9f, big[0], 0.01f)
        assertEquals(2f, big[0] / big[1], 1e-3f)
        // slajd poziomy (tablica 960×540) do zeszytu A4 pionowo
        val slide = SnipCut.fit(960f, 540f, 595f, 842f)
        assertTrue(slide[0] <= 595f * 0.9f + 0.01f && slide[1] <= 842f * 0.9f)
    }

    @Test
    fun captionNamesTheSource() {
        assertEquals("slajd 12 · wykład.pdf", SnipCut.caption(11, "wykład.pdf", pdf = true))
        assertEquals("str. 3 · Fizyka", SnipCut.caption(2, " Fizyka ", pdf = false))
        assertEquals("slide 1", SnipCut.caption(0, "", pdf = true, polish = false))
    }

    @Test
    fun slideSnipDroppedNearTheEdgeOfAnotherNotebookLandsWholeOnThePage() {
        // wycinek pół slajdu 960×540 (480×270 pt) upuszczony przy prawym dolnym rogu strony A4 drugiego dokumentu
        val plan = SnipCut.plan(SnipCut.rect(0f, 0f, 480f, 270f), 960f, 540f, zoom = 0.8f)!!
        assertEquals(3f, plan.scale)                                   // 1440×810 px — ostro także po powiększeniu
        val wh = SnipCut.fit(plan.rect.w, plan.rect.h, 595f, 842f)
        assertArrayEquals(floatArrayOf(480f, 270f), wh, 1e-3f)          // mieści się — skala 1:1 z kartką źródła
        var id = 100
        val els = SnipCut.elements("a.png", wh[0], wh[1], 580f, 830f, "slajd 3 · wykład.pdf") { id++ }
        assertEquals(2, els.size)
        val img = els[0] as ImageEl; val cap = els[1] as TextBox
        assertEquals("a.png", img.asset)
        assertTrue("podpis pod obrazem", cap.bounds.t >= img.bounds.b)
        assertEquals(SnipCut.CAPTION_PT, cap.size)
        var b = Bounds.EMPTY
        for (e in els) b = b.union(e.bounds)
        val (dx, dy) = PageDrop.pushInside(b, 595f, 842f)
        val moved = Bounds(b.l + dx, b.t + dy, b.r + dx, b.b + dy)
        assertTrue("całość na stronie: $moved", moved.l >= 0f && moved.t >= 0f && moved.r <= 595f + 1e-3f && moved.b <= 842f + 1e-3f)
        // bez podpisu — sam obraz
        assertEquals(1, SnipCut.elements("a.png", 10f, 10f, 5f, 5f, null) { id++ }.size)
    }

    @Test
    fun insertedSnipWithCaptionIsOneUndoStep() {
        val host = FakeHost().also { it.addPage("p1") }
        val stack = UndoStack()
        var id = 1
        val els = SnipCut.elements("a.png", 200f, 100f, 300f, 300f, "str. 2 · Fizyka") { id++ }
        val e = DocEdit.Elements("p1", ElementsEdit.add(host.contents.getValue("p1"), els))
        e.apply(host); stack.push(e)
        assertEquals(2, host.contents.getValue("p1").size)
        stack.undo(host)
        assertEquals(0, host.contents.getValue("p1").size)
        stack.redo(host)
        assertEquals(2, host.contents.getValue("p1").size)
    }
}

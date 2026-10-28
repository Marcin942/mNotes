package com.mdev.mnotes.core

import com.mdev.mnotes.core.layout.DocLayout
import com.mdev.mnotes.core.layout.LayoutMode
import com.mdev.mnotes.core.layout.Workspace
import com.mdev.mnotes.core.model.PageSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

/** 2d-A1: stanowisko jako jeden dokument — geometria pasów, mapowania, ruch, zoom, rzut. */
class WorkspaceTest {
    private val viewW = 1600f
    private val viewH = 2400f

    private fun lane(pages: Int, horizontal: Boolean) =
        DocLayout.of(List(pages) { PageSpec() }, if (horizontal) LayoutMode.HORIZONTAL else LayoutMode.VERTICAL)

    private fun ws(horizontal: Boolean = true, lanes: Int = 2, pages: Int = 4) =
        Workspace(horizontal, List(lanes) { lane(pages, horizontal) }, viewW, viewH)

    /** Stan w środku zakresów (bez dociągania do krawędzi) przy powiększeniu ×[k] „100 %”. */
    private fun Workspace.middle(k: Float = 1.5f): Workspace {
        val z = baseZoom() * k
        set(Workspace.Pose(z, 0f, FloatArray(size)))
        val c = crossRange(); val m = FloatArray(size) { mainRange(it).let { r -> (r[0] + r[1]) / 2f + it * 37f } }
        set(Workspace.Pose(z, (c[0] + c[1]) / 2f, m))
        return this
    }

    @Test fun laneGapEqualsPageGapAtEveryZoom() {
        for (horizontal in listOf(true, false)) for (k in listOf(0.4f, 1f, 3f)) {
            val w = ws(horizontal).middle(k)
            val z = w.zoom
            val a = w.lanes[0]; val b = w.lanes[1]
            if (horizontal) {
                val pageGap = w.toScreenX(0, a.left(1)) - w.toScreenX(0, a.left(0) + a.width(0))
                val laneGap = w.toScreenY(1, b.top(0)) - w.toScreenY(0, a.top(0) + a.height(0))
                assertEquals(DocLayout.DEFAULT_GAP * z, pageGap, 1e-2f)
                assertEquals("k=$k", pageGap, laneGap, 1e-2f)
                // Przy krawędziach margines jak w dokumencie.
                assertEquals(DocLayout.DEFAULT_PAD * z, w.toScreenY(0, a.top(0)) - w.cross, 1e-2f)
                assertEquals(DocLayout.DEFAULT_PAD * z, w.total * z + w.cross - w.toScreenY(1, b.top(0) + b.height(0)), 1e-2f)
            } else {
                val pageGap = w.toScreenY(0, a.top(1)) - w.toScreenY(0, a.top(0) + a.height(0))
                val laneGap = w.toScreenX(1, b.left(0)) - w.toScreenX(0, a.left(0) + a.width(0))
                assertEquals(DocLayout.DEFAULT_GAP * z, pageGap, 1e-2f)
                assertEquals("k=$k", pageGap, laneGap, 1e-2f)
            }
        }
    }

    @Test fun screenToPageAndBack() {
        for (horizontal in listOf(true, false)) {
            val w = ws(horizontal, lanes = 3).middle(0.8f)
            val rnd = Random(7)
            repeat(200) {
                val sx = rnd.nextFloat() * viewW; val sy = rnd.nextFloat() * viewH
                val h = w.locate(sx, sy)!!
                val l = w.lanes[h.lane]
                assertEquals(sx, w.toScreenX(h.lane, l.left(h.page) + h.x), 1e-2f)
                assertEquals(sy, w.toScreenY(h.lane, l.top(h.page) + h.y), 1e-2f)
                assertEquals(h.lane, w.laneAt(sx, sy))
            }
        }
        // Granica pasów w połowie odstępu stron.
        val w = ws().middle(1f)
        val a = w.lanes[0]
        val gapMid = w.toScreenY(0, a.top(0) + a.height(0)) + w.gap * w.zoom / 2f
        assertEquals(0, w.laneAt(100f, gapMid - 1f)); assertEquals(1, w.laneAt(100f, gapMid + 1f))
        val r0 = w.laneRect(0); val r1 = w.laneRect(1)
        if (r0 != null && r1 != null) assertEquals(r0[3], r1[1], 1e-3f)
    }

    @Test fun pinchKeepsPointStillInAllLanes() {
        for (horizontal in listOf(true, false)) {
            // Przy połowie „100 %” oba pasy są na ekranie naraz.
            val w = ws(horizontal).middle(0.5f)
            val px = 600f; val py = 1150f
            val i = w.laneAt(px, py)
            val dx = w.toDocX(i, px); val dy = w.toDocY(i, py)
            // Punkt w drugim pasie: skaluje się względem tego samego punktu (jak w jednym dokumencie).
            val qx = 300f; val qy = if (horizontal) 1900f else 700f
            val qx2 = if (horizontal) qx else 1300f
            val j = w.laneAt(qx2, qy)
            assertTrue(i != j)
            val ex = w.toDocX(j, qx2); val ey = w.toDocY(j, qy)
            val k = 1.3f
            w.zoomAbout(px, py, k)
            assertEquals(px, w.toScreenX(i, dx), 0.5f); assertEquals(py, w.toScreenY(i, dy), 0.5f)
            assertEquals(px + (qx2 - px) * k, w.toScreenX(j, ex), 0.5f)
            assertEquals(py + (qy - py) * k, w.toScreenY(j, ey), 0.5f)
        }
    }

    @Test fun pinchSequenceHasNoOscillation() {
        val w = ws().middle(1.2f)
        val rnd = Random(3)
        var z = w.zoom
        repeat(40) { step ->
            val f = 1.015f
            val px = 600f + rnd.nextFloat() * 400f; val py = 1000f + rnd.nextFloat() * 400f
            val i = w.laneAt(px, py)
            val dx = w.toDocX(i, px); val dy = w.toDocY(i, py)
            w.zoomAbout(px, py, f)
            assertTrue("krok $step: zoom nie rośnie", w.zoom > z)
            assertEquals(z * f, w.zoom, z * 1e-5f)
            assertEquals(px, w.toScreenX(i, dx), 0.5f); assertEquals(py, w.toScreenY(i, dy), 0.5f)
            z = w.zoom
        }
        // I z powrotem tą samą drogą: wraca do stanu startowego bez narastania błędu.
        val start = ws().middle(1.2f)
        val back = ws().middle(1.2f)
        repeat(30) { back.zoomAbout(800f, 1200f, 1.02f) }
        repeat(30) { back.zoomAbout(800f, 1200f, 1f / 1.02f) }
        assertEquals(start.zoom, back.zoom, start.zoom * 1e-4f)
        assertEquals(start.cross, back.cross, 0.5f)
        for (l in 0 until start.size) assertEquals(start.main[l], back.main[l], 0.5f)
        // Granice zoomu jak w dokumencie.
        repeat(200) { back.zoomAbout(800f, 1200f, 1.2f) }
        assertEquals(back.maxZoom(), back.zoom, 1e-4f)
        repeat(200) { back.zoomAbout(800f, 1200f, 0.8f) }
        assertEquals(back.minZoom(), back.zoom, 1e-5f)
    }

    @Test fun panAlongPagesMovesOneLaneAcrossMovesAll() {
        for (horizontal in listOf(true, false)) {
            val w = ws(horizontal, lanes = 3).middle(1.5f)
            fun snapshot() = Triple(w.cross, w.main.copyOf(), FloatArray(3) { if (horizontal) w.ty(it) else w.tx(it) })
            val (c0, m0, _) = snapshot()
            // Ruch w osi stron: tylko pas pod palcem.
            if (horizontal) w.pan(2, -100f, 0f) else w.pan(2, 0f, -100f)
            assertEquals(c0, w.cross, 0f)
            assertEquals(m0[0], w.main[0], 0f); assertEquals(m0[1], w.main[1], 0f); assertEquals(m0[2] - 100f, w.main[2], 1e-3f)
            // Ruch w osi wspólnej: wszystkie pasy razem.
            val (c1, m1, t1) = snapshot()
            if (horizontal) w.pan(0, 0f, -80f) else w.pan(0, -80f, 0f)
            assertEquals(c1 - 80f, w.cross, 1e-3f)
            for (l in 0 until 3) { assertEquals(m1[l], w.main[l], 0f); assertEquals(t1[l] - 80f, if (horizontal) w.ty(l) else w.tx(l), 1e-3f) }
            // Po skosie: obie składowe naraz, w jednym wywołaniu.
            val (c2, m2, _) = snapshot()
            w.pan(1, -50f, -30f)
            if (horizontal) { assertEquals(m2[1] - 50f, w.main[1], 1e-3f); assertEquals(c2 - 30f, w.cross, 1e-3f) }
            else { assertEquals(m2[1] - 30f, w.main[1], 1e-3f); assertEquals(c2 - 50f, w.cross, 1e-3f) }
            assertEquals(m2[0], w.main[0], 0f); assertEquals(m2[2], w.main[2], 0f)
        }
    }

    @Test fun panReportsWhatFitsBeforeTheEnd() {
        val w = ws().middle(1.5f)
        val r = w.mainRange(0)
        w.set(Workspace.Pose(w.zoom, w.cross, floatArrayOf(r[0] + 30f, w.main[1])))
        val applied = w.pan(0, -100f, 0f)
        assertEquals(-30f, applied, 1e-3f)     // reszta (70 px) to odciągnięcie „nowa strona”
        assertEquals(r[0], w.main[0], 1e-3f)
    }

    @Test fun flingMovesOnlyTheLaneUnderFingerAndStopsAtBounds() {
        val w = ws().middle(1.5f)
        val m1 = w.main[1]; val c0 = w.cross; val m0 = w.main[0]
        val f = w.fling(0, -2500f, -1500f, density = 2f)
        var t = 0f
        var lastM = w.main[0]; var lastC = w.cross
        while (!(f.first.done(t) && f.second.done(t))) {
            t += 16f
            w.flingFrame(0, f, t)
            assertTrue(w.main[0] <= lastM + 1e-3f && w.cross <= lastC + 1e-3f)
            assertEquals(m1, w.main[1], 0f)
            lastM = w.main[0]; lastC = w.cross
        }
        assertEquals(m0 + f.first.distance, w.main[0], 1f)
        assertEquals(c0 + f.second.distance, w.cross, 1f)
        // Rzut w stronę bliskiej granicy wspólnej: staje na granicy, oś stron leci dalej.
        val c = w.crossRange()
        w.set(Workspace.Pose(w.zoom, c[0] + 40f, w.main))
        val g = w.fling(0, -2500f, -4000f, density = 2f)
        var u = 0f
        while (!g.second.done(u)) { u += 16f; w.flingFrame(0, g, u) }
        assertEquals(c[0], w.cross, 1e-3f)
        assertTrue("oś stron leci dalej", !g.first.done(u))
    }

    @Test fun movedElementStaysInPlaceOnScreen() {
        val w = ws().middle(1.2f)
        val a = w.lanes[0]
        // Element na stronie 1 pasa 0 w (100, 600) pt strony, przeciągnięty do pasa 1.
        val sx = w.toScreenX(0, a.left(1) + 100f); val sy0 = w.toScreenY(0, a.top(1) + 600f)
        // Upuszczony 0,6 wysokości strony niżej — już w drugim pasie.
        val sy = sy0 + a.height(1) * w.zoom * 0.6f
        val target = w.locate(sx, sy)!!
        assertEquals(1, target.lane)
        val d = w.relocate(0, 1, 1, target.page)
        val b = w.lanes[1]
        // Ten sam punkt ekranu: pozycja w pasie docelowym = pozycja źródła + przesunięcie.
        val nx = 100f + d[0]; val ny = 600f + a.height(1) * 0.6f + d[1]
        assertEquals(sx, w.toScreenX(1, b.left(target.page) + nx), 1e-2f)
        assertEquals(sy, w.toScreenY(1, b.top(target.page) + ny), 1e-2f)
        assertEquals(target.x, nx, 1e-2f); assertEquals(target.y, ny, 1e-2f)
    }

    @Test fun startFitsAllLanesAndLanesCanChange() {
        val w = ws(lanes = 2)
        assertTrue(w.total * w.zoom <= viewH + 1e-3f)
        assertTrue(w.zoom >= w.minZoom() && w.zoom <= w.baseZoom())
        // Dołożony trzeci pas zaczyna od początku, dwa pierwsze zostają, gdzie były.
        w.set(Workspace.Pose(w.baseZoom(), 0f, floatArrayOf(-500f, -900f)))
        val before = w.main.copyOf()
        w.setLanes(w.lanes + lane(2, true))
        assertEquals(3, w.size)
        assertEquals(before[0], w.main[0], 0f); assertEquals(before[1], w.main[1], 0f)
        val r = w.mainRange(2)
        assertTrue(w.main[2] == r[1])
        // Pas poza ekranem nie ma prostokąta.
        w.set(Workspace.Pose(w.baseZoom() * 2f, 0f, w.main))
        assertNull(w.laneRect(2))
        assertNotNull(w.laneRect(0))
    }

    @Test fun resizeKeepsCenter() {
        val w = ws().middle(1.4f)
        val i = w.laneAt(viewW / 2f, viewH / 2f)
        val dx = w.toDocX(i, viewW / 2f); val dy = w.toDocY(i, viewH / 2f)
        w.resize(2400f, 1600f)
        assertTrue(abs(w.toScreenX(i, dx) - 1200f) < 0.5f)
        assertTrue(abs(w.toScreenY(i, dy) - 800f) < 0.5f)
    }

    @Test fun poseForPageJumpKeepsOtherLanesAroundCenter() {
        val w = ws().middle(1.5f)
        val a = w.lanes[0]
        // Pas 0 chce pokazać stronę 3 od lewej krawędzi przy tym samym zoomie.
        val p = w.poseFor(0, w.zoom, -(a.left(3) - a.pad * 0.5f) * w.zoom, w.ty(0))
        assertEquals(-(a.left(3) - a.pad * 0.5f) * w.zoom, p.main[0], 1e-3f)
        assertEquals(w.main[1], p.main[1], 1e-3f)
        assertEquals(w.cross, p.cross, 1e-3f)
        // Połowa animacji: zoom geometrycznie, przesunięcia liniowo.
        val q = w.poseFor(0, w.zoom * 2f, w.tx(0), w.ty(0))
        val h = w.lerp(w.pose(), q, 0.5f)
        assertEquals(w.zoom * kotlin.math.sqrt(2f), h.zoom, 1e-3f)
    }
}

package com.mdev.mnotes.core

import com.mdev.mnotes.core.geom.Axes
import com.mdev.mnotes.core.geom.AxesSnap
import com.mdev.mnotes.core.geom.AxesSpec
import com.mdev.mnotes.core.model.Element
import com.mdev.mnotes.core.model.Ids
import com.mdev.mnotes.core.model.PageFormat
import com.mdev.mnotes.core.model.PageSpec
import com.mdev.mnotes.core.model.PenKind
import com.mdev.mnotes.core.model.Stroke
import com.mdev.mnotes.core.model.Template
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

/** 2b-A6: układ współrzędnych dociskany do kratki — wstawianie, przesuwanie, kreski „po kratkach”. */
class AxesSnapTest {
    private val gridPage = PageSpec.of(PageFormat.A4, template = Template.GRID)
    private val g = AxesSnap.pageGrid(gridPage)!!

    private fun onNode(v: Float, o: Float, sp: Float) = abs((v - o) / sp - Math.round((v - o) / sp)) < 1e-3f

    /** Układ tak, jak wstawia go edytor (osie, znaczniki, siatka jako łamane z dwóch punktów). */
    private fun axesEls(spec: AxesSpec, left: Float, top: Float): List<Element> {
        val d = Axes.build(spec, left, top)
        fun line(xy: FloatArray) = Stroke(Ids.next(), PenKind.BALLPOINT, 0, 1f, FloatArray(xy.size / 2 * 3) { k -> if (k % 3 == 2) 1f else xy[k / 3 * 2 + k % 3] }, poly = true)
        return (d.grid + d.axes + d.ticks).map { line(it) }
    }

    @Test fun pageGridMatchesTheTemplate() {
        assertEquals(14.17f, g.sp, 1e-3f)
        assertEquals((gridPage.w % 14.17f) / 2f, g.x0, 1e-3f)
        assertNull(AxesSnap.pageGrid(PageSpec.of(PageFormat.A4, template = Template.BLANK)))
        assertEquals(2.835f, AxesSnap.pageGrid(PageSpec.of(PageFormat.A4, template = Template.MILLIMETER))!!.sp, 1e-3f)
    }

    @Test fun insertionPutsOriginOnANodeAndTicksOnLines() {
        val r = AxesSnap.alignInsert(unit = 20f, step = 1f, ox = 203.4f, oy = 377.9f, g = g)
        assertEquals(0f, (r[0] / g.sp) - Math.round(r[0] / g.sp), 1e-4f)         // jednostka = wielokrotność oczka
        assertTrue(onNode(203.4f + r[1], g.x0, g.sp)); assertTrue(onNode(377.9f + r[2], g.y0, g.sp))
        assertTrue(abs(r[1]) <= g.sp / 2 + 1e-3f && abs(r[2]) <= g.sp / 2 + 1e-3f)
    }

    @Test fun detectsTheInsertedSystemAndItsGrid() {
        val spec = AxesSpec(-3f, 4f, -2f, 3f, unit = 2f * g.sp)
        val f = AxesSnap.frames(axesEls(spec, 100f, 200f)).single()
        assertEquals(100f + 3f * 2f * g.sp, f.ox, 1e-3f)        // x = 0
        assertEquals(200f + 3f * 2f * g.sp, f.oy, 1e-3f)        // y = 0 (od góry: yMax = 3)
        assertEquals(2f * g.sp, f.gx!!, 1e-3f); assertEquals(2f * g.sp, f.gy!!, 1e-3f)
    }

    @Test fun movingSnapsTheOriginToANode() {
        val r = AxesSnap.snapMove(130.2f, 250.7f, 33.3f, -12.9f, g)
        assertTrue(onNode(130.2f + r[0], g.x0, g.sp)); assertTrue(onNode(250.7f + r[1], g.y0, g.sp))
    }

    @Test fun strokeAlongAGridLineLandsOnItAndCrossingsStay() {
        val spec = AxesSpec(0f, 6f, 0f, 4f, unit = g.sp)
        val f = AxesSnap.frames(axesEls(spec, 100f, 100f)).single()
        val rnd = Random(4)
        // Odcinek wykresu wzdłuż poziomej linii y = 2 (3 oczka) z drżeniem ±0,8 pt.
        val yLine = f.oy - 2f * g.sp
        val along = FloatArray(3 * 40) { k -> when (k % 3) { 0 -> f.ox + g.sp * 0.5f + (k / 3) * g.sp * 3f / 40f; 1 -> yLine + (rnd.nextFloat() - 0.5f) * 1.6f; else -> 1f } }
        val s = AxesSnap.followGrid(along, f, thr = 1.5f)
        assertNotNull(s)
        for (i in 0 until 40) assertEquals(yLine, s!![3 * i + 1], 1e-4f)
        // Ukośna prosta przecina linie siatki — zostaje bez zmian.
        val diag = FloatArray(3 * 60) { k -> val i = k / 3; when (k % 3) { 0 -> f.ox + i * 1.3f; 1 -> f.oy - i * 0.9f; else -> 1f } }
        assertNull(AxesSnap.followGrid(diag, f, thr = 1.5f))
        // Kreska poza układem — bez zmian.
        val outside = FloatArray(3 * 20) { k -> when (k % 3) { 0 -> 500f + k; 1 -> 700f; else -> 1f } }
        assertNull(AxesSnap.followGrid(outside, f, thr = 1.5f))
    }
}

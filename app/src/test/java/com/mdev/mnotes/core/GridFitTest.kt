package com.mdev.mnotes.core

import com.mdev.mnotes.core.edit.GridFit
import com.mdev.mnotes.core.geom.Bounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pomysł Fable nr 11: zdjęcie wyrównane do kratki po wstawieniu. */
class GridFitTest {
    private val sp = 14.17f
    private val pw = 595f; private val ph = 842f
    private val x0 = (pw % sp) / 2f; private val y0 = (ph % sp) / 2f

    private fun onGrid(v: Float, o: Float) = kotlin.math.abs(((v - o) / sp) - Math.round((v - o) / sp)) < 1e-3f

    @Test fun cornerOnGridAndWidthInCells() {
        val r = GridFit.fit(Bounds(103f, 207f, 303f, 357f), 200f / 150f, sp, pw, ph)
        assertTrue(onGrid(r.l, x0)); assertTrue(onGrid(r.t, y0))
        assertTrue(onGrid(r.w, 0f))
        assertEquals(200f / 150f, r.w / r.h, 1e-3f)
        assertEquals(14, Math.round(r.w / sp))
    }

    @Test fun staysOnPage() {
        val r = GridFit.fit(Bounds(500f, 800f, 900f, 1100f), 4f / 3f, sp, pw, ph)
        assertTrue(r.r <= pw + 0.01f)
        assertTrue(r.b <= ph + 0.01f)
        assertTrue(r.l >= x0 - 0.01f && r.t >= y0 - 0.01f)
        assertEquals(Bounds(1f, 2f, 3f, 4f), GridFit.fit(Bounds(1f, 2f, 3f, 4f), 1f, 0f, pw, ph))
    }
}

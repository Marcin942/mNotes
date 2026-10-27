package com.mdev.mnotes.core

import com.mdev.mnotes.core.geom.Bounds
import com.mdev.mnotes.core.layout.MiniMap
import org.junit.Assert.assertEquals
import org.junit.Test

/** Pomysł Fable nr 3: mini-mapa strony przy dużym powiększeniu. */
class MiniMapTest {
    @Test fun boxSitsInCornerKeepingAspectAndShowsView() {
        val g = MiniMap.geometry(595f, 842f, Bounds(100f, 200f, 200f, 300f), 2000f, 1200f, 200f, 200f, 16f)
        assertEquals(1984f, g.box.r, 1e-3f); assertEquals(1184f, g.box.b, 1e-3f)
        assertEquals(595f / 842f, g.box.w / g.box.h, 1e-3f)
        assertEquals(g.box.l + 100f * g.scale, g.view.l, 1e-3f)
        assertEquals(100f * g.scale, g.view.w, 1e-3f)
    }

    @Test fun touchMapsBackToPageAndClamps() {
        val g = MiniMap.geometry(600f, 600f, Bounds(0f, 0f, 10f, 10f), 1000f, 1000f, 120f, 120f, 10f)
        val p = MiniMap.toPage(g, g.box.cx, g.box.cy, 600f, 600f)
        assertEquals(300f, p[0], 1e-2f); assertEquals(300f, p[1], 1e-2f)
        val out = MiniMap.toPage(g, 0f, 0f, 600f, 600f)
        assertEquals(0f, out[0], 0f); assertEquals(0f, out[1], 0f)
        // Widok wychodzący poza stronę jest przycięty do mini-mapy.
        val g2 = MiniMap.geometry(600f, 600f, Bounds(-50f, 500f, 700f, 900f), 1000f, 1000f, 120f, 120f, 10f)
        assertEquals(g2.box.l, g2.view.l, 1e-3f); assertEquals(g2.box.b, g2.view.b, 1e-3f)
    }
}

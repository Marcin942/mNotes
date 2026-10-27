package com.mdev.mnotes.core

import com.mdev.mnotes.core.geom.Bounds
import com.mdev.mnotes.core.layout.MiniMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    /** 2b-A3: mapa w wybranym narożniku i rozmiarze; widok i dotyk liczone względem jej pola. */
    @Test fun cornerAndSizeAreConfigurable() {
        val vis = Bounds(0f, 0f, 100f, 100f)
        val tl = MiniMap.geometry(600f, 800f, vis, 1000f, 1200f, 160f, 200f, 10f, MiniMap.TOP_LEFT)
        assertEquals(10f, tl.box.l, 1e-3f); assertEquals(10f, tl.box.t, 1e-3f)
        val bl = MiniMap.geometry(600f, 800f, vis, 1000f, 1200f, 160f, 200f, 10f, MiniMap.BOTTOM_LEFT)
        assertEquals(10f, bl.box.l, 1e-3f); assertEquals(1190f, bl.box.b, 1e-3f)
        val tr = MiniMap.geometry(600f, 800f, vis, 1000f, 1200f, 160f, 200f, 10f, MiniMap.TOP_RIGHT)
        assertEquals(990f, tr.box.r, 1e-3f); assertEquals(10f, tr.box.t, 1e-3f)
        // Większe pole = większa mapa; punkt w środku mapy to dalej środek strony.
        val small = MiniMap.geometry(600f, 800f, vis, 1000f, 1200f, 80f, 100f, 10f)
        val big = MiniMap.geometry(600f, 800f, vis, 1000f, 1200f, 200f, 250f, 10f)
        assertTrue(big.box.w > 2f * small.box.w)
        val p = MiniMap.toPage(tl, tl.box.cx, tl.box.cy, 600f, 800f)
        assertEquals(300f, p[0], 0.1f); assertEquals(400f, p[1], 0.1f)
        assertEquals(tl.box.l, tl.view.l, 1e-3f)
    }

    /** A2-2: mapa widoczna tylko chwilę po ruchu widoku, potem gaśnie (jak pasek przewijania). */
    @Test fun showsOnlyAfterMovingThenFades() {
        assertEquals(0f, MiniMap.alpha(5_000L, 0L), 0f)                 // bez ruchu nic nie zasłania
        assertEquals(1f, MiniMap.alpha(1_000L, 1_000L), 0f)
        assertEquals(1f, MiniMap.alpha(1_000L + MiniMap.HOLD_MS, 1_000L), 0f)
        val mid = MiniMap.alpha(1_000L + MiniMap.HOLD_MS + MiniMap.FADE_MS / 2, 1_000L)
        assertTrue(mid > 0.3f && mid < 0.7f)
        assertEquals(0f, MiniMap.alpha(1_000L + MiniMap.HOLD_MS + MiniMap.FADE_MS, 1_000L), 0f)
        assertEquals(0f, MiniMap.alpha(60_000L, 1_000L), 0f)
    }

    /** A2-2: rysik nigdy nie trafia w mapę (pisze pod nią), palec tylko w widoczną mapę. */
    @Test fun onlyFingerOnVisibleMapTakesTouch() {
        assertTrue(MiniMap.takesTouch(finger = true, alpha = 1f, inBox = true))
        assertFalse(MiniMap.takesTouch(finger = false, alpha = 1f, inBox = true))
        assertFalse(MiniMap.takesTouch(finger = true, alpha = 0f, inBox = true))
        assertFalse(MiniMap.takesTouch(finger = true, alpha = 1f, inBox = false))
    }
}

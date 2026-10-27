package com.mdev.mnotes.core

import com.mdev.mnotes.core.codec.PageCodec
import com.mdev.mnotes.core.model.PageContent
import com.mdev.mnotes.core.model.PenKind
import com.mdev.mnotes.core.model.Stroke
import com.mdev.mnotes.core.shapes.Bezier
import com.mdev.mnotes.core.shapes.CurveDraft
import com.mdev.mnotes.core.shapes.CurveFit
import com.mdev.mnotes.core.shapes.CurveMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/** 2b-A10: tryby krzywej — przez punkty, kontur zamknięty, piórko, odręczna z małą liczbą węzłów. */
class CurveDraftTest {

    private val pts = floatArrayOf(0f, 0f, 40f, 30f, 55f, 32f, 120f, -10f, 200f, 0f)

    /** Najmniejsza odległość punktu (x, y) od łamanej krzywej [ctrl]. */
    private fun dist(ctrl: FloatArray, x: Float, y: Float): Float {
        val s = Bezier.sample(ctrl, 0.25f)
        var best = Float.MAX_VALUE
        for (k in 0 until s.size / 3) best = minOf(best, hypot(s[3 * k] - x, s[3 * k + 1] - y))
        return best
    }

    @Test fun throughPassesEveryPointAndIsSmooth() {
        val c = CurveFit.through(pts, closed = false)!!
        assertEquals(5, Bezier.anchors(c))
        for (i in 0 until 5) {
            assertEquals(pts[2 * i], c[6 * i], 1e-4f)
            assertEquals(pts[2 * i + 1], c[6 * i + 1], 1e-4f)
        }
        // Wewnętrzne kotwice gładkie: uchwyty po obu stronach na jednej prostej.
        for (a in 1..3) assertTrue("kotwica $a", Bezier.smooth(c, 3 * a))
        assertFalse(CurveFit.closed(c))
    }

    @Test fun unevenSpacingDoesNotOvershoot() {
        // Dwa punkty blisko siebie między dalekimi: zwykły Catmull‑Rom robi tu pętlę, dośrodkowy — nie.
        val p = floatArrayOf(0f, 0f, 100f, 0f, 102f, 2f, 200f, 0f)
        val c = CurveFit.through(p, closed = false)!!
        val s = Bezier.sample(c, 0.5f)
        for (k in 0 until s.size / 3) {
            assertTrue("x ${s[3 * k]}", s[3 * k] in -1f..201f)
            assertTrue("y ${s[3 * k + 1]}", s[3 * k + 1] in -25f..25f)
        }
    }

    @Test fun closedEndsWhereItStartsAndIsSmoothAtSeam() {
        val sq = floatArrayOf(0f, 0f, 100f, 0f, 100f, 100f, 0f, 100f)
        val c = CurveFit.through(sq, closed = true)!!
        assertTrue(CurveFit.closed(c))
        assertEquals(5, Bezier.anchors(c))   // 4 punkty + powtórzony pierwszy
        assertTrue(Bezier.smooth(c, 0))
        for (i in 0 until 4) assertTrue(dist(c, sq[2 * i], sq[2 * i + 1]) < 0.01f)
    }

    @Test fun movingFirstAnchorOfClosedCurveMovesLastToo() {
        val c = CurveFit.through(floatArrayOf(0f, 0f, 100f, 0f, 100f, 100f, 0f, 100f), closed = true)!!
        val n = c.size / 2
        val m = Bezier.moved(c, 0, -10f, -5f)
        assertTrue(CurveFit.closed(m))
        assertEquals(-10f, m[2 * n - 2], 1e-4f)
        // Uchwyt ostatniego odcinka (przy szwie) przesunięty razem z kotwicą.
        assertEquals(c[2 * (n - 2)] - 10f, m[2 * (n - 2)], 1e-4f)
        // Przeciągnięcie uchwytu przy szwie obraca uchwyt po drugiej stronie (krzywa bez załamania).
        val h = Bezier.moved(c, 1, c[2] + 5f, c[3] + 20f)
        assertTrue(Bezier.smooth(h, 0))
    }

    @Test fun draftTapsFinishAndClose() {
        val d = CurveDraft(CurveMode.THROUGH)
        assertEquals(CurveDraft.Tap.ADD, d.tapKind(0f, 0f, 5f))
        d.press(0f, 0f); d.release()
        d.press(50f, 10f); d.drag(60f, 20f); d.release()     // palec przesunął punkt przed puszczeniem
        assertEquals(60f, d.x(1), 0f)
        d.press(100f, 0f); d.release()
        assertEquals(CurveDraft.Tap.FINISH, d.tapKind(101f, 1f, 5f))
        assertEquals(CurveDraft.Tap.CLOSE, d.tapKind(2f, -1f, 5f))
        assertEquals(CurveDraft.Tap.ADD, d.tapKind(50f, 50f, 5f))
        assertFalse(CurveFit.closed(d.ctrl()))
        assertTrue(CurveFit.closed(d.ctrl(close = true)))
        d.dropLast()
        assertEquals(2, d.count)
        // Za mało punktów na krzywą.
        d.dropLast(); assertNull(d.ctrl())
    }

    @Test fun closedModeAlwaysCloses() {
        val d = CurveDraft(CurveMode.CLOSED)
        for (i in 0 until 3) { d.press(i * 40f, if (i == 1) 50f else 0f); d.release() }
        assertTrue(CurveFit.closed(d.ctrl()))
    }

    @Test fun penNodesUseMirroredHandles() {
        val d = CurveDraft(CurveMode.PEN)
        d.press(0f, 0f); d.release()                       // narożnik
        d.press(100f, 0f); d.drag(130f, 40f); d.release()  // gładki węzeł z uchwytem
        d.press(200f, 0f); d.release()
        assertTrue(d.hasHandle(1)); assertFalse(d.hasHandle(0))
        val c = d.ctrl()!!
        assertEquals(3, Bezier.anchors(c))
        // Uchwyt wejściowy węzła 1 = lustro wyjściowego: (70, −40) i (130, 40).
        assertEquals(70f, c[4], 1e-4f); assertEquals(-40f, c[5], 1e-4f)
        assertEquals(130f, c[8], 1e-4f); assertEquals(40f, c[9], 1e-4f)
        assertTrue(Bezier.smooth(c, 3))
        // Odcinek bez uchwytów dostaje uchwyty na ⅓ i ⅔ — jest za co chwycić przy edycji.
        val e = CurveDraft(CurveMode.PEN).apply { press(0f, 0f); release(); press(90f, 0f); release() }.ctrl()!!
        assertEquals(30f, e[2], 1e-4f); assertEquals(60f, e[4], 1e-4f)
        // Domknięte piórko: ostatni odcinek wraca do pierwszego węzła.
        assertTrue(CurveFit.closed(d.ctrl(close = true)))
    }

    @Test fun freehandToleranceGivesFewNodes() {
        val rnd = Random(7)
        val n = 200
        val wave = FloatArray(2 * n) { k -> val i = k / 2; if (k % 2 == 0) i * 1.5f else 40f * sin(i * 1.5f / 50f) + (rnd.nextFloat() - 0.5f) * 1.2f }
        val tight = Bezier.fit(wave, 0.8f)!!
        val loose = Bezier.fit(wave, CurveFit.freehandTol(1f))!!
        assertTrue("luźna ${Bezier.anchors(loose)} vs ciasna ${Bezier.anchors(tight)}", Bezier.anchors(loose) < Bezier.anchors(tight))
        assertTrue(Bezier.anchors(loose) <= 6)
        assertTrue(Bezier.maxDistance(wave, loose) < CurveFit.freehandTol(1f) + 1f)
    }

    @Test fun fillSurvivesCodecAndEditsButNotOpening() {
        val c = CurveFit.through(floatArrayOf(0f, 0f, 100f, 0f, 50f, 80f), closed = true)!!
        val s = Stroke(1, PenKind.BALLPOINT, 0xFF2255AA.toInt(), 1.5f, Bezier.sample(c), ctrl = c, fill = 0xFF2255AA.toInt())
        val back = PageCodec.decode(PageCodec.encode(PageContent.of(listOf(s)))).elements.single() as Stroke
        assertEquals(s.fill, back.fill)
        assertNotNull(back.ctrl)
        // Edycja kotwicy zostawia wypełnienie; zmiana koloru przenosi je na nowy kolor.
        assertEquals(s.fill, Bezier.withCtrl(back, Bezier.moved(back.ctrl!!, 3, 110f, 5f)).fill)
        assertEquals(0xFFCC3333.toInt(), back.withStyle(color = 0xFFCC3333.toInt()).fill)
        // Krzywa przestała być zamknięta → bez wypełnienia.
        val open = CurveFit.through(floatArrayOf(0f, 0f, 100f, 0f, 50f, 80f), closed = false)!!
        assertEquals(0, Bezier.withCtrl(back, open).fill)
        // Kreska bez wypełnienia zapisuje się bajt w bajt jak dawniej (bez ogona).
        val plain = Stroke(2, PenKind.BALLPOINT, 0xFF000000.toInt(), 1f, Bezier.sample(c), ctrl = c)
        val a = PageCodec.encode(PageContent.of(listOf(plain)))
        val b = PageCodec.encode(PageContent.of(listOf(Stroke(2, PenKind.BALLPOINT, 0xFF000000.toInt(), 1f, Bezier.sample(c), ctrl = c, fill = 0))))
        assertTrue(a.contentEquals(b))
    }

    @Test fun hintsFollowProgress() {
        assertTrue(CurveDraft.hint(CurveMode.THROUGH, 0, true).contains("pierwszy"))
        assertTrue(CurveDraft.hint(CurveMode.CLOSED, 3, true).contains("domyka"))
        assertTrue(CurveDraft.hint(CurveMode.PEN, 0, false).contains("handles"))
        assertTrue(CurveMode.entries.all { CurveMode.of(it.ordinal) == it })
        assertEquals(CurveMode.FREEHAND, CurveMode.of(99))
    }
}

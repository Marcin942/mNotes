package com.mdev.mnotes.core

import com.mdev.mnotes.core.edit.EraseMath
import com.mdev.mnotes.core.edit.EraseSession
import com.mdev.mnotes.core.geom.Bounds
import com.mdev.mnotes.core.geom.BoundsGrid
import com.mdev.mnotes.core.geom.GeomMath
import com.mdev.mnotes.core.model.Element
import com.mdev.mnotes.core.model.ElementsEdit
import com.mdev.mnotes.core.model.Ids
import com.mdev.mnotes.core.model.PageContent
import com.mdev.mnotes.core.model.PenKind
import com.mdev.mnotes.core.model.Stroke
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

class EraseSessionTest {

    /** Gęsta strona: 40 wierszy „pisma” po 75 kresek (po 40 punktów) i 20 długich zakreślaczy przez całą szerokość. */
    private fun heavyPage(): List<Element> {
        val out = ArrayList<Element>()
        for (row in 0 until 40) for (w in 0 until 75) {
            val x0 = 10f + w * 8f; val y0 = 40f + row * 18f
            val pts = FloatArray(3 * 40)
            for (i in 0 until 40) { pts[3 * i] = x0 + i * 0.18f; pts[3 * i + 1] = y0 + 5f * sin(i * 0.7f); pts[3 * i + 2] = 0.6f + 0.01f * i }
            out.add(Stroke(Ids.next(), PenKind.BALLPOINT, 0xFF000000.toInt(), 1.2f, pts))
        }
        for (h in 0 until 20) {
            val pts = FloatArray(3 * 800)
            for (i in 0 until 800) { pts[3 * i] = 5f + i * 0.75f; pts[3 * i + 1] = 60f + h * 36f + 2f * sin(i * 0.05f); pts[3 * i + 2] = 1f }
            out.add(Stroke(Ids.next(), PenKind.HIGHLIGHTER, 0xFFF5CF17.toInt(), 12f, pts))
        }
        return out
    }

    /** Zygzak gumki przez stronę: [events] zdarzeń ruchu, każde z [sub] punktami (historia zdarzenia). */
    private fun path(events: Int, sub: Int): FloatArray {
        val out = FloatArray(2 * events * sub)
        var k = 0
        for (e in 0 until events) for (s in 0 until sub) {
            val t = (e * sub + s) / (events * sub).toFloat()
            out[k++] = 20f + 560f * (0.5f + 0.5f * sin(t * 40f))
            out[k++] = 30f + 720f * t
        }
        return out
    }

    /** Dawny algorytm z NoteView: kroki kół co 0,6 r, każdy po całej liście, kopia listy przy trafieniu. */
    private fun oldErase(start: List<Element>, pts: FloatArray, r: Float): List<Element> {
        var work = start
        var lx = pts[0]; var ly = pts[1]
        work = oldStep(work, lx, ly, r) ?: work
        for (i in 1 until pts.size / 2) {
            val px = pts[2 * i]; val py = pts[2 * i + 1]
            val steps = ceil(hypot(px - lx, py - ly) / (r * 0.6f)).toInt().coerceAtLeast(1)
            for (k in 1..steps) { val f = k.toFloat() / steps; oldStep(work, lx + (px - lx) * f, ly + (py - ly) * f, r)?.let { work = it } }
            lx = px; ly = py
        }
        return work
    }

    private fun oldStep(src: List<Element>, px: Float, py: Float, r: Float): List<Element>? {
        var out: ArrayList<Element>? = null
        for (i in src.indices) {
            val e = src[i]
            var repl: List<Element>? = null
            var hit = false
            if (e is Stroke && e.bounds.inflate(r + 1f).contains(px, py)) {
                val res = EraseMath.eraseLocal(e, px, py, r)
                if (res != null) { hit = true; repl = res.pieces.map { Stroke(Ids.next(), e.pen, e.color, e.width, it, false) } }
            }
            if (hit && out == null) { out = ArrayList(src.size); for (j in 0 until i) out.add(src[j]) }
            if (out != null) { if (!hit) out.add(e) else if (repl != null) out.addAll(repl) }
        }
        return out
    }

    private fun newErase(start: List<Element>, pts: FloatArray, r: Float, pixel: Boolean = true): EraseSession {
        val s = EraseSession(start, pixel = pixel, keepHighlighter = false)
        s.sweep(pts[0], pts[1], pts[0], pts[1], r)
        for (i in 1 until pts.size / 2) s.sweep(pts[2 * i - 2], pts[2 * i - 1], pts[2 * i], pts[2 * i + 1], r)
        return s
    }

    private fun distToPath(x: Float, y: Float, pts: FloatArray): Float {
        var best = GeomMath.distSq(x, y, pts[0], pts[1])
        for (i in 1 until pts.size / 2) best = minOf(best, GeomMath.distSqPointSegment(x, y, pts[2 * i - 2], pts[2 * i - 1], pts[2 * i], pts[2 * i + 1]))
        return sqrt(best)
    }

    @Test fun nothingStaysUnderTheSweptPath() {
        val page = heavyPage()
        val pts = path(60, 3)
        val r = 3f
        val out = newErase(page, pts, r).elements()
        for (e in out) {
            val s = e as Stroke
            for (i in 0 until s.count) assertTrue(distToPath(s.pts[3 * i], s.pts[3 * i + 1], pts) > r * 0.999f)
        }
    }

    @Test fun distantStrokesKeepTheirInstances() {
        val page = heavyPage()
        val pts = floatArrayOf(300f, 41f, 310f, 41f)   // krótki ruch w pierwszym wierszu
        val s = newErase(page, pts, 2f)
        assertTrue(s.changed)
        val out = s.elements()
        val d = ElementsEdit.diff(PageContent.of(page), PageContent.of(out))
        assertTrue("usunięte ${d.removed.size}", d.removed.size in 1..6)
        // Kreski w innych wierszach to te same obiekty (bez kopii).
        val far = page.filter { it.bounds.t > 100f }
        val outSet = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Element, Boolean>()).apply { addAll(out) }
        for (e in far) assertTrue(e in outSet)
        assertTrue(s.dirty.w < 40f)
    }

    @Test fun untouchedSessionReturnsBaseList() {
        val page = heavyPage()
        val s = EraseSession(page, pixel = true, keepHighlighter = false)
        assertFalse(s.sweep(-500f, -500f, -400f, -500f, 5f))
        assertSame(page, s.elements())
    }

    @Test fun objectEraserCatchesThinLineBetweenSamples() {
        // Szybki ruch przez cienką kreskę: koło w punktach zdarzeń jej nie dotyka, kapsuła między nimi tak.
        val line = Stroke(Ids.next(), PenKind.BALLPOINT, 0, 0.5f, floatArrayOf(100f, 0f, 1f, 100f, 200f, 1f))
        val s = EraseSession(listOf(line), pixel = false, keepHighlighter = false)
        assertTrue(s.sweep(0f, 100f, 200f, 100f, 1f))
        assertEquals(0, s.elements().size)
    }

    @Test fun keepHighlighterAndLockedAreSkipped() {
        val hl = Stroke(Ids.next(), PenKind.HIGHLIGHTER, 0, 12f, floatArrayOf(0f, 0f, 1f, 100f, 0f, 1f))
        val locked = Stroke(Ids.next(), PenKind.BALLPOINT, 0, 1f, floatArrayOf(0f, 5f, 1f, 100f, 5f, 1f), locked = true)
        val s = EraseSession(listOf(hl, locked), pixel = true, keepHighlighter = true)
        assertFalse(s.sweep(50f, -20f, 50f, 20f, 4f))
    }

    @Test fun gridVisitsEachCandidateOnce() {
        val bs = listOf(Bounds(0f, 0f, 10f, 10f), Bounds(5f, 5f, 500f, 500f), Bounds(100f, 100f, 110f, 110f), Bounds(-60f, -60f, -50f, -50f))
        val g = BoundsGrid(bs, cell = 16f)
        val seen = ArrayList<Int>()
        g.query(Bounds(0f, 0f, 120f, 120f)) { seen.add(it) }
        assertEquals(listOf(0, 1, 2), seen.sorted())
        seen.clear()
        g.query(Bounds(-70f, -70f, -40f, -40f)) { seen.add(it) }
        assertEquals(listOf(3), seen)
    }

    @Test fun heavyPageEraseIsFast() {
        val page = heavyPage()
        val pts = path(300, 3)
        for (r in floatArrayOf(1f, 6f)) {
            // Rozgrzewka JIT, potem pomiar.
            newErase(page, path(30, 3), r).elements()
            val t0 = System.nanoTime()
            val s = newErase(page, pts, r)
            val out = s.elements()
            val tNew = (System.nanoTime() - t0) / 1e6
            val t1 = System.nanoTime()
            val old = oldErase(page, path(60, 3), r)   // dawny algorytm tylko na 1/5 ścieżki, bo trwa długo
            val tOld = (System.nanoTime() - t1) / 1e6 * 5
            println("gumka r=$r: nowy ${"%.1f".format(tNew)} ms / 300 zdarzeń, dawny ≈ ${"%.0f".format(tOld)} ms (${out.size} / ${old.size} elementów)")
            assertTrue("nowy algorytm: $tNew ms na 300 zdarzeń", tNew < 1500.0)
        }
    }
}

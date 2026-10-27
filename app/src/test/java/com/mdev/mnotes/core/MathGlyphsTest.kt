package com.mdev.mnotes.core

import com.mdev.mnotes.core.geom.Bounds
import com.mdev.mnotes.core.shapes.Bezier
import com.mdev.mnotes.core.shapes.MathGlyphs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** F28: auto-korekta √, ∑ i ∫ z zachowaniem rozmiaru. */
class MathGlyphsTest {

    /** Odręczna łamana przez wierzchołki: punkty co ~2 pt z drżeniem [jit]. */
    private fun hand(vararg v: Float, jit: Float = 0.6f, seed: Int = 3): FloatArray {
        val rnd = Random(seed)
        val out = ArrayList<Float>()
        for (i in 0 until v.size / 2 - 1) {
            val x0 = v[2 * i]; val y0 = v[2 * i + 1]; val x1 = v[2 * i + 2]; val y1 = v[2 * i + 3]
            val n = (kotlin.math.hypot(x1 - x0, y1 - y0) / 2f).toInt().coerceAtLeast(2)
            for (k in 0 until n) { val t = k / n.toFloat(); out.add(x0 + (x1 - x0) * t + (rnd.nextFloat() - 0.5f) * jit); out.add(y0 + (y1 - y0) * t + (rnd.nextFloat() - 0.5f) * jit) }
        }
        out.add(v[v.size - 2]); out.add(v[v.size - 1])
        return out.toFloatArray()
    }

    private fun box(xy: FloatArray): Bounds = Bounds.ofPoints(xy)
    private fun all(xy: FloatArray) = MathGlyphs.recognize(xy, root = true, sum = true, integral = true)

    @Test fun rootIsCorrectedWithinTheSameBox() {
        for (seed in 0 until 10) {
            val xy = hand(0f, 62f, 8f, 58f, 20f, 100f, 34f, 0f, 120f, 0f, seed = seed)
            val g = all(xy)
            assertEquals("seed $seed", MathGlyphs.Kind.ROOT, g?.kind)
            val a = box(xy); val b = box(g!!.xy)
            assertEquals(a.w, b.w, 0.1f * a.w); assertEquals(a.h, b.h, 0.1f * a.h)
            // Kreska u góry jest pozioma.
            assertEquals(g.xy[g.xy.size - 1], g.xy[g.xy.size - 3], 1e-4f)
        }
    }

    @Test fun sumIsCorrected() {
        for (seed in 0 until 10) {
            val g = all(hand(60f, 0f, 0f, 0f, 33f, 50f, 0f, 100f, 62f, 100f, seed = seed))
            assertEquals("seed $seed", MathGlyphs.Kind.SUM, g?.kind)
            assertEquals(5, g!!.xy.size / 2)
        }
    }

    @Test fun integralWithHooksIsCorrectedButSlashIsNot() {
        val s = hand(26f, 10f, 24f, 3f, 20f, 0f, 16f, 2f, 14f, 8f, 10f, 92f, 8f, 98f, 4f, 100f, 0f, 97f, -2f, 92f, jit = 0.4f)
        val g = all(s)
        assertEquals(MathGlyphs.Kind.INTEGRAL, g?.kind)
        assertTrue(g!!.curve)
        assertEquals(4, Bezier.anchors(g.xy))
        val a = box(s); val b = box(Bezier.sample(g.xy).let { p -> FloatArray(2 * (p.size / 3)) { p[3 * (it / 2) + it % 2] } })
        assertEquals(a.h, b.h, 0.12f * a.h)
        // Wariant z ✗: prosta ukośna kreska zostaje (rozpozna ją zwykłe prostowanie jako linię).
        assertNull(all(hand(30f, 0f, 0f, 100f, jit = 0.3f)))
    }

    @Test fun lookalikesAreNotCorrected() {
        assertNull(all(hand(0f, 50f, 15f, 80f, 50f, 0f)))                       // ptaszek ✓: bez kreski u góry
        assertNull(all(hand(0f, 0f, 60f, 0f, 0f, 100f, 60f, 100f)))            // Z
        assertNull(all(hand(0f, 0f, 60f, 0f, 60f, 60f, 0f, 60f, 0f, 0f)))      // kwadrat
        // Wyłączone kafelki nic nie poprawiają.
        assertNull(MathGlyphs.recognize(hand(0f, 62f, 8f, 58f, 20f, 100f, 34f, 0f, 120f, 0f), root = false, sum = true, integral = true))
    }
}

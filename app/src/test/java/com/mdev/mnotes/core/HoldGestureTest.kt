package com.mdev.mnotes.core

import com.mdev.mnotes.core.pen.HoldGesture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** B6 (A2-12): przytrzymanie do rozpoznania kształtu — drżąca ręka nie wydłuża czekania, pismo go nie wywołuje. */
class HoldGestureTest {
    private val dp = 2f            // px na dp
    private val hold = 550L

    /** Próbki co 8 ms (≈ 120 Hz rysika) od t = 0 do [ms]; [at] daje położenie w px. */
    private fun run(ms: Long, at: (Long) -> Pair<Float, Float>, onSample: (HoldGesture, Long) -> Unit = { _, _ -> }): HoldGesture {
        val g = HoldGesture(dp)
        val (x0, y0) = at(0)
        g.start(0, x0, y0)
        var t = 8L
        while (t <= ms) { val (x, y) = at(t); g.move(t, x, y); onSample(g, t); t += 8 }
        return g
    }

    @Test fun shakyHandWithSlowDriftFiresOnTime() {
        // Drżenie ±1,5 dp i powolne zsuwanie się rysika o 15 dp w 0,6 s: przy dawnym promieniu 10 dp licznik startowałby od nowa.
        val at = { t: Long -> (100f + 15f * dp * t / 600f + 1.5f * dp * sin(t * 0.09f)) to (100f + 1.5f * dp * cos(t * 0.07f)) }
        var restarts = 0
        val g = HoldGesture(dp).apply { start(0, 100f, 100f + 1.5f * dp) }
        var t = 8L; var maxFrom0 = 0f
        while (t <= 600L) { val (x, y) = at(t); if (g.move(t, x, y)) restarts++; maxFrom0 = maxOf(maxFrom0, hypot(x - 100f, y - 100f - 1.5f * dp)); t += 8 }
        assertEquals(0, restarts)
        assertTrue("dawny promień 10 dp zostałby przekroczony", maxFrom0 > 10f * dp)
        assertTrue(g.due(600L, hold))
    }

    @Test fun tinyHandwritingInPlaceNeverFires() {
        // Drobne pismo: pętelki o promieniu 5 dp w miejscu, 150 dp/s — cały czas w promieniu dryfu, ale pióro się rusza.
        val r = 5f * dp; val w = 150f * dp / r / 1000f
        run(1500, { t -> (100f + r * cos(t * w)) to (100f + r * sin(t * w)) }) { g, t -> assertFalse("odpaliło w $t ms", g.due(t, hold)) }
    }

    @Test fun ordinaryDrawingNeverFires() {
        run(2000, { t -> (100f + 100f * dp * t / 1000f) to 100f }) { g, t -> assertFalse("odpaliło w $t ms", g.due(t, hold)) }
    }

    @Test fun stopAfterDrawingFiresAfterHoldTime() {
        // 300 ms rysowania (100 dp/s), potem pióro stoi: kształt po 550 ms od ostatniego większego ruchu.
        val at = { t: Long -> (100f + 100f * dp * minOf(t, 300L) / 1000f) to 100f }
        val g = run(1000, at)
        assertTrue(g.anchorT in 200L..300L)
        assertTrue(g.due(g.anchorT + hold, hold))
        assertFalse(g.due(g.anchorT + hold - 50, hold))
    }

    // — 2c-A4: kształt po przytrzymaniu nie ucina końca kreski —

    /** Kreska przez [corners] (px) co [step] px, potem [dwell] próbek w miejscu wg [wobble]; próbki co 8 ms przez [HoldGesture]. */
    private fun heldStroke(corners: List<Pair<Float, Float>>, step: Float, dwell: Int, wobble: (Int) -> Pair<Float, Float>): FloatArray {
        val pts = ArrayList<Float>()
        for (k in 0 until corners.size - 1) {
            val (ax, ay) = corners[k]; val (bx, by) = corners[k + 1]
            val n = (hypot(bx - ax, by - ay) / step).toInt().coerceAtLeast(1)
            for (i in 0 until n) { pts += ax + (bx - ax) * i / n; pts += ay + (by - ay) * i / n }
        }
        val (ex, ey) = corners.last()
        for (i in 0 until dwell) { val (dx, dy) = wobble(i); pts += ex + dx; pts += ey + dy }
        val raw = pts.toFloatArray()
        val g = HoldGesture(dp)
        g.start(0, raw[0], raw[1])
        var holdIdx = 0
        for (i in 1 until raw.size / 2) if (g.move(8L * i, raw[2 * i], raw[2 * i + 1])) holdIdx = i + 1
        lastHoldIdx = holdIdx
        return HoldGesture.shapePoints(raw, holdIdx, raw.size / 2, HoldGesture.DRIFT_DP * dp)
    }
    private var lastHoldIdx = 0

    @Test fun heldLineKeepsItsFullLength() {
        val len = 300f * dp
        // Drżenie 1,5 dp w miejscu: ostatnie zerowanie licznika wypada ok. 14 dp przed końcem — dawniej tu ucinało.
        val tremor = heldStroke(listOf(0f to 0f, len to 0f), 2f * dp, 120) { i -> 1.5f * dp * sin(i * 0.7f) to 1.5f * dp * cos(i * 0.9f) }
        val line = com.mdev.mnotes.core.shapes.ShapeRecognizer.recognize(tremor)!!
        assertEquals(com.mdev.mnotes.core.shapes.ShapeKind.LINE, line.kind)
        assertTrue("długość ${hypot(line.xy[2] - line.xy[0], line.xy[3] - line.xy[1]) / len}", hypot(line.xy[2] - line.xy[0], line.xy[3] - line.xy[1]) >= 0.98f * len)
        assertTrue("licznik zerował się przed końcem", lastHoldIdx in 3 until (len / (2f * dp)).toInt())
        // Dryf 15 dp (rysik powoli się zsuwa i wraca): koniec nadal na swoim miejscu.
        val drift = heldStroke(listOf(0f to 0f, len to 0f), 2f * dp, 150) { i -> val a = i / 150f * 6.283f; 7.5f * dp * (1f - cos(a)) to 7.5f * dp * sin(a) }
        val l2 = com.mdev.mnotes.core.shapes.ShapeRecognizer.recognize(drift)!!
        assertEquals(com.mdev.mnotes.core.shapes.ShapeKind.LINE, l2.kind)
        val d2 = hypot(l2.xy[2] - l2.xy[0], l2.xy[3] - l2.xy[1])
        assertTrue("długość ${d2 / len}", d2 >= 0.98f * len && d2 <= 1.06f * len)
    }

    @Test fun heldRectangleKeepsItsSides() {
        val w = 200f * dp; val h = 100f * dp
        val pts = heldStroke(listOf(0f to 0f, w to 0f, w to h, 0f to h, 0f to 0f), 2f * dp, 120) { i -> 1.5f * dp * sin(i * 0.7f) to 1.5f * dp * cos(i * 0.9f) }
        val r = com.mdev.mnotes.core.shapes.ShapeRecognizer.recognize(pts)!!
        assertEquals(com.mdev.mnotes.core.shapes.ShapeKind.RECT, r.kind)
        val xs = (0 until r.xy.size / 2).map { r.xy[2 * it] }; val ys = (0 until r.xy.size / 2).map { r.xy[2 * it + 1] }
        assertEquals(w, xs.max() - xs.min(), 0.02f * w)
        assertEquals(h, ys.max() - ys.min(), 0.02f * h)
    }

    @Test fun strokeWithoutHoldIsUntouched() {
        val raw = floatArrayOf(0f, 0f, 1f, 1f, 2f, 2f, 3f, 3f)
        assertTrue(raw.contentEquals(HoldGesture.shapePoints(raw, 0, 4, 40f)))
    }
}

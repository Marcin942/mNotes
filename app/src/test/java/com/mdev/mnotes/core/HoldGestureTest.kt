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
}

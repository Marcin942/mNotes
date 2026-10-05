package com.mdev.mnotes.core

import com.mdev.mnotes.editor.LaserTrail
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class LaserTrailTest {
    @Test fun trailStaysFullWhileDownAndDuringHold() {
        val t = LaserTrail()
        t.begin(0); t.add(0f, 0f, 1f, 0); t.add(10f, 0f, 1f, 5000)
        assertEquals(1f, t.fade(60_000), 0f)
        t.end(5000)
        assertEquals(1f, t.fade(5000 + LaserTrail.HOLD_MS), 0f)
    }

    @Test fun trailFadesSmoothlyAfterHoldAndThenDisappears() {
        val t = LaserTrail()
        t.begin(0); t.add(0f, 0f, 1f, 0); t.add(10f, 0f, 1f, 10); t.end(10)
        val mid = t.fade(10 + LaserTrail.HOLD_MS + LaserTrail.FADE_MS / 2)
        assertTrue(mid > 0f && mid < 1f)
        assertEquals(0f, t.fade(10 + LaserTrail.HOLD_MS + LaserTrail.FADE_MS), 0f)
    }

    @Test fun manyPointsAreKeptNotOverwritten() {
        val t = LaserTrail()
        t.begin(0)
        for (i in 0 until 5000) t.add(i * 3f, 0f, 1f, i.toLong())
        assertEquals(5000, t.runs[0].n)
        assertEquals(14997f, t.runs[0].x[4999], 0f)
    }

    @Test fun closePointsMoveTheHeadInsteadOfGrowing() {
        val t = LaserTrail()
        t.begin(0); t.add(0f, 0f, 2f, 0); t.add(10f, 0f, 2f, 1); t.add(10.5f, 0f, 2f, 2)
        assertEquals(3, t.runs[0].n)
        t.add(10.8f, 0.1f, 2f, 3)
        assertEquals(3, t.runs[0].n)
        assertEquals(10.8f, t.runs[0].x[2], 0f)
    }

    @Test fun slowMovementKeepsTheCurveInsteadOfStraighteningIt() {
        val t = LaserTrail()
        t.begin(0)
        // wolne kółko o promieniu 20, krok ≪ minDist
        for (i in 0..720) { val a = Math.toRadians(i.toDouble() / 2); t.add((20 * Math.cos(a)).toFloat(), (20 * Math.sin(a)).toFloat(), 1.5f, i.toLong()) }
        val r = t.runs[0]
        assertTrue(r.n > 60)
        var maxR = 0f
        for (i in 0 until r.n) maxR = maxOf(maxR, Math.hypot(r.x[i].toDouble(), r.y[i].toDouble()).toFloat())
        assertEquals(20f, maxR, 0.5f)
    }

    @Test fun newTouchWithinHoldKeepsOldRunAfterFadeStartsFresh() {
        val t = LaserTrail()
        t.begin(0); t.add(0f, 0f, 1f, 0); t.add(5f, 5f, 1f, 1); t.end(1)
        t.begin(1000)
        assertEquals(2, t.runs.size)
        t.end(1000)
        t.begin(1000 + LaserTrail.HOLD_MS + LaserTrail.FADE_MS + 1)
        assertEquals(1, t.runs.size)
    }

    @Test fun capDropsOldestRuns() {
        val t = LaserTrail()
        for (r in 0 until 3) { t.begin(r.toLong()); for (i in 0 until 20_000) t.add(i * 3f, r.toFloat(), 1f, r.toLong()); t.end(r.toLong()) }
        assertTrue(t.runs.sumOf { it.n } <= LaserTrail.MAX_POINTS)
        assertEquals(2, t.runs.size)
    }
}

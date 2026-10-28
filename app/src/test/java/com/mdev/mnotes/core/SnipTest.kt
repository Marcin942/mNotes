package com.mdev.mnotes.core

import com.mdev.mnotes.core.edit.Snip
import com.mdev.mnotes.core.geom.Bounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** F14: wycinek strony jako obraz. */
class SnipTest {

    @Test fun planAddsMarginAndClipsToPage() {
        val p = Snip.plan(Bounds(2f, 100f, 102f, 150f), 420f, 595f)!!
        assertEquals(0f, p.rect.l, 0f)          // margines przycięty do brzegu strony
        assertEquals(96f, p.rect.t, 0f)
        assertEquals(106f, p.rect.r, 0f)
        assertEquals(3f, p.scale, 0f)
        assertEquals(318, p.wPx)
    }

    @Test fun bigSnipIsScaledDown() {
        val p = Snip.plan(Bounds(0f, 0f, 2000f, 500f), 3000f, 3000f, maxPx = 3000)!!
        assertTrue(p.wPx <= 3000)
        assertTrue(p.scale < 3f)
        assertNull(Snip.plan(Bounds(500f, 500f, 600f, 600f), 100f, 100f))
    }

    @Test fun pagePlanTakesWholeSheet() {
        val p = Snip.pagePlan(595f, 842f, board = false, content = null)!!
        assertRect(0f, 0f, 595f, 842f, p.rect)    // bez marginesu: dokładnie kartka, także pusta
        assertEquals(2f, p.scale, 0f)
        assertEquals(1190, p.wPx)
        assertEquals(1684, p.hPx)
        // Długa strona (np. poziomy pasek z PDF-u) nie przekracza limitu po dłuższym boku.
        val long = Snip.pagePlan(5000f, 800f, board = false, content = null)!!
        assertTrue(long.wPx <= 2400 && long.scale < 2f)
    }

    @Test fun boardPagePlanIsContentFrameOnly() {
        // Tablica 3000 × 3000 pt z rysunkiem w środku: obraz to kadr treści z zapasem, nie całe płótno.
        val p = Snip.pagePlan(3000f, 3000f, board = true, content = Bounds(1000f, 1200f, 1300f, 1400f))!!
        assertRect(976f, 1176f, 1324f, 1424f, p.rect)
        assertEquals(696, p.wPx)
        assertNull(Snip.pagePlan(3000f, 3000f, board = true, content = null))   // pusta tablica: nic do kopiowania
    }

    private fun assertRect(l: Float, t: Float, r: Float, b: Float, x: Bounds) {
        assertEquals(l, x.l, 1e-3f); assertEquals(t, x.t, 1e-3f); assertEquals(r, x.r, 1e-3f); assertEquals(b, x.b, 1e-3f)
    }

    @Test fun placementPrefersRightThenBelow() {
        val right = Snip.placement(Bounds(10f, 10f, 110f, 60f), 420f, 595f)
        assertEquals(170f, right[0], 1e-4f)
        assertEquals(35f, right[1], 1e-4f)
        val below = Snip.placement(Bounds(200f, 10f, 400f, 60f), 420f, 595f)
        assertEquals(300f, below[0], 1e-4f)
        assertEquals(95f, below[1], 1e-4f)
        // Brak miejsca wokół: na wycinku, przesunięty i w granicach strony.
        val over = Snip.placement(Bounds(0f, 0f, 420f, 595f), 420f, 595f)
        assertTrue(over[0] <= 420f - 210f + 1e-3f && over[1] <= 595f - 297.5f + 1e-3f)
    }
}

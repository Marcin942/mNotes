package com.mdev.mnotes.core

import com.mdev.mnotes.core.edit.CardPlacement
import com.mdev.mnotes.core.edit.CardSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CardPlacementTest {
    private fun overlaps(x: Int, y: Int, w: Int, h: Int, l: Float, t: Float, r: Float, b: Float) =
        minOf(x + w.toFloat(), r) > maxOf(x.toFloat(), l) && minOf(y + h.toFloat(), b) > maxOf(y.toFloat(), t)

    @Test fun cardGoesBelowASmallImageAndStaysInView() {
        val s = CardPlacement.place(300f, 200f, 700f, 500f, 400, 160, 1200, 900, 12f, null)
        assertEquals(CardSide.BELOW, s.side)
        assertEquals(512, s.y)
        assertTrue(s.x >= 0 && s.x + 400 <= 1200)
    }

    @Test fun cardGoesAboveWhenThereIsNoRoomBelow() {
        val s = CardPlacement.place(300f, 400f, 700f, 850f, 400, 160, 1200, 900, 12f, null)
        assertEquals(CardSide.ABOVE, s.side)
        assertTrue(!overlaps(s.x, s.y, 400, 160, 300f, 400f, 700f, 850f))
    }

    @Test fun cardGoesBesideAnImageThatSpansTheHeight() {
        val s = CardPlacement.place(50f, 20f, 650f, 880f, 400, 160, 1200, 900, 12f, null)
        assertEquals(CardSide.RIGHT, s.side)
        assertTrue(s.x >= 650)
    }

    @Test fun cardDoesNotJumpWhileItsSideStillWorks() {
        val s = CardPlacement.place(300f, 200f, 700f, 560f, 400, 160, 1200, 900, 12f, CardSide.ABOVE)
        assertEquals(CardSide.ABOVE, s.side)
    }

    @Test fun cardLeavesASideThatNowCoversTheImage() {
        val s = CardPlacement.place(300f, 200f, 700f, 700f, 400, 160, 1200, 900, 12f, CardSide.BELOW)
        assertTrue(!overlaps(s.x, s.y, 400, 160, 300f, 200f, 700f, 700f))
    }

    @Test fun cardCoversAsLittleAsPossibleWhenTheImageFillsTheView() {
        val s = CardPlacement.place(10f, 10f, 1190f, 890f, 400, 160, 1200, 900, 12f, null)
        val cover = 400f * 160f
        val got = maxOf(0f, minOf(s.x + 400f, 1190f) - maxOf(s.x.toFloat(), 10f)) * maxOf(0f, minOf(s.y + 160f, 890f) - maxOf(s.y.toFloat(), 10f))
        assertTrue(got <= cover)
        assertTrue(s.x >= 0 && s.y >= 0 && s.x + 400 <= 1200 && s.y + 160 <= 900)
    }
}

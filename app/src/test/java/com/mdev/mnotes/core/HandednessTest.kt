package com.mdev.mnotes.core

import com.mdev.mnotes.core.layout.Handedness
import com.mdev.mnotes.core.layout.Handedness.LEFT
import com.mdev.mnotes.core.layout.Handedness.RIGHT
import com.mdev.mnotes.core.layout.MiniMap
import org.junit.Assert.assertEquals
import org.junit.Test

/** 3c-A13: jedno ustawienie ręki przestawia wszystko, co stoi po którejś stronie ekranu. */
class HandednessTest {
    private val defaults = Handedness.Sides(toolbar = 0, options = 0, undoDock = 0, miniMap = MiniMap.BOTTOM_RIGHT, scrollMargin = false)

    @Test fun leftHandMirrorsSidesAndKeepsTopAndBottom() {
        val l = Handedness.switch(defaults, RIGHT, LEFT)
        assertEquals(0, l.toolbar)                       // pasek u góry zostaje u góry
        assertEquals(1, l.options)                       // listwa pióra z prawej, z dala od lewej dłoni
        assertEquals(MiniMap.BOTTOM_LEFT, l.miniMap)
        assertEquals(true, l.scrollMargin)
        val side = Handedness.Sides(toolbar = 2, options = 2, undoDock = 4, miniMap = MiniMap.TOP_LEFT, scrollMargin = true)
        assertEquals(Handedness.Sides(3, 2, 3, MiniMap.TOP_RIGHT, true), Handedness.switch(side, RIGHT, LEFT))
    }

    @Test fun switchingBackRestoresTheLayout() {
        for (tb in 0..3) for (o in 0..2) for (u in 0..4) for (c in 0..3) {
            val s = Handedness.Sides(tb, o, u, c, false)
            val back = Handedness.switch(Handedness.switch(s, RIGHT, LEFT), LEFT, RIGHT)
            assertEquals(s.copy(scrollMargin = true), back)   // zapas przewijania zostaje — przeszkadza najmniej
        }
    }

    @Test fun palmIsOnTheWritingHandSide() {
        val dp = 2f; val sx = 500f; val sy = 400f
        // Prawa ręka: dłoń z prawej i poniżej rysika — dłoń; palec lewej ręki z lewej — nie; daleko nad rysikiem — nie.
        assertEquals(true, Handedness.palmSide(RIGHT, 700f, 520f, sx, sy, dp))
        assertEquals(false, Handedness.palmSide(RIGHT, 200f, 520f, sx, sy, dp))
        assertEquals(false, Handedness.palmSide(RIGHT, 700f, 100f, sx, sy, dp))
        // Lewa ręka: lustrzanie.
        assertEquals(true, Handedness.palmSide(LEFT, 300f, 520f, sx, sy, dp))
        assertEquals(false, Handedness.palmSide(LEFT, 800f, 520f, sx, sy, dp))
    }

    @Test fun sameHandChangesNothing() {
        val s = Handedness.Sides(2, 1, 3, MiniMap.TOP_RIGHT, false)
        assertEquals(s, Handedness.switch(s, LEFT, LEFT))
        assertEquals(s, Handedness.switch(s, RIGHT, RIGHT))
    }
}

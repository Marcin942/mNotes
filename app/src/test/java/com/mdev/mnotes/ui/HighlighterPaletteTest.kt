package com.mdev.mnotes.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HighlighterPaletteTest {
    private fun lum(c: Int) = 0.2126f * (c shr 16 and 0xFF) + 0.7152f * (c shr 8 and 0xFF) + 0.0722f * (c and 0xFF)

    @Test fun paletteGivesLightPastelsOnWhite() {
        for (c in HIGHLIGHTER_PALETTE) assertTrue("#%06X za ciemny po multiply".format(c and 0xFFFFFF), lum(highlighterLook(c)) >= 0.72f * 255)
    }

    @Test fun lookIsHalfwayToWhite() {
        assertEquals(0xFF808080.toInt() and 0xFFFFFF, highlighterLook(0xFF000000.toInt()) and 0xFFFFFF)
    }

    @Test fun softenLeavesPaletteUntouchedAndLightensDarkInk() {
        for (c in HIGHLIGHTER_PALETTE) assertEquals(c, softenForHighlighter(c))
        val s = softenForHighlighter(0xFF000000.toInt())
        assertTrue(lum(s) >= 91f)
        assertEquals(0xFF, s ushr 24)
    }
}

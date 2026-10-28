package com.mdev.mnotes.core

import com.mdev.mnotes.core.model.ColorInvert
import com.mdev.mnotes.core.model.PageFormat
import com.mdev.mnotes.core.model.PageSpec
import com.mdev.mnotes.core.model.PdfRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** F11: odwrócone kolory strony (ciemny tryb dokumentu). */
class ColorInvertTest {
    private fun ch(c: Int, sh: Int) = (c shr sh) and 255

    @Test fun whiteAndBlackSwap() {
        assertEquals(0xFF000000.toInt(), ColorInvert.apply(0xFFFFFFFF.toInt()))
        assertEquals(0xFFFFFFFF.toInt(), ColorInvert.apply(0xFF000000.toInt()))
    }

    @Test fun hueIsKeptAndLightnessFlips() {
        // Ciemnoczerwony atrament staje się jasnoczerwony, a nie turkusowy jak przy zwykłym odwróceniu RGB.
        val red = ColorInvert.apply(0xFF8B1A1A.toInt())
        assertTrue(ch(red, 16) > ch(red, 8) && ch(red, 16) > ch(red, 0))
        assertTrue(ch(red, 16) + ch(red, 8) + ch(red, 0) > 0x8B + 0x1A + 0x1A)
        // Granat → jasnoniebieski.
        val blue = ColorInvert.apply(0xFF1A237E.toInt())
        assertTrue(ch(blue, 0) >= ch(blue, 16) && ch(blue, 0) >= ch(blue, 8))
        // Alfa bez zmian.
        assertEquals(0x80, (ColorInvert.apply(0x80FFFFFF.toInt()) ushr 24) and 255)
    }

    @Test fun flagRoundTripsAndKeepsOtherOptions() {
        val pdf = PageSpec.of(PageFormat.A4).copy(pdf = PdfRef("a.pdf", 3))
        val inv = pdf.withInverted(true)
        assertTrue(inv.inverted)
        assertTrue(inv.marginLine)
        val back = PageSpec.fromJson(inv.toJson())
        assertTrue(back.inverted)
        assertEquals(PdfRef("a.pdf", 3), back.pdf)
        assertFalse(back.withInverted(false).inverted)
        assertTrue(back.withInverted(false).marginLine)
        // Stary zapis bez flagi: zwykła strona.
        assertFalse(PageSpec.fromJson("{\"w\":595,\"h\":842,\"o\":1}").inverted)
    }
}

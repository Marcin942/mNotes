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

    /**
     * A2-6: kryjącą kreskę w trakcie rysujemy od razu odwróconym kolorem zamiast warstwy z filtrem na każdą klatkę — tam,
     * gdzie zakrywa tło, to ten sam piksel co po zatwierdzeniu (kafelek w oryginale + filtr). Kreski mieszające się z tłem
     * idą przez warstwę, bo macierz obcina nasycone kanały i odwrócenie nie przenosi się przez mieszanie.
     */
    @Test fun liveInkMatchesCommittedLook() {
        fun ch(c: Int, sh: Int) = (c shr sh) and 255
        fun over(src: Int, dst: Int, a: Float): Int {
            fun m(sh: Int) = Math.round(ch(src, sh) * a + ch(dst, sh) * (1f - a)).coerceIn(0, 255) shl sh
            return (0xFF shl 24) or m(16) or m(8) or m(0)
        }
        val rnd = kotlin.random.Random(11)
        repeat(200) {
            val ink = rnd.nextInt() or (0xFF shl 24); val paper = rnd.nextInt() or (0xFF shl 24)
            // Kryjący atrament: po zatwierdzeniu = odwrócenie (atrament na papierze), w trakcie = odwrócony atrament na odwróconym papierze.
            val committed = ColorInvert.apply(over(ink, paper, 1f))
            val live = over(ColorInvert.liveInk(ink, inverted = true, mixes = false)!!, ColorInvert.apply(paper), 1f)
            assertEquals("ink=%08X".format(ink), committed, live)
        }
        // Półprzezroczysty nasycony czerwony na bieli: mieszanie przed i po odwróceniu daje inny kolor → potrzebna warstwa.
        val red = 0xFFFF0000.toInt(); val white = -1
        val committed = ColorInvert.apply(over(red, white, 0.5f))
        val naive = over(ColorInvert.apply(red), ColorInvert.apply(white), 0.5f)
        assertTrue(kotlin.math.abs(ch(committed, 16) - ch(naive, 16)) > 30)
        assertEquals(null, ColorInvert.liveInk(red, inverted = true, mixes = true))
        // Strona bez odwrócenia: kolor bez zmian.
        assertEquals(0xFF1565C0.toInt(), ColorInvert.liveInk(0xFF1565C0.toInt(), inverted = false, mixes = true))
    }
}

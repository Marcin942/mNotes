package com.mdev.mnotes.core

import com.mdev.mnotes.core.text.MathText
import org.junit.Assert.assertEquals
import org.junit.Test

/** L2: grecki i indeksy przy pisaniu w polu tekstu. */
class MathTextTest {
    private fun c(s: String) = MathText.convert(s)

    @Test fun greekAfterBoundary() {
        assertEquals("α ", c("\\alpha "))
        assertEquals("α+β=γ ", c("\\alpha+\\beta=\\gamma "))
        assertEquals("Δ x", c("\\Delta x"))
        assertEquals("\\alph", c("\\alph"))          // w trakcie pisania nic się nie zmienia
        assertEquals("\\alpha", c("\\alpha"))        // jeszcze bez granicy
        assertEquals("\\alphabet ", c("\\alphabet "))
        assertEquals("x→∞,", c("x\\to\\infty,"))
    }

    @Test fun superAndSubscripts() {
        assertEquals("x² + y²", c("x^2 + y^2"))
        assertEquals("a₁, a₂", c("a_1, a_2"))
        assertEquals("x⁻¹", c("x^{-1}"))
        assertEquals("aᵢⱼ", c("a_{ij}"))
        assertEquals("eⁱ ", c("e^i "))
        assertEquals("x^n", c("x^n"))   // litera na końcu czeka na granicę
        assertEquals("xⁿ+1", c("x^n+1"))
    }

    @Test fun ordinaryTextStays() {
        assertEquals("nazwa_pliku.txt", c("nazwa_pliku.txt"))
        assertEquals("C^", c("C^"))
        assertEquals("_x ", c("_x "))          // brak podstawy przed znakiem
        assertEquals("a_{xyz}", c("a_{xyz}"))  // y nie ma indeksu dolnego w Unicode — zostaje jak było
        assertEquals("zwykły tekst", c("zwykły tekst"))
    }
}

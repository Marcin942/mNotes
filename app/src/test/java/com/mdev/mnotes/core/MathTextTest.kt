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
        assertEquals("x² + y²,", c("x^2 + y^2,"))
        assertEquals("x² + y^2", c("x^2 + y^2"))   // cyfra na końcu czeka, bo może przyjść następna
        assertEquals("a₁, a₂ ", c("a_1, a_2 "))
        assertEquals("x⁻¹", c("x^{-1}"))
        assertEquals("aᵢⱼ", c("a_{ij}"))
        assertEquals("eⁱ ", c("e^i "))
        assertEquals("x^n", c("x^n"))   // litera na końcu czeka na granicę
        assertEquals("xⁿ+1", c("x^n+1"))
    }

    /** A2-1 (przegląd Fable): „x^10” dawało „x¹0”, bo cyfra zamieniała się od razu. */
    @Test fun multiDigitRuns() {
        assertEquals("x¹⁰ ", c("x^10 "))
        assertEquals("a₁₂ ", c("a_12 "))
        assertEquals("x^1", c("x^1"))             // bez granicy nic się nie zmienia
        assertEquals("x^10", c("x^10"))
        assertEquals("x⁻¹ ", c("x^-1 "))         // znak i cyfra razem, nie „x⁻1”
        assertEquals("Na⁺ ", c("Na^+ "))
        assertEquals("10¹²·", c("10^12·"))
        assertEquals("x²y ", c("x^2y "))         // po „^” litera kończy wykładnik
        assertEquals("plik_2a.txt", c("plik_2a.txt"))   // po „_” nie — nazwy plików
        assertEquals("img_20.jpg", c("img_20.jpg"))     // 2c-A15: „_cyfry” przed „.litera” to nazwa pliku
        assertEquals("img_20.", c("img_20."))           // kropka na końcu jeszcze czeka na następny znak
        assertEquals("a₁₂. ", c("a_12. "))              // kropka kończąca zdanie: indeks
        assertEquals("a₁₀+b₂₃=", c("a_10+b_23="))
    }

    /** Zatwierdzenie („Gotowe”) kończy znacznik na końcu tekstu, ale nie pojedynczą literę. */
    @Test fun finalFlush() {
        assertEquals("x¹⁰", MathText.convert("x^10", atEnd = true))
        assertEquals("α", MathText.convert("\\alpha", atEnd = true))
        assertEquals("plik_a", MathText.convert("plik_a", atEnd = true))
        assertEquals("x^", MathText.convert("x^", atEnd = true))
        assertEquals("\\alphabet", MathText.convert("\\alphabet", atEnd = true))
    }

    @Test fun ordinaryTextStays() {
        assertEquals("nazwa_pliku.txt", c("nazwa_pliku.txt"))
        assertEquals("C^", c("C^"))
        assertEquals("_x ", c("_x "))          // brak podstawy przed znakiem
        assertEquals("a_{xyz}", c("a_{xyz}"))  // y nie ma indeksu dolnego w Unicode — zostaje jak było
        assertEquals("zwykły tekst", c("zwykły tekst"))
    }
}

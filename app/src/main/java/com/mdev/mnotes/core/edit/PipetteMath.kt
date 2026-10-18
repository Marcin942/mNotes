package com.mdev.mnotes.core.edit

import com.mdev.mnotes.core.model.Element
import com.mdev.mnotes.core.model.FillEl
import com.mdev.mnotes.core.model.PenKind
import com.mdev.mnotes.core.model.Stroke

/** Pipeta w trybie „pierwotny kolor”: czysta logika wyboru obiektu pod punktem. */
object PipetteMath {

    private fun isHighlight(e: Element) = (e is Stroke && e.pen == PenKind.HIGHLIGHTER) || (e is FillEl && e.hl)

    /**
     * Kolor zapisany w obiekcie pod punktem (x, y) strony; null, gdy nie leży tam kreska ani wypełnienie.
     * Zakreślacz ma pierwszeństwo przed pismem: leży pod albo nad tekstem, ale to jego barwę chce pobrać ktoś,
     * kto wskazuje zakreślone miejsce (pismo pod nim dałoby np. biel albo czerń). Poza zakreślaczem wygrywa
     * obiekt leżący najwyżej.
     */
    fun originalColor(elements: List<Element>, x: Float, y: Float, r: Float): Int? {
        var top: Element? = null
        var hl: Element? = null
        for (i in elements.indices.reversed()) {
            val e = elements[i]
            if (e !is Stroke && e !is FillEl) continue
            if (!e.bounds.inflate(r + 1f).contains(x, y) || !EraseMath.hitsElement(e, x, y, r)) continue
            if (top == null) top = e
            if (hl == null && isHighlight(e)) { hl = e; break }
        }
        val pick = hl ?: top
        val c = when (pick) { is Stroke -> pick.color; is FillEl -> pick.color; else -> return null }
        return c or 0xFF000000.toInt()
    }
}

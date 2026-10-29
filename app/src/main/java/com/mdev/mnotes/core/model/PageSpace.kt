package com.mdev.mnotes.core.model

import com.mdev.mnotes.core.geom.Affine
import kotlin.math.max
import kotlin.math.min

/**
 * Wstawianie / usuwanie odstępu pionowego (EDT-065): wszystko poniżej poziomej linii na wysokości `y` przesuwa się
 * o `dy` (dodatnie = wstawia miejsce, ujemne = usuwa puste miejsce). Zablokowane elementy się nie ruszają.
 */
object PageSpace {
    /** Elementy do przesunięcia: niezablokowane, ze środkiem obwiedni na linii lub poniżej niej. */
    fun movers(content: PageContent, y: Float): List<Element> =
        content.elements.filter { !it.locked && it.bounds.cy >= y }

    /**
     * Ogranicza `dy` tak, by przesuwane elementy nie wyszły poza stronę wysokości `pageH` ani nie najechały na
     * elementy pozostające nad linią (usuwać można tylko puste miejsce). Zwraca 0, gdy nic nie leży pod linią.
     */
    fun clamp(content: PageContent, pageH: Float, y: Float, dy: Float): Float {
        val mv = movers(content, y)
        if (mv.isEmpty()) return 0f
        var top = Float.MAX_VALUE; var bottom = -Float.MAX_VALUE
        for (e in mv) { top = min(top, e.bounds.t); bottom = max(bottom, e.bounds.b) }
        if (dy >= 0f) return min(dy, max(0f, pageH - bottom))
        // Zablokowane (tło strony, przypięty obraz) nie są przeszkodą, tak jak nie są przesuwane: obraz tła na całą
        // stronę ma środek w połowie wysokości i dół na krawędzi, więc poniżej połowy blokował usuwanie odstępu.
        var floor = 0f
        for (e in content.elements) if (!e.locked && e.bounds.cy < y) floor = max(floor, e.bounds.b)
        return max(dy, -max(0f, top - floor))
    }

    /** Edycja przesuwająca elementy poniżej `y` o (już ograniczone) `dy`; pusta, gdy nie ma czego ruszać. */
    fun edit(content: PageContent, y: Float, dy: Float): ElementsEdit {
        if (dy == 0f) return ElementsEdit.EMPTY
        val mv = movers(content, y)
        if (mv.isEmpty()) return ElementsEdit.EMPTY
        return ElementsEdit.replace(content, mv.associate { it.id to it.transformed(Affine.translation(0f, dy)) })
    }
}

package com.mdev.mnotes.core.layout

/**
 * Auto-dodawanie stron przy pisaniu tekstem (F10): pole tekstowe, które wychodzi poza dół ostatniej strony, albo
 * dotknięcie narzędziem Tekst za ostatnią stroną zakłada nową stronę, zamiast gubić tekst poza kartką.
 */
object PageOverflow {
    /** Margines (pt): tekst kończący się tuż przy dolnej krawędzi też „wychodzi”, bo dalej nie ma już miejsca na wiersz. */
    const val MARGIN = 4f

    /** Czy pole z dolną krawędzią [bottom] na stronie wysokości [pageH] wymaga nowej strony; tylko na ostatniej stronie. */
    fun textOverflows(isLast: Boolean, bottom: Float, pageH: Float): Boolean = isLast && bottom > pageH - MARGIN

    /**
     * Czy punkt dokumentu leży za ostatnią stroną wzdłuż osi układu (pod nią w pionie, na prawo w poziomie), ale nie
     * dalej niż jedna strona — dalsze dotknięcie to raczej pomyłka.
     */
    fun beyondEnd(layout: DocLayout, x: Float, y: Float): Boolean {
        if (layout.count == 0) return false
        val r = layout.rect(layout.count - 1)
        return if (layout.mode == LayoutMode.VERTICAL) y > r.b && y < r.b + layout.gap + r.h && x >= r.l && x <= r.r
        else x > r.r && x < r.r + layout.gap + r.w && y >= r.t && y <= r.b
    }

    /** Punkt (x, y) dotknięcia za końcem przeliczony na współrzędne nowej strony o rozmiarze [w]×[h] (jak ostatnia). */
    fun onNewPage(layout: DocLayout, x: Float, y: Float, w: Float, h: Float): FloatArray {
        val r = layout.rect(layout.count - 1)
        val px: Float; val py: Float
        if (layout.mode == LayoutMode.VERTICAL) { px = x - r.l; py = y - (r.b + layout.gap) } else { px = x - (r.r + layout.gap); py = y - r.t }
        return floatArrayOf(px.coerceIn(MARGIN, w - MARGIN), py.coerceIn(MARGIN, h - MARGIN))
    }
}

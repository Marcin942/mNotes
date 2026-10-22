package com.mdev.mnotes.core.layout

import com.mdev.mnotes.core.model.PageSpec

/**
 * Układ stron w edytorze: strony jedna pod drugą, jedna obok drugiej albo po jednej na ekran.
 * Kody są częścią formatu zeszytu (`vm` w meta.json) i ustawień, więc nie zmieniaj ich wartości.
 */
object ViewMode {
    const val VERTICAL = 0
    const val HORIZONTAL = 1
    const val SINGLE = 2

    /** Dokument bez własnego wyboru: układ bierze się z ustawień domyślnych dla jego rodzaju. */
    const val DEFAULT = -1

    /** Fabryczne układy domyślne: zeszyty poziomo, dokumenty PDF pionowo. */
    const val DEFAULT_NOTEBOOK = HORIZONTAL
    const val DEFAULT_PDF = VERTICAL

    fun valid(mode: Int) = mode in VERTICAL..SINGLE

    /** Układ otwieranego dokumentu: własny wybór, a bez niego domyślny dla jego rodzaju. */
    fun resolve(own: Int, pdfDocument: Boolean, defaultNotebook: Int, defaultPdf: Int): Int {
        if (valid(own)) return own
        val def = if (pdfDocument) defaultPdf else defaultNotebook
        return if (valid(def)) def else if (pdfDocument) DEFAULT_PDF else DEFAULT_NOTEBOOK
    }

    /**
     * Czy strony wyglądają na zaimportowany PDF: każda ma tło z jednego pliku, w kolejności jego stron, i nic poza nim.
     * Służy tylko zeszytom zapisanym, zanim meta.json dostał znacznik rodzaju.
     */
    fun looksLikePdf(pages: List<PageSpec>): Boolean {
        if (pages.isEmpty()) return false
        val first = pages[0].pdf ?: return false
        return pages.withIndex().all { (i, p) ->
            val ref = p.pdf
            ref != null && ref.asset == first.asset && ref.page == i && p.template == com.mdev.mnotes.core.model.Template.BLANK
        }
    }
}

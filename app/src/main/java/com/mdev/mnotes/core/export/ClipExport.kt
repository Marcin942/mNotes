package com.mdev.mnotes.core.export

/**
 * Eksport do schowka systemu: jak każdy format trafia do schowka i jakim tłem wypełnić przezroczystość wycinka.
 * Logika czysta, testowana jednostkowo; samo kładzenie do schowka (FileProvider, ClipData) robi warstwa Androida.
 */
object ClipExport {
    /** Jak treść formatu trafia do schowka. */
    enum class Mode {
        /** Tekst (Markdown, TikZ): wkleja się w każdym polu tekstowym. */
        TEXT,
        /** Obraz na stronę (PNG, SVG): każda strona osobnym elementem schowka, wkleja się jak zdjęcie. */
        IMAGE_PER_PAGE,
        /** Jeden plik (PDF, .mnotes, archiwum ZIP): wklejają go aplikacje przyjmujące pliki (poczta, menedżer plików). */
        FILE,
    }

    /**
     * Najwięcej stron jako osobne obrazy. Dalej schowek dostaje jedno archiwum ZIP: dziesiątki zdjęć naraz i tak
     * wkleja mało która aplikacja, a każdy obraz to pełny render strony.
     */
    const val MAX_IMAGES = 20

    /**
     * Tryb dla formatu: [text] — format tekstowy, [perPageImage] — format „jedna strona = jeden obraz” (PNG, SVG);
     * [pages] — liczba eksportowanych stron, [split] — osobne pliki wg zakładek (wtedy zawsze archiwum).
     */
    fun mode(text: Boolean, perPageImage: Boolean, pages: Int, split: Boolean): Mode = when {
        text && !split -> Mode.TEXT
        perPageImage && !split && pages in 1..MAX_IMAGES -> Mode.IMAGE_PER_PAGE
        else -> Mode.FILE
    }

    /** Tło wycinka przy kopiowaniu do schowka: przezroczyste (PNG z alfą), czarne albo białe. */
    const val BG_NONE = 0
    const val BG_BLACK = 1
    const val BG_WHITE = 2

    /** Kolor tła (ARGB, nieprzezroczysty) dla ustawienia [mode]; null — zostaje przezroczystość. */
    fun background(mode: Int): Int? = when (mode) {
        BG_BLACK -> 0xFF000000.toInt()
        BG_WHITE -> 0xFFFFFFFF.toInt()
        else -> null
    }

    /**
     * Piksel [argb] (niepremnożony, jak z `Bitmap.getPixels`) położony na nieprzezroczystym tle [bg]: zwykłe
     * „source over”, wynik nieprzezroczysty. Brzeg lassa (krycie pośrednie) miesza się z tłem bez ząbków.
     */
    fun over(argb: Int, bg: Int): Int {
        val a = argb ushr 24
        if (a == 255) return argb
        if (a == 0) return bg or (0xFF shl 24)
        fun ch(s: Int) = ((argb shr s and 255) * a + (bg shr s and 255) * (255 - a) + 127) / 255
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
}

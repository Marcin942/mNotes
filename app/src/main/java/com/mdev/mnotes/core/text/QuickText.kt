package com.mdev.mnotes.core.text

/**
 * Prostsze wstawianie tekstu (3c-A12): krótka listwa okna tekstu (rozmiar „A−/A+”, ostatnie kolory) i decyzja, co
 * zrobić z tekstem, gdy okno zamknie się stuknięciem obok. Logika czysta — testowana jednostkowo.
 */
object QuickText {
    /** Typowe rozmiary w punktach; „A−/A+” skacze po nich zamiast po 1 pt. */
    val SIZES = floatArrayOf(8f, 10f, 12f, 14f, 16f, 18f, 20f, 24f, 28f, 32f, 40f, 48f, 64f, 80f, 96f)

    /** Następny rozmiar z [SIZES] w kierunku [dir] (+1 albo −1); rozmiar spoza listy idzie do najbliższego w tę stronę. */
    fun step(size: Float, dir: Int): Float =
        if (dir > 0) SIZES.firstOrNull { it > size + 0.01f } ?: SIZES.last()
        else SIZES.lastOrNull { it < size - 0.01f } ?: SIZES.first()

    /** Kolory na listwie: bieżący na początku, potem ostatnio używane bez powtórzeń, razem najwyżej [n]. */
    fun swatches(current: Int, recent: List<Int>, n: Int = 4): List<Int> =
        (listOf(current) + recent).distinct().take(n)

    /**
     * Stuknięcie poza okno: true = zapisz jak „Gotowe”. Nowy tekst zapisuje się, gdy coś wpisano; istniejący, gdy
     * zmieniono treść albo wygląd i nie został pusty (wyczyszczenie pola do zera usuwa tekst tylko przez „Gotowe”,
     * żeby przypadkowe stuknięcie obok niczego nie skasowało).
     */
    fun keepOnDismiss(isNew: Boolean, original: String, text: String, styleChanged: Boolean): Boolean = when {
        text.isBlank() -> false
        isNew -> true
        else -> text != original || styleChanged
    }
}

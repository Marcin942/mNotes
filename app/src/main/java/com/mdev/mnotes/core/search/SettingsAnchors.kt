package com.mdev.mnotes.core.search

/**
 * 3c-A17b: „Ustawienia tej funkcji” — przytrzymany przycisk paska otwiera Ustawienia od razu w miejscu, które go dotyczy.
 * Kotwica to zapytanie wyszukiwarki ustawień (3c-A6): wyniki pokazują dokładnie te wiersze, a test pilnuje, że
 * oczekiwany wiersz jest wśród pierwszych. Brak kotwicy — funkcja nie ma własnych ustawień (np. taśma, odstęp).
 */
object SettingsAnchors {
    /** Zapytanie do wyszukiwarki ustawień dla przycisku paska [barId] (identyfikatory jak w `BarConfig`). */
    fun of(barId: String): String? = when (barId) {
        "undo", "redo" -> "cofnij dotknięciem dwóch palców"
        "ballpoint", "fountain", "pencil", "brush", "highlighter" -> "wygładzanie"
        "eraser" -> "tryb gumki"
        "painter" -> "pędzel formatu"
        "fill" -> "wiaderko szpary"
        "pipette" -> "pobrany kolor paleta"
        "lasso" -> "kształt lassa"
        "text" -> "grecki indeksy w tekście"
        "laser" -> "kolor śladu lasera"
        "hand" -> "ponowne dotknięcie ręki wraca do pisania"
        "curve" -> "wygładzić w krzywą"
        "ruler", "protractor" -> "przyciągaj do równych kątów"
        "geometry" -> "przyczep do figur"
        "snip" -> "kształt wycinka"
        else -> null
    }
}

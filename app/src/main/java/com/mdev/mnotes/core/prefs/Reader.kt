package com.mdev.mnotes.core.prefs

/**
 * Tryb „do odczytu” (3c-A9): zeszyt tylko do oglądania, pod mały ekran. Palec i rysik wyłącznie przewijają i powiększają,
 * paski są schowane, strona dopasowana do szerokości. Włącza go ustawienie (także z profilu „Do odczytu”), sam telefon
 * (do wyłączenia) albo zeszyt będący kopią z folderu wymiany (3c-A8), której nie da się edytować.
 */
object Reader {
    const val KEY = "view.reader"
    const val AUTO_KEY = "view.reader_auto"

    /** Najmniejsza szerokość ekranu (dp), od której urządzenie to już tablet, nie telefon (próg Androida dla sw600dp). */
    const val PHONE_BELOW_DP = 600

    fun isPhone(smallestWidthDp: Int) = smallestWidthDp in 1 until PHONE_BELOW_DP

    /** Czy zeszyt otwiera się w trybie do odczytu. [mirror] — kopia z folderu wymiany. */
    fun onOpen(on: Boolean, auto: Boolean, smallestWidthDp: Int, mirror: Boolean): Boolean =
        on || mirror || (auto && isPhone(smallestWidthDp))

    /** Stan edytora w trybie: blokada edycji, bez pasków; [editable] — czy wolno go opuścić do edycji (kopia z folderu nie). */
    data class State(val readOnly: Boolean, val chrome: Boolean, val editable: Boolean)

    fun enter(mirror: Boolean) = State(readOnly = true, chrome = false, editable = !mirror)

    /** „Edytuj”: wraca paski i edycję; kopia z folderu wymiany zostaje zablokowana (zmiany i tak zastąpi nowsza wersja). */
    fun leave(mirror: Boolean) = State(readOnly = mirror, chrome = true, editable = !mirror)
}

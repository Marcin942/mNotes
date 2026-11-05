package com.mdev.mnotes.core.edit

/**
 * LP-2: co robi gotowy wycinek. Domyślnie pyta (pasek „Wstaw / Kopiuj / Udostępnij” przy wycinku), bo jeden wycinek
 * idzie do notatek, a następny do czatu; pozostałe tryby robią jedną rzecz od razu, bez paska.
 */
object SnipMode {
    const val ASK = 0
    const val INSERT = 1
    const val CLIPBOARD = 2
    const val SHARE = 3
    val ALL = intArrayOf(ASK, INSERT, CLIPBOARD, SHARE)

    /** Wartość z ustawień: nieznana (np. z nowszej wersji w imporcie ustawień) wraca do pytania. */
    fun of(v: Int): Int = if (v in ALL) v else ASK

    /** Dawny przełącznik „Wycinek od razu do schowka” (`snip.clipboard`) na tryb. */
    fun fromLegacy(clipboard: Boolean): Int = if (clipboard) CLIPBOARD else ASK

    /** Polecenie „Wycinek od razu do schowka” (skrót i menu) przełącza schowek ↔ pytanie; z innego trybu idzie do schowka. */
    fun toggleClipboard(v: Int): Int = if (of(v) == CLIPBOARD) ASK else CLIPBOARD

    /** Pasek przy wycinku tylko przy pytaniu; inne tryby kończą wycinek same. */
    fun showsBar(v: Int): Boolean = of(v) == ASK

    /**
     * Dokąd wstawić od razu (tryb [INSERT]): dokument, w którym ostatnio pisano, spośród tych do zapisu, tak jak
     * domyślny przycisk „Wstaw do:” na pasku; źródło, gdy innego nie ma. Indeks w [rows] albo -1 (wszystkie do odczytu).
     */
    fun insertTarget(src: Int, writable: List<Boolean>, lastEdit: List<Long>): Int {
        val others = writable.indices.filter { it != src && writable[it] }.maxByOrNull { lastEdit[it] }
        return others ?: if (writable.getOrElse(src) { false }) src else -1
    }
}

package com.mdev.mnotes.core.edit

/**
 * Seria zmian z jednego przeciągnięcia suwaka (kolor, grubość) idzie do historii cofania jako jeden krok, a gdy
 * przeciąganie trwa dłużej niż [windowMs], co [windowMs] zaczyna się kolejny krok. Bez tego każda klatka ruchu
 * palca była osobnym krokiem i cofnięcie jednej zmiany koloru wymagało kilkunastu stuknięć.
 */
class EditBurst(private val windowMs: Long = 2000L) {
    private var startAt: Long = -1L

    val active: Boolean get() = startAt >= 0L

    /** Zmiana w chwili [now]; true, gdy trwająca seria ma się zamknąć osobnym krokiem przed tą zmianą. */
    fun change(now: Long): Boolean {
        val split = active && now - startAt >= windowMs
        if (!active || split) startAt = now
        return split
    }

    /** Koniec serii (puszczenie suwaka, inna edycja); true, gdy jakaś seria trwała. */
    fun end(): Boolean { val was = active; startAt = -1L; return was }
}

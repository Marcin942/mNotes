package com.mdev.mnotes.core.prefs

/**
 * 3c-A15: język aplikacji z Ustawień (0 systemowy, 1 polski, 2 angielski) a systemowy „Język aplikacji” Androida 13+.
 * Oba miejsca mają pokazywać to samo, więc przy starcie i przy każdej zmianie trzeba ustalić, która strona zmieniła
 * się ostatnio. Pomaga zapamiętany tag z ostatniego uzgodnienia: system inny niż wtedy — zmieniono go w ustawieniach
 * systemu; inaczej rozstrzyga ustawienie aplikacji.
 */
object AppLanguage {
    /** Tag dla systemu: "" = jak w systemie. */
    fun tag(mode: Int): String = when (mode) { 1 -> "pl"; 2 -> "en"; else -> "" }

    /** Tryb z listy tagów systemu („pl-PL,en”); liczy się pierwszy, nieobsługiwany język = systemowy. */
    fun mode(tags: String): Int = when (tags.substringBefore(',').trim().substringBefore('-').substringBefore('_').lowercase()) {
        "pl" -> 1
        "en" -> 2
        else -> 0
    }

    /** Wynik uzgodnienia: tryb dla aplikacji, tag do ustawienia w systemie (null — bez zmian) i tag do zapamiętania. */
    data class Decision(val mode: Int, val push: String?, val last: String)

    /**
     * [app] — tryb z Ustawień, [system] — bieżące tagi systemu dla aplikacji, [last] — tag z poprzedniego uzgodnienia
     * (null: nigdy nie uzgadniano, np. pierwsze uruchomienie po aktualizacji).
     */
    fun reconcile(app: Int, system: String, last: String?): Decision {
        val sys = tag(mode(system))
        val mine = tag(app)
        return when {
            sys == mine -> Decision(app, null, sys)
            // System zmieniony od ostatniego razu, a przy pierwszym uzgodnieniu — wybrany w systemie przed otwarciem aplikacji.
            (last == null && sys.isNotEmpty()) || (last != null && sys != last) -> Decision(mode(sys), null, sys)
            else -> Decision(app, mine, mine)
        }
    }
}

package com.mdev.mnotes.core.layout

/**
 * Listwa przy narzędziach pomocniczych — gumce, lassie i laserze (2b-A2, uwagi użytkownika). Dwa tryby do wyboru w
 * Ustawieniach:
 *  - [WRITE_AND_MENU] (domyślny): listwa zostaje przy pisaniu (kolory, grubości) — dotknięcie koloru wraca do ostatniego
 *    narzędzia pisania — a opcje gumki/lassa/lasera wyskakują w małym menu przy ikonie narzędzia, jak „Pobierz format”
 *    przy pędzlu;
 *  - [TOOL_OPTIONS]: opcje narzędzia na listwie (jak w F7) i przycisk „wróć do pisania” z ostatnim narzędziem pisania.
 * Menu jest konfigurowalne: dla których narzędzi się pokazuje i które grupy opcji zawiera ([Config]).
 */
object HelperStrip {
    const val WRITE_AND_MENU = 0
    const val TOOL_OPTIONS = 1

    /** Klucz dawnego przełącznika F7 (wersje testowe): opcje gumki, lassa i lasera na listwie. */
    const val OLD_TOOL_STRIP = "toolbar.tool_strip"

    /**
     * 2c-A15: tryb listwy z dawnego [OLD_TOOL_STRIP]: włączony — [TOOL_OPTIONS], wyłączony — [WRITE_AND_MENU]. Null: nie
     * ma czego przenosić albo nowe ustawienie już jest zapisane (wygrywa).
     */
    fun fromToolStrip(old: Boolean?, hasNew: Boolean): Int? = if (old == null || hasNew) null else if (old) TOOL_OPTIONS else WRITE_AND_MENU

    enum class Helper { ERASER, LASSO, LASER, SNIP }

    /** Grupy opcji w menu (i na listwie w trybie [TOOL_OPTIONS] — tam zawsze wszystkie). */
    enum class Item(val helper: Helper) {
        ERASER_MODES(Helper.ERASER), ERASER_SIZES(Helper.ERASER), ERASER_HIGHLIGHTER(Helper.ERASER),
        LASSO_SHAPE(Helper.LASSO), LASSO_MODE(Helper.LASSO),
        LASER_COLORS(Helper.LASER),
        SNIP_SHAPE(Helper.SNIP), SNIP_CAPTION(Helper.SNIP),
    }

    /**
     * Co jest wyłączone w menu: narzędzia (cała pozycja) i grupy opcji. Zapis w preferencjach jako lista wyłączonych
     * nazw po przecinku, więc pusty tekst = wszystko włączone (rozsądne domyślne) i nowe grupy w przyszłości są włączone.
     */
    class Config private constructor(private val off: Set<String>) {
        fun menuFor(h: Helper): Boolean = h.name !in off
        fun shows(i: Item): Boolean = i.name !in off
        fun withMenu(h: Helper, on: Boolean) = Config(if (on) off - h.name else off + h.name)
        fun withItem(i: Item, on: Boolean) = Config(if (on) off - i.name else off + i.name)
        fun encode(): String = off.sorted().joinToString(",")

        companion object {
            val DEFAULT = Config(emptySet())
            private val KNOWN = Helper.entries.map { it.name }.toSet() + Item.entries.map { it.name }
            fun parse(s: String): Config = Config(s.split(',').map { it.trim() }.filter { it in KNOWN }.toSet())
        }
    }

    /** Czy listwa pokazuje opcje narzędzia pomocniczego [helper] (null = w ręku narzędzie pisania). */
    fun stripShowsToolOptions(mode: Int, helper: Helper?): Boolean = mode == TOOL_OPTIONS && helper != null

    /** Czy pokazać wyskakujące menu przy ikonie [helper]; nie, gdy jego panel jest już otwarty. */
    fun menuVisible(mode: Int, helper: Helper?, cfg: Config, panelOpen: Boolean): Boolean =
        mode == WRITE_AND_MENU && helper != null && cfg.menuFor(helper) && !panelOpen && items(helper, cfg).isNotEmpty()

    /** Grupy opcji w menu narzędzia [helper], w stałej kolejności. */
    fun items(helper: Helper, cfg: Config): List<Item> = Item.entries.filter { it.helper == helper && cfg.shows(it) }
}

/**
 * Ostatnia wartość spełniająca [accepts] — tu: ostatnie narzędzie pisania (pióro albo krzywa), do którego wraca kolor
 * dotknięty na listwie i przycisk „wróć do pisania” (2b-A2, wcześniej zawsze pióro, także po krzywej).
 */
class LastOf<T>(initial: T, private val accepts: (T) -> Boolean) {
    var value: T = initial
        private set

    /** Zapamiętuje [t], jeśli to narzędzie pisania; inne (gumka, lasso…) nic nie zmieniają. */
    fun note(t: T) { if (accepts(t)) value = t }
}

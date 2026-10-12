package com.mdev.mnotes.core.text

/** Działania na nazwach tagów, wspólne dla zeszytów i tabeli kolorów (`tags.json`). */
object TagNames {
    /** Nazwa wpisana przez użytkownika: jedna linia, bez wiodącego `#`. */
    fun clean(raw: String): String = NameText.oneLine(raw).removePrefix("#").trim()

    /** Lista tagów zeszytu po zmianie nazwy [old] na [new]; tag zostaje na swoim miejscu, a powtórka (zlanie z istniejącym) znika. */
    fun renamed(tags: List<String>, old: String, new: String): List<String> = tags.map { if (it == old) new else it }.distinct()

    /** Kolory po zmianie nazwy: tag [new] zachowuje własny kolor, jeśli już istniał (zlanie), inaczej dziedziczy kolor po [old]. */
    fun renamedColors(colors: Map<String, Int>, old: String, new: String): Map<String, Int> {
        val c = colors[new] ?: colors[old]
        val rest = colors - old - new
        return if (c == null) rest else rest + (new to c)
    }
}

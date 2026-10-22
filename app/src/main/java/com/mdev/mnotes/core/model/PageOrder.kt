package com.mdev.mnotes.core.model

/** Logika panelu stron: przestawianie przeciąganiem i podpisy zakładek. */
object PageOrder {
    /**
     * Gdzie ma stanąć przeciągana miniatura o indeksie [index], której środek jest w [mid].
     * Zamiana z sąsiadem następuje dopiero po minięciu jego środka, więc miniatury różnej wysokości
     * (strona pionowa obok poziomej) nie przeskakują tam i z powrotem.
     */
    fun step(index: Int, mid: Float, prevCenter: Float?, nextCenter: Float?): Int = when {
        nextCenter != null && mid > nextCenter -> index + 1
        prevCenter != null && mid < prevCenter -> index - 1
        else -> index
    }

    fun <T> move(list: MutableList<T>, from: Int, to: Int) {
        if (from == to || from !in list.indices || to !in list.indices) return
        list.add(to, list.removeAt(from))
    }

    private val autoTitle = Regex("""(Strona|Page) \d+""")

    /**
     * Podpis zakładki strony [index]. Zakładka dostaje przy dodaniu tytuł „Strona N”, który po przestawieniu
     * stron kłamie, więc taki tytuł (i pusty) zastępujemy bieżącym numerem; własny tytuł zostaje.
     */
    fun bookmarkTitle(title: String, index: Int, word: String): String =
        if (title.isBlank() || autoTitle.matches(title.trim())) "$word ${index + 1}" else title
}

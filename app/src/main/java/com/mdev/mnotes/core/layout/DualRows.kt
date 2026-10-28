package com.mdev.mnotes.core.layout

/** Wybór dokumentu do dołożenia na stanowisko (F4 → 2b-A7); geometria stanowiska jest w [Workspace] (2d-A1). */
object DualRows {
    /** Zeszyty do dołożenia na stanowisko: bez tych, które już są, ostatnio otwierane najpierw. */
    fun <T> candidates(all: List<T>, id: (T) -> String, opened: (T) -> Long, exclude: Set<String>): List<T> =
        all.filter { id(it) !in exclude }.sortedByDescending(opened)
}

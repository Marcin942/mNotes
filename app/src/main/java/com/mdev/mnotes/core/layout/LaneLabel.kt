package com.mdev.mnotes.core.layout

/**
 * Nagłówek pasa stanowiska (3c-A7): ikonka rodzaju dokumentu i jego nazwa, żeby przy dwóch–trzech dokumentach od razu
 * było widać, który jest który. Zeszyt pokazuje miniaturę swojej okładki, PDF — okładkę ze znakiem dokumentu; dokument
 * do odczytu dostaje kłódkę. Logika czysta (tekst, skrót nazwy, opis dla czytnika ekranu) — testowana jednostkowo.
 */
object LaneLabel {
    enum class Kind { NOTEBOOK, PDF }

    /** Najwęższy nagłówek (dp): ikonka + kilka liter; węższy pas pokazuje samą ikonkę. */
    const val MIN_DP = 72
    /** Najszerszy nagłówek (dp) — dłuższa nazwa jest skracana wielokropkiem. */
    const val MAX_DP = 260

    data class Label(val kind: Kind, val title: String, val readOnly: Boolean, val showTitle: Boolean, val maxDp: Int, val description: String)

    /**
     * Nagłówek dokumentu [title] w pasie szerokim na [laneDp]: nazwa bez rozszerzenia „.pdf” (ikonka już mówi, że to PDF),
     * pusta → „Bez tytułu”; szerokość najwyżej [MAX_DP] i nie więcej niż pas minus marginesy.
     */
    fun of(title: String, pdf: Boolean, readOnly: Boolean, laneDp: Float, polish: Boolean = true): Label {
        val kind = if (pdf) Kind.PDF else Kind.NOTEBOOK
        var t = title.trim()
        if (pdf && t.endsWith(".pdf", ignoreCase = true) && t.length > 4) t = t.dropLast(4).trimEnd()
        if (t.isEmpty()) t = if (polish) "Bez tytułu" else "Untitled"
        val room = (laneDp - 16f).toInt()
        val what = when {
            pdf && polish -> "dokument PDF"
            pdf -> "PDF document"
            polish -> "zeszyt"
            else -> "notebook"
        }
        val ro = if (!readOnly) "" else if (polish) ", tylko do odczytu" else ", read-only"
        return Label(kind, t, readOnly, room >= MIN_DP, room.coerceIn(0, MAX_DP), "$t — $what$ro")
    }
}

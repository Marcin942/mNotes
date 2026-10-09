package com.mdev.mnotes.core.text

/**
 * Nazwy (zeszytów, folderów, tagów) są jednowierszowe, ale IME i schowek potrafią wstawić znak nowej linii mimo
 * `singleLine` (wklejenie, pismo S Pen). Logika czysta — testowana jednostkowo.
 */
object NameText {
    private val breaks = "\r\n\u0085  "
    private val breakRun = Regex("\\s*[$breaks]+\\s*")

    /** Podmienia każdy znak łamania linii na spację, zachowując długość tekstu (kursor w polu się nie przesuwa). */
    fun noBreaks(s: String): String = if (s.none { it in breaks }) s else s.map { if (it in breaks) ' ' else it }.joinToString("")

    /** Postać do zapisania: łamanie linii (z otaczającymi spacjami) staje się jedną spacją, brzegi obcięte. */
    fun oneLine(s: String): String = s.replace(breakRun, " ").trim()
}

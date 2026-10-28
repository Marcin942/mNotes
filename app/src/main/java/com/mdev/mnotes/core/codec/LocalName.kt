package com.mdev.mnotes.core.codec

/**
 * Nazwy stron i zasobów pochodzą z pliku zeszytu (`meta.json`, rekordy obrazów), a ten może przyjść od obcej osoby:
 * zanim staną się częścią ścieżki w katalogu zeszytu, nie mogą wskazywać na inny katalog (`../`, `/`, `\`).
 */
object LocalName {
    /** Czy [name] to zwykła nazwa jednego pliku. */
    fun isPlain(name: String): Boolean =
        name.isNotEmpty() && name != "." && name != ".." && name.none { it == '/' || it == '\\' || it == '\u0000' }

    /** [name] bez zmian albo nazwa zastępcza, która nie istnieje i zostaje w katalogu zeszytu. */
    fun plain(name: String): String = if (isPlain(name)) name else "invalid-" + Integer.toHexString(name.hashCode())
}

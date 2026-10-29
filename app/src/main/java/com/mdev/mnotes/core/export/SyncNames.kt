package com.mdev.mnotes.core.export

/**
 * Nazwy plików w folderze synchronizacji (lustro biblioteki). Folder jest kopią zapasową, więc aplikacja niczego w nim
 * nie zastępuje: plik, który w poprzednich przebiegach należał do innego zeszytu, nie dostaje się innemu. 3c-A16: nazwy
 * były przydzielane od nowa w każdym przebiegu — po przeniesieniu do kosza starszego z dwóch zeszytów „Matma” młodszy
 * dostawał „Matma.mnotes” i nadpisywał jedyną zewnętrzną kopię tamtego.
 */
object SyncNames {
    /** Zeszyt do przydziału: [dir] — ścieżka folderów („A/B”, "" dla głównego), [title] — tytuł już oczyszczony na nazwę pliku. */
    class Book(val id: String, val dir: String, val title: String, val created: Long)

    /** Ścieżka pliku w znaczniku stanu i w [owners]: „A/B/Nazwa.ext”, dla głównego folderu „/Nazwa.ext”. */
    fun path(dir: String, base: String, ext: String) = "$dir/$base.$ext"

    /**
     * Nazwa bazowa (bez rozszerzenia) dla każdego zeszytu. [owners] — ścieżka pliku (małe litery) → zeszyt, który go
     * zapisał w poprzednich przebiegach; [exts] — rozszerzenia zapisywanych formatów. Powtórzony tytuł albo nazwa
     * cudzego pliku dostaje końcówkę z identyfikatora.
     */
    fun assign(books: List<Book>, owners: Map<String, String>, exts: List<String>): Map<String, String> {
        val taken = HashSet<String>()
        val out = HashMap<String, String>()
        fun free(b: Book, base: String) = "${b.dir}/${base.lowercase()}" !in taken &&
            exts.none { e -> owners[path(b.dir, base, e).lowercase()].let { it != null && it != b.id } }
        for (b in books.sortedBy { it.created }) {
            var base = b.title
            if (!free(b, base)) {
                base = "${b.title}-${b.id.take(4)}"
                var n = 2
                while (!free(b, base) && n < 100) base = "${b.title}-${b.id.take(4)}-${n++}"
            }
            taken.add("${b.dir}/${base.lowercase()}")
            out[b.id] = base
        }
        return out
    }

    /** Właściciele plików ze stanu poprzednich przebiegów: klucz „id|ext” → znacznik „…|ścieżka”. */
    fun owners(items: Map<String, String>): Map<String, String> =
        items.entries.associate { (k, stamp) -> stamp.substringAfterLast('|').lowercase() to k.substringBefore('|') }
}

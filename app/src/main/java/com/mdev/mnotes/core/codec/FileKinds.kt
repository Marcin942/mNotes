package com.mdev.mnotes.core.codec

/** Rodzaj pliku przyjmowanego przez import. */
enum class FileKind { PDF, IMAGE, MNOTES, SDOCX, UNKNOWN }

/**
 * Rozpoznaje plik po zawartości, a nazwa i typ MIME są tylko podpowiedzią. Menedżery plików i komunikatory często
 * podają `application/octet-stream` i adres bez rozszerzenia (`content://…/msf%3A123`), więc samo rozszerzenie
 * nie wystarcza (to właśnie psuło otwieranie `.mnotes`).
 */
object FileKinds {
    private fun startsWith(head: ByteArray, vararg sig: Int): Boolean =
        head.size >= sig.size && sig.indices.all { (head[it].toInt() and 0xff) == sig[it] }

    fun isZip(head: ByteArray) = startsWith(head, 0x50, 0x4b, 0x03, 0x04)

    /** PDF może mieć przed nagłówkiem do 1024 B śmieci, więc szukamy sygnatury w pierwszym kilobajcie. */
    fun isPdf(head: ByteArray): Boolean {
        val n = minOf(head.size, 1024) - 4
        for (i in 0..n) if (head[i] == '%'.code.toByte() && head[i + 1] == 'P'.code.toByte() && head[i + 2] == 'D'.code.toByte() && head[i + 3] == 'F'.code.toByte()) return true
        return false
    }

    fun isImage(head: ByteArray): Boolean =
        startsWith(head, 0xff, 0xd8, 0xff) || startsWith(head, 0x89, 0x50, 0x4e, 0x47) || startsWith(head, 0x47, 0x49, 0x46, 0x38) ||
            (startsWith(head, 0x52, 0x49, 0x46, 0x46) && head.size >= 12 && String(head, 8, 4, Charsets.ISO_8859_1) == "WEBP")

    /**
     * @param head pierwsze bajty pliku (zalecane 1024)
     * @param zipNames nazwy wpisów ZIP-a; wołane tylko dla archiwów, może zwrócić część (wystarczy do pierwszych kilku wpisów)
     */
    fun classify(name: String, mime: String, head: ByteArray, zipNames: () -> Collection<String>): FileKind {
        val low = name.lowercase()
        val byName = when {
            low.endsWith(".pdf") -> FileKind.PDF
            low.endsWith(".mnotes") -> FileKind.MNOTES
            low.endsWith(".sdocx") -> FileKind.SDOCX
            else -> FileKind.UNKNOWN
        }
        if (head.isNotEmpty()) {
            // ZIP najpierw: .mnotes trzyma tło PDF bez kompresji, więc „%PDF” bywa w pierwszym kilobajcie archiwum
            // i zeszyt importował się jako sam PDF (jedna strona zamiast wszystkich).
            if (isZip(head)) {
                val names = runCatching { zipNames() }.getOrDefault(emptyList())
                return when {
                    // Także przepakowane archiwum z folderem na wierzchu („Zeszyt/meta.json”).
                    MnotesArchive.rootOf(names) != null -> FileKind.MNOTES
                    "note.note" in names -> FileKind.SDOCX
                    // Archiwum bez znanych wpisów: wierzymy nazwie (np. uszkodzony .mnotes ma dostać komunikat o błędzie, nie zniknąć).
                    byName == FileKind.MNOTES || byName == FileKind.SDOCX -> byName
                    else -> FileKind.UNKNOWN
                }
            }
            if (isPdf(head)) return FileKind.PDF
            if (isImage(head)) return FileKind.IMAGE
            return FileKind.UNKNOWN
        }
        return when {
            byName != FileKind.UNKNOWN -> byName
            mime == "application/pdf" -> FileKind.PDF
            mime == "application/x-mnotes" -> FileKind.MNOTES
            mime.startsWith("image/") -> FileKind.IMAGE
            else -> FileKind.UNKNOWN
        }
    }
}

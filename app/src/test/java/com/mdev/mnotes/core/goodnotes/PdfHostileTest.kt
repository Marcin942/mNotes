package com.mdev.mnotes.core.goodnotes

import org.junit.Test
import java.io.ByteArrayOutputStream

/** Wrogie PDF-y (audyt bezpieczeństwa, docs/BEZPIECZENSTWO.md): kilka KB nie może zająć wątku na minuty ani setek MB. */
class PdfHostileTest {

    /** PDF z jednym strumieniem xref o podanym słowniku (bez danych) i `startxref` wskazującym na niego. */
    private fun withXrefStream(dict: String): ByteArray {
        val out = ByteArrayOutputStream()
        fun w(s: String) = out.write(s.toByteArray(Charsets.ISO_8859_1))
        w("%PDF-1.5\n")
        val at = out.size()
        w("1 0 obj\n<</Type /XRef $dict /Length 0>>\nstream\n\nendstream\nendobj\n")
        w("startxref\n$at\n%%EOF\n")
        return out.toByteArray()
    }

    @Test(timeout = 5_000)
    fun xrefStreamWithZeroWidthRecordsDoesNotSpin() {
        // /W [0 0 0]: rekord ma 0 bajtów, więc kontrola końca danych nigdy nie przerywała pętli po /Index (2³¹ obrotów na parę)
        val pairs = (1..50).joinToString(" ") { "0 2147483647" }
        runCatching { PdfDocument(withXrefStream("/W [0 0 0] /Size 1 /Index [$pairs]")) }
        runCatching { GoodNotesConvert.detect(withXrefStream("/W [0 0 0] /Size 1 /Index [$pairs]")) }
    }
}

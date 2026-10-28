package com.mdev.mnotes.core.goodnotes

import com.mdev.mnotes.core.samsung.ConvertOptions
import com.mdev.mnotes.core.samsung.ImportMode
import org.junit.Assert.assertTrue
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

    /** Najprostszy PDF z GoodNotes (jak w GoodNotesConvertTest): jedna strona z podaną treścią. */
    private fun goodNotes(content: String): ByteArray {
        val bodies = listOf(
            "<</Type /Catalog /Pages 2 0 R>>",
            "<</Type /Pages /Kids [4 0 R] /Count 1>>",
            "<</Producer (GoodNotes 6)>>",
            "<</Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 5 0 R>>",
            "<</Length ${content.length}>>\nstream\n$content\nendstream",
        )
        val out = ByteArrayOutputStream()
        fun w(s: String) = out.write(s.toByteArray(Charsets.ISO_8859_1))
        w("%PDF-1.4\n")
        val off = IntArray(bodies.size)
        bodies.forEachIndexed { i, b -> off[i] = out.size(); w("${i + 1} 0 obj\n$b\nendobj\n") }
        val x = out.size()
        w("xref\n0 ${bodies.size + 1}\n0000000000 65535 f \n")
        for (o in off) w("%010d 00000 n \n".format(o))
        w("trailer\n<</Size ${bodies.size + 1} /Root 1 0 R /Info 3 0 R>>\nstartxref\n$x\n%%EOF\n")
        return out.toByteArray()
    }

    @Test(timeout = 30_000)
    fun hugeCurvesDoNotExhaustMemory() {
        // ~60 KB treści: 2000 krzywych o współrzędnych 1e9 dzieliło się do głębokości 16, po 2¹⁶ punktów każda (GB pamięci)
        val ink = "0 0 0 RG 2 w " + (1..2000).joinToString(" ") { "0 0 m 0 1000000000 1000000000 -1000000000 1000000000 0 c S" }
        val r = runCatching { GoodNotesConvert.convert(goodNotes(ink), "wrogi.pdf", ConvertOptions(ImportMode.STANDARD)) }
        // wyjątek (strona odrzucona) jest w porządku; błąd braku pamięci nie, a wynik nie może mieć setek milionów punktów
        assertTrue(r.exceptionOrNull().toString(), r.exceptionOrNull() == null || r.exceptionOrNull() is Exception)
        val points = r.getOrNull()?.pages?.sumOf { p -> p.elements.sumOf { (it as? com.mdev.mnotes.core.model.Stroke)?.count ?: 0 } } ?: 0
        assertTrue("$points", points < 4_000_000)
    }

    @Test(timeout = 5_000)
    fun predictorRowLongerThanDataIsNotAllocated() {
        val doc = PdfDocument(goodNotes("0 0 m 1 1 l S"))
        val s = PdfStream(PdfDict(linkedMapOf("DecodeParms" to PdfDict(linkedMapOf("Predictor" to 12.0, "Colors" to 32.0, "BitsPerComponent" to 16.0, "Columns" to 1048576.0)), "Filter" to PdfName("FlateDecode"))), java.util.zip.Deflater().let { d -> d.setInput(ByteArray(1)); d.finish(); val b = ByteArray(64); val n = d.deflate(b); d.end(); b.copyOf(n) })
        // wcześniej: 64 MB na wiersz z jednego bajtu
        assertTrue(doc.decodeStream(s).size <= 1)
    }
}

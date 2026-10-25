package com.mdev.mnotes.core.goodnotes

import com.mdev.mnotes.core.model.Stroke
import com.mdev.mnotes.core.samsung.ConvertOptions
import com.mdev.mnotes.core.samsung.ImportMode
import com.mdev.mnotes.core.samsung.Level
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GoodNotesConvertTest {
    /** Najprostszy poprawny PDF: strony 612×792 z podaną treścią, producent w /Info. */
    private fun pdf(producer: String, vararg contents: String, title: String? = null): ByteArray {
        val bodies = ArrayList<String>()
        val n = contents.size
        // 1 katalog, 2 drzewo stron, 3 info, 4 font, potem pary (strona, treść)
        bodies.add("<</Type /Catalog /Pages 2 0 R>>")
        bodies.add("<</Type /Pages /Kids [${(0 until n).joinToString(" ") { "${5 + 2 * it} 0 R" }}] /Count $n>>")
        bodies.add("<</Producer ($producer)" + (title?.let { " /Title ($it)" } ?: "") + ">>")
        bodies.add("<</Type /Font /Subtype /Type1 /BaseFont /Helvetica>>")
        for ((i, c) in contents.withIndex()) {
            bodies.add("<</Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources <</Font <</F1 4 0 R>>>> /Contents ${6 + 2 * i} 0 R>>")
            bodies.add("<</Length ${c.length}>>\nstream\n$c\nendstream")
        }
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

    private val ink = "0 0 0 RG 2 w 1 J 1 j 100 100 m 200 150 l 300 120 l S"
    private val text = "BT /F1 24 Tf 100 400 Td (Hello) Tj ET"

    @Test fun defaultTitleDropsExtensionAndCopySuffix() {
        assertEquals("Ćwiczenia", GoodNotesConvert.defaultTitle("Ćwiczenia.pdf"))
        assertEquals("Notatki", GoodNotesConvert.defaultTitle("/a/b/Notatki (2).pdf"))
        assertEquals("Notatka GoodNotes", GoodNotesConvert.defaultTitle(".pdf"))
    }

    @Test fun looksCompatibleFindsMarkerAcrossReadBoundary() {
        val pad = ByteArray(64 * 1024 - 5) { 'x'.code.toByte() }
        val withMarker = pad + "Quartz PDFContext".toByteArray()
        assertTrue(GoodNotesConvert.looksCompatible(withMarker.inputStream()))
        assertFalse(GoodNotesConvert.looksCompatible(pad.inputStream()))
    }

    @Test fun detectFollowsProducerAndNeverThrows() {
        assertTrue(GoodNotesConvert.detect(pdf("GoodNotes 6", ink)))
        assertFalse(GoodNotesConvert.detect(pdf("LibreOffice", ink)))
        // Quartz bez papieru na całą stronę to zwykły PDF z macOS-a, nie GoodNotes
        assertFalse(GoodNotesConvert.detect(pdf("Quartz PDFContext", ink)))
        assertFalse(GoodNotesConvert.detect(ByteArray(10)))
    }

    @Test fun foreignPdfBecomesPlainPdfBackground() {
        val note = GoodNotesConvert.convert(pdf("LibreOffice", ink, text), "obce.pdf", ConvertOptions(ImportMode.STANDARD))
        assertEquals(2, note.pages.size)
        assertTrue(note.pages.all { it.pdf != null && it.elements.isEmpty() })
        assertEquals(0, note.strokeCount)
        assertTrue(note.report.any { it.level == Level.INFO })
        val written = PdfDocument(note.pdfs.getValue(0))
        assertEquals(2, written.pages.size)
    }

    @Test fun inkBecomesNativeStrokeWithoutPdf() {
        val note = GoodNotesConvert.convert(pdf("GoodNotes", ink, title = "Mój zeszyt"), "x.pdf", ConvertOptions(ImportMode.STANDARD))
        assertEquals("Mój zeszyt", note.title)
        assertEquals(1, note.pages.size)
        assertTrue(note.pdfs.isEmpty())
        assertNull(note.pages[0].pdf)
        assertEquals(1, note.strokeCount)
        assertTrue(note.pages[0].elements.any { it is Stroke })
    }

    @Test fun visibleTextStaysInPdfWhileInkStaysEditable() {
        val note = GoodNotesConvert.convert(pdf("GoodNotes", "$ink\n$text"), "x.pdf", ConvertOptions(ImportMode.ORIGINAL))
        val page = note.pages.single()
        assertNotNull(page.pdf)
        assertEquals(1, page.elements.count { it is Stroke })
        assertTrue(note.report.any { it.level == Level.WARN })
        val written = PdfDocument(note.pdfs.getValue(0))
        val content = String(written.contents(written.pages.single()), Charsets.ISO_8859_1)
        assertTrue("tekst zostaje w tle", "Hello" in content)
        assertFalse("kreska jest wygaszona w tle", " l " in content)
    }

    @Test fun invisibleTextIsNotALeftover() {
        val ocr = "BT /F1 12 Tf 3 Tr 100 400 Td (Hello) Tj ET"
        val note = GoodNotesConvert.convert(pdf("GoodNotes", "$ink\n$ocr"), "x.pdf", ConvertOptions(ImportMode.STANDARD))
        assertTrue(note.pdfs.isEmpty())
        assertEquals(1, note.strokeCount)
    }
}

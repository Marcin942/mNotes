package com.mdev.mnotes.data

import com.mdev.mnotes.core.codec.FileKind
import com.mdev.mnotes.core.codec.FileKinds
import com.mdev.mnotes.core.codec.MnotesArchive
import com.mdev.mnotes.core.codec.PageCodec
import com.mdev.mnotes.core.model.Ids
import com.mdev.mnotes.core.model.PageContent
import com.mdev.mnotes.core.model.PageFormat
import com.mdev.mnotes.core.model.PageSpec
import com.mdev.mnotes.core.model.PdfRef
import com.mdev.mnotes.core.model.PenKind
import com.mdev.mnotes.core.model.Stroke
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** B5: import `.mnotes` z wieloma stronami (i tłem PDF zapisanym bez kompresji) ma przynieść wszystkie strony. */
class MnotesArchiveTest {
    @get:Rule val tmp = TemporaryFolder()

    private val ids = listOf("a1", "b2", "c3", "d4", "e5")

    private fun page(i: Int): ByteArray {
        val pts = floatArrayOf(10f + i, 10f, 1f, 50f + i, 60f, 1f)
        return PageCodec.encode(PageContent.of(listOf(Stroke(Ids.next(), PenKind.BALLPOINT, 0, 1f, pts))))
    }

    private fun meta(): String {
        val spec = PageSpec.of(PageFormat.A5)
        val pdfSpec = spec.copy(pdf = PdfRef("tlo.pdf", 0))
        val pages = ids.mapIndexed { i, id -> PageMeta(id, if (i == 0) pdfSpec else spec) }
        return NotebookMeta("stary", "Zeszyt z tabletu", null, 1L, 2L, 3L, 0, 0, pages = pages).toJson()
    }

    /** Archiwum jak z eksportu (`meta.json` pierwszy), z tłem PDF bez kompresji tuż za nim; [prefix] i [sep] udają przepakowanie. */
    private fun archive(prefix: String = "", sep: Char = '/'): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z ->
            fun put(name: String, data: ByteArray, stored: Boolean = false) {
                val e = ZipEntry(prefix + name.replace('/', sep))
                if (stored) { e.method = ZipEntry.STORED; e.size = data.size.toLong(); e.compressedSize = data.size.toLong(); e.crc = CRC32().apply { update(data) }.value }
                z.putNextEntry(e); z.write(data); z.closeEntry()
            }
            put("meta.json", meta().toByteArray())
            put("assets/tlo.pdf", "%PDF-1.4\n% tło strony\n".toByteArray() + ByteArray(2000), stored = true)
            ids.forEachIndexed { i, id -> put("p/$id.mnp", page(i)) }
        }
        return bos.toByteArray()
    }

    private fun names(zip: ByteArray): List<String> {
        val out = ArrayList<String>()
        ZipInputStream(ByteArrayInputStream(zip)).use { z -> while (true) { out.add((z.nextEntry ?: break).name) } }
        return out
    }

    private fun checkAllPages(dst: File, res: MnotesArchive.Result?) {
        assertNotNull(res)
        val m = NotebookMeta.fromJson(res!!.metaJson)
        assertEquals(ids, m.pages.map { it.id })
        assertEquals(ids.toSet(), res.pageIds)
        for (id in ids) assertEquals(1, PageCodec.decode(File(dst, "p/$id.mnp").readBytes()).size)
        assertTrue(File(dst, "assets/tlo.pdf").exists())
    }

    @Test fun multiPageArchiveIsRecognizedAndFullyExtracted() {
        val zip = archive()
        // „%PDF” tła leży w pierwszym kilobajcie: rozpoznanie musi wybrać zeszyt, nie PDF (dawniej jedna strona).
        val head = zip.copyOf(1024)
        assertTrue(FileKinds.isPdf(head))
        assertEquals(FileKind.MNOTES, FileKinds.classify("plik", "application/octet-stream", head) { names(zip) })
        val dst = tmp.newFolder("nb")
        checkAllPages(dst, MnotesArchive.extract(ByteArrayInputStream(zip), dst, 1L shl 30))
        assertFalse(File(dst, ".import.zip").exists())
    }

    @Test fun repackedArchiveWithFolderAndBackslashes() {
        val zip = archive(prefix = "Zeszyt z tabletu\\", sep = '\\')
        val dst = tmp.newFolder("nb")
        checkAllPages(dst, MnotesArchive.extract(ByteArrayInputStream(zip), dst, 1L shl 30))
        assertEquals(FileKind.MNOTES, FileKinds.classify("plik", "", zip.copyOf(1024)) { names(zip) })
    }

    @Test fun foreignAndEscapingEntriesAreIgnored() {
        assertNull(MnotesArchive.target("../evil.txt", ""))
        assertNull(MnotesArchive.target("p/../../evil.mnp", ""))
        assertNull(MnotesArchive.target("thumb.png", ""))
        assertEquals("p/x.mnp", MnotesArchive.target("./p/x.mnp", ""))
        assertEquals("assets/a.jpg", MnotesArchive.target("Z/assets/a.jpg", "Z/"))
        assertNull(MnotesArchive.target("Inny/p/x.mnp", "Z/"))
    }

    @Test fun zipWithoutMetaOrOverLimitIsRejected() {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z -> z.putNextEntry(ZipEntry("notatki.txt")); z.write("x".toByteArray()); z.closeEntry() }
        assertNull(MnotesArchive.extract(ByteArrayInputStream(bos.toByteArray()), tmp.newFolder("a"), 1L shl 30))
        assertNull(MnotesArchive.extract(ByteArrayInputStream(archive()), tmp.newFolder("b"), 100L))
    }
}

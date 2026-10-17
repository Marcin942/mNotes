package com.mdev.mnotes.core.samsung

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream

class SdocxReaderTest {
    private val doc = SdocxReader.read(ByteArrayInputStream(SynthSdocx.build(SynthSdocx.samplePages())))

    @Test fun readsPagesInOrderWithBackground() {
        assertEquals(2, doc.pages.size)
        assertEquals("aaaaaaaa-0000-4000-8000-000000000001", doc.pages[0].uuid)
        assertEquals(0xFFE6E6E6.toInt(), doc.pages[0].bgColor)
        assertEquals(5, doc.pages[0].templateType)
        assertEquals(1600, doc.pages[0].width)
        assertEquals(2262, doc.pages[0].height)
        assertNull(doc.pages[1].templateType)
        assertEquals(0xFFFCFCFC.toInt(), doc.pages[1].bgColor)
    }

    @Test fun readsStrokesWithPenColorAndSize() {
        val objs = doc.pages[0].allObjects().filter { it.type == 1 }
        assertEquals(4, objs.size)
        val s = SdocxReader.parseStroke(objs[0].payload, doc.strings)
        assertEquals(30, s.count)
        assertEquals(SynthSdocx.PEN_INK, s.pen)
        assertEquals(0xFF000000.toInt(), s.color)
        assertEquals(16.77f, s.size!!, 1e-4f)
        assertEquals(100.0, s.x[0], 1e-9)
        assertEquals(102.0, s.x[1], 1e-9)   // przyrost 2,0 mieści się dokładnie w 1/32
        assertEquals(200.5, s.y[1], 1e-9)
        assertEquals(1000, s.t[0])
        assertEquals(1010, s.t[1])
        assertEquals(0.5f, s.pressure[0], 1e-6f)
    }

    @Test fun markerIsFlaggedByProps() {
        val objs = doc.pages[0].allObjects().filter { it.type == 1 }
        val m = SdocxReader.parseStroke(objs[3].payload, doc.strings)
        assertEquals(SynthSdocx.PEN_MARKER, m.pen)
        assertTrue(m.props and 0x40L != 0L)
    }

    @Test fun detectsSdocxByContent() {
        assertTrue(SdocxReader.looksLikeSdocx(ByteArrayInputStream(SynthSdocx.build(SynthSdocx.samplePages()))))
        assertFalse(SdocxReader.looksLikeSdocx(ByteArrayInputStream("to nie jest zip".toByteArray())))
    }

    @Test fun rejectsArchiveWithoutNote() {
        try {
            SdocxReader.read(ByteArrayInputStream(ByteArray(0)))
            fail("oczekiwano wyjątku")
        } catch (e: SamsungFormatException) {
            assertNotNull(e.message)
        }
    }

    @Test fun brokenPageBecomesErrorPageNotFailure() {
        val bytes = SynthSdocx.build(listOf(SynthSdocx.Page("bbbbbbbb-0000-4000-8000-000000000001")))
        // ucięta strona: czytnik ma ją zachować jako stronę z błędem, a nie przerwać całego pliku
        val cut = java.io.ByteArrayOutputStream().also { out ->
            java.util.zip.ZipInputStream(ByteArrayInputStream(bytes)).use { zin ->
                java.util.zip.ZipOutputStream(out).use { zout ->
                    while (true) {
                        val e = zin.nextEntry ?: break
                        val data = zin.readBytes()
                        zout.putNextEntry(java.util.zip.ZipEntry(e.name))
                        zout.write(if (e.name.endsWith(".page")) data.copyOf(12) else data)
                        zout.closeEntry()
                    }
                }
            }
        }.toByteArray()
        val d = SdocxReader.read(ByteArrayInputStream(cut))
        assertEquals(1, d.pages.size)
        assertNotNull(d.pages[0].error)
    }
}

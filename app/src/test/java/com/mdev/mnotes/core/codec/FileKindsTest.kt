package com.mdev.mnotes.core.codec

import org.junit.Assert.assertEquals
import org.junit.Test

class FileKindsTest {
    private val zip = byteArrayOf(0x50, 0x4b, 0x03, 0x04, 0, 0, 0, 0)
    private val pdf = "%PDF-1.7\n".toByteArray()
    private val jpg = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0xe0.toByte())

    @Test fun mnotesBezRozszerzeniaZOctetStream() {
        // Tak podaje plik menedżer plików: adres bez rozszerzenia i typ octet-stream.
        assertEquals(FileKind.MNOTES, FileKinds.classify("msf:1234", "application/octet-stream", zip) { listOf("meta.json", "p/a.mnp") })
    }

    @Test fun sdocxPoZawartosci() {
        assertEquals(FileKind.SDOCX, FileKinds.classify("Notes", "application/zip", zip) { listOf("note.note", "pageIdInfo.dat") })
    }

    @Test fun pdfPoSygnaturzeMimoZlegoTypu() {
        assertEquals(FileKind.PDF, FileKinds.classify("x", "application/octet-stream", pdf) { emptyList() })
    }

    @Test fun pdfZeSmieciamiPrzedNaglowkiem() {
        assertEquals(FileKind.PDF, FileKinds.classify("x", "", "\u0000\u0000\r\n".toByteArray() + pdf) { emptyList() })
    }

    @Test fun mnotesZPdfemWPierwszymKilobajcieToNiePdf() {
        // Tło PDF zapisane w archiwum bez kompresji: „%PDF” pojawia się przed 1024. bajtem pliku.
        val head = zip + ByteArray(900) + pdf
        assertEquals(FileKind.MNOTES, FileKinds.classify("cw_3.mnotes", "application/octet-stream", head) { listOf("meta.json", "p/a.mnp", "assets/b.pdf") })
    }

    @Test fun obrazPoSygnaturze() {
        assertEquals(FileKind.IMAGE, FileKinds.classify("zdjecie", "application/octet-stream", jpg) { emptyList() })
    }

    @Test fun zwyklyZipToNieMnotes() {
        assertEquals(FileKind.UNKNOWN, FileKinds.classify("archiwum.zip", "application/zip", zip) { listOf("a.txt") })
    }

    @Test fun uszkodzonyMnotesZostajeMnotes() {
        assertEquals(FileKind.MNOTES, FileKinds.classify("x.mnotes", "", zip) { throw IllegalStateException("zepsuty") })
    }

    @Test fun bezBajtowDecydujeNazwaPotemMime() {
        assertEquals(FileKind.SDOCX, FileKinds.classify("a.SDOCX", "", ByteArray(0)) { emptyList() })
        assertEquals(FileKind.PDF, FileKinds.classify("a", "application/pdf", ByteArray(0)) { emptyList() })
        assertEquals(FileKind.IMAGE, FileKinds.classify("a", "image/png", ByteArray(0)) { emptyList() })
        assertEquals(FileKind.UNKNOWN, FileKinds.classify("a", "text/plain", ByteArray(0)) { emptyList() })
    }

    @Test fun nieznanaZawartoscToNieMnotesNawetZNazwaPdf() {
        assertEquals(FileKind.UNKNOWN, FileKinds.classify("a.pdf", "application/pdf", "hello world".toByteArray()) { emptyList() })
    }
}

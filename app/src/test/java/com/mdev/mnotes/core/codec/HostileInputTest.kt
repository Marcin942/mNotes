package com.mdev.mnotes.core.codec

import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.zip.CRC32
import java.util.zip.Deflater

/** Wrogie pliki stron (audyt bezpieczeństwa, docs/BEZPIECZENSTWO.md): kilkadziesiąt bajtów nie może zająć setek MB. */
class HostileInputTest {

    /** Nagłówek MNP1 z poprawną sumą kontrolną, flagą kompresji i dowolną zadeklarowaną długością po rozpakowaniu. */
    private fun page(rawLen: Int, raw: ByteArray): ByteArray {
        val d = Deflater().apply { setInput(raw); finish() }
        val buf = ByteArray(raw.size + 64)
        val payload = buf.copyOf(d.deflate(buf)).also { d.end() }
        val w = ByteWriter()
        w.u8('M'.code); w.u8('N'.code); w.u8('P'.code); w.u8('1'.code)
        w.u8(1); w.u8(1)
        w.i32(CRC32().apply { update(payload) }.value.toInt())
        w.varint(rawLen)
        w.bytes(payload, 0, payload.size)
        return w.toByteArray()
    }

    @Test
    fun declaredLengthBeyondDeflateRatioIsRejectedBeforeAllocating() {
        // ~20 bajtów danych, a nagłówek obiecuje 256 MB: wcześniej dekoder przydzielał całość, zanim sprawdził dane.
        val data = page(1 shl 28, ByteArray(10))
        try {
            PageCodec.decode(data)
            fail("strona z niemożliwą długością przeszła")
        } catch (e: CodecException) {
            assertTrue(e.message, e.message!!.contains("długość"))
        }
    }

    @Test
    fun pageAndAssetNamesFromFileCannotLeaveNotebookFolder() {
        // Identyfikator strony z meta.json i nazwa obrazu z rekordu strony stają się ścieżką w katalogu zeszytu.
        val hostile = listOf("../../../../shared_prefs/x.xml", "..", ".", "", "a/b", "a\\b", "/data/x", "x\u0000.png")
        for (n in hostile) {
            val p = LocalName.plain(n)
            assertTrue(n, LocalName.isPlain(p))
            assertTrue(n, p.startsWith("invalid-"))
        }
        // zwykłe nazwy (w tym z kropkami) bez zmian: stare zeszyty otwierają się jak dawniej
        for (n in listOf("3f9a01b2c4d5", "3f9a01b2c4d5.webp", "img-1.webp", "a..b.png")) assertTrue(LocalName.plain(n) == n)
    }

    @Test
    fun highlyCompressiblePageStillDecodes() {
        // Prawdziwa strona dobrze się kompresuje (np. 0 elementów + dopełnienie): limit z ilorazu deflate jej nie odcina.
        val raw = ByteArray(200_000).also { it[0] = 0 }
        val back = PageCodec.decode(page(raw.size, raw))
        assertTrue(back.elements.isEmpty())
    }
}

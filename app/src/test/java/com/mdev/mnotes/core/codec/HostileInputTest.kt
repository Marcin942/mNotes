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
    fun highlyCompressiblePageStillDecodes() {
        // Prawdziwa strona dobrze się kompresuje (np. 0 elementów + dopełnienie): limit z ilorazu deflate jej nie odcina.
        val raw = ByteArray(200_000).also { it[0] = 0 }
        val back = PageCodec.decode(page(raw.size, raw))
        assertTrue(back.elements.isEmpty())
    }
}

package com.mdev.mnotes.core

import com.mdev.mnotes.core.codec.ByteReader
import com.mdev.mnotes.core.codec.ByteWriter
import com.mdev.mnotes.core.codec.CodecException
import com.mdev.mnotes.core.codec.PageCodec
import com.mdev.mnotes.core.model.Ids
import com.mdev.mnotes.core.model.ImageEl
import com.mdev.mnotes.core.model.PageContent
import com.mdev.mnotes.core.model.PenKind
import com.mdev.mnotes.core.model.Stroke
import com.mdev.mnotes.core.model.TextBox
import com.mdev.mnotes.core.pen.LineStyle
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.Random
import java.util.zip.CRC32

class PageCodecTest {

    private fun randomStroke(rnd: Random, pen: PenKind): Stroke {
        val n = 2 + rnd.nextInt(80)
        val pts = FloatArray(n * 3)
        var x = rnd.nextFloat() * 500f
        var y = rnd.nextFloat() * 800f
        for (i in 0 until n) {
            x += (rnd.nextFloat() - 0.5f) * 6f
            y += (rnd.nextFloat() - 0.5f) * 6f
            pts[3 * i] = x; pts[3 * i + 1] = y; pts[3 * i + 2] = rnd.nextFloat()
        }
        return Stroke(Ids.next(), pen, rnd.nextInt(), 0.5f + rnd.nextFloat() * 10f, pts, rnd.nextBoolean())
    }

    @Test
    fun roundTripMixedContent() {
        val rnd = Random(7)
        val list = ArrayList<com.mdev.mnotes.core.model.Element>()
        repeat(60) { list.add(randomStroke(rnd, PenKind.entries[it % PenKind.entries.size])) }
        list.add(TextBox(Ids.next(), 120.5f, 300f, 200f, 40f, 0.3f, "Zażółć gęślą jaźń ✓", 14f, 0xFF00AA00.toInt(),
            TextBox.FAMILY_SERIF, TextBox.STYLE_BOLD or TextBox.STYLE_UNDERLINE, TextBox.ALIGN_CENTER, 0x33FFFF00, true))
        list.add(TextBox(Ids.next(), 60f, 500f, 180f, 30f, 0f, "auto", 12f, 0xFF000000.toInt(), fit = TextBox.FIT_AUTO))
        list.add(TextBox(Ids.next(), 60f, 560f, 180f, 30f, 0f, "stałe", 12f, 0xFF000000.toInt(), fit = TextBox.FIT_FIXED))
        list.add(TextBox(Ids.next(), 60f, 620f, 180f, 30f, 0f, "interlinia", 12f, 0xFF000000.toInt(), lineSpacing = 1.15f))
        list.add(ImageEl(Ids.next(), "img-1.webp", 250f, 400f, 120f, 80f, -0.2f, 0.5f, false))
        list.add(ImageEl(Ids.next(), "img-1.webp", 300f, 450f, 60f, 80f, 0f, 1f, true, com.mdev.mnotes.core.model.ImageCrop(0.25f, 0f, 0.75f, 0.5f)))
        list.add(com.mdev.mnotes.core.model.FillEl(Ids.next(), 0xFF3366CC.toInt(), false, listOf(floatArrayOf(0f, 0f, 600f, 0f, 600f, 800f, 0f, 800f), floatArrayOf(100.5f, 100f, 200f, 100f, 150f, 180.25f))))
        val src = PageContent.of(list)

        val back = PageCodec.decode(PageCodec.encode(src))
        assertEquals(src.size, back.size)
        for (i in 0 until src.size) {
            val a = src.elements[i]
            val b = back.elements[i]
            assertEquals(a.locked, b.locked)
            when (a) {
                is Stroke -> {
                    b as Stroke
                    assertEquals(a.pen, b.pen)
                    assertEquals(a.color, b.color)
                    assertEquals(a.width, b.width, 0.006f)
                    assertEquals(a.count, b.count)
                    for (k in 0 until a.count) {
                        assertEquals(a.pts[3 * k], b.pts[3 * k], 0.006f)
                        assertEquals(a.pts[3 * k + 1], b.pts[3 * k + 1], 0.006f)
                        assertEquals(a.pts[3 * k + 2], b.pts[3 * k + 2], 0.004f)
                    }
                }
                is TextBox -> {
                    b as TextBox
                    assertEquals(a.text, b.text)
                    assertEquals(a.cx, b.cx, 0f)
                    assertEquals(a.rot, b.rot, 0f)
                    assertEquals(a.style, b.style)
                    assertEquals(a.align, b.align)
                    assertEquals(a.family, b.family)
                    assertEquals(a.fill, b.fill)
                    assertEquals(a.size, b.size, 0f)
                    assertEquals(a.fit, b.fit)
                    assertEquals(a.lineSpacing, b.lineSpacing, 0f)
                }
                is ImageEl -> {
                    b as ImageEl
                    assertEquals(a.asset, b.asset)
                    assertEquals(a.w, b.w, 0f)
                    assertEquals(a.alpha, b.alpha, 0.003f)
                    assertEquals(a.crop, b.crop)
                }
                is com.mdev.mnotes.core.model.FillEl -> {
                    b as com.mdev.mnotes.core.model.FillEl
                    assertEquals(a.color, b.color)
                    assertEquals(a.hl, b.hl)
                    assertEquals(a.rings.size, b.rings.size)
                    for (k in a.rings.indices) assertArrayEquals(a.rings[k], b.rings[k], 0.006f)
                }
                is com.mdev.mnotes.core.model.TapeEl -> {
                    b as com.mdev.mnotes.core.model.TapeEl
                    assertEquals(a.color, b.color)
                    assertEquals(a.hidden, b.hidden)
                }
            }
        }
    }

    @Test
    fun emptyAndBlank() {
        assertTrue(PageCodec.decode(ByteArray(0)).isEmpty)
        assertTrue(PageCodec.decode(PageCodec.encode(PageContent.EMPTY)).isEmpty)
    }

    @Test
    fun compressionShrinksLongStrokes() {
        val rnd = Random(3)
        val c = PageContent.of(List(200) { randomStroke(rnd, PenKind.FOUNTAIN) })
        val bytes = PageCodec.encode(c)
        val rawEstimate = c.elements.sumOf { (it as Stroke).count * 12 }
        assertTrue("zapis ${bytes.size} B powinien być mniejszy niż surowe $rawEstimate B", bytes.size < rawEstimate / 2)
    }

    @Test
    fun polyFlagRoundTrips() {
        val pts = floatArrayOf(0f, 0f, 1f, 10f, 0f, 1f, 10f, 10f, 1f, 0f, 0f, 1f)
        val c = PageContent.of(listOf(
            Stroke(Ids.next(), PenKind.BALLPOINT, 0xFF112233.toInt(), 2f, pts, locked = true, poly = true),
            Stroke(Ids.next(), PenKind.BALLPOINT, 0xFF112233.toInt(), 2f, pts, locked = false, poly = false),
        ))
        val back = PageCodec.decode(PageCodec.encode(c)).elements.map { it as Stroke }
        assertTrue(back[0].poly && back[0].locked && back[0].closed)
        assertTrue(!back[1].poly && !back[1].locked)
    }

    @Test
    fun lineStyleRoundTrips() {
        val pts = floatArrayOf(0f, 0f, 1f, 10f, 0f, 1f, 10f, 10f, 1f, 0f, 0f, 1f)
        val c = PageContent.of(listOf(
            Stroke(Ids.next(), PenKind.BALLPOINT, 0xFF112233.toInt(), 2f, pts, locked = true, poly = true, line = LineStyle.DASHED),
            Stroke(Ids.next(), PenKind.HIGHLIGHTER, 0x66FFEE00, 12f, pts, line = LineStyle.DOTTED),
            Stroke(Ids.next(), PenKind.PENCIL, 0xFF112233.toInt(), 2f, pts, ellipse = true),
        ))
        val back = PageCodec.decode(PageCodec.encode(c)).elements.map { it as Stroke }
        assertEquals(listOf(LineStyle.DASHED, LineStyle.DOTTED, LineStyle.SOLID), back.map { it.line })
        // Styl nie zjada sąsiednich flag kreski.
        assertTrue(back[0].poly && back[0].locked && !back[0].ellipse)
        assertTrue(!back[1].poly && !back[1].locked)
        assertTrue(back[2].ellipse)
    }

    @Test
    fun detectsCorruption() {
        val rnd = Random(1)
        val bytes = PageCodec.encode(PageContent.of(List(5) { randomStroke(rnd, PenKind.BALLPOINT) }))
        bytes[bytes.size / 2] = (bytes[bytes.size / 2].toInt() xor 0x55).toByte()
        try {
            PageCodec.decode(bytes)
            fail("oczekiwano CodecException")
        } catch (_: CodecException) {
        }
        try {
            PageCodec.decode(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13))
            fail("oczekiwano CodecException")
        } catch (_: CodecException) {
        }
    }

    @Test
    fun skipsUnknownRecordTags() {
        val body = ByteWriter()
        body.varint(2)
        body.u8(99); body.varint(3); body.u8(1); body.u8(2); body.u8(3)
        val w = ByteWriter()
        w.u8(PenKind.BALLPOINT.code); w.i32(0xFF000000.toInt()); w.varint(0); w.varint(200); w.varint(2)
        w.zigzag(1000); w.zigzag(2000); w.u8(255)
        w.zigzag(100); w.zigzag(-50); w.u8(0)
        body.u8(1); body.varint(w.size); body.bytes(w.buf, 0, w.size)

        val payload = body.toByteArray()
        val crc = CRC32().apply { update(payload) }.value.toInt()
        val out = ByteWriter()
        out.u8('M'.code); out.u8('N'.code); out.u8('P'.code); out.u8('1'.code)
        out.u8(1); out.u8(0); out.i32(crc); out.varint(payload.size); out.bytes(payload, 0, payload.size)

        val c = PageCodec.decode(out.toByteArray())
        assertEquals(1, c.size)
        val s = c.elements[0] as Stroke
        assertEquals(2, s.count)
        assertEquals(10f, s.pts[0], 1e-4f)
        assertEquals(20f, s.pts[1], 1e-4f)
        assertEquals(11f, s.pts[3], 1e-4f)
        assertEquals(19.5f, s.pts[4], 1e-4f)
        assertEquals(2f, s.width, 1e-4f)
    }

    @Test
    fun defaultLineSpacingAddsNoBytes() {
        fun enc(ls: Float) = PageCodec.encode(PageContent.of(listOf(
            TextBox(Ids.next(), 60f, 620f, 180f, 30f, 0f, "a", 12f, 0xFF000000.toInt(), lineSpacing = ls),
        )))
        // interlinia 1 zapisuje się jak przed jej wprowadzeniem; inna wartość dokłada flagę i jeden f32
        assertTrue(enc(1.15f).size - enc(1f).size <= 5)
        assertEquals(1f, (PageCodec.decode(enc(1f)).elements[0] as TextBox).lineSpacing, 0f)
        assertEquals(1.15f, (PageCodec.decode(enc(1.15f)).elements[0] as TextBox).lineSpacing, 0f)
    }

    @Test
    fun varintAndZigzagRoundTrip() {
        val w = ByteWriter(4)
        val vals = intArrayOf(0, 1, -1, 63, -64, 64, 127, 128, 300, -300, Int.MAX_VALUE, Int.MIN_VALUE)
        for (v in vals) w.zigzag(v)
        for (v in intArrayOf(0, 1, 127, 128, 16384, Int.MAX_VALUE)) w.varint(v)
        w.string("zażółć")
        w.f32(-1.25f)
        val r = ByteReader(w.toByteArray())
        for (v in vals) assertEquals(v, r.zigzag())
        for (v in intArrayOf(0, 1, 127, 128, 16384, Int.MAX_VALUE)) assertEquals(v, r.varint())
        assertEquals("zażółć", r.string())
        assertEquals(-1.25f, r.f32(), 0f)
        assertEquals(0, r.remaining)
    }
}

package com.mdev.mnotes.core.samsung

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Syntetyczny `.sdocx` budowany w pamięci (port `skills/samsung-notes-do-mnotes/scripts/synth_sdocx.py`): tablica
 * napisów, kolejność stron, nagłówek strony (papier, wzór) i pociągnięcia w wersji skompresowanej. Dzięki niemu testy
 * przechodzą cały tor od ZIP-a do [ConvertedNote] bez prywatnych plików użytkownika. To nie jest pełny koder formatu.
 */
internal object SynthSdocx {
    const val PEN_INK = "com.samsung.android.sdk.pen.pen.preload.InkPen2"
    const val PEN_BRUSH = "com.samsung.android.sdk.pen.pen.preload.BrushPen"
    const val PEN_MARKER = "com.samsung.android.sdk.pen.pen.preload.Marker3"
    private val STRINGS = linkedMapOf(1 to PEN_INK, 2 to PEN_BRUSH, 3 to PEN_MARKER)
    private val PROPS = mapOf(PEN_INK to 0x425L, PEN_BRUSH to 0x425L, PEN_MARKER to 0x465L)

    class Pt(val x: Double, val y: Double, val p: Double, val t: Int)
    class Pen(val pen: String, val color: Int, val size: Float, val pts: List<Pt>)
    class Page(
        val uuid: String,
        val strokes: List<Pen> = emptyList(),
        val bg: Int? = null,
        val template: Int? = null,
        /** Dodatkowe obiekty innych typów (typ, treść), np. pole tekstowe (2). */
        val extra: List<Pair<Int, ByteArray>> = emptyList(),
        val width: Int = 1600,
        val height: Int = 2262,
    )

    private class Le {
        private val o = ByteArrayOutputStream()
        val size: Int get() = o.size()
        fun u8(v: Int) = apply { o.write(v) }
        fun u16(v: Int) = apply { o.write(v and 0xFF); o.write((v shr 8) and 0xFF) }
        fun i32(v: Int) = apply { for (i in 0..3) o.write((v shr (8 * i)) and 0xFF) }
        fun i64(v: Long) = apply { for (i in 0..7) o.write(((v shr (8 * i)) and 0xFF).toInt()) }
        fun f32(v: Float) = i32(v.toRawBits())
        fun f64(v: Double) = i64(v.toRawBits())
        fun bytes(b: ByteArray) = apply { o.write(b) }
        fun zeros(n: Int) = apply { o.write(ByteArray(n)) }
        fun s16(s: String) = apply { u16(s.length); bytes(s.toByteArray(Charsets.UTF_16LE)) }
        fun mask(v: Long) = apply {
            val n = maxOf(1, (64 - java.lang.Long.numberOfLeadingZeros(v) + 7) / 8)
            u8(n)
            for (i in 0 until n) o.write(((v shr (8 * i)) and 0xFF).toInt())
        }
        fun toByteArray(): ByteArray = o.toByteArray()
    }

    private fun dq(le: Le, v: Double, scale: Double) {
        val raw = minOf(Math.rint(Math.abs(v) * scale).toInt(), 0x7FFF)
        le.u16(raw or (if (v < 0) 0x8000 else 0))
    }

    fun strokePayload(s: Pen, uuid: String): ByteArray {
        val n = s.pts.size
        val props = PROPS.getValue(s.pen)
        val fmask = (1L shl 2) or (1L shl 3) or (1L shl 7)
        val u = uuid.toByteArray(Charsets.US_ASCII)
        val base0 = Le().u16(0).i32(0).mask(0).mask(0).i32(1).u16(u.size).bytes(u).i64(0)
        repeat(4) { base0.f64(0.0) }
        val b0 = base0.toByteArray()
        val base = Le().i32(4 + b0.size).bytes(b0).toByteArray()

        val body = Le()
        val first = s.pts[0]
        body.f64(first.x).f64(first.y)
        var px = first.x
        var py = first.y
        for (pt in s.pts.drop(1)) {
            dq(body, pt.x - px, 32.0); dq(body, pt.y - py, 32.0)
            px = pt.x; py = pt.y
        }
        body.f32(first.p.toFloat())
        var pp = first.p
        for (pt in s.pts.drop(1)) { dq(body, pt.p - pp, 4096.0); pp = pt.p }
        body.i32(first.t)
        var pt0 = first.t
        for (pt in s.pts.drop(1)) { body.u16(pt.t - pt0); pt0 = pt.t }
        repeat(2) { body.f32(0f); body.zeros(2 * (n - 1)) }
        body.zeros(2)
        val bodyBytes = body.toByteArray()

        val fixedLen = 4 + 2 + 4 + Le().mask(props).size + Le().mask(fmask).size + 2 + bodyBytes.size
        val extra = Le().i32(s.color).f32(s.size).i32(STRINGS.entries.first { it.value == s.pen }.key).toByteArray()
        val frame = Le().i32(fixedLen + extra.size).u16(1).i32(fixedLen).mask(props).mask(fmask).u16(n)
            .bytes(bodyBytes).bytes(extra).toByteArray()
        return base + frame
    }

    private fun obj(type: Int, payload: ByteArray): ByteArray =
        Le().u8(type).u16(0).i32(payload.size + 32).bytes(payload).zeros(32).toByteArray()

    fun pageBytes(p: Page): ByteArray {
        var fm = 0L
        val flex = Le()
        if (p.bg != null) { fm = fm or 32; flex.i32(p.bg) }
        if (p.template != null) { fm = fm or 512; flex.i32(p.template) }
        val headFixed = Le().mask(0).mask(fm).i32(0).i32(p.width).i32(p.height).i32(0).i32(0).s16(p.uuid).i64(0).i32(1).i32(1)
            .toByteArray()
        val flexBytes = flex.toByteArray()
        val flexOff = 8 + headFixed.size
        val layerOff = flexOff + flexBytes.size
        val head = Le().i32(layerOff).i32(flexOff).bytes(headFixed).toByteArray()
        val objs = Le()
        p.strokes.forEachIndexed { i, s -> objs.bytes(obj(1, strokePayload(s, "s$i"))) }
        for ((t, payload) in p.extra) objs.bytes(obj(t, payload))
        val masks = Le().mask(0).mask(0).toByteArray()
        val layer = Le().i32(8 + masks.size).i32(0).bytes(masks).i32(p.strokes.size + p.extra.size)
            .bytes(objs.toByteArray()).zeros(32).toByteArray()
        return head + flexBytes + Le().u16(1).u16(0).toByteArray() + layer
    }

    private fun noteNote(): ByteArray {
        val entries = Le()
        for ((id, s) in STRINGS) entries.i32(id).s16(s)
        val e = entries.toByteArray()
        return Le().i32(2 + e.size).u16(STRINGS.size).bytes(e).toByteArray()
    }

    fun build(pages: List<Page>): ByteArray {
        val order = Le().zeros(32).u16(pages.size)
        for (p in pages) order.s16(p.uuid).zeros(32)
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            fun put(name: String, data: ByteArray) { z.putNextEntry(ZipEntry(name)); z.write(data); z.closeEntry() }
            put("note.note", noteNote())
            put("pageIdInfo.dat", order.toByteArray())
            for (p in pages) put("${p.uuid}.page", pageBytes(p))
            put("end_tag.bin", byteArrayOf(0))
        }
        return out.toByteArray()
    }

    private fun line(x: Double, y: Double, n: Int, dx: Double, dy: Double, dt: Int, p: (Int) -> Double) =
        List(n) { Pt(x + it * dx, y + it * dy, p(it), 1000 + it * dt) }

    /** Siatka i jasny papier (pióro wolne i szybkie, pędzel, zakreślacz) plus gładka strona z jednym piórem. */
    fun samplePages(): List<Page> {
        val slow = line(100.0, 200.0, 30, 2.0, 0.5, 10) { 0.5 }
        val fast = line(100.0, 400.0, 30, 12.0, 0.0, 3) { 0.5 }
        val brush = line(100.0, 600.0, 30, 6.0, 1.0, 8) { minOf(0.9, 0.1 + 0.03 * it) }
        val marker = line(100.0, 800.0, 20, 20.0, 0.0, 8) { 0.5 }
        return listOf(
            Page(
                "aaaaaaaa-0000-4000-8000-000000000001", bg = 0xFFE6E6E6.toInt(), template = 5,
                strokes = listOf(
                    Pen(PEN_INK, 0xFF000000.toInt(), 16.77f, slow),
                    Pen(PEN_INK, 0xFF1565C0.toInt(), 16.77f, fast),
                    Pen(PEN_BRUSH, 0xFFC62828.toInt(), 19.88f, brush),
                    Pen(PEN_MARKER, 0x73FFEB3B, 31.0f, marker),
                ),
            ),
            Page("aaaaaaaa-0000-4000-8000-000000000002", bg = 0xFFFCFCFC.toInt(), strokes = listOf(Pen(PEN_INK, 0xFF2E7D32.toInt(), 4.98f, slow))),
        )
    }
}

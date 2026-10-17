package com.mdev.mnotes.core.samsung

import kotlin.math.cos
import kotlin.math.sin

/**
 * Syntetyczne obiekty typu 3 (obraz) i 7 (kształt) oraz ramka tekstu płynącego, ułożone tak jak w prawdziwych
 * notatkach (rozmiary i przesunięcia zmierzone na próbce). Wspólne dla testów czytnika i konwertera.
 */
internal object SynthSdObjects {
    class Box(val x0: Double, val y0: Double, val x1: Double, val y1: Double)
    class Seg(val kind: Int, vararg val pts: Double)
    class Span(val kind: Int, val start: Int, val end: Int, val value: ByteArray)

    fun floatBytes(v: Float): ByteArray = SynthSdocx.Le().f32(v).toByteArray()
    fun intBytes(v: Int): ByteArray = SynthSdocx.Le().i32(v).toByteArray()

    private fun box(le: SynthSdocx.Le, r: Box) = le.f64(r.x0).f64(r.y0).f64(r.x1).f64(r.y1)

    /** Ramka bazowa: rozmiar, typ 0, przesunięcie, dwie maski, wersja, identyfikator ASCII, czas i obwiednia. */
    private fun baseFrame(bounds: Box): ByteArray {
        val u = "00000000-0000-4000-8000-000000000000".toByteArray(Charsets.US_ASCII)
        val rest = SynthSdocx.Le().u16(0).i32(0).mask(0).mask(0).i32(4000).u16(u.size).bytes(u).i64(0)
        box(rest, bounds)
        val b = rest.toByteArray()
        return SynthSdocx.Le().i32(4 + b.size).bytes(b).toByteArray()
    }

    /** Ramka obiektu: nagłówek (rozmiar, typ, przesunięcie do [tail], dwie maski), potem [head] i [tail]. */
    private fun frame(type: Int, head: ByteArray, tail: ByteArray = ByteArray(0)): ByteArray {
        val headerLen = 4 + 2 + 4 + 2 + 2
        return SynthSdocx.Le().i32(headerLen + head.size + tail.size).u16(type).i32(headerLen + head.size)
            .mask(0).mask(0).bytes(head).bytes(tail).toByteArray()
    }

    /** Obraz: [visible] to widoczna część bez obrotu, [full] to cały obraz przed przycięciem (null = bez przycięcia). */
    fun image(visible: Box, mediaId: Int, rotationDeg: Double = 0.0, full: Box? = null): ByteArray {
        val cx = (visible.x0 + visible.x1) / 2
        val cy = (visible.y0 + visible.y1) / 2
        val hw = (visible.x1 - visible.x0) / 2
        val hh = (visible.y1 - visible.y0) / 2
        val th = Math.toRadians(rotationDeg)
        fun at(dx: Double, dy: Double) = doubleArrayOf(cx + dx * cos(th) - dy * sin(th), cy + dx * sin(th) + dy * cos(th))
        val f2head = SynthSdocx.Le().i32(4)
        for (p in listOf(at(0.0, -hh), at(hw, 0.0), at(0.0, hh), at(-hw, 0.0))) f2head.f64(p[0]).f64(p[1])
        val f3tail = SynthSdocx.Le().i32(0x3e).u16(2).i32(mediaId).zeros(8).toByteArray()
        val rest = if (full == null) SynthSdocx.Le().i32(17).zeros(13) else SynthSdocx.Le().i32(65).zeros(29).also { box(it, full) }
        return baseFrame(visible) + frame(6, f2head.toByteArray()) + frame(7, ByteArray(6), f3tail) + rest.toByteArray()
    }

    /** Kształt albo krzywa: kolor ARGB i grubość w drugiej ramce, ścieżka w trzeciej. */
    fun shape(kind: Int, rect: Box, argb: Int, widthUnits: Float, segs: List<Seg>): ByteArray {
        val f2head = SynthSdocx.Le().i32(2).f64(rect.x0).f64(rect.y0).f64(rect.x1).f64(rect.y1).zeros(9)
        val color = SynthSdocx.Le().i32(19).u16(1).u8(0)
            .u8(argb and 0xFF).u8((argb shr 8) and 0xFF).u8((argb shr 16) and 0xFF).u8((argb shr 24) and 0xFF).zeros(12)
        val width = SynthSdocx.Le().i32(12).f32(widthUnits).zeros(8)
        val path = SynthSdocx.Le()
        for (s in segs) { path.u8(s.kind); for (v in s.pts) path.f64(v) }
        val pathBytes = path.toByteArray()
        val f3head = SynthSdocx.Le().i32(kind)
        box(f3head, rect)
        f3head.zeros(4).i32(4 + pathBytes.size).i32(segs.size).bytes(pathBytes)
        return baseFrame(rect) + frame(6, f2head.toByteArray(), color.bytes(width.toByteArray()).toByteArray()) +
            frame(7, f3head.toByteArray())
    }

    /**
     * Ramka tekstu płynącego. [pages] to pary (długość, koniec włącznie) jak w pliku, [images] to gotowe obiekty obrazów
     * (z [image]) wstawione w kolejności znaków U+FFFC.
     */
    fun flow(
        text: String,
        spans: List<Span> = emptyList(),
        margins: FloatArray = floatArrayOf(16f, 10f, 16f, 10f),
        pages: List<Pair<Int, Int>>,
        images: List<ByteArray> = emptyList(),
    ): ByteArray {
        val body = SynthSdocx.Le()
        body.i32(text.length).bytes(text.toByteArray(Charsets.UTF_16LE))
        body.i32(spans.size)
        for (s in spans) body.u16(16 + s.value.size).i32(s.kind).i32(s.start).i32(s.end).i32(0).bytes(s.value)
        body.i32(2)
        repeat(2) { body.u16(20).i32(3).i32(0).i32(text.length).zeros(8) }
        for (m in margins) body.f32(m)
        body.u8(0).i32(pages.size).u16(0)
        for ((len, end) in pages) body.i32(len).i32(end)
        if (images.isNotEmpty()) {
            body.i32(0).i32(images.size)
            for (img in images) body.i32(8 + img.size).i32(507).i32(3).bytes(img)
        }
        val b = body.toByteArray()
        return SynthSdocx.Le().i32(b.size).bytes(b).toByteArray()
    }
}

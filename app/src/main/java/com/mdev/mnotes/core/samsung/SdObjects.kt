package com.mdev.mnotes.core.samsung

import kotlin.math.abs
import kotlin.math.atan2

/** Prostokąt w jednostkach pliku (1 jednostka = 0,375 pt), zawsze x0 ≤ x1 i y0 ≤ y1. */
class SdRect(val x0: Double, val y0: Double, val x1: Double, val y1: Double) {
    val w: Double get() = x1 - x0
    val h: Double get() = y1 - y0
    val cx: Double get() = (x0 + x1) / 2
    val cy: Double get() = (y0 + y1) / 2

    fun contains(o: SdRect, eps: Double): Boolean =
        o.x0 >= x0 - eps && o.y0 >= y0 - eps && o.x1 <= x1 + eps && o.y1 <= y1 + eps
}

/**
 * Obraz umieszczony na stronie (obiekt typu 3) albo w tekście płynącym. [rect] to widoczna część obrazu bez obrotu,
 * [rotationDeg] obrót w stopniach w prawo wokół środka [rect]. [full] to cały, nieprzycięty obraz (tylko gdy Samsung
 * go przyciął): okno w pliku JPEG to [rect] względem [full].
 */
class SdImage(val rect: SdRect, val rotationDeg: Double, val mediaId: Int, val full: SdRect?)

/** Odcinek ścieżki kształtu: [kind] 1 = moveTo, 2 = lineTo, 3 = quadTo, 4 = cubicTo, 6 = close; [pts] to pary x, y. */
class SdSeg(val kind: Int, val pts: DoubleArray)

/**
 * Kształt albo krzywa odręczna (obiekt typu 7): kontur [segs] w jednostkach pliku, kolor ARGB i grubość linii
 * w jednostkach pliku. Wypełnienia format nie udostępnia w tym układzie ramek, więc kształt jest zawsze obrysem.
 */
class SdShape(val kind: Int, val rect: SdRect, val color: Int, val widthUnits: Float, val segs: List<SdSeg>)

/**
 * Dekoder obiektów typu 3 (obraz) i 7 (kształt). Każdy obiekt to trzy ramki jedna za drugą: bazowa (obwiednia),
 * druga (punkty, kolor, grubość) i trzecia (geometria), a obraz ma jeszcze ramkę końcową. Rozmiar każdej ramki stoi
 * w jej pierwszych czterech bajtach, więc kolejne ramki znajdujemy skokami, nie zgadywaniem.
 */
internal object SdObjects {
    private const val MAX_SEGS = 100_000
    private const val EPS = 1.5

    private class Frame(val start: Int, val size: Int, val type: Int, val flex: Int) {
        val end: Int get() = start + size
    }

    private fun frameAt(b: ByteArray, at: Int): Frame {
        val r = LeReader(b, at)
        val size = r.u32()
        val type = r.u16().toShort().toInt()
        val flex = r.u32()
        if (size < 10 || at + size > b.size || flex > size) throw SamsungFormatException("zła ramka obiektu (pozycja $at)")
        return Frame(at, size.toInt(), type, flex.toInt())
    }

    private fun rect(r: LeReader): SdRect {
        val x0 = r.f64(); val y0 = r.f64(); val x1 = r.f64(); val y1 = r.f64()
        if (!(x0.isFinite() && y0.isFinite() && x1.isFinite() && y1.isFinite()) || x1 < x0 || y1 < y0 ||
            maxOf(abs(x0), abs(y0), abs(x1), abs(y1)) > 1e6
        ) throw SamsungFormatException("zła obwiednia obiektu")
        return SdRect(x0, y0, x1, y1)
    }

    /** Obwiednia z ramki bazowej: dla obrazu widoczna część bez obrotu, dla kształtu obrys razem z grubością linii. */
    fun bounds(b: ByteArray, at: Int = 0): SdRect {
        val r = LeReader(b, at)
        r.u32(); r.skip(2); r.u32()
        r.mask(); r.mask()
        r.u32()
        r.skip(r.u16())
        r.i64()
        return rect(r)
    }

    /** Początek treści ramki za nagłówkiem (rozmiar, typ, przesunięcie) i dwiema maskami. */
    private fun body(b: ByteArray, f: Frame): LeReader {
        val r = LeReader(b, f.start + 10)
        r.mask(); r.mask()
        if (r.p > f.end) throw SamsungFormatException("zła maska ramki")
        return r
    }

    fun image(b: ByteArray, at: Int = 0): SdImage {
        val base = frameAt(b, at)
        val visible = bounds(b, at)
        val f2 = frameAt(b, base.end)
        val f3 = frameAt(b, f2.end)

        // Cztery punkty drugiej ramki to środki krawędzi obróconego prostokąta: górnej, prawej, dolnej, lewej.
        var rot = 0.0
        val r2 = body(b, f2)
        val n = r2.u32()
        if (n == 4L && r2.p + 64 <= f2.end) {
            val p = DoubleArray(8) { r2.f64() }
            if (p.all { it.isFinite() }) {
                val deg = Math.toDegrees(atan2(p[3] - p[7], p[2] - p[6]))
                if (abs(deg) > 0.05) rot = deg
            }
        }

        if (f3.flex + 10 > f3.size) throw SamsungFormatException("ramka obrazu bez numeru pliku")
        val mediaId = LeReader(b, f3.start + f3.flex + 6).u32()
        if (mediaId > 100_000L) throw SamsungFormatException("zły numer pliku obrazu")

        // Ramka końcowa: przy przycięciu (65 bajtów) ostatnie 32 bajty to cały obraz przed przycięciem.
        var full: SdRect? = null
        val rs = f3.end
        if (rs + 4 <= b.size) {
            val restSize = LeReader(b, rs).u32()
            if (restSize == 65L && rs + 65 <= b.size) {
                full = try {
                    rect(LeReader(b, rs + 65 - 32)).takeIf { it.w > 0 && it.h > 0 && it.contains(visible, EPS) }
                } catch (_: SamsungFormatException) {
                    null
                }
            }
        }
        return SdImage(visible, rot, mediaId.toInt(), full)
    }

    fun shape(b: ByteArray, at: Int = 0): SdShape {
        val base = frameAt(b, at)
        val f2 = frameAt(b, base.end)
        val f3 = frameAt(b, f2.end)

        // Druga ramka kończy się listą bloków z prefiksem długości: kolor (BGRA od 4. bajtu) i grubość (f32).
        var p = f2.start + f2.flex
        val r2 = LeReader(b, p)
        val len1 = r2.u32()
        if (len1 < 7 || p + 4 + len1 > f2.end) throw SamsungFormatException("brak bloku koloru kształtu")
        val c = LeReader(b, p + 4 + 3)
        val bl = c.u8(); val g = c.u8(); val rd = c.u8(); val a = c.u8()
        p += 4 + len1.toInt()
        val r3w = LeReader(b, p)
        val len2 = r3w.u32()
        if (len2 < 4 || p + 4 + len2 > f2.end) throw SamsungFormatException("brak bloku grubości kształtu")
        val width = r3w.f32()
        if (!width.isFinite() || width <= 0f || width > 2000f) throw SamsungFormatException("zła grubość kształtu")

        val r = body(b, f3)
        val kind = r.u32().toInt()
        val rc = rect(r)
        r.skip(4)
        val pathLen = r.u32()
        val start = r.p
        val cnt = r.u32()
        if (cnt < 1 || cnt > MAX_SEGS || start + pathLen > f3.end) throw SamsungFormatException("zła ścieżka kształtu")
        val segs = ArrayList<SdSeg>(cnt.toInt())
        repeat(cnt.toInt()) {
            val k = r.u8()
            val np = when (k) {
                1, 2 -> 1
                3 -> 2
                4 -> 3
                6 -> 0
                else -> throw SamsungFormatException("nieznany odcinek ścieżki: $k")
            }
            val pts = DoubleArray(2 * np) { r.f64() }
            if (!pts.all { it.isFinite() && abs(it) < 1e6 }) throw SamsungFormatException("zły punkt ścieżki")
            segs.add(SdSeg(k, pts))
        }
        if (r.p - start != pathLen.toInt()) throw SamsungFormatException("długość ścieżki ${r.p - start} != $pathLen")
        val argb = (a shl 24) or (rd shl 16) or (g shl 8) or bl
        return SdShape(kind, rc, argb, width, segs)
    }
}

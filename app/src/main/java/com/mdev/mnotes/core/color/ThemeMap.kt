package com.mdev.mnotes.core.color

import com.mdev.mnotes.core.model.ColorInvert
import com.mdev.mnotes.core.model.Paper
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Tryb ciemny i jasny stron (2b-A9, zastępuje proste odwrócenie F11; uzasadnienie w `docs/TRYB-CIEMNY.md`). Strona ma
 * natywny wygląd (jasny albo ciemny papier); gdy tryb każe pokazać ją inaczej, kolory idą przez [Transform]: papier i
 * szarości zamieniają jasność, kolory zachowują odcień i zmieniają jasność tylko tyle, by kontrast do nowego papieru
 * był co najmniej taki jak wcześniej, zakreślacz zostaje czytelną, nie świecącą plamą. Liczone w OKLab.
 */
object ThemeMap {
    /** Tryby (ustawienie `pages.theme`): każda strona jak powstała (z flagą F11), wszystkie jasne, wszystkie ciemne. */
    const val MIXED = 0
    const val LIGHT = 1
    const val DARK = 2

    private const val DARK_PAPER_L = 0.24
    private const val LIGHT_PAPER_L = 0.97
    private const val INK_MAX = 0.94
    private const val INK_MIN = 0.12
    private const val NEUTRAL_C = 0.04
    private const val HL_DARK_MAX = 0.6
    private const val HL_LIGHT_MIN = 0.8
    private const val SURFACE_C = 0.12
    /** Kontrast tekstu na wypełnieniu po zamianie (WCAG AA dla zwykłego tekstu). */
    const val TEXT_CONTRAST = 4.5
    private const val WHITE = -0x1
    private const val BLACK = -0x1000000

    /** Przekształcenie strony o papierze [srcPaper] do ciemnego ([toDark]) albo do jasnego wyglądu. Bezpieczne wątkowo. */
    class Transform(val toDark: Boolean, val srcPaper: Int) {
        val dstPaper: Int = paperFor(srcPaper, toDark)
        private val inks = ConcurrentHashMap<Int, Int>()
        private val hls = ConcurrentHashMap<Int, Int>()

        fun ink(argb: Int): Int = inks.getOrPut(argb) { mapInk(argb, srcPaper, dstPaper, toDark) }
        fun highlighter(argb: Int): Int = hls.getOrPut(argb) { mapHighlighter(argb, toDark) }
        /** Kryte wypełnienie (tło pola tekstu, wiaderko): zachowuje się jak papier, nie jak atrament (2c-A2). */
        fun surface(argb: Int): Int = surfaces.getOrPut(argb) { mapSurface(argb, srcPaper, dstPaper, toDark) }
        /** Tekst [argb] na wypełnieniu [fill] (oba w oryginale): po zamianie czytelny względem [surface] wypełnienia. */
        fun textOn(argb: Int, fill: Int): Int =
            mapTextOn(argb, over(fill, srcPaper), ink(argb), over(surface(fill), dstPaper))
        private val surfaces = ConcurrentHashMap<Int, Int>()

        /** Macierz 4×5 (`ColorMatrix`) dla tła PDF i obrazów: odwrócenie jasności z odcieniem, biel → nowy papier. */
        val imageMatrix: FloatArray by lazy { imageMatrix(toDark, dstPaper) }

        /** Przekształcenie w drugą stronę (pipeta: widoczny kolor → atrament, który tak wygląda). */
        fun reverse(): Transform = transform(dstPaper, !toDark)
    }

    private val transforms = ConcurrentHashMap<Long, Transform>()

    fun transform(paper: Int, toDark: Boolean): Transform =
        transforms.getOrPut((paper.toLong() shl 1) or (if (toDark) 1L else 0L)) { Transform(toDark, paper) }

    /**
     * Przekształcenie strony o papierze [paper] z flagą F11 [inverted] w trybie [mode]; null — strona pokazana tak,
     * jak powstała. Natywnie ciemna = ciemny papier (zapisany w stronie, więc to „pamięć”, jaka strona powstała).
     */
    fun forPage(paper: Int, inverted: Boolean, mode: Int): Transform? {
        val native = Paper.isDark(paper)
        val shown = when (mode) { DARK -> true; LIGHT -> false; else -> native != inverted }
        return if (shown == native) null else transform(paper, shown)
    }

    // — OKLab (Björn Ottosson) —

    private fun lin(c: Int): Double { val v = c / 255.0; return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4) }
    private fun srgb(v: Double): Double = if (v <= 0.0031308) 12.92 * v else 1.055 * v.pow(1 / 2.4) - 0.055

    /** (L, a, b) koloru ARGB. */
    fun oklab(argb: Int): DoubleArray {
        val r = lin(argb shr 16 and 255); val g = lin(argb shr 8 and 255); val b = lin(argb and 255)
        val l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
        val m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
        val s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
        return doubleArrayOf(
            0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
            1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
            0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s,
        )
    }

    private fun linearOf(L: Double, a: Double, b: Double): DoubleArray {
        val l = (L + 0.3963377774 * a + 0.2158037573 * b).pow(3)
        val m = (L - 0.1055613458 * a - 0.0638541728 * b).pow(3)
        val s = (L - 0.0894841775 * a - 1.2914855480 * b).pow(3)
        return doubleArrayOf(
            4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
            -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
            -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s,
        )
    }

    /** Kolor o jasności [L] i odcieniu (a, b); poza sRGB nasycenie spada, aż się zmieści (odcień zostaje). */
    fun fromOklab(L: Double, a: Double, b: Double, alpha: Int = 255): Int {
        var k = 1.0
        var rgb = linearOf(L, a, b)
        if (rgb.any { it < -1e-4 || it > 1 + 1e-4 }) {
            var lo = 0.0; var hi = 1.0
            repeat(16) { val mid = (lo + hi) / 2; if (linearOf(L, a * mid, b * mid).all { it in -1e-4..1 + 1e-4 }) lo = mid else hi = mid }
            k = lo; rgb = linearOf(L, a * k, b * k)
        }
        fun ch(v: Double) = (srgb(v.coerceIn(0.0, 1.0)) * 255).roundToInt().coerceIn(0, 255)
        return (alpha shl 24) or (ch(rgb[0]) shl 16) or (ch(rgb[1]) shl 8) or ch(rgb[2])
    }

    /** Odcień w stopniach (do testów). */
    fun hue(argb: Int): Double { val o = oklab(argb); return Math.toDegrees(atan2(o[2], o[1])) }

    fun paperFor(src: Int, toDark: Boolean): Int {
        val o = oklab(src)
        return fromOklab(if (toDark) DARK_PAPER_L else LIGHT_PAPER_L, o[1] * 0.25, o[2] * 0.25)
    }

    /** Atrament (kreska, tekst, wypełnienie, linie wzoru) przy przejściu z papieru [src] na [dst]; krycie bez zmian. */
    fun mapInk(argb: Int, src: Int, dst: Int, toDark: Boolean): Int {
        val o = oklab(argb); val L = o[0]
        val lp = oklab(src)[0]; val lq = oklab(dst)[0]
        val neutral = hypot(o[1], o[2]) < NEUTRAL_C
        val nl = if (toDark) {
            val d = max(0.0, lp - L)
            if (neutral) lq + d * (INK_MAX - lq) / max(lp, 0.05) else max(L, lq + d).coerceAtMost(INK_MAX + 0.04)
        } else {
            val d = max(0.0, L - lp)
            if (neutral) lq - d * (lq - INK_MIN) / max(1.0 - lp, 0.05) else min(L, lq - d).coerceAtLeast(INK_MIN - 0.04)
        }
        return fromOklab(nl, o[1], o[2], argb ushr 24)
    }

    /**
     * Wypełnienie przy przejściu z papieru [src] na [dst]: jasność odwrócona jak u papieru i szarości (pastel na białym
     * → ciemny pastel na ciemnym), odcień zostaje, nasycenie najwyżej [SURFACE_C], żeby duża plama nie świeciła.
     * [mapInk] tu nie pasuje: nasycony kolor zachowuje w nim jasność, więc jasne tło zostawało jasne pod jaśniejącym tekstem.
     */
    fun mapSurface(argb: Int, src: Int, dst: Int, toDark: Boolean): Int {
        val o = oklab(argb)
        val lp = oklab(src)[0]; val lq = oklab(dst)[0]
        val nl = if (toDark) lq + max(0.0, lp - o[0]) * (INK_MAX - lq) / max(lp, 0.05)
            else lq - max(0.0, o[0] - lp) * (lq - INK_MIN) / max(1.0 - lp, 0.05)
        val c = hypot(o[1], o[2])
        val k = if (c > SURFACE_C) SURFACE_C / c else 1.0
        return fromOklab(nl, o[1] * k, o[2] * k, argb ushr 24)
    }

    /**
     * Tekst po zamianie [mapped] na tle [bg] (oba już zamienione, kryte): gdy kontrast WCAG spadł poniżej
     * min([TEXT_CONTRAST], kontrast oryginału [srcText] na [srcBg]), jasność tekstu odsuwa się od tła tylko o tyle,
     * ile trzeba; odcień zostaje.
     */
    fun mapTextOn(srcText: Int, srcBg: Int, mapped: Int, bg: Int): Int {
        val need = min(TEXT_CONTRAST, contrastWcag(opaque(srcText), srcBg))
        if (contrastWcag(opaque(mapped), bg) >= need - 1e-3) return mapped
        val o = oklab(mapped)
        val target = if (contrastWcag(WHITE, bg) >= contrastWcag(BLACK, bg)) 1.0 else 0.0
        fun at(t: Double) = fromOklab(o[0] + (target - o[0]) * t, o[1], o[2], mapped ushr 24)
        if (contrastWcag(opaque(at(1.0)), bg) < need) return at(1.0)
        var lo = 0.0; var hi = 1.0
        repeat(20) { val mid = (lo + hi) / 2; if (contrastWcag(opaque(at(mid)), bg) >= need) hi = mid else lo = mid }
        return at(hi)
    }

    /** Kontrast WCAG 2 (1–21) dwóch kolorów; krycie pomijane. */
    fun contrastWcag(a: Int, b: Int): Double {
        val la = luminance(a); val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    private fun luminance(c: Int): Double = 0.2126 * lin(c shr 16 and 255) + 0.7152 * lin(c shr 8 and 255) + 0.0722 * lin(c and 255)
    private fun opaque(c: Int) = c or (0xFF shl 24)

    /** [fg] nałożony na kryty [bg] (zwykłe mieszanie w sRGB, jak Canvas). */
    fun over(fg: Int, bg: Int): Int {
        val a = (fg ushr 24) / 255.0
        fun ch(sh: Int) = ((fg shr sh and 255) * a + (bg shr sh and 255) * (1 - a)).roundToInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /** Zakreślacz: na ciemnym papierze ciemniejszy i nasycony (nie świeci na biało), na jasnym — pastel. */
    fun mapHighlighter(argb: Int, toDark: Boolean): Int {
        val o = oklab(argb)
        val nl = if (toDark) min(o[0], HL_DARK_MAX) else max(o[0], HL_LIGHT_MIN)
        return fromOklab(nl, o[1], o[2], argb ushr 24)
    }

    /**
     * Macierz koloru (4×5, wartości 0–255) dla tła PDF i obrazów: odwrócenie jasności z obrotem barwy ([ColorInvert])
     * ściśnięte tak, by tło szło w nowy papier [dstPaper], a pismo w jasny (do ciemnego) albo ciemny (do jasnego) odcień.
     */
    fun imageMatrix(toDark: Boolean, dstPaper: Int): FloatArray {
        val m = ColorInvert.MATRIX
        val out = FloatArray(20)
        for (row in 0 until 3) {
            val p = (dstPaper shr (16 - 8 * row) and 255).toFloat()
            val lo = if (toDark) p else 30f
            val hi = if (toDark) 232f else p
            val f = (hi - lo) / 255f
            for (k in 0 until 3) out[5 * row + k] = m[5 * row + k] * f
            out[5 * row + 3] = 0f
            out[5 * row + 4] = m[5 * row + 4] * f + lo
        }
        out[18] = 1f
        return out
    }

    /** Kontrast jasności OKLab między dwoma kolorami (do testów i dokumentacji). */
    fun contrast(a: Int, b: Int): Double = abs(oklab(a)[0] - oklab(b)[0])
}

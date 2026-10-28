// Algorytmy kredy na podstawie programu kreda — autor i licencja do potwierdzenia przed publikacją (app/src/kreda/NOTICE.md).

package com.mdev.mnotes.core.image

import kotlin.math.max
import kotlin.math.min

/**
 * Obraz ARGB w pamięci (piksel = 0xAARRGGBB) bez zależności od Androida: na nim działa cała kreda i jej testy JVM.
 * Czysta logika (konwencja `core/`), ale w zbiorze źródeł `lab`, bo decyzja planu nocy: kreda tylko w wariancie lab.
 */
class Raster(val w: Int, val h: Int, val px: IntArray = IntArray(w * h)) {
    init {
        require(w > 0 && h > 0 && px.size == w * h) { "zły rozmiar obrazu ${w}×$h" }
    }

    operator fun get(x: Int, y: Int): Int = px[y * w + x]
    operator fun set(x: Int, y: Int, c: Int) { px[y * w + x] = c }
    fun copy() = Raster(w, h, px.copyOf())

    /** Kanał jasności 0..1 (lub inny wyliczany z piksela) jako płaska tablica. */
    fun plane(f: (Int) -> Float): FloatArray = FloatArray(px.size) { f(px[it]) }

    /** Pomniejszenie uśrednianiem pola (bez aliasingu) do [dw]×[dh]; kanał alfa uśredniany tak samo. */
    fun downscale(dw: Int, dh: Int): Raster {
        if (dw >= w && dh >= h) return copy()
        val out = IntArray(dw * dh)
        for (y in 0 until dh) {
            val y0 = y * h / dh
            val y1 = max(y0 + 1, (y + 1) * h / dh)
            for (x in 0 until dw) {
                val x0 = x * w / dw
                val x1 = max(x0 + 1, (x + 1) * w / dw)
                var sa = 0; var sr = 0; var sg = 0; var sb = 0
                for (yy in y0 until y1) for (xx in x0 until x1) {
                    val c = px[yy * w + xx]
                    sa += c ushr 24; sr += (c shr 16) and 255; sg += (c shr 8) and 255; sb += c and 255
                }
                val n = (y1 - y0) * (x1 - x0)
                out[y * dw + x] = argb((sa + n / 2) / n, (sr + n / 2) / n, (sg + n / 2) / n, (sb + n / 2) / n)
            }
        }
        return Raster(dw, dh, out)
    }

    /** Pomniejszenie tak, by dłuższy bok miał najwyżej [longest] pikseli (zdjęcia z telefonu są za duże do pracy). */
    fun fit(longest: Int): Raster {
        val m = max(w, h)
        if (m <= longest) return this
        val s = longest.toFloat() / m
        return downscale(max(1, (w * s).toInt()), max(1, (h * s).toInt()))
    }

    /** Wycinek prostokąta [x0, x1) × [y0, y1). */
    fun crop(x0: Int, y0: Int, x1: Int, y1: Int): Raster {
        val cw = x1 - x0; val ch = y1 - y0
        val out = IntArray(cw * ch)
        for (y in 0 until ch) System.arraycopy(px, (y0 + y) * w + x0, out, y * cw, cw)
        return Raster(cw, ch, out)
    }

    companion object {
        fun argb(a: Int, r: Int, g: Int, b: Int): Int = (a shl 24) or (r shl 16) or (g shl 8) or b
        fun q(v: Float): Int = (min(1f, max(0f, v)) * 255f + 0.5f).toInt()
        fun rgbF(r: Float, g: Float, b: Float, a: Float = 1f): Int = argb(q(a), q(r), q(g), q(b))
        fun alpha(c: Int): Int = c ushr 24
        fun red(c: Int): Int = (c shr 16) and 255
        fun green(c: Int): Int = (c shr 8) and 255
        fun blue(c: Int): Int = c and 255
        fun luma(c: Int): Float = (0.299f * red(c) + 0.587f * green(c) + 0.114f * blue(c)) / 255f
        fun maxRgb(c: Int): Float = max(red(c), max(green(c), blue(c))) / 255f
        fun minRgb(c: Int): Float = min(red(c), min(green(c), blue(c))) / 255f

        /** Jednolity obraz w kolorze [c]. */
        fun filled(w: Int, h: Int, c: Int) = Raster(w, h, IntArray(w * h) { c })
    }
}

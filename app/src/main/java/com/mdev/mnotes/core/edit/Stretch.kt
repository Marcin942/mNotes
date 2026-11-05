package com.mdev.mnotes.core.edit

import com.mdev.mnotes.core.geom.Affine
import com.mdev.mnotes.core.model.ImageEl
import com.mdev.mnotes.core.model.TextBox
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * LP-4: rozciąganie zaznaczenia w jedną oś (uchwyty w połowie boków ramki). Daje samo przekształcenie afiniczne:
 * elementy zmienia wspólna ścieżka `Element.transformed(m)`, więc rozciąganie działa tak samo jak skala i obrót.
 */
object Stretch {
    /** Najmniejszy i największy współczynnik jednego ruchu uchwytu (jak przy uchwytach narożnych). */
    const val MIN_FACTOR = 0.05f
    const val MAX_FACTOR = 30f

    /**
     * Skala o [k] wzdłuż kierunku ([nx], [ny]) (nie musi być jednostkowy) z punktem stałym ([ax], [ay]):
     * `I + (k − 1)·n·nᵀ`. Kierunek prostopadły zostaje bez zmian, więc działa dla ramki obróconej o dowolny kąt.
     */
    fun along(nx: Float, ny: Float, k: Float, ax: Float, ay: Float): Affine {
        val len = hypot(nx, ny)
        if (len < 1e-6f) return Affine.IDENTITY
        val ux = nx / len; val uy = ny / len
        val q = k - 1f
        val a = 1f + q * ux * ux
        val b = q * ux * uy
        val d = 1f + q * uy * uy
        return Affine(a, b, b, d, ax - (a * ax + b * ay), ay - (b * ax + d * ay))
    }

    /**
     * Współczynnik rozciągnięcia, gdy uchwyt zaczynał w ([hx], [hy]), kotwica (środek przeciwległego boku) leży w
     * ([ax], [ay]), a palec jest w ([px], [py]): rzut przesunięcia palca na oś kotwica–uchwyt.
     */
    fun factor(ax: Float, ay: Float, hx: Float, hy: Float, px: Float, py: Float): Float {
        val vx = hx - ax; val vy = hy - ay
        val d2 = (vx * vx + vy * vy).coerceAtLeast(1e-3f)
        return (((px - ax) * vx + (py - ay) * vy) / d2).coerceIn(MIN_FACTOR, MAX_FACTOR)
    }

    /** Przekształcenie uchwytu bocznego: rozciąga wzdłuż osi kotwica → uchwyt, kotwica stoi w miejscu. */
    fun handle(ax: Float, ay: Float, hx: Float, hy: Float, px: Float, py: Float): Affine =
        along(hx - ax, hy - ay, factor(ax, ay, hx, hy, px, py), ax, ay)

    /** Czy tekst jest rozciągnięty (z tolerancją na błąd zaokrągleń po kilku przekształceniach). */
    fun isStretched(t: TextBox): Boolean = abs(t.stretch - 1f) > EPS

    /**
     * Tekst bez rozciągnięcia, lewy-górny róg ramki (w jej osiach) zostaje w miejscu, a podział na wiersze się nie
     * zmienia. Poszerzony traci nadmiar szerokości (litery jak przed rozciągnięciem), zwężony — wysokość liter.
     */
    fun restoredText(t: TextBox): TextBox {
        if (!isStretched(t)) return t
        val s = t.stretch
        val (w, h, size) = if (s > 1f) Triple(t.w / s, t.h, t.size) else Triple(t.w, t.h * s, t.size * s)
        val (cx, cy) = keepCorner(t.cx, t.cy, t.w, t.h, t.rot, w, h)
        return t.copy(cx = cx, cy = cy, w = w, h = h, size = size, stretch = 1f)
    }

    /** Proporcje źródłowe obrazu (szerokość/wysokość pokazanego wycinka pliku [iw]×[ih] px). */
    fun sourceAspect(im: ImageEl, iw: Float, ih: Float): Float? {
        val sw = im.crop.width * iw; val sh = im.crop.height * ih
        return if (sw > 0f && sh > 0f) sw / sh else null
    }

    /** Czy ramka obrazu ma inne proporcje niż źródło. */
    fun isImageStretched(im: ImageEl, iw: Float, ih: Float): Boolean {
        val rs = sourceAspect(im, iw, ih) ?: return false
        if (im.w <= 0f || im.h <= 0f) return false
        return abs(im.w / im.h / rs - 1f) > EPS
    }

    /**
     * Obraz z proporcjami źródła: ramka kurczy się do największej, która mieści się w obecnej (nic nie wychodzi poza
     * dotychczasowe miejsce na stronie), a lewy-górny róg w osiach obrazu zostaje w miejscu.
     */
    fun restoredImage(im: ImageEl, iw: Float, ih: Float): ImageEl {
        if (!isImageStretched(im, iw, ih)) return im
        val rs = sourceAspect(im, iw, ih) ?: return im
        val (w, h) = if (im.w / im.h > rs) (im.h * rs) to im.h else im.w to (im.w / rs)
        val (cx, cy) = keepCorner(im.cx, im.cy, im.w, im.h, im.rot, w, h)
        return im.copy(cx = cx, cy = cy, w = w, h = h)
    }

    /** Nowy środek ramki o wymiarach [nw]×[nh], której lewy-górny róg (w osiach obróconych o [rot]) się nie rusza. */
    private fun keepCorner(cx: Float, cy: Float, w: Float, h: Float, rot: Float, nw: Float, nh: Float): Pair<Float, Float> {
        val c = cos(rot); val s = sin(rot)
        val dw = (nw - w) * 0.5f; val dh = (nh - h) * 0.5f
        return (cx + c * dw - s * dh) to (cy + s * dw + c * dh)
    }

    private const val EPS = 1e-3f
}

package com.mdev.mnotes.core.edit

import com.mdev.mnotes.core.model.ImageCrop
import com.mdev.mnotes.core.model.ImageEl
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Ramka przycięcia w pikselach pliku: środek, połowy boków w jej własnych osiach i kąt tych osi względem pliku. */
class CropFrame(val cx: Float, val cy: Float, val hw: Float, val hh: Float, val a: Float) {
    fun copy(cx: Float = this.cx, cy: Float = this.cy, hw: Float = this.hw, hh: Float = this.hh, a: Float = this.a) =
        CropFrame(cx, cy, hw, hh, a)
}

/** Uchwyt ramki: [sx], [sy] to strona (-1, 0, 1) w jej własnych osiach; narożniki mają oba, krawędzie jeden. */
enum class CropHandle(val sx: Int, val sy: Int) {
    TL(-1, -1), T(0, -1), TR(1, -1), R(1, 0), BR(1, 1), B(0, 1), BL(-1, 1), L(-1, 0)
}

/**
 * Położenie pliku na stronie podczas przycinania: środek pliku leży w ([ox], [oy]), skala to [s] punktów strony
 * na piksel pliku, a sam plik jest obrócony o [phi] (zgodnie z ruchem wskazówek zegara na ekranie).
 */
class CropPlace(val ox: Float, val oy: Float, val s: Float, val phi: Float) {
    fun toPage(fx: Float, fy: Float, iw: Float, ih: Float): FloatArray {
        val dx = (fx - iw * 0.5f) * s; val dy = (fy - ih * 0.5f) * s
        val c = cos(phi); val sn = sin(phi)
        return floatArrayOf(ox + dx * c - dy * sn, oy + dx * sn + dy * c)
    }

    fun toFile(px: Float, py: Float, iw: Float, ih: Float): FloatArray {
        val dx = px - ox; val dy = py - oy
        val c = cos(phi); val sn = sin(phi)
        return floatArrayOf((dx * c + dy * sn) / s + iw * 0.5f, (-dx * sn + dy * c) / s + ih * 0.5f)
    }

    /** To samo położenie po obróceniu pliku do kąta [newPhi] tak, by punkt pliku ([fx], [fy]) został na swoim miejscu strony. */
    fun turned(newPhi: Float, fx: Float, fy: Float, iw: Float, ih: Float): CropPlace {
        val p = toPage(fx, fy, iw, ih)
        val dx = (fx - iw * 0.5f) * s; val dy = (fy - ih * 0.5f) * s
        val c = cos(newPhi); val sn = sin(newPhi)
        return CropPlace(p[0] - (dx * c - dy * sn), p[1] - (dx * sn + dy * c), s, newPhi)
    }
}

/**
 * Przycinanie obrazu (czysta logika). Wycinek to prostokąt w pikselach pliku obrócony o kąt wokół środka
 * ([ImageCrop]); podczas edycji ramka stoi prosto na stronie, a plik pod nią się obraca ([CropPlace]).
 * Ramka zawsze mieści się w pliku (z marginesem na próbkowanie przy ukośnych brzegach), więc eksport nigdy
 * nie pokazuje przezroczystych narożników.
 */
object CropGeom {
    private const val TWO_PI = (2.0 * PI).toFloat()
    const val HALF_PI = (PI / 2.0).toFloat()

    /** Margines od brzegu pliku (px) dla ramek ukośnych: bez niego filtr dwuliniowy mieszałby brzeg z przezroczystością. */
    const val MARGIN = 1f

    /** Najmniejsza ramka na stronie (pt): mniejszej nie da się uchwycić palcem. */
    const val MIN_SIZE_PT = 24f

    private const val EPS = 1e-3f

    fun frameOf(c: ImageCrop, iw: Float, ih: Float) =
        CropFrame((c.l + c.r) * 0.5f * iw, (c.t + c.b) * 0.5f * ih, c.width * iw * 0.5f, c.height * ih * 0.5f, c.a)

    /** Ramka obejmująca cały plik, bez kąta. */
    fun full(iw: Float, ih: Float) = CropFrame(iw * 0.5f, ih * 0.5f, iw * 0.5f, ih * 0.5f, 0f)

    fun normAngle(a: Float): Float {
        var x = a % TWO_PI
        if (x > PI.toFloat()) x -= TWO_PI
        if (x <= -PI.toFloat()) x += TWO_PI
        return x
    }

    /**
     * Ćwierć obrotu, wokół której prostuje się ramka o kącie [a]: odchylenie mieści się w (-45°, 45°]. Liczone raz
     * na początku przycinania; potem zmieniają je tylko przyciski 90°, żeby suwak przy ±45° nie przeskakiwał.
     */
    fun quarterOf(a: Float): Int = ceil(a / HALF_PI - 0.5f).toInt().mod(4)

    /** Odchylenie kąta [a] od ćwierci obrotu [q], w (-π, π]. */
    fun tiltOf(a: Float, q: Int): Float = normAngle(a - q * HALF_PI)

    /** Czy ramka ma boki równoległe do boków pliku (też po obrocie o ćwierć obrotu). */
    fun isAxisAligned(a: Float): Boolean = abs(sin(a)) < 1e-4f || abs(cos(a)) < 1e-4f

    private fun margin(a: Float) = if (isAxisAligned(a)) 0f else MARGIN

    /** Połowa szerokości prostokąta opisanego na ramce w osiach pliku. */
    fun extentX(f: CropFrame) = f.hw * abs(cos(f.a)) + f.hh * abs(sin(f.a))

    /** Połowa wysokości prostokąta opisanego na ramce w osiach pliku. */
    fun extentY(f: CropFrame) = f.hw * abs(sin(f.a)) + f.hh * abs(cos(f.a))

    fun fits(f: CropFrame, iw: Float, ih: Float): Boolean {
        val m = margin(f.a) - EPS
        val ex = extentX(f); val ey = extentY(f)
        return f.cx - ex >= m && f.cx + ex <= iw - m && f.cy - ey >= m && f.cy + ey <= ih - m
    }

    /**
     * Najbliższa ramka, która mieści się w pliku: najpierw zmniejsza ją wokół środka (tylko gdy jej obrys jest
     * większy od pliku), potem przesuwa środek do wnętrza. Rozmiar zostaje, gdy wystarczy przesunięcie.
     */
    fun fitted(f: CropFrame, iw: Float, ih: Float): CropFrame {
        val m = margin(f.a)
        val ex = extentX(f); val ey = extentY(f)
        val k = min(1f, min((iw - 2f * m) / (2f * ex), (ih - 2f * m) / (2f * ey)))
        val g = if (k < 1f) f.copy(hw = f.hw * k, hh = f.hh * k) else f
        val nx = extentX(g); val ny = extentY(g)
        return g.copy(cx = clampMid(g.cx, m + nx, iw - m - nx), cy = clampMid(g.cy, m + ny, ih - m - ny))
    }

    /** Jak `coerceIn`, ale gdy zaokrąglenie zrobi zakres pusty (ramka tak duża jak plik), daje jego środek zamiast wyjątku. */
    private fun clampMid(v: Float, lo: Float, hi: Float) = if (lo > hi) (lo + hi) * 0.5f else v.coerceIn(lo, hi)

    /** Ramka przesunięta o ([dx], [dy]) pikseli pliku, zatrzymana na brzegu pliku. */
    fun moved(f: CropFrame, dx: Float, dy: Float, iw: Float, ih: Float): CropFrame =
        fitted(f.copy(cx = f.cx + dx, cy = f.cy + dy), iw, ih)

    /**
     * Ramka po przeciągnięciu uchwytu [h] do punktu pliku ([px], [py]): przeciwległy bok (narożnik) stoi w miejscu.
     * [minHalf] to najmniejsza połowa boku, [ratio] (szerokość/wysokość) blokuje proporcje. Gdy cel wychodzi poza
     * plik, ramka rośnie tak daleko, jak się da.
     */
    fun resized(f: CropFrame, h: CropHandle, px: Float, py: Float, iw: Float, ih: Float, minHalf: Float, ratio: Float? = null): CropFrame {
        val c = cos(f.a); val sn = sin(f.a)
        val dx = px - f.cx; val dy = py - f.cy
        val u = dx * c + dy * sn
        val v = -dx * sn + dy * c
        var lo = -f.hw; var hi = f.hw; var vlo = -f.hh; var vhi = f.hh
        val min2 = 2f * minHalf
        if (h.sx < 0) lo = min(u, hi - min2) else if (h.sx > 0) hi = max(u, lo + min2)
        if (h.sy < 0) vlo = min(v, vhi - min2) else if (h.sy > 0) vhi = max(v, vlo + min2)
        if (ratio != null && ratio > 0f) {
            var w = hi - lo; var g = vhi - vlo
            if (h.sx != 0 && h.sy != 0) { if (w / g > ratio) g = w / ratio else w = g * ratio }
            else if (h.sx != 0) g = w / ratio
            else w = g * ratio
            val grow = max(1f, max(min2 / w, min2 / g))
            w *= grow; g *= grow
            if (h.sx < 0) lo = hi - w else if (h.sx > 0) hi = lo + w else { val m = (lo + hi) * 0.5f; lo = m - w * 0.5f; hi = m + w * 0.5f }
            if (h.sy < 0) vlo = vhi - g else if (h.sy > 0) vhi = vlo + g else { val m = (vlo + vhi) * 0.5f; vlo = m - g * 0.5f; vhi = m + g * 0.5f }
        }
        val mu = (lo + hi) * 0.5f; val mv = (vlo + vhi) * 0.5f
        val target = CropFrame(f.cx + mu * c - mv * sn, f.cy + mu * sn + mv * c, (hi - lo) * 0.5f, (vhi - vlo) * 0.5f, f.a)
        if (fits(target, iw, ih)) return target
        if (!fits(f, iw, ih)) return fitted(target, iw, ih)
        // zbiór dozwolonych ramek (środek, połowy boków) jest wypukły, więc największy dozwolony krok znajdzie połowienie
        var okT = 0f; var badT = 1f
        repeat(24) {
            val t = (okT + badT) * 0.5f
            if (fits(lerp(f, target, t), iw, ih)) okT = t else badT = t
        }
        return lerp(f, target, okT)
    }

    private fun lerp(a: CropFrame, b: CropFrame, t: Float) = CropFrame(
        a.cx + (b.cx - a.cx) * t, a.cy + (b.cy - a.cy) * t, a.hw + (b.hw - a.hw) * t, a.hh + (b.hh - a.hh) * t, a.a,
    )

    /** Ramka o tym samym środku i polu, ale z proporcjami [ratio] (szerokość/wysokość), wpasowana w plik. */
    fun withRatio(f: CropFrame, ratio: Float, iw: Float, ih: Float): CropFrame {
        val hw = sqrt(f.hw * f.hh * ratio)
        return fitted(f.copy(hw = hw, hh = hw / ratio), iw, ih)
    }

    /**
     * Ramka na ćwierć obrotu: ten sam fragment pliku, tylko opisany w osiach obróconych o 90° (boki zamieniają się
     * miejscami), więc na stronie stoi prosto, a zawartość obraca się o ćwierć obrotu.
     */
    fun quarter(f: CropFrame, clockwise: Boolean) =
        CropFrame(f.cx, f.cy, f.hh, f.hw, normAngle(if (clockwise) f.a - HALF_PI else f.a + HALF_PI))

    /** Wycinek do zapisu w obrazie; krawędzie bliskie brzegu nieobróconego pliku przyciąga do niego. */
    fun cropOf(f: CropFrame, iw: Float, ih: Float): ImageCrop {
        val a = normAngle(f.a).let { if (abs(it) < 1e-5f) 0f else it }
        var l = f.cx - f.hw; var t = f.cy - f.hh; var r = f.cx + f.hw; var b = f.cy + f.hh
        if (a == 0f) {
            if (l < 0.5f) l = 0f
            if (t < 0.5f) t = 0f
            if (r > iw - 0.5f) r = iw
            if (b > ih - 0.5f) b = ih
        }
        return if (a == 0f && l <= 0f && t <= 0f && r >= iw && b >= ih) ImageCrop.FULL
        else ImageCrop(l / iw, t / ih, r / iw, b / ih, a)
    }

    /** Położenie pliku dla obrazu, tak że ramka z jego wycinka leży dokładnie tam, gdzie obraz stoi na stronie. */
    fun placeOf(im: ImageEl, iw: Float, ih: Float): CropPlace {
        val f = frameOf(im.crop, iw, ih)
        val s = im.w / (2f * f.hw)
        val phi = im.rot - f.a
        val c = cos(phi); val sn = sin(phi)
        val dx = (f.cx - iw * 0.5f) * s; val dy = (f.cy - ih * 0.5f) * s
        return CropPlace(im.cx - (dx * c - dy * sn), im.cy - (dx * sn + dy * c), s, phi)
    }

    /** Obraz z nową ramką: to, co jest w ramce, zostaje na stronie tam, gdzie je widać podczas edycji. */
    fun applied(im: ImageEl, f: CropFrame, p: CropPlace, iw: Float, ih: Float): ImageEl {
        val crop = cropOf(f, iw, ih)
        val g = if (crop.isFull) full(iw, ih) else frameOf(crop, iw, ih)
        val at = p.toPage(g.cx, g.cy, iw, ih)
        return im.copy(cx = at[0], cy = at[1], w = 2f * g.hw * p.s, h = 2f * g.hh * p.s, rot = normAngle(p.phi + g.a), crop = crop)
    }

    /** Cztery narożniki ramki w pikselach pliku, zgodnie z ruchem wskazówek zegara od lewego górnego (w osiach ramki). */
    fun corners(f: CropFrame): FloatArray {
        val c = cos(f.a); val sn = sin(f.a)
        val out = FloatArray(8)
        val sx = intArrayOf(-1, 1, 1, -1); val sy = intArrayOf(-1, -1, 1, 1)
        for (i in 0 until 4) {
            val u = sx[i] * f.hw; val v = sy[i] * f.hh
            out[i * 2] = f.cx + u * c - v * sn
            out[i * 2 + 1] = f.cy + u * sn + v * c
        }
        return out
    }
}

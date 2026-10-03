package com.mdev.mnotes.core.pen

import com.mdev.mnotes.core.geom.FloatList
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Zamienia kreskę o zmiennej grubości (trójki x, y, p) na zamknięty wielokąt (x, y …) z okrągłymi końcami.
 * Wynik wypełnia się regułą nonzero, więc drobne pętle po wewnętrznej stronie ostrych zakrętów są niewidoczne.
 */
object StrokeOutline {
    private const val CAP_SEGMENTS = 8

    /** Czy wszystkie współczynniki są (prawie) równe — wtedy tańsza jest gruba linia z okrągłymi końcami. */
    fun isConstant(pts: FloatArray, n: Int = pts.size / 3): Boolean {
        if (n < 2) return true
        val p0 = pts[2]
        for (i in 1 until n) if (kotlin.math.abs(pts[3 * i + 2] - p0) > 0.012f) return false
        return true
    }

    fun build(pts: FloatArray, n: Int, width: Float, out: FloatList) {
        out.clear()
        if (n <= 0) return
        // Odfiltrowanie powtórzonych punktów (zerowa długość psuje styczne).
        val xs = FloatArray(n); val ys = FloatArray(n); val hs = FloatArray(n)
        var m = 0
        for (i in 0 until n) {
            val x = pts[3 * i]; val y = pts[3 * i + 1]
            if (m > 0 && hypot(x - xs[m - 1], y - ys[m - 1]) < 1e-3f) {
                hs[m - 1] = maxOf(hs[m - 1], pts[3 * i + 2] * width * 0.5f)
                continue
            }
            xs[m] = x; ys[m] = y; hs[m] = pts[3 * i + 2] * width * 0.5f
            m++
        }
        if (m == 1) { circle(xs[0], ys[0], hs[0], out); return }

        val nx = FloatArray(m); val ny = FloatArray(m)
        for (i in 0 until m) {
            val a = if (i == 0) 0 else i - 1
            val b = if (i == m - 1) m - 1 else i + 1
            var tx = xs[b] - xs[a]; var ty = ys[b] - ys[a]
            val l = hypot(tx, ty)
            if (l > 0f) { tx /= l; ty /= l } else { tx = 1f; ty = 0f }
            nx[i] = -ty; ny[i] = tx
        }
        for (i in 0 until m) out.add(xs[i] + nx[i] * hs[i], ys[i] + ny[i] * hs[i])
        cap(xs[m - 1], ys[m - 1], hs[m - 1], nx[m - 1], ny[m - 1], out, end = true)
        for (i in m - 1 downTo 0) out.add(xs[i] - nx[i] * hs[i], ys[i] - ny[i] * hs[i])
        cap(xs[0], ys[0], hs[0], nx[0], ny[0], out, end = false)
    }

    /** Półokrąg zamykający koniec. Styczna t = (ny, -nx), bo n = (-ty, tx). */
    private fun cap(x: Float, y: Float, h: Float, nx: Float, ny: Float, out: FloatList, end: Boolean) {
        val tx = ny; val ty = -nx
        val s = if (end) 1f else -1f
        for (k in 1 until CAP_SEGMENTS) {
            val a = PI.toFloat() * k / CAP_SEGMENTS
            val c = cos(a); val sn = sin(a)
            // Koniec: od lewej (n) przez przód (t) do prawej (−n). Początek: od prawej (−n) przez tył (−t) do lewej (n).
            val px = s * (nx * c + tx * sn)
            val py = s * (ny * c + ty * sn)
            out.add(x + h * px, y + h * py)
        }
    }

    private fun circle(x: Float, y: Float, r: Float, out: FloatList) {
        val seg = 16
        for (k in 0 until seg) {
            val a = 2f * PI.toFloat() * k / seg
            out.add(x + r * cos(a), y + r * sin(a))
        }
    }
}

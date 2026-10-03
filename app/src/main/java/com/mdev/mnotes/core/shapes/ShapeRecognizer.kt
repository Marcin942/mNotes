package com.mdev.mnotes.core.shapes

import com.mdev.mnotes.core.geom.GeomMath
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

enum class ShapeKind { LINE, ELLIPSE, RECT, TRIANGLE, ARROW }

/** Wynik rozpoznania: rodzaj i gotowa łamana (x, y …) do zapisania jako kreska. */
class Shape(val kind: ShapeKind, val xy: FloatArray)

/**
 * Autokorekta „rysuj i przytrzymaj”: z odręcznej łamanej (x, y …, w pt) wykrywa prostą linię, elipsę, prostokąt,
 * trójkąt i strzałkę. Zwraca null, gdy kształt jest niejednoznaczny — wtedy zostaje odręczna kreska.
 */
object ShapeRecognizer {

    fun recognize(xy: FloatArray, n: Int = xy.size / 2): Shape? {
        if (n < 4) return null
        val len = GeomMath.polylineLength(xy, n)
        if (len < 18f) return null
        var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
        for (i in 0 until n) {
            l = min(l, xy[2 * i]); r = max(r, xy[2 * i]); t = min(t, xy[2 * i + 1]); b = max(b, xy[2 * i + 1])
        }
        val diag = hypot(r - l, b - t)
        if (diag < 14f) return null
        val gap = GeomMath.dist(xy[0], xy[1], xy[2 * n - 2], xy[2 * n - 1])
        val closed = gap < 0.22f * len && len > 2.2f * gap

        if (!closed) {
            line(xy, n, len, diag)?.let { return it }
            arrow(xy, n, len)?.let { return it }
            return null
        }
        polygon(xy, n, diag)?.let { return it }
        return ellipse(xy, n, l, t, r, b)
    }

    private fun line(xy: FloatArray, n: Int, len: Float, diag: Float): Shape? {
        if (len > 1.35f * diag) return null
        val idx = GeomMath.simplify(xy, n, max(2.5f, 0.045f * len))
        if (idx.size != 2) return null
        var x1 = xy[0]; var y1 = xy[1]; var x2 = xy[2 * n - 2]; var y2 = xy[2 * n - 1]
        // Przyciągnięcie do 0°, 45°, 90° w granicach 4°.
        val ang = atan2(y2 - y1, x2 - x1)
        val step = (PI / 4).toFloat()
        val snapped = (ang / step).roundToInt() * step
        if (abs(ang - snapped) < Math.toRadians(4.0)) {
            val l = hypot(x2 - x1, y2 - y1)
            val mx = (x1 + x2) / 2f; val my = (y1 + y2) / 2f
            x1 = mx - cos(snapped) * l / 2f; y1 = my - sin(snapped) * l / 2f
            x2 = mx + cos(snapped) * l / 2f; y2 = my + sin(snapped) * l / 2f
        }
        return Shape(ShapeKind.LINE, floatArrayOf(x1, y1, x2, y2))
    }

    /** Strzałka: trzon (najdłuższy odcinek), po nim ruch z powrotem do grotu i skrzydełka przy czubku. */
    private fun arrow(xy: FloatArray, n: Int, len: Float): Shape? {
        val idx = GeomMath.simplify(xy, n, max(2.5f, 0.05f * len))
        if (idx.size !in 4..6) return null
        val px = FloatArray(idx.size) { xy[2 * idx[it]] }
        val py = FloatArray(idx.size) { xy[2 * idx[it] + 1] }
        val shaft = GeomMath.dist(px[0], py[0], px[1], py[1])
        if (shaft < 0.55f * len) return null
        for (i in 2 until idx.size) {
            if (GeomMath.dist(px[i], py[i], px[1], py[1]) > 0.4f * shaft) return null
        }
        val ux = (px[1] - px[0]) / shaft; val uy = (py[1] - py[0]) / shaft
        val wing = (0.22f * shaft).coerceIn(8f, 26f)
        val a = (28.0 * PI / 180.0).toFloat()
        fun w(sign: Float): Pair<Float, Float> {
            val c = cos(a * sign); val s = sin(a * sign)
            // Wektor wstecz (−u) obrócony o ±28°.
            val bx = -ux * c + uy * s
            val by = -ux * s - uy * c
            return (px[1] + bx * wing) to (py[1] + by * wing)
        }
        val (w1x, w1y) = w(1f); val (w2x, w2y) = w(-1f)
        return Shape(
            ShapeKind.ARROW,
            floatArrayOf(px[0], py[0], px[1], py[1], w1x, w1y, px[1], py[1], w2x, w2y),
        )
    }

    private fun polygon(xy: FloatArray, n: Int, diag: Float): Shape? {
        // Domknięcie i uproszczenie z tolerancją zależną od rozmiaru.
        val cx = FloatArray(2 * (n + 1))
        System.arraycopy(xy, 0, cx, 0, 2 * n)
        cx[2 * n] = xy[0]; cx[2 * n + 1] = xy[1]
        val idx = GeomMath.simplify(cx, n + 1, max(3f, 0.07f * diag))
        val k = idx.size - 1 // liczba wierzchołków bez powtórzonego pierwszego
        if (k != 3 && k != 4) return null
        val vx = FloatArray(k) { cx[2 * idx[it]] }
        val vy = FloatArray(k) { cx[2 * idx[it] + 1] }
        if (k == 3) {
            // Każdy bok musi mieć sensowną długość względem przekątnej.
            for (i in 0 until 3) if (GeomMath.dist(vx[i], vy[i], vx[(i + 1) % 3], vy[(i + 1) % 3]) < 0.2f * diag) return null
            return Shape(ShapeKind.TRIANGLE, floatArrayOf(vx[0], vy[0], vx[1], vy[1], vx[2], vy[2], vx[0], vy[0]))
        }
        // Czworokąt: kąty ≈ 90°, przeciwległe boki podobne.
        for (i in 0 until 4) {
            val a = (i + 3) % 4; val c = (i + 1) % 4
            val ang = GeomMath.angleBetween(vx[a] - vx[i], vy[a] - vy[i], vx[c] - vx[i], vy[c] - vy[i])
            if (abs(ang - PI.toFloat() / 2f) > Math.toRadians(24.0)) return null
        }
        val s0 = GeomMath.dist(vx[0], vy[0], vx[1], vy[1]); val s2 = GeomMath.dist(vx[2], vy[2], vx[3], vy[3])
        val s1 = GeomMath.dist(vx[1], vy[1], vx[2], vy[2]); val s3 = GeomMath.dist(vx[3], vy[3], vx[0], vy[0])
        if (abs(s0 - s2) > 0.35f * max(s0, s2) || abs(s1 - s3) > 0.35f * max(s1, s3)) return null
        val mcx = vx.average().toFloat(); val mcy = vy.average().toFloat()
        val w = (s0 + s2) / 2f; val h = (s1 + s3) / 2f
        // Kąt boku 0→1 sprowadzony do [−45°, 45°]; nieparzysta liczba ćwiartek = ten bok leży „pionowo”.
        val q = (PI / 2).toFloat()
        val raw = atan2(vy[1] - vy[0], vx[1] - vx[0])
        val turns = (raw / q).roundToInt()
        var th = raw - turns * q
        val ww = if (turns % 2 != 0) h else w
        val hh = if (turns % 2 != 0) w else h
        if (abs(th) < Math.toRadians(6.0)) th = 0f
        val c = cos(th); val s = sin(th)
        fun pt(sx: Float, sy: Float) = floatArrayOf(mcx + sx * ww / 2f * c - sy * hh / 2f * s, mcy + sx * ww / 2f * s + sy * hh / 2f * c)
        val a = pt(-1f, -1f); val b = pt(1f, -1f); val d = pt(1f, 1f); val e = pt(-1f, 1f)
        return Shape(ShapeKind.RECT, floatArrayOf(a[0], a[1], b[0], b[1], d[0], d[1], e[0], e[1], a[0], a[1]))
    }

    /** Elipsa dopasowana do prostokąta otaczającego; akceptowana, gdy punkty leżą blisko niej. */
    private fun ellipse(xy: FloatArray, n: Int, l: Float, t: Float, r: Float, b: Float): Shape? {
        val rx = (r - l) / 2f; val ry = (b - t) / 2f
        if (rx < 6f || ry < 6f) return null
        val cx = (l + r) / 2f; val cy = (t + b) / 2f
        var sum = 0f; var sum2 = 0f
        for (i in 0 until n) {
            val dx = (xy[2 * i] - cx) / rx; val dy = (xy[2 * i + 1] - cy) / ry
            val d = sqrt(dx * dx + dy * dy)
            sum += d; sum2 += d * d
        }
        val mean = sum / n
        val sd = sqrt(max(0f, sum2 / n - mean * mean))
        if (abs(mean - 1f) > 0.2f || sd > 0.16f) return null
        val seg = 48
        val out = FloatArray(2 * (seg + 1))
        for (k in 0..seg) {
            val a = 2f * PI.toFloat() * k / seg
            out[2 * k] = cx + rx * cos(a); out[2 * k + 1] = cy + ry * sin(a)
        }
        return Shape(ShapeKind.ELLIPSE, out)
    }
}

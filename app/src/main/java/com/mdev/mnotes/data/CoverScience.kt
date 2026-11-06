package com.mdev.mnotes.data

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin

/**
 * Wzory okładek z nauk ścisłych: [CoverStyle.INTEGRALS] (znak całki i pole pod krzywą z sumą Riemanna),
 * [CoverStyle.MOLECULES] (styren i kofeina we wzorach szkieletowych) oraz [CoverStyle.DECAY] (emisja cząstki alfa
 * z jądra i krzywa rozpadu z kolejnymi czasami połowicznego zaniku). Logika czysta i deterministyczna;
 * rysuje ją `CoverArt` (Compose), a podgląd w testach AWT.
 *
 * Współrzędne: x jako ułamek szerokości okładki, y jako wielokrotność szerokości (y ∈ [0, ar], gdzie ar = wysokość/szerokość),
 * więc koła pozostają kołami przy każdej proporcji. Wszystkie długości (promienie, grubości) są w szerokościach okładki.
 * Pas grzbietu (x < [SPINE]) i środek okładki pod etykietą tytułu zostają puste.
 */
object CoverScience {
    /** Łamana ([pts] = x0,y0,x1,y1…): obrys linią [width] albo wypełnienie ([fill]); [a] to krycie względne 0..1. */
    class Poly(val pts: FloatArray, val closed: Boolean, val fill: Boolean, val width: Float, val a: Float, val dash: Boolean = false)

    /** Koło o środku ([x], [y]) i promieniu [r]: wypełnione albo sam obrys o grubości [width]. */
    class Disc(val x: Float, val y: Float, val r: Float, val fill: Boolean, val width: Float, val a: Float)

    class Layout(val polys: List<Poly>, val discs: List<Disc>)

    const val SPINE = 0.14f

    private val cache = HashMap<Int, Layout>()

    /** Układ dla [style] na okładce o proporcji [ar] (wysokość/szerokość); proporcja jest zaokrąglana do 0,05. */
    fun layout(style: Int, ar: Float): Layout {
        val q = (ar.coerceIn(0.9f, 2.2f) * 20f + 0.5f).toInt()
        return synchronized(cache) { cache.getOrPut(style * 1000 + q) { build(style, q / 20f) } }
    }

    private fun build(style: Int, ar: Float): Layout {
        val b = Builder()
        when (style) {
            CoverStyle.INTEGRALS -> integrals(b, ar)
            CoverStyle.MOLECULES -> molecules(b, ar)
            CoverStyle.DECAY -> decay(b, ar)
        }
        return Layout(b.polys, b.discs)
    }

    // ─────────────── Całki ───────────────

    private fun integrals(b: Builder, ar: Float) {
        integralSign(b, 0.70f, 0.20f, 0.155f, 1f)
        integralSign(b, 0.31f, 0.22f, 0.085f, 0.6f)

        // Wykres: oś, krzywa f(u) = exp(−((u−½)/0,27)²) i suma Riemanna (środki przedziałów) między a i b.
        val ox = 0.21f; val oy = ar - 0.15f; val wx = 0.70f; val hy = 0.40f
        fun f(u: Float) = exp(-((u - 0.5f) / 0.27f).pow(2))
        fun px(u: Float) = ox + u * wx
        fun py(u: Float) = oy - f(u) * hy
        val ua = 0.20f; val ub = 0.80f; val n = 9
        val bars = ArrayList<Float>()
        val step = (ub - ua) / n
        bars += px(ua); bars += oy
        val tops = FloatArray(n) { py(ua + (it + 0.5f) * step) }
        for (i in 0 until n) { bars += px(ua + i * step); bars += tops[i]; bars += px(ua + (i + 1) * step); bars += tops[i] }
        bars += px(ub); bars += oy
        b.polys += Poly(bars.toFloatArray(), closed = true, fill = true, width = 0f, a = 0.20f)
        b.polys += Poly(bars.toFloatArray(), closed = false, fill = false, width = 0.0045f, a = 0.55f)
        for (i in 1 until n) {
            val x = px(ua + i * step)
            b.line(x, oy, x, minOf(tops[i - 1], tops[i]), 0.003f, 0.35f)
        }
        val curve = FloatArray(2 * 41)
        for (k in 0..40) { val u = k / 40f; curve[2 * k] = px(u); curve[2 * k + 1] = py(u) }
        b.polys += Poly(curve, closed = false, fill = false, width = 0.0085f, a = 1f)
        axes(b, ox - 0.03f, oy, ox + wx + 0.04f, oy - hy - 0.08f, ox)
        for (u in listOf(ua, ub)) b.line(px(u), oy - 0.012f, px(u), oy + 0.022f, 0.0055f, 0.9f)
    }

    /** Znak ∫ pisany piórem: pochylony rdzeń z haczykami i kulkami na końcach, grubszy w środku. */
    private fun integralSign(b: Builder, cx: Float, cy: Float, half: Float, a: Float) {
        val p = ArrayList<FloatArray>()
        fun cubic(x0: Float, y0: Float, x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
            for (k in 0..14) {
                val t = k / 15f; val u = 1 - t
                p += floatArrayOf(u * u * u * x0 + 3 * u * u * t * x1 + 3 * u * t * t * x2 + t * t * t * x3,
                    u * u * u * y0 + 3 * u * u * t * y1 + 3 * u * t * t * y2 + t * t * t * y3)
            }
        }
        cubic(-0.60f, 0.64f, -0.52f, 1.00f, -0.04f, 1.04f, 0.00f, 0.52f)
        cubic(0.00f, 0.52f, 0.02f, 0.18f, -0.02f, -0.18f, 0.00f, -0.52f)
        cubic(0.00f, -0.52f, 0.04f, -1.04f, 0.52f, -1.00f, 0.60f, -0.64f)
        p += floatArrayOf(0.60f, -0.64f)
        val pts = p.map { floatArrayOf(cx + (it[0] - 0.20f * it[1]) * half, cy + it[1] * half) }
        val m = pts.size
        val left = ArrayList<Float>(); val right = ArrayList<Float>()
        for (i in 0 until m) {
            val j0 = maxOf(i - 1, 0); val j1 = minOf(i + 1, m - 1)
            var dx = pts[j1][0] - pts[j0][0]; var dy = pts[j1][1] - pts[j0][1]
            val len = hypot(dx, dy).coerceAtLeast(1e-6f); dx /= len; dy /= len
            val t = i / (m - 1f)
            val hw = half * (0.012f + 0.052f * sin(PI.toFloat() * t).coerceAtLeast(0f).pow(0.7f))
            left += pts[i][0] - dy * hw; left += pts[i][1] + dx * hw
            right += pts[i][0] + dy * hw; right += pts[i][1] - dx * hw
        }
        val out = ArrayList<Float>(left)
        for (i in m - 1 downTo 0) { out += right[2 * i]; out += right[2 * i + 1] }
        b.polys += Poly(out.toFloatArray(), closed = true, fill = true, width = 0f, a = a)
        b.discs += Disc(pts.first()[0], pts.first()[1], half * 0.085f, true, 0f, a)
        b.discs += Disc(pts.last()[0], pts.last()[1], half * 0.085f, true, 0f, a)
    }

    // ─────────────── Molekuły ───────────────

    private class Atom(val x: Float, val y: Float, val label: Char? = null)

    private fun molecules(b: Builder, ar: Float) {
        // Styren: pierścień benzenowy (wiązania na przemian pojedyncze i podwójne) z grupą winylową.
        run {
            val s = 0.088f; val cx = 0.42f; val cy = 0.215f
            val v = (0 until 6).map { Atom(cx + s * cosD(-90 + 60 * it), cy + s * sinD(-90 + 60 * it)) }
            for (i in 0 until 6) {
                b.bond(v[i], v[(i + 1) % 6], s)
                if (i % 2 == 0) b.inner(v[i], v[(i + 1) % 6], cx, cy, s * 0.17f, s * 0.15f)
            }
            val ca = Atom(v[1].x + s * cosD(-30), v[1].y + s * sinD(-30))
            val cb = Atom(ca.x + s * cosD(30), ca.y + s * sinD(30))
            b.bond(v[1], ca, s); b.bond(ca, cb, s, double = true)
        }
        // Kofeina (1,3,7-trimetyloksantyna): sześcio- i pięcioczłonowy pierścień purynowy, dwie grupy C=O, trzy grupy metylowe.
        run {
            val s = 0.118f; val cx = 0.50f; val cy = ar - 0.315f
            fun hv(deg: Int, label: Char? = null) = Atom(cx + s * cosD(deg), cy + s * sinD(deg), label)
            val c6 = hv(-90); val c5 = hv(-30); val c4 = hv(30); val n3 = hv(90, 'N'); val c2 = hv(150); val n1 = hv(210, 'N')
            val apo = s / (2f * kotlin.math.tan(PI.toFloat() / 5f))
            val px = cx + s * 0.8660254f + apo; val py = cy
            val rr = s / (2f * sin(PI.toFloat() / 5f))
            fun pv(deg: Int, label: Char? = null) = Atom(px + rr * cosD(deg), py + rr * sinD(deg), label)
            val n7 = pv(-72, 'N'); val c8 = pv(0); val n9 = pv(72, 'N')
            fun out(from: Atom, deg: Int, label: Char? = null) = Atom(from.x + s * cosD(deg), from.y + s * sinD(deg), label)
            b.bond(c6, c5, s); b.bond(c5, c4, s, innerToward = px to py); b.bond(c4, n3, s)
            b.bond(n3, c2, s); b.bond(c2, n1, s); b.bond(n1, c6, s)
            b.bond(c5, n7, s); b.bond(n7, c8, s); b.bond(c8, n9, s, innerToward = px to py); b.bond(n9, c4, s)
            b.bond(c6, out(c6, -90, 'O'), s, double = true); b.bond(c2, out(c2, 150, 'O'), s, double = true)
            b.bond(n1, out(n1, 210), s); b.bond(n3, out(n3, 90), s)
            val outDeg = -72
            b.bond(n7, Atom(n7.x + s * cosD(outDeg), n7.y + s * sinD(outDeg)), s)
        }
    }

    private fun cosD(deg: Int) = cos(deg * PI.toFloat() / 180f)
    private fun sinD(deg: Int) = sin(deg * PI.toFloat() / 180f)

    // ─────────────── Rozpady jąder ───────────────

    private fun decay(b: Builder, ar: Float) {
        // Jądro macierzyste (siedem nukleonów: pełne to protony, puste to neutrony) i uciekająca cząstka alfa (2p + 2n).
        val nr = 0.034f; val nx = 0.34f; val ny = 0.21f
        nucleon(b, nx, ny, nr, true)
        for (k in 0 until 6) nucleon(b, nx + 2f * nr * cosD(30 + 60 * k), ny + 2f * nr * sinD(30 + 60 * k), nr, k % 2 == 1)
        val ar2 = 0.0235f; val ax = 0.80f; val ay = 0.115f
        nucleon(b, ax - ar2, ay - ar2, ar2, true); nucleon(b, ax + ar2, ay - ar2, ar2, false)
        nucleon(b, ax - ar2, ay + ar2, ar2, false); nucleon(b, ax + ar2, ay + ar2, ar2, true)
        // Tor cząstki: przerywana strzałka od jądra do cząstki alfa.
        val dx = ax - nx; val dy = ay - ny; val len = hypot(dx, dy)
        val ux = dx / len; val uy = dy / len
        val x0 = nx + ux * 0.145f; val y0 = ny + uy * 0.145f
        val x1 = ax - ux * 0.072f; val y1 = ay - uy * 0.072f
        b.polys += Poly(floatArrayOf(x0, y0, x1, y1), closed = false, fill = false, width = 0.0055f, a = 0.8f, dash = true)
        b.arrowHead(x1, y1, ux, uy, 0.026f, 0.8f)

        // Krzywa rozpadu N(t) = N₀·2^(−t/T) z zaznaczonymi trzema czasami połowicznego zaniku (N₀/2, N₀/4, N₀/8).
        val ox = 0.21f; val oy = ar - 0.15f; val wx = 0.70f; val hy = 0.42f; val t12 = 0.20f
        fun py(t: Float) = oy - hy * 2f.pow(-t / t12)
        val curve = FloatArray(2 * 41)
        for (k in 0..40) { val t = k / 40f * wx; curve[2 * k] = ox + t; curve[2 * k + 1] = py(t) }
        for (k in 1..3) {
            val x = ox + k * t12; val y = py(k * t12)
            b.polys += Poly(floatArrayOf(x, oy, x, y), closed = false, fill = false, width = 0.0045f, a = 0.7f, dash = true)
            b.polys += Poly(floatArrayOf(ox, y, x, y), closed = false, fill = false, width = 0.0045f, a = 0.7f, dash = true)
        }
        b.polys += Poly(curve, closed = false, fill = false, width = 0.0085f, a = 1f)
        for (k in 0..3) b.discs += Disc(ox + k * t12, py(k * t12), 0.012f, true, 0f, 1f)
        axes(b, ox - 0.03f, oy, ox + wx + 0.04f, oy - hy - 0.08f, ox)
    }

    private fun nucleon(b: Builder, x: Float, y: Float, r: Float, proton: Boolean) {
        if (proton) b.discs += Disc(x, y, r * 0.92f, true, 0f, 0.9f)
        else b.discs += Disc(x, y, r * 0.86f, false, 0.0055f, 0.9f)
    }

    // ─────────────── Wspólne ───────────────

    /** Osie układu współrzędnych z grotami: pozioma od ([x0], [y0]) do [x1], pionowa od [yAxisX] do [y1]. */
    private fun axes(b: Builder, x0: Float, y0: Float, x1: Float, y1: Float, yAxisX: Float) {
        b.line(x0, y0, x1, y0, 0.0055f, 0.8f)
        b.line(yAxisX, y0 + 0.03f, yAxisX, y1, 0.0055f, 0.8f)
        b.arrowHead(x1, y0, 1f, 0f, 0.022f, 0.8f)
        b.arrowHead(yAxisX, y1, 0f, -1f, 0.022f, 0.8f)
    }

    private class Builder {
        val polys = ArrayList<Poly>()
        val discs = ArrayList<Disc>()

        fun line(x0: Float, y0: Float, x1: Float, y1: Float, w: Float, a: Float) {
            polys += Poly(floatArrayOf(x0, y0, x1, y1), closed = false, fill = false, width = w, a = a)
        }

        fun arrowHead(x: Float, y: Float, ux: Float, uy: Float, size: Float, a: Float) {
            val nx = -uy; val ny = ux
            polys += Poly(floatArrayOf(x, y, x - ux * size + nx * size * 0.4f, y - uy * size + ny * size * 0.4f,
                x - ux * size - nx * size * 0.4f, y - uy * size - ny * size * 0.4f), closed = true, fill = true, width = 0f, a = a)
        }

        /** Wiązanie [p]–[q] o długości [s]; końce z literą atomu są skracane, a litera rysowana kreskami. */
        fun bond(p: Atom, q: Atom, s: Float, double: Boolean = false, innerToward: Pair<Float, Float>? = null) {
            val dx = q.x - p.x; val dy = q.y - p.y
            val len = hypot(dx, dy); val ux = dx / len; val uy = dy / len
            val tp = if (p.label != null) s * 0.27f else 0f
            val tq = if (q.label != null) s * 0.27f else 0f
            val ax = p.x + ux * tp; val ay = p.y + uy * tp
            val bx = q.x - ux * tq; val by = q.y - uy * tq
            val w = 0.0075f; val a = 0.95f
            if (double && innerToward == null) {
                val off = s * 0.085f; val nx = -uy * off; val ny = ux * off
                line(ax + nx, ay + ny, bx + nx, by + ny, w, a); line(ax - nx, ay - ny, bx - nx, by - ny, w, a)
            } else {
                line(ax, ay, bx, by, w, a)
                if (innerToward != null) inner(p, q, innerToward.first, innerToward.second, s * 0.17f, s * 0.15f)
            }
            glyph(p, s); glyph(q, s)
        }

        /** Wewnętrzna kreska wiązania podwójnego, przesunięta o [off] w stronę punktu ([tx], [ty]) i skrócona o [cut] z obu stron. */
        fun inner(p: Atom, q: Atom, tx: Float, ty: Float, off: Float, cut: Float) {
            val dx = q.x - p.x; val dy = q.y - p.y
            val len = hypot(dx, dy); val ux = dx / len; val uy = dy / len
            var nx = -uy; var ny = ux
            if ((tx - (p.x + q.x) / 2) * nx + (ty - (p.y + q.y) / 2) * ny < 0) { nx = -nx; ny = -ny }
            val tp = if (p.label != null) cut * 2.4f else cut; val tq = if (q.label != null) cut * 2.4f else cut
            line(p.x + ux * tp + nx * off, p.y + uy * tp + ny * off, q.x - ux * tq + nx * off, q.y - uy * tq + ny * off, 0.0075f, 0.95f)
        }

        private fun glyph(at: Atom, s: Float) {
            when (at.label) {
                'N' -> {
                    val hw = s * 0.13f; val hh = s * 0.17f
                    polys += Poly(floatArrayOf(at.x - hw, at.y + hh, at.x - hw, at.y - hh, at.x + hw, at.y + hh, at.x + hw, at.y - hh),
                        closed = false, fill = false, width = 0.0065f, a = 0.95f)
                }
                'O' -> discs += Disc(at.x, at.y, s * 0.19f, false, 0.0065f, 0.95f)
            }
        }
    }
}

package com.mdev.mnotes.core.samsung

import com.mdev.mnotes.core.model.Ids
import com.mdev.mnotes.core.model.PenKind
import com.mdev.mnotes.core.model.Stroke
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Kształty Samsunga (obiekt typu 7) jako kreski mNotes: mNotes nie ma osobnego elementu kształtu, a jego kreski z
 * uchwytami ([Stroke.poly], [Stroke.ellipse]) są tym samym, co daje autokorekta „rysuj i przytrzymaj”, więc kształt
 * zostaje w pełni edytowalny. Ścieżka kształtu leży w pliku we współrzędnych strony, już po obrocie i skalowaniu.
 */
internal object SdShapes {
    private const val QUAD_STEPS = 8
    private const val CUBIC_STEPS = 14
    private const val ELLIPSE_SEGMENTS = 48
    private const val ELLIPSE_MAX_MEAN_ERR = 0.06
    private const val ELLIPSE_MAX_SD = 0.05

    /** Jeden podrys ścieżki: punkty w pt, [closed] gdy zakończył go odcinek „zamknij”, [straight] gdy ma same odcinki proste. */
    private class Sub(val pts: ArrayList<DoubleArray> = ArrayList(), var closed: Boolean = false, var straight: Boolean = true, var quads: Boolean = true)

    /** Kreski dla jednego kształtu; [unit] to pt na jednostkę pliku, [color] to już końcowy kolor. */
    fun strokes(shape: SdShape, unit: Double, color: Int): List<Stroke> {
        val subs = subpaths(shape, unit)
        val width = (shape.widthUnits * unit).toFloat().coerceAtLeast(0.2f)
        val out = ArrayList<Stroke>()
        for (s in subs) {
            if (s.pts.size < 2) continue
            if (shape.kind == 1 && s.closed && s.quads && s.pts.size >= 8) {
                val e = ellipseStroke(s.pts, width, color)
                if (e != null) { out.add(e); continue }
            }
            out.add(plain(s, width, color))
        }
        return out
    }

    private fun plain(s: Sub, width: Float, color: Int): Stroke {
        val pts = ArrayList(s.pts)
        if (s.closed && (pts.first()[0] != pts.last()[0] || pts.first()[1] != pts.last()[1])) pts.add(pts.first())
        return Stroke(Ids.next(), PenKind.BALLPOINT, color, width, flat(pts), poly = s.straight)
    }

    private fun flat(pts: List<DoubleArray>): FloatArray {
        val out = FloatArray(pts.size * 3)
        pts.forEachIndexed { i, p -> out[3 * i] = p[0].toFloat(); out[3 * i + 1] = p[1].toFloat(); out[3 * i + 2] = 1f }
        return out
    }

    private fun subpaths(shape: SdShape, unit: Double): List<Sub> {
        val subs = ArrayList<Sub>()
        var cur: Sub? = null
        var x = 0.0
        var y = 0.0
        for (g in shape.segs) {
            when (g.kind) {
                1 -> {
                    cur = Sub().also { it.pts.add(doubleArrayOf(g.pts[0] * unit, g.pts[1] * unit)); subs.add(it) }
                    x = g.pts[0]; y = g.pts[1]
                }
                2 -> {
                    val c = cur ?: continue
                    c.pts.add(doubleArrayOf(g.pts[0] * unit, g.pts[1] * unit)); c.quads = false
                    x = g.pts[0]; y = g.pts[1]
                }
                3 -> {
                    val c = cur ?: continue
                    c.straight = false
                    for (k in 1..QUAD_STEPS) {
                        val t = k.toDouble() / QUAD_STEPS
                        val u = 1 - t
                        c.pts.add(doubleArrayOf(
                            (u * u * x + 2 * u * t * g.pts[0] + t * t * g.pts[2]) * unit,
                            (u * u * y + 2 * u * t * g.pts[1] + t * t * g.pts[3]) * unit,
                        ))
                    }
                    x = g.pts[2]; y = g.pts[3]
                }
                4 -> {
                    val c = cur ?: continue
                    c.straight = false; c.quads = false
                    for (k in 1..CUBIC_STEPS) {
                        val t = k.toDouble() / CUBIC_STEPS
                        val u = 1 - t
                        c.pts.add(doubleArrayOf(
                            (u * u * u * x + 3 * u * u * t * g.pts[0] + 3 * u * t * t * g.pts[2] + t * t * t * g.pts[4]) * unit,
                            (u * u * u * y + 3 * u * u * t * g.pts[1] + 3 * u * t * t * g.pts[3] + t * t * t * g.pts[5]) * unit,
                        ))
                    }
                    x = g.pts[4]; y = g.pts[5]
                }
                6 -> cur?.closed = true
            }
        }
        return subs
    }

    /**
     * Elipsa mNotes (49 punktów, punkt 0 na osi głównej) dopasowana do obrysu: środek i osie z momentów punktów, a
     * półosie z ich największego rzutu. Przyjmowana tylko wtedy, gdy obrys naprawdę jest elipsą; inaczej zostaje kreska.
     */
    private fun ellipseStroke(p: List<DoubleArray>, width: Float, color: Int): Stroke? {
        val n = p.size
        var cx = 0.0; var cy = 0.0
        for (q in p) { cx += q[0]; cy += q[1] }
        cx /= n; cy /= n
        var sxx = 0.0; var syy = 0.0; var sxy = 0.0
        for (q in p) {
            val dx = q[0] - cx; val dy = q[1] - cy
            sxx += dx * dx; syy += dy * dy; sxy += dx * dy
        }
        val th = 0.5 * atan2(2 * sxy, sxx - syy)
        var ux = cos(th); var uy = sin(th)
        var vx = -uy; var vy = ux
        var a = 0.0; var b = 0.0
        for (q in p) {
            a = max(a, abs((q[0] - cx) * ux + (q[1] - cy) * uy))
            b = max(b, abs((q[0] - cx) * vx + (q[1] - cy) * vy))
        }
        if (a < 1.0 || b < 1.0) return null
        if (a < b) {
            val tx = ux; val ty = uy
            ux = vx; uy = vy; vx = -tx; vy = -ty
            val t = a; a = b; b = t
        }
        var sum = 0.0; var sum2 = 0.0
        for (q in p) {
            val pu = ((q[0] - cx) * ux + (q[1] - cy) * uy) / a
            val pv = ((q[0] - cx) * vx + (q[1] - cy) * vy) / b
            val d = sqrt(pu * pu + pv * pv)
            sum += d; sum2 += d * d
        }
        val mean = sum / n
        val sd = sqrt(max(0.0, sum2 / n - mean * mean))
        if (abs(mean - 1.0) > ELLIPSE_MAX_MEAN_ERR || sd > ELLIPSE_MAX_SD) return null
        val out = FloatArray(3 * (ELLIPSE_SEGMENTS + 1))
        for (k in 0..ELLIPSE_SEGMENTS) {
            val t = if (k == ELLIPSE_SEGMENTS) 0.0 else 2 * PI * k / ELLIPSE_SEGMENTS
            val ca = a * cos(t); val sb = b * sin(t)
            out[3 * k] = (cx + ca * ux + sb * vx).toFloat()
            out[3 * k + 1] = (cy + ca * uy + sb * vy).toFloat()
            out[3 * k + 2] = 1f
        }
        return Stroke(Ids.next(), PenKind.BALLPOINT, color, width, out, ellipse = true)
    }
}

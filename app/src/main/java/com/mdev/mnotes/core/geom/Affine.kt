package com.mdev.mnotes.core.geom

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Przekształcenie afiniczne 2D w konwencji Canvas/SVG:
 * x' = a·x + c·y + e,  y' = b·x + d·y + f.
 */
class Affine(
    @JvmField val a: Float,
    @JvmField val b: Float,
    @JvmField val c: Float,
    @JvmField val d: Float,
    @JvmField val e: Float,
    @JvmField val f: Float,
) {
    fun mapX(x: Float, y: Float): Float = a * x + c * y + e
    fun mapY(x: Float, y: Float): Float = b * x + d * y + f

    /** Najpierw this, potem [next]. */
    fun then(next: Affine): Affine = Affine(
        a = next.a * a + next.c * b,
        b = next.b * a + next.d * b,
        c = next.a * c + next.c * d,
        d = next.b * c + next.d * d,
        e = next.a * e + next.c * f + next.e,
        f = next.b * e + next.d * f + next.f,
    )

    val det: Float get() = a * d - b * c

    /** Średni współczynnik skali (pierwiastek z |det|). */
    val scaleFactor: Float get() = sqrt(abs(det))

    val rotation: Float get() = atan2(b, a)

    val isIdentity: Boolean get() = a == 1f && b == 0f && c == 0f && d == 1f && e == 0f && f == 0f

    fun invert(): Affine? {
        val dt = det
        if (abs(dt) < 1e-12f) return null
        val ia = d / dt
        val ib = -b / dt
        val ic = -c / dt
        val id = a / dt
        return Affine(ia, ib, ic, id, -(ia * e + ic * f), -(ib * e + id * f))
    }

    fun mapBounds(bb: Bounds): Bounds {
        val x0 = mapX(bb.l, bb.t); val y0 = mapY(bb.l, bb.t)
        val x1 = mapX(bb.r, bb.t); val y1 = mapY(bb.r, bb.t)
        val x2 = mapX(bb.r, bb.b); val y2 = mapY(bb.r, bb.b)
        val x3 = mapX(bb.l, bb.b); val y3 = mapY(bb.l, bb.b)
        return Bounds(
            min(min(x0, x1), min(x2, x3)), min(min(y0, y1), min(y2, y3)),
            max(max(x0, x1), max(x2, x3)), max(max(y0, y1), max(y2, y3)),
        )
    }

    /** Długość obrazu wektora jednostkowego osi X. */
    val scaleX: Float get() = hypot(a, b)

    /** Długość obrazu wektora jednostkowego osi Y. */
    val scaleY: Float get() = hypot(c, d)

    override fun toString(): String = "Affine($a, $b, $c, $d, $e, $f)"

    companion object {
        val IDENTITY = Affine(1f, 0f, 0f, 1f, 0f, 0f)

        fun translation(dx: Float, dy: Float) = Affine(1f, 0f, 0f, 1f, dx, dy)

        fun scale(sx: Float, sy: Float, px: Float = 0f, py: Float = 0f) =
            Affine(sx, 0f, 0f, sy, px - sx * px, py - sy * py)

        fun rotation(rad: Float, px: Float = 0f, py: Float = 0f): Affine {
            val cs = cos(rad); val sn = sin(rad)
            return Affine(cs, sn, -sn, cs, px - cs * px + sn * py, py - sn * px - cs * py)
        }
    }
}

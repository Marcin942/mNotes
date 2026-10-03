package com.mdev.mnotes.core.geom

import kotlin.math.max
import kotlin.math.min

/** Niezmienny prostokąt osiowy w układzie współrzędnych dokumentu (punkty, 1/72 cala). */
class Bounds(
    @JvmField val l: Float,
    @JvmField val t: Float,
    @JvmField val r: Float,
    @JvmField val b: Float,
) {
    val w: Float get() = r - l
    val h: Float get() = b - t
    val cx: Float get() = (l + r) * 0.5f
    val cy: Float get() = (t + b) * 0.5f
    val isEmpty: Boolean get() = l > r || t > b

    fun intersects(o: Bounds): Boolean = l <= o.r && o.l <= r && t <= o.b && o.t <= b

    fun intersects(ol: Float, ot: Float, or: Float, ob: Float): Boolean =
        l <= or && ol <= r && t <= ob && ot <= b

    fun contains(x: Float, y: Float): Boolean = x >= l && x <= r && y >= t && y <= b

    fun contains(o: Bounds): Boolean = o.l >= l && o.r <= r && o.t >= t && o.b <= b

    fun union(o: Bounds): Bounds = when {
        isEmpty -> o
        o.isEmpty -> this
        else -> Bounds(min(l, o.l), min(t, o.t), max(r, o.r), max(b, o.b))
    }

    fun inflate(d: Float): Bounds = if (isEmpty) this else Bounds(l - d, t - d, r + d, b + d)

    fun offset(dx: Float, dy: Float): Bounds = if (isEmpty) this else Bounds(l + dx, t + dy, r + dx, b + dy)

    fun intersection(o: Bounds): Bounds? {
        if (!intersects(o)) return null
        return Bounds(max(l, o.l), max(t, o.t), min(r, o.r), min(b, o.b))
    }

    override fun equals(other: Any?): Boolean =
        other is Bounds && l == other.l && t == other.t && r == other.r && b == other.b

    override fun hashCode(): Int {
        var h = l.hashCode()
        h = 31 * h + t.hashCode()
        h = 31 * h + r.hashCode()
        return 31 * h + b.hashCode()
    }

    override fun toString(): String = "Bounds($l, $t, $r, $b)"

    companion object {
        val EMPTY = Bounds(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY)

        fun ofPoints(xy: FloatArray, count: Int = xy.size / 2): Bounds {
            if (count <= 0) return EMPTY
            var l = xy[0]; var t = xy[1]; var r = l; var b = t
            for (i in 1 until count) {
                val x = xy[2 * i]; val y = xy[2 * i + 1]
                if (x < l) l = x else if (x > r) r = x
                if (y < t) t = y else if (y > b) b = y
            }
            return Bounds(l, t, r, b)
        }
    }
}

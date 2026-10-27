package com.mdev.mnotes.core.geom

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Linijka: prostokąt o środku ([cx], [cy]), obrócony o [a] radianów, długości [len] i grubości [thick]
 * (jednostki dokumentu). Układ lokalny: oś u biegnie wzdłuż linijki, oś v w poprzek (v > 0 = „dolna” krawędź).
 */
class Ruler(override var cx: Float, override var cy: Float, override var a: Float, var len: Float, val thick: Float = 70f) : Instrument {
    fun u(x: Float, y: Float) = (x - cx) * cos(a) + (y - cy) * sin(a)
    fun v(x: Float, y: Float) = -(x - cx) * sin(a) + (y - cy) * cos(a)
    fun x(u: Float, v: Float) = cx + u * cos(a) - v * sin(a)
    fun y(u: Float, v: Float) = cy + u * sin(a) + v * cos(a)

    /**
     * Przy której długiej krawędzi leży punkt: +1 dolna, −1 górna, 0 żadna. Pasmo obejmuje [out] na zewnątrz
     * korpusu i [inn] do wewnątrz; wzdłuż linijki punkt może wystawać o [along] poza jej koniec.
     */
    fun edgeAt(x: Float, y: Float, out: Float, inn: Float, along: Float): Int {
        val u = u(x, y); val v = v(x, y); val h = thick / 2
        if (abs(u) > len / 2 + along) return 0
        return when {
            v >= h - inn && v <= h + out -> 1
            v <= -h + inn && v >= -h - out -> -1
            else -> 0
        }
    }

    /** Trafienie w korpus (bez pasa [inn] przy krawędziach, który należy do rysowania). */
    fun inBody(x: Float, y: Float, inn: Float): Boolean = abs(u(x, y)) <= len / 2 && abs(v(x, y)) <= thick / 2 - inn

    companion object {
        /** Przyciąga kąt do wielokrotności 45°, jeśli leży w odległości [tol] radianów; w przeciwnym razie zwraca [a]. */
        fun snap(a: Float, tol: Float): Float {
            val q = (PI / 4).toFloat()
            val n = (a / q).roundToInt()
            val s = n * q
            return if (abs(a - s) <= tol) s else a
        }
    }
}

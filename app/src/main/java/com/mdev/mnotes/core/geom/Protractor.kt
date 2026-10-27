package com.mdev.mnotes.core.geom

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/** Przyrząd na stronie, który przesuwa się i obraca palcami (linijka, kątomierz); środek i kąt w jednostkach dokumentu. */
interface Instrument {
    var cx: Float
    var cy: Float
    var a: Float
}

/**
 * Kątomierz (FB4, faza 2 A2-13): półkole o środku ([cx], [cy]) na linii bazowej obróconej o [a] radianów i promieniu [r]
 * (jednostki dokumentu), pod linią bazową wąski pasek [base]. Układ lokalny jak w [Ruler]: u wzdłuż linii bazowej,
 * v w poprzek; łuk leży po stronie v < 0, a 0° podziałki to prawy koniec linii bazowej (jak na szkolnym kątomierzu).
 *
 * Kreska zaczęta w środku kątomierza biegnie po promieniu: kąt przyciąga się do pełnych stopni, długość idzie za piórem
 * — tak rysuje się kąt o zadanej mierze.
 */
class Protractor(override var cx: Float, override var cy: Float, override var a: Float, var r: Float) : Instrument {
    val base: Float get() = r * 0.14f

    fun u(x: Float, y: Float) = (x - cx) * cos(a) + (y - cy) * sin(a)
    fun v(x: Float, y: Float) = -(x - cx) * sin(a) + (y - cy) * cos(a)
    fun x(u: Float, v: Float) = cx + u * cos(a) - v * sin(a)
    fun y(u: Float, v: Float) = cy + u * sin(a) + v * cos(a)

    /** Kąt punktu na podziałce w stopniach 0–360 (0 = prawy koniec linii bazowej, 90 = szczyt łuku). */
    fun degAt(x: Float, y: Float): Float {
        val d = Math.toDegrees(atan2(-v(x, y), u(x, y)).toDouble()).toFloat()
        return if (d < 0f) d + 360f else d
    }

    /** Trafienie w korpus: półkole (bez pasa [inn] przy środku, który należy do rysowania promienia) i pasek pod bazą. */
    fun inBody(x: Float, y: Float, inn: Float): Boolean {
        val u = u(x, y); val v = v(x, y)
        if (hypot(u, v) < inn) return false
        return (v <= 0f && hypot(u, v) <= r) || (v in 0f..base && abs(u) <= r)
    }

    fun nearCenter(x: Float, y: Float, tol: Float) = hypot(x - cx, y - cy) <= tol

    /**
     * Promień od środka do punktu ([x], [y]) z kątem przyciągniętym do wielokrotności [stepDeg]: [koniec x, koniec y,
     * kąt w stopniach 0–360]. Długość = odległość punktu od środka.
     */
    fun ray(x: Float, y: Float, stepDeg: Float = 1f): FloatArray {
        val len = hypot(x - cx, y - cy)
        val deg = ((degAt(x, y) / stepDeg).roundToInt() * stepDeg) % 360f
        val rad = Math.toRadians(deg.toDouble())
        val u = len * cos(rad).toFloat(); val v = -len * sin(rad).toFloat()
        return floatArrayOf(x(u, v), y(u, v), deg)
    }

    companion object {
        /** Miara do podpisu: kąt promienia z podziałki 0–180 (pod linią bazową liczony od tej samej krawędzi). */
        fun shown(deg: Float): Float = if (deg <= 180f) deg else 360f - deg

        /** Kreski podziałki co 1°: długość względna 1 (co 10°), 0,6 (co 5°), 0,35 (pozostałe). */
        fun tick(deg: Int): Float = when { deg % 10 == 0 -> 1f; deg % 5 == 0 -> 0.6f; else -> 0.35f }
    }
}

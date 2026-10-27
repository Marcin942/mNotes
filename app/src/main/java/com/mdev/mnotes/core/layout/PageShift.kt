package com.mdev.mnotes.core.layout

/**
 * Animacja zmiany kolejności stron na płótnie (F6): strony, które po przestawieniu (albo wstawieniu/usunięciu sąsiadki)
 * są gdzie indziej, dojeżdżają na nowe miejsce zamiast skakać.
 *
 * Położenia „przed” są w pikselach ekranu, a różnice liczy dopiero pierwsza klatka po zmianie ([begin]), bo widok może
 * w tym czasie przesunąć się tak, by przestawiana strona została w miejscu (`keepPageInPlace`) — wtedy ona sama nie
 * rusza się wcale, a jadą tylko sąsiadki.
 */
class PageShift(private val from: Map<String, FloatArray>, private val durMs: Long) {
    private var start = -1L
    private val delta = HashMap<String, FloatArray>()

    val started: Boolean get() = start >= 0L

    /** Ustala przesunięcia: [to] daje bieżące położenie strony na ekranie albo null, gdy jej już nie ma. */
    fun begin(now: Long, to: (String) -> FloatArray?) {
        start = now
        for ((id, a) in from) {
            val b = to(id) ?: continue
            val dx = a[0] - b[0]; val dy = a[1] - b[1]
            if (dx * dx + dy * dy > 0.25f) delta[id] = floatArrayOf(dx, dy)
        }
    }

    fun active(now: Long): Boolean = !started || (delta.isNotEmpty() && now - start < durMs)

    /** Przesunięcie strony [id] (px ekranu) w chwili [now]; null = strona stoi na swoim miejscu. */
    fun offset(id: String, now: Long): FloatArray? {
        if (!started || durMs <= 0L) return null
        val d = delta[id] ?: return null
        val t = ((now - start).toFloat() / durMs).coerceIn(0f, 1f)
        if (t >= 1f) return null
        val k = 1f - ease(t)
        return floatArrayOf(d[0] * k, d[1] * k)
    }

    companion object {
        /** Ease-out (sześcienna): szybki start, łagodne dojście. */
        fun ease(t: Float): Float { val u = 1f - t; return 1f - u * u * u }
    }
}

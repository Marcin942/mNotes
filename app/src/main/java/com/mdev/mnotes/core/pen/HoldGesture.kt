package com.mdev.mnotes.core.pen

import kotlin.math.hypot

/**
 * Przytrzymanie pióra do rozpoznania kształtu (B6, faza 2 A2-12): „uchwyt regulacji rozmiaru pojawia się po ~1 s
 * albo więcej”. Licznik startował od nowa przy każdym dryfie > 10 dp, więc lekko drżąca ręka czekała dwa, trzy razy
 * po 550 ms. Teraz promień dryfu to 20 dp, a żeby wolne rysowanie nie było brane za przytrzymanie, w chwili odpalenia
 * pióro musi naprawdę stać: w ostatnich [STILL_WINDOW_MS] przesunęło się najwyżej o [STILL_DP].
 *
 * Wszystkie odległości w px ekranu; [dp] = px na dp.
 */
class HoldGesture(private val dp: Float) {
    private val ts = LongArray(N); private val xs = FloatArray(N); private val ys = FloatArray(N)
    private var head = 0; private var size = 0
    private var ax = 0f; private var ay = 0f

    /** Chwila, od której liczy się czas przytrzymania (początek kreski albo ostatni większy dryf). */
    var anchorT = 0L
        private set

    /** Początek kreski w ([x], [y]) w chwili [t]. */
    fun start(t: Long, x: Float, y: Float) { size = 0; head = 0; ax = x; ay = y; anchorT = t; push(t, x, y) }

    /** Ruch pióra; true = dryf poza promień, licznik przytrzymania liczy od nowa (od [t]). */
    fun move(t: Long, x: Float, y: Float): Boolean {
        push(t, x, y)
        if (hypot(x - ax, y - ay) <= DRIFT_DP * dp) return false
        ax = x; ay = y; anchorT = t
        return true
    }

    /** Czy pióro stoi w chwili [now]: od próbki sprzed ok. [STILL_WINDOW_MS] przesunęło się najwyżej o [STILL_DP]. */
    fun still(now: Long): Boolean {
        if (size == 0) return true
        val last = (head - 1 + N) % N
        var k = last
        for (i in 0 until size) {
            val j = (last - i + N) % N
            k = j
            if (now - ts[j] >= STILL_WINDOW_MS) break
        }
        return hypot(xs[last] - xs[k], ys[last] - ys[k]) <= STILL_DP * dp
    }

    /** Czy w chwili [now] przytrzymanie trwa już [holdMs] i pióro stoi — można rozpoznawać kształt. */
    fun due(now: Long, holdMs: Long): Boolean = now - anchorT >= holdMs && still(now)

    private fun push(t: Long, x: Float, y: Float) {
        ts[head] = t; xs[head] = x; ys[head] = y
        head = (head + 1) % N; if (size < N) size++
    }

    companion object {
        /**
         * 2c-A4: punkty kreski do rozpoznania kształtu po przytrzymaniu. Dawniej brano próbki tylko do ostatniego
         * zerowania licznika ([holdIdx]), a ono wypada do [radius] przed końcem — prosta i bok prostokąta wychodziły
         * krótsze. Teraz koniec kreski zostaje, a drżenie w miejscu (próbki od końca, które leżą w promieniu dryfu od
         * środka spoczynku) zastępuje jeden punkt: ich średnia.
         *
         * [raw] = x, y kolejnych próbek ([count] próbek), [radius] — promień dryfu w tych samych jednostkach. [holdIdx] < 3
         * znaczy, że licznik nie zerował się od początku kreski (cała mieści się w promieniu) — wtedy wszystkie próbki.
         */
        fun shapePoints(raw: FloatArray, holdIdx: Int, count: Int, radius: Float): FloatArray {
            val n = count.coerceAtMost(raw.size / 2)
            if (holdIdx < 3 || n < 4) return raw.copyOf(2 * n)
            val tail = minOf(5, n)
            var cx = 0f; var cy = 0f
            for (i in n - tail until n) { cx += raw[2 * i]; cy += raw[2 * i + 1] }
            cx /= tail; cy /= tail
            var j = n
            // Dwa przejścia: skupisko wokół średniej ostatnich próbek, potem wokół średniej samego skupiska.
            repeat(2) {
                j = n
                while (j > 0 && hypot(raw[2 * (j - 1)] - cx, raw[2 * (j - 1) + 1] - cy) <= radius) j--
                var sx = 0f; var sy = 0f
                for (i in j until n) { sx += raw[2 * i]; sy += raw[2 * i + 1] }
                if (j < n) { cx = sx / (n - j); cy = sy / (n - j) }
            }
            if (j < 2) return raw.copyOf(2 * n)
            val out = raw.copyOf(2 * (j + 1))
            out[2 * j] = cx; out[2 * j + 1] = cy
            return out
        }

        const val DRIFT_DP = 20f
        const val STILL_WINDOW_MS = 100L
        const val STILL_DP = 6f
        /** Gdy w chwili odpalenia pióro jeszcze się porusza — sprawdzamy ponownie po tylu ms. */
        const val RECHECK_MS = 80L
        private const val N = 64
    }
}

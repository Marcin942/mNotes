package com.mdev.mnotes.core.pen

import com.mdev.mnotes.core.geom.FloatList
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Tnie kreskę (trójki x, y, p) na kawałki linii przerywanej albo kropkowanej, po długości łuku. Kawałki rysuje się
 * tak samo jak zwykłą kreskę (okrągłe końce, zmienna grubość), więc styl linii działa dla każdego pióra, a kropka
 * to po prostu kawałek z jednego punktu.
 *
 * Trzy sposoby rozkładania wzoru:
 *  - pismo odręczne: wzór biegnie od początku stałym krokiem, więc podgląd na żywo nie „pływa” w trakcie pisania;
 *  - łamana z kształtu ([sharp]): każdy odcinek dostaje całkowitą liczbę okresów, dzięki czemu w każdym rogu stoi
 *    pełna kreska albo kropka, a otwarte końce zaczynają się i kończą pełną kreską;
 *  - gładki kształt ([fit] bez [sharp], czyli elipsa): wzór dopasowany do całego obwodu, bez szwu w punkcie startu.
 */
object StrokeDash {
    /** Długość kreski i przerwy wzdłuż osi, w grubościach pióra; okrągłe końce dokładają kresce jeszcze jedną grubość. */
    private const val DASH = 3f
    private const val GAP = 3f

    /** Odstęp środków kropek, w grubościach pióra. */
    private const val DOT_STEP = 2f

    /** Cienkie pióra liczą wzór jak dla 1 pt, inaczej kreski zlewałyby się w szarą linię. */
    private const val MIN_UNIT = 1f

    /** Odcinek, na którym przerwa wyszłaby ciaśniejsza niż ta część zwykłej, rysujemy ciągły. */
    private const val MIN_GAP = 0.4f

    private const val EPS = 1e-4f

    /** Wynik cięcia i bufory robocze; jeden obiekt na rysującego, żeby podgląd na żywo nie alokował co klatkę. */
    class Pieces {
        /** Punkty wszystkich kawałków po kolei (trójki x, y, p). */
        val pts = FloatList(256)
        private var ends = IntArray(32)
        var count = 0
            private set

        /** Indeks pierwszej trójki kawałka [i] w [pts]. */
        fun start(i: Int): Int = if (i == 0) 0 else ends[i - 1]

        /** Liczba punktów kawałka [i]. */
        fun size(i: Int): Int = ends[i] - start(i)

        /** Kopia kawałka [i] (trójki x, y, p). */
        fun piece(i: Int): FloatArray = pts.a.copyOfRange(3 * start(i), 3 * ends[i])

        internal val iv = FloatList(64)
        internal var w = FloatArray(0)
        internal var cum = FloatArray(0)
        internal var m = 0

        internal fun clear() { pts.clear(); iv.clear(); count = 0; m = 0 }

        internal fun close() {
            val e = pts.size / 3
            if (e == start(count)) return
            if (count == ends.size) ends = ends.copyOf(count * 2)
            ends[count++] = e
        }

        /** Punkty bez odcinków zerowej długości i długość łuku do każdego z nich. */
        internal fun load(src: FloatArray, n: Int) {
            if (w.size < 3 * n) { w = FloatArray(3 * n); cum = FloatArray(n) }
            m = 0
            for (i in 0 until n) {
                val x = src[3 * i]; val y = src[3 * i + 1]; val p = src[3 * i + 2]
                if (m > 0) {
                    val d = hypot(x - w[3 * m - 3], y - w[3 * m - 2])
                    if (d < EPS) { w[3 * m - 1] = max(w[3 * m - 1], p); continue }
                    cum[m] = cum[m - 1] + d
                } else cum[0] = 0f
                w[3 * m] = x; w[3 * m + 1] = y; w[3 * m + 2] = p
                m++
            }
        }
    }

    /**
     * Kawałki kreski [pts] ([n] punktów) o grubości [width] w stylu [style]. [sharp] = łamana o prostych odcinkach,
     * [fit] = kształt (wzór dopasowany do długości). Linia ciągła wychodzi jako jeden kawałek.
     */
    fun cut(pts: FloatArray, n: Int, width: Float, style: LineStyle, sharp: Boolean, fit: Boolean, out: Pieces) {
        out.clear()
        if (n <= 0) return
        out.load(pts, n)
        val m = out.m
        val total = out.cum[m - 1]
        if (style == LineStyle.SOLID || m < 2) {
            for (i in 0 until m) out.pts.add(out.w[3 * i], out.w[3 * i + 1], out.w[3 * i + 2])
            out.close()
            return
        }
        val unit = max(width, MIN_UNIT)
        val dots = style == LineStyle.DOTTED
        val dash = if (dots) 0f else DASH * unit
        val period = if (dots) DOT_STEP * unit else (DASH + GAP) * unit
        val closed = (sharp || fit) && m > 2 && out.w[0] == out.w[3 * m - 3] && out.w[1] == out.w[3 * m - 2]

        if (sharp) {
            for (j in 0 until m - 1) {
                // Zawrotka (ramię grotu strzałki) kończy się jak otwarty koniec, inaczej oba ramiona wychodziłyby różnie.
                val openA = if (j == 0) !closed else turnsBack(out, j)
                val openB = if (j == m - 2) !closed else turnsBack(out, j + 1)
                fitted(out, out.cum[j], out.cum[j + 1], openA, openB, dash, period)
            }
        } else if (fit) {
            fitted(out, 0f, total, !closed, !closed, dash, period)
        } else {
            val h = dash * 0.5f
            var c = h
            while (c - h < total) { span(out, max(0f, c - h), min(total, c + h)); c += period }
        }

        val iv = out.iv
        val cnt = iv.size / 2
        var from = 0; var to = cnt
        if (closed && cnt > 1 && iv[0] <= EPS && iv[2 * cnt - 1] >= total - EPS) {
            // Kreska w punkcie startu zamkniętego kształtu przechodzi przez szew jako jeden kawałek.
            sub(out, iv[2 * cnt - 2], total, false)
            sub(out, 0f, iv[1], true)
            out.close()
            from = 1; to = cnt - 1
        }
        for (i in from until to) { sub(out, iv[2 * i], iv[2 * i + 1], false); out.close() }
    }

    /**
     * Wzór na odcinku łuku [a]..[b] z całkowitą liczbą okresów: środki kresek stoją w obu końcach i równo między nimi.
     * Otwarty koniec ([openA], [openB]) dostaje całą kreskę zamiast jej połowy.
     */
    private fun fitted(out: Pieces, a: Float, b: Float, openA: Boolean, openB: Boolean, dash: Float, period: Float) {
        val h = dash * 0.5f
        val first = if (openA) a + h else a
        val last = if (openB) b - h else b
        val inner = last - first
        if (inner <= 0f) { span(out, a, b); return }
        val k = max(1, (inner / period).roundToInt())
        val step = inner / k
        if (dash > 0f && step - dash < MIN_GAP * (period - dash)) { span(out, a, b); return }
        for (i in 0..k) {
            val c = if (i == k) last else first + i * step
            span(out, max(a, c - h), min(b, c + h))
        }
    }

    /** Czy w punkcie [j] łamana zawraca po własnym śladzie. */
    private fun turnsBack(out: Pieces, j: Int): Boolean {
        val w = out.w
        val ax = w[3 * j] - w[3 * j - 3]; val ay = w[3 * j + 1] - w[3 * j - 2]
        val bx = w[3 * j + 3] - w[3 * j]; val by = w[3 * j + 4] - w[3 * j + 1]
        return ax * bx + ay * by < -0.98f * hypot(ax, ay) * hypot(bx, by)
    }

    /** Dopisuje przedział długości łuku; stykające się przedziały (róg łamanej) łączą się w jeden kawałek. */
    private fun span(out: Pieces, a: Float, b: Float) {
        val iv = out.iv
        if (iv.size >= 2 && a <= iv[iv.size - 1] + EPS) {
            if (b > iv[iv.size - 1]) iv.a[iv.size - 1] = b
        } else iv.add(a, b)
    }

    /** Punkty kreski między długościami łuku [sa] i [sb], z końcami wyliczonymi na odcinkach. */
    private fun sub(out: Pieces, sa: Float, sb: Float, skipFirst: Boolean) {
        val cum = out.cum; val m = out.m
        var lo = 0; var hi = m - 2
        while (lo < hi) {
            val mid = (lo + hi + 1) ushr 1
            if (cum[mid] <= sa) lo = mid else hi = mid - 1
        }
        if (!skipFirst) point(out, lo, sa)
        if (sb <= sa) return
        var j = lo + 1
        while (j < m - 1 && cum[j] < sb - EPS) {
            if (cum[j] > sa + EPS) out.pts.add(out.w[3 * j], out.w[3 * j + 1], out.w[3 * j + 2])
            j++
        }
        point(out, j - 1, sb)
    }

    private fun point(out: Pieces, i: Int, s: Float) {
        val w = out.w; val cum = out.cum
        val t = (s - cum[i]) / (cum[i + 1] - cum[i])
        // Koniec odcinka co do bitu: zamknięty kształt ma w szwie dokładnie ten sam punkt co na początku.
        val a = if (t >= 1f) 3 * i + 3 else 3 * i
        if (t <= 0f || t >= 1f) { out.pts.add(w[a], w[a + 1], w[a + 2]); return }
        out.pts.add(w[a] + (w[a + 3] - w[a]) * t, w[a + 1] + (w[a + 4] - w[a + 1]) * t, w[a + 2] + (w[a + 5] - w[a + 2]) * t)
    }
}

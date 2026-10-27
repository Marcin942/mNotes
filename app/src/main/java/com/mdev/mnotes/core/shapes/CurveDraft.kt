package com.mdev.mnotes.core.shapes

import com.mdev.mnotes.core.geom.FloatList
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Tryby narzędzia Krzywa (2b-A10), w kolejności listwy; numer = ustawienie `curve.mode`.
 * Odręczna — kreska dopasowana do krzywej z małą liczbą węzłów (dawne F17); przez punkty — gładka krzywa przez stuknięte
 * punkty; kontur — to samo, zamknięte; piórko — jak w Inkscape: stuknięcie stawia węzeł, przeciągnięcie wyciąga uchwyty.
 */
enum class CurveMode(val pl: String, val en: String) {
    FREEHAND("Odręczna → krzywa", "Freehand → curve"),
    THROUGH("Przez wskazane punkty", "Through tapped points"),
    CLOSED("Kontur zamknięty", "Closed outline"),
    PEN("Piórko (węzły i uchwyty)", "Pen (nodes and handles)");

    /** Tryb, w którym krzywą buduje się z kolejnych dotknięć (szkic [CurveDraft]), a nie jedną kreską. */
    val stepwise: Boolean get() = this != FREEHAND

    companion object {
        fun of(i: Int): CurveMode = entries.getOrElse(i) { FREEHAND }
    }
}

object CurveFit {
    /** Tolerancja dopasowania odręcznej kreski w pt; [ptPerDp] = ile pt strony mieści 1 dp ekranu. Luźna — mało węzłów. */
    fun freehandTol(ptPerDp: Float): Float = max(1.2f, 3.5f * ptPerDp)

    /** Czy zapis [ctrl] jest zamknięty: ostatnia kotwica w miejscu pierwszej (jak zamknięta łamana). */
    fun closed(ctrl: FloatArray?): Boolean {
        if (ctrl == null || ctrl.size < 14) return false
        val n = ctrl.size / 2
        return ctrl[0] == ctrl[2 * n - 2] && ctrl[1] == ctrl[2 * n - 1]
    }

    /**
     * Gładka krzywa przez punkty (x, y …): Catmull‑Rom w wersji dośrodkowej (α = ½) zamieniony na odcinki Béziera.
     * Wersja dośrodkowa nie robi pętli ani dużych wybrzuszeń przy nierówno rozstawionych punktach (Yuksel i in. 2011).
     * [closed] łączy ostatni punkt z pierwszym; zwraca zapis [Bezier] albo null dla mniej niż 2 punktów.
     */
    fun through(xyIn: FloatArray, closed: Boolean): FloatArray? {
        val p = dedupe(xyIn, closed)
        val n = p.size / 2
        if (n < 2) return null
        if (n == 2 && !closed) return floatArrayOf(p[0], p[1], p[0] + (p[2] - p[0]) / 3f, p[1] + (p[3] - p[1]) / 3f, p[0] + 2f * (p[2] - p[0]) / 3f, p[1] + 2f * (p[3] - p[1]) / 3f, p[2], p[3])
        val segs = if (closed) n else n - 1
        // Punkt k (z zawinięciem albo z odbiciem na końcach — wtedy styczna na końcu biegnie w stronę sąsiada).
        fun px(k: Int): Float = when {
            closed -> p[2 * Math.floorMod(k, n)]
            k < 0 -> 2f * p[0] - p[2]
            k >= n -> 2f * p[2 * n - 2] - p[2 * n - 4]
            else -> p[2 * k]
        }
        fun py(k: Int): Float = when {
            closed -> p[2 * Math.floorMod(k, n) + 1]
            k < 0 -> 2f * p[1] - p[3]
            k >= n -> 2f * p[2 * n - 1] - p[2 * n - 3]
            else -> p[2 * k + 1]
        }
        val out = FloatList(6 * segs + 2)
        out.add(px(0), py(0))
        for (i in 0 until segs) {
            val x0 = px(i - 1); val y0 = py(i - 1); val x1 = px(i); val y1 = py(i)
            val x2 = px(i + 1); val y2 = py(i + 1); val x3 = px(i + 2); val y3 = py(i + 2)
            // d = |Δ|^α, α = ½; d² to sama długość.
            val d1 = sqrt(max(hypot(x1 - x0, y1 - y0), 1e-4f))
            val d2 = sqrt(max(hypot(x2 - x1, y2 - y1), 1e-4f))
            val d3 = sqrt(max(hypot(x3 - x2, y3 - y2), 1e-4f))
            val a = 2f * d1 * d1 + 3f * d1 * d2 + d2 * d2
            val b = 3f * d1 * (d1 + d2)
            val c = 2f * d3 * d3 + 3f * d3 * d2 + d2 * d2
            val e = 3f * d3 * (d3 + d2)
            out.add((d1 * d1 * x2 - d2 * d2 * x0 + a * x1) / b, (d1 * d1 * y2 - d2 * d2 * y0 + a * y1) / b)
            out.add((d3 * d3 * x1 - d2 * d2 * x3 + c * x2) / e, (d3 * d3 * y1 - d2 * d2 * y3 + c * y2) / e)
            out.add(x2, y2)
        }
        val r = out.toArray()
        // Zamknięta: koniec dokładnie w początku (tak [closed] ją rozpoznaje po zapisie).
        if (closed) { r[r.size - 2] = r[0]; r[r.size - 1] = r[1] }
        return r
    }

    /** Pomija punkty powtórzone (podwójne stuknięcie w to samo miejsce), a w zamkniętej — ostatni równy pierwszemu. */
    private fun dedupe(xy: FloatArray, closed: Boolean): FloatArray {
        val out = FloatList(xy.size)
        for (i in 0 until xy.size / 2) {
            val x = xy[2 * i]; val y = xy[2 * i + 1]
            if (out.size >= 2 && hypot(x - out[out.size - 2], y - out[out.size - 1]) < 0.5f) continue
            out.add(x, y)
        }
        if (closed && out.size >= 6 && hypot(out[0] - out[out.size - 2], out[1] - out[out.size - 1]) < 0.5f) { out.size -= 2 }
        return out.toArray()
    }
}

/**
 * Szkic krzywej budowanej krok po kroku (tryby [CurveMode.stepwise]) — stan między dotknięciami, bez Androida.
 * Współrzędne w pt strony. Przez punkty i kontur: palec przy stronie przesuwa ostatni punkt, puszczenie go zostawia.
 * Piórko: dotknięcie stawia węzeł, przeciągnięcie wyciąga uchwyt wyjściowy (wejściowy jest jego lustrem — węzeł gładki).
 */
class CurveDraft(val mode: CurveMode) {
    companion object {
        /** Podpowiedź nad płótnem: co zrobić przy [count] postawionych węzłach. */
        fun hint(mode: CurveMode, count: Int, pl: Boolean): String {
            fun t(p: String, e: String) = if (pl) p else e
            return when {
                mode == CurveMode.FREEHAND -> t("Rysuj — kreska stanie się krzywą z uchwytami", "Draw — the stroke becomes a curve with handles")
                count == 0 && mode == CurveMode.PEN -> t("Stuknij węzeł albo przeciągnij, by wyciągnąć uchwyty", "Tap a node or drag to pull out handles")
                count == 0 -> t("Stuknij pierwszy punkt krzywej", "Tap the first point of the curve")
                count == 1 -> t("Stuknij kolejny punkt", "Tap the next point")
                count == 2 -> t("Kolejny punkt; ostatni stuknięty drugi raz kończy", "Next point; tapping the last one again finishes")
                mode == CurveMode.CLOSED -> t("Kolejny punkt; pierwszy albo ostatni stuknięty ponownie domyka", "Next point; tapping the first or last again closes")
                else -> t("Kolejny punkt; ostatni ponownie — koniec, pierwszy — domknięcie", "Next point; last again — finish, first — close")
            }
        }
    }

    /** Węzły: x, y, uchwyt wyjściowy x, y (w trybach przez punkty uchwyt = węzeł, nieużywany). */
    private val nodes = FloatList(32)
    private var pressing = false

    val count: Int get() = nodes.size / 4
    val isEmpty: Boolean get() = count == 0

    fun x(i: Int) = nodes[4 * i]
    fun y(i: Int) = nodes[4 * i + 1]
    fun hx(i: Int) = nodes[4 * i + 2]
    fun hy(i: Int) = nodes[4 * i + 3]

    /** Czy węzeł [i] ma wyciągnięty uchwyt (piórko). */
    fun hasHandle(i: Int) = hypot(hx(i) - x(i), hy(i) - y(i)) > 0.01f

    /** Co zrobi dotknięcie w (x, y) przy promieniu trafienia [r]: domknięcie, zakończenie albo nowy węzeł. */
    enum class Tap { ADD, CLOSE, FINISH }

    fun tapKind(x: Float, y: Float, r: Float): Tap = when {
        count >= 3 && hypot(x - x(0), y - y(0)) <= r -> Tap.CLOSE
        count >= 2 && hypot(x - x(count - 1), y - y(count - 1)) <= r -> Tap.FINISH
        else -> Tap.ADD
    }

    /** Początek dotyku: nowy węzeł w (x, y). */
    fun press(x: Float, y: Float) {
        nodes.add(x, y); nodes.add(x, y)
        pressing = true
    }

    /** Ruch dotyku: przez punkty — węzeł idzie za palcem; piórko — uchwyt wyjściowy. */
    fun drag(x: Float, y: Float) {
        if (!pressing || isEmpty) return
        val o = 4 * (count - 1)
        val a = nodes.a
        if (mode == CurveMode.PEN) { a[o + 2] = x; a[o + 3] = y }
        else { a[o] = x; a[o + 1] = y; a[o + 2] = x; a[o + 3] = y }
    }

    fun release() { pressing = false }

    /** Cofa ostatni węzeł (np. dotyk przerwany albo stuknięcie, które okazało się zakończeniem). */
    fun dropLast() { if (!isEmpty) nodes.size -= 4; pressing = false }

    fun clear() { nodes.size = 0; pressing = false }

    /** Zapis [Bezier] szkicu; [close] — domknięty (kontur zawsze, gdy ma ≥ 3 węzły). Null, gdy za mało węzłów. */
    fun ctrl(close: Boolean = mode == CurveMode.CLOSED): FloatArray? {
        val n = count
        if (n < 2) return null
        val closed = close && n >= 3
        if (mode != CurveMode.PEN) {
            val xy = FloatArray(2 * n) { k -> nodes[4 * (k / 2) + k % 2] }
            return CurveFit.through(xy, closed)
        }
        val segs = if (closed) n else n - 1
        val out = FloatList(6 * segs + 2)
        out.add(x(0), y(0))
        for (s in 0 until segs) {
            val a = s; val b = (s + 1) % n
            out.add(hx(a), hy(a))
            // Uchwyt wejściowy = lustro wyjściowego; bez uchwytu oba w węźle (narożnik).
            out.add(2f * x(b) - hx(b), 2f * y(b) - hy(b))
            out.add(x(b), y(b))
        }
        val r = out.toArray()
        // Odcinek bez żadnego uchwytu: uchwyty na ⅓ i ⅔, żeby edycja po fakcie miała za co chwycić.
        for (s in 0 until segs) {
            val o = 6 * s
            val flat = hypot(r[o + 2] - r[o], r[o + 3] - r[o + 1]) < 0.01f && hypot(r[o + 4] - r[o + 6], r[o + 5] - r[o + 7]) < 0.01f
            if (flat) {
                r[o + 2] = r[o] + (r[o + 6] - r[o]) / 3f; r[o + 3] = r[o + 1] + (r[o + 7] - r[o + 1]) / 3f
                r[o + 4] = r[o] + 2f * (r[o + 6] - r[o]) / 3f; r[o + 5] = r[o + 1] + 2f * (r[o + 7] - r[o + 1]) / 3f
            }
        }
        return r
    }
}

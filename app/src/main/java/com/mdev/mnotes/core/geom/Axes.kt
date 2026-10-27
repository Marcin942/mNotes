package com.mdev.mnotes.core.geom

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/**
 * Układ współrzędnych (F8): osie ze strzałkami, podziałka, siatka i opisy liczbowe dla zakresu [xMin, xMax] × [yMin, yMax].
 * Wykres rysuje się potem samemu piórem — narzędzie daje tylko równą, odmierzoną „kratkę” (rysowanie ze wzoru → lab).
 *
 * [unit] to pt strony na jedną jednostkę osi. Oś leży na zerze, a gdy zero jest poza zakresem — na brzegu zakresu.
 */
data class AxesSpec(
    val xMin: Float = -5f,
    val xMax: Float = 5f,
    val yMin: Float = -5f,
    val yMax: Float = 5f,
    val xStep: Float = 1f,
    val yStep: Float = 1f,
    val unit: Float = 20f,
    val grid: Boolean = true,
    val labels: Boolean = true,
    /** Co który znacznik podpisywać (1 = każdy). */
    val labelEvery: Int = 1,
    val arrows: Boolean = true,
    val xName: String = "x",
    val yName: String = "y",
) {
    /** Poprawiony zakres: min < max, krok > 0, nie więcej niż [MAX_TICKS] znaczników na oś. */
    fun normalized(): AxesSpec {
        val x0 = min(xMin, xMax); val x1 = max(xMin, xMax).let { if (it - x0 < 1e-3f) x0 + 1f else it }
        val y0 = min(yMin, yMax); val y1 = max(yMin, yMax).let { if (it - y0 < 1e-3f) y0 + 1f else it }
        val sx = max(abs(xStep), (x1 - x0) / MAX_TICKS).coerceAtLeast(1e-3f)
        val sy = max(abs(yStep), (y1 - y0) / MAX_TICKS).coerceAtLeast(1e-3f)
        return copy(xMin = x0, xMax = x1, yMin = y0, yMax = y1, xStep = sx, yStep = sy, unit = unit.coerceIn(2f, 400f), labelEvery = labelEvery.coerceAtLeast(1))
    }

    /** Zapis do ustawień (ostatnio użyty układ): liczby i flagi rozdzielone „;”. */
    fun encode(): String = listOf(xMin, xMax, yMin, yMax, xStep, yStep, unit).joinToString(";") { it.toString() } +
        ";" + listOf(grid, labels, arrows).joinToString(";") { if (it) "1" else "0" } + ";$labelEvery;" + xName.replace(";", "") + ";" + yName.replace(";", "")

    companion object {
        const val MAX_TICKS = 200

        /** Odczyt [encode]; uszkodzony albo pusty zapis daje układ domyślny. */
        fun decode(s: String): AxesSpec = runCatching {
            val p = s.split(";")
            val f = p.take(7).map { it.toFloat() }
            AxesSpec(f[0], f[1], f[2], f[3], f[4], f[5], f[6], p[7] == "1", p[8] == "1", p[10].toInt(), p[9] == "1", p.getOrElse(11) { "x" }, p.getOrElse(12) { "y" })
        }.getOrDefault(AxesSpec())

        /** Liczba wpisana po polsku albo angielsku („0,5”, „−2”, „1.5”); null, gdy to nie liczba. */
        fun parse(t: String): Float? = t.trim().replace(',', '.').replace('−', '-').toFloatOrNull()?.takeIf { it.isFinite() }
    }
}

/** Opis liczbowy: tekst i punkt zaczepienia; [ax], [ay] ∈ {-1, 0, 1} mówią, którą stroną tekst przylega do punktu. */
class AxisLabel(val x: Float, val y: Float, val text: String, val ax: Int, val ay: Int)

/** Gotowy rysunek: łamane osi i strzałek, odcinki podziałki, linie siatki (cieńsze, jaśniejsze) i opisy. */
class AxesDrawing(val axes: List<FloatArray>, val ticks: List<FloatArray>, val grid: List<FloatArray>, val labels: List<AxisLabel>, val bounds: Bounds)

object Axes {
    /** Długość znacznika podziałki i grotu strzałki w pt. */
    const val TICK = 3f
    const val ARROW = 6f
    /** Wysunięcie osi poza zakres (pod strzałkę). */
    const val OVERHANG = 10f

    /** Wartości znaczników k·step mieszczące się w [lo, hi]. */
    fun ticks(lo: Float, hi: Float, step: Float): List<Float> {
        val out = ArrayList<Float>()
        var k = kotlin.math.ceil(lo / step - 1e-4f).toLong()
        while (true) {
            val v = k * step
            if (v > hi + step * 1e-4f || out.size > AxesSpec.MAX_TICKS) break
            out.add(if (abs(v) < step * 1e-4f) 0f else v)
            k++
        }
        return out
    }

    /** Liczba po polsku: przecinek dziesiętny, bez zbędnych zer, minus jako „−”. */
    fun format(v: Float): String {
        val r = (v * 1000.0).roundToLong()
        if (r % 1000L == 0L) return (r / 1000L).toString().replace('-', '−')
        var s = String.format(java.util.Locale.ROOT, "%.3f", r / 1000.0).trimEnd('0').trimEnd('.')
        s = s.replace('.', ',').replace('-', '−')
        return s
    }

    /**
     * Rysunek układu z lewym górnym rogiem zakresu w punkcie strony ([left], [top]). Oś y rośnie do góry, więc
     * punkt (x, y) układu leży w (left + (x − xMin)·unit, top + (yMax − y)·unit).
     */
    fun build(specIn: AxesSpec, left: Float, top: Float): AxesDrawing {
        val s = specIn.normalized()
        val u = s.unit
        fun px(x: Float) = left + (x - s.xMin) * u
        fun py(y: Float) = top + (s.yMax - y) * u
        val ox = if (s.xMin > 0f) s.xMin else if (s.xMax < 0f) s.xMax else 0f
        val oy = if (s.yMin > 0f) s.yMin else if (s.yMax < 0f) s.yMax else 0f
        val x0 = px(s.xMin); val x1 = px(s.xMax); val y0 = py(s.yMax); val y1 = py(s.yMin)
        val axY = py(oy); val axX = px(ox)
        val over = if (s.arrows) OVERHANG else 0f
        val axes = ArrayList<FloatArray>()
        axes.add(floatArrayOf(x0, axY, x1 + over, axY))
        axes.add(floatArrayOf(axX, y1, axX, y0 - over))
        if (s.arrows) {
            val a = ARROW; val w = ARROW * 0.55f
            axes.add(floatArrayOf(x1 + over - a, axY - w, x1 + over, axY, x1 + over - a, axY + w))
            axes.add(floatArrayOf(axX - w, y0 - over + a, axX, y0 - over, axX + w, y0 - over + a))
        }
        val ticks = ArrayList<FloatArray>()
        val grid = ArrayList<FloatArray>()
        val labels = ArrayList<AxisLabel>()
        val xs = ticks(s.xMin, s.xMax, s.xStep)
        val ys = ticks(s.yMin, s.yMax, s.yStep)
        for ((i, v) in xs.withIndex()) {
            val x = px(v)
            if (s.grid) grid.add(floatArrayOf(x, y0, x, y1))
            if (v == ox) continue
            ticks.add(floatArrayOf(x, axY - TICK, x, axY + TICK))
            if (s.labels && labelIndex(v, s.xStep) % s.labelEvery == 0L) labels.add(AxisLabel(x, axY + TICK + 1.5f, format(v), 0, 1))
        }
        for (v in ys) {
            val y = py(v)
            if (s.grid) grid.add(floatArrayOf(x0, y, x1, y))
            if (v == oy) continue
            ticks.add(floatArrayOf(axX - TICK, y, axX + TICK, y))
            if (s.labels && labelIndex(v, s.yStep) % s.labelEvery == 0L) labels.add(AxisLabel(axX - TICK - 1.5f, y, format(v), -1, 0))
        }
        if (s.labels) {
            // Początek układu podpisany raz, w lewym dolnym kącie przecięcia osi.
            if (ox == 0f && oy == 0f) labels.add(AxisLabel(axX - 2f, axY + 2f, "0", -1, 1))
            if (s.xName.isNotBlank()) labels.add(AxisLabel(x1 + over, axY - TICK - 2f, s.xName, -1, -1))
            if (s.yName.isNotBlank()) labels.add(AxisLabel(axX + TICK + 2f, y0 - over, s.yName, 1, 0))
        }
        val b = Bounds(x0 - 1f, y0 - over - ARROW, x1 + over + 1f, y1 + 1f)
        return AxesDrawing(axes, ticks, grid, labels, b)
    }

    /** Numer znacznika (całkowity), żeby „co drugi opis” liczył się od zera, a nie od brzegu zakresu. */
    private fun labelIndex(v: Float, step: Float): Long = abs(floor(v / step + 0.5f).toLong())
}

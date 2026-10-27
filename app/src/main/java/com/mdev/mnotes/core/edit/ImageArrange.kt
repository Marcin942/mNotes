package com.mdev.mnotes.core.edit

import com.mdev.mnotes.core.geom.Bounds
import kotlin.math.abs

/**
 * „Uporządkuj obrazy” (F23): kilka zdjęć w równych wierszach w zadanym prostokącie — kolejność zostaje, proporcje
 * też, wiersze mają wspólną wysokość i wypełniają szerokość (jak galeria), całość jest wyśrodkowana.
 */
object ImageArrange {
    /** Miejsce obrazu: środek i rozmiar (pt). */
    class Slot(val cx: Float, val cy: Float, val w: Float, val h: Float)

    /** [aspects] = szerokość / wysokość kolejnych obrazów. Wybiera liczbę wierszy, która daje największe obrazy. */
    fun arrange(aspects: List<Float>, area: Bounds, gap: Float = 6f): List<Slot> {
        val n = aspects.size
        if (n == 0 || area.w <= 0f || area.h <= 0f) return emptyList()
        var best: List<Slot> = emptyList(); var bestArea = -1f
        for (rows in 1..n) {
            val slots = layout(aspects, rows, area, gap)
            val a = slots.sumOf { (it.w * it.h).toDouble() }.toFloat()
            if (a > bestArea) { bestArea = a; best = slots }
        }
        return best
    }

    /** Podział na [rows] wierszy o możliwie równych sumach proporcji (zachłannie, po kolei). */
    private fun split(aspects: List<Float>, rows: Int): List<List<Int>> {
        val total = aspects.sum()
        val target = total / rows
        val out = ArrayList<List<Int>>()
        var cur = ArrayList<Int>(); var acc = 0f
        for (i in aspects.indices) {
            val left = aspects.size - i
            val rowsLeft = rows - out.size
            // Przechodzimy do nowego wiersza, gdy dołożenie obrazu oddala sumę od celu, a obrazów starczy na resztę wierszy.
            if (cur.isNotEmpty() && rowsLeft > 1 && (abs(acc + aspects[i] - target) > abs(acc - target) || left < rowsLeft)) {
                out.add(cur); cur = ArrayList(); acc = 0f
            }
            cur.add(i); acc += aspects[i]
        }
        out.add(cur)
        return out
    }

    private fun layout(aspects: List<Float>, rows: Int, area: Bounds, gap: Float): List<Slot> {
        val groups = split(aspects, rows)
        // Wysokość wiersza, przy której wypełnia całą szerokość; potem wspólne pomniejszenie, gdy wiersze nie mieszczą się w pionie.
        val hs = groups.map { g -> (area.w - gap * (g.size - 1)) / g.sumOf { aspects[it].toDouble() }.toFloat() }
        val k = minOf(1f, (area.h - gap * (groups.size - 1)).coerceAtLeast(1f) / hs.sum().coerceAtLeast(1e-3f))
        val usedH = hs.sum() * k + gap * (groups.size - 1)
        var y = area.t + (area.h - usedH) / 2f
        val out = arrayOfNulls<Slot>(aspects.size)
        for ((gi, g) in groups.withIndex()) {
            val h = hs[gi] * k
            val rowW = g.sumOf { (aspects[it] * h).toDouble() }.toFloat() + gap * (g.size - 1)
            var x = area.l + (area.w - rowW) / 2f
            for (i in g) {
                val w = aspects[i] * h
                out[i] = Slot(x + w / 2f, y + h / 2f, w, h)
                x += w + gap
            }
            y += h + gap
        }
        return out.map { it!! }
    }
}

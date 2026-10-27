package com.mdev.mnotes.core.edit

import com.mdev.mnotes.core.geom.Bounds
import com.mdev.mnotes.core.geom.FloatList
import com.mdev.mnotes.core.model.BoxElement
import com.mdev.mnotes.core.model.Element
import com.mdev.mnotes.core.model.FillEl
import com.mdev.mnotes.core.model.Ids
import com.mdev.mnotes.core.model.Stroke
import com.mdev.mnotes.core.model.TapeEl

/**
 * Gumka „Wycinanie” (F12): obrys pętlą usuwa z kresek dokładnie to, co jest w środku — cięcie wypada na linii pętli
 * (punkty przecięcia liczone dokładnie), więc nie ma ząbków i okruchów jak przy gumce pikselowej, a od lassa różni
 * się tym, że usuwa. Ramki (tekst, obraz, taśma) i wypełnienia w całości w pętli znikają; przecięte zostają (obrazu
 * nie da się wyciąć dowolnym kształtem bez zmiany formatu).
 */
object CutOut {

    /** Wynik na stronie: nowa lista elementów (te same instancje dla nietkniętych) albo null, gdy nic się nie zmieniło. */
    fun apply(elements: List<Element>, poly: FloatArray): List<Element>? {
        if (poly.size < 6) return null
        val pb = Bounds.ofPoints(poly)
        var changed = false
        val out = ArrayList<Element>(elements.size)
        for (e in elements) {
            if (e.locked || !e.bounds.intersects(pb)) { out.add(e); continue }
            when (e) {
                is Stroke -> {
                    val pieces = cutStroke(e, poly)
                    if (pieces == null) out.add(e)
                    else { changed = true; for (p in pieces) out.add(Stroke(Ids.next(), e.pen, e.color, e.width, p, false, poly = e.poly && !e.ellipse, line = e.line)) }
                }
                is BoxElement, is FillEl -> {
                    if (e !is TapeEl && whollyInside(e, poly)) changed = true else out.add(e)
                }
            }
        }
        return if (changed) out else null
    }

    private fun whollyInside(e: Element, poly: FloatArray): Boolean {
        val b = e.bounds
        return EraseMath.polygonContains(poly, b.l, b.t) && EraseMath.polygonContains(poly, b.r, b.t) &&
            EraseMath.polygonContains(poly, b.r, b.b) && EraseMath.polygonContains(poly, b.l, b.b)
    }

    /**
     * Kreska po wycięciu: kawałki poza pętlą (trójki x, y, p), z punktami cięcia dokładnie na krawędzi pętli; null =
     * nietknięta, pusta lista = cała w środku.
     */
    fun cutStroke(s: Stroke, poly: FloatArray): List<FloatArray>? {
        val p = s.pts
        val n = s.count
        if (n == 0) return null
        if (n == 1) return if (EraseMath.polygonContains(poly, p[0], p[1])) emptyList() else null
        val m = poly.size / 2
        val pieces = ArrayList<FloatList>()
        var cur: FloatList? = null
        var touched = false
        val ts = ArrayList<Float>()
        for (i in 0 until n - 1) {
            val x0 = p[3 * i]; val y0 = p[3 * i + 1]; val p0 = p[3 * i + 2]
            val x1 = p[3 * i + 3]; val y1 = p[3 * i + 4]; val p1 = p[3 * i + 5]
            // Parametry przecięć odcinka z krawędziami pętli (zamkniętej).
            ts.clear()
            for (j in 0 until m) {
                val k = (j + 1) % m
                val t = segT(x0, y0, x1, y1, poly[2 * j], poly[2 * j + 1], poly[2 * k], poly[2 * k + 1])
                if (t != null && t > 1e-6f && t < 1f - 1e-6f) ts.add(t)
            }
            ts.sort()
            val cuts = ArrayList<Float>(ts.size + 2).apply { add(0f); addAll(ts); add(1f) }
            if (ts.isNotEmpty()) touched = true
            for (q in 0 until cuts.size - 1) {
                val a = cuts[q]; val b = cuts[q + 1]
                val mid = (a + b) / 2f
                val inside = EraseMath.polygonContains(poly, x0 + (x1 - x0) * mid, y0 + (y1 - y0) * mid)
                if (inside) { touched = true; cur = null; continue }
                if (cur == null) cur = FloatList().also { pieces.add(it); it.add(x0 + (x1 - x0) * a, y0 + (y1 - y0) * a, p0 + (p1 - p0) * a) }
                cur.add(x0 + (x1 - x0) * b, y0 + (y1 - y0) * b, p0 + (p1 - p0) * b)
            }
        }
        if (!touched) return null
        val out = pieces.filter { it.size >= 6 }.map { it.toArray() }
        // Zamknięta kreska rozcięta raz: kawałek z końca i kawałek z początku to jedna kreska.
        if (s.closed && out.size >= 2) {
            val first = out.first(); val last = out.last()
            if (first[0] == p[0] && first[1] == p[1] && last[last.size - 3] == p[0] && last[last.size - 2] == p[1])
                return listOf(last + first.copyOfRange(3, first.size)) + out.subList(1, out.size - 1)
        }
        return out
    }

    /** Parametr t ∈ [0, 1] na odcinku AB, gdzie przecina on odcinek CD; null, gdy się nie przecinają (albo są równoległe). */
    private fun segT(ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float, dx: Float, dy: Float): Float? {
        val rx = bx - ax; val ry = by - ay; val sx = dx - cx; val sy = dy - cy
        val den = rx * sy - ry * sx
        if (kotlin.math.abs(den) < 1e-9f) return null
        val t = ((cx - ax) * sy - (cy - ay) * sx) / den
        val u = ((cx - ax) * ry - (cy - ay) * rx) / den
        return if (t in 0f..1f && u in 0f..1f) t else null
    }
}

package com.mdev.mnotes.core.edit

import com.mdev.mnotes.core.geom.Bounds
import com.mdev.mnotes.core.geom.FloatList
import com.mdev.mnotes.core.model.FillEl
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Gumka pikselowa na wypełnieniu: dokładna różnica wielokąta i koła, bez rastra. Wypełnienie jest parzysto-nieparzyste,
 * więc kierunek pierścieni nie ma znaczenia: nowy brzeg to kawałki starych pierścieni poza kołem i łuki koła leżące w
 * wypełnieniu, sklejone w punktach przecięcia. Koszt jest liniowy w liczbie wierzchołków, więc krok gumki nie zacina
 * rysowania nawet na dużym wypełnieniu.
 */
object FillErase {

    /** Wierzchołków łuku na pełny okrąg: na gumce wielkości kciuka odchylenie od koła jest ułamkiem punktu. */
    private const val ARC_STEPS = 48

    /** Pierścienie o mniejszym polu (pt²) to okruchy po zetknięciu gumki z brzegiem: znikają. */
    private const val MIN_AREA = 0.05f

    /** Odchylenie (pt), poniżej którego wierzchołek przy gumce jest zbędny: znacznie mniej niż piksel przy dużym zoomie. */
    private const val TIDY_EPS = 0.04f

    /** Wynik: nowe pierścienie (pusta lista = wypełnienie starte w całości) i obszar strony do przemalowania. */
    class Erased(val rings: List<FloatArray>, val dirty: Bounds)

    private class Crossing(val x: Float, val y: Float, val angle: Double) {
        var piece = -1
        var arc = -1
    }

    /** Kawałek starego pierścienia poza kołem: od przecięcia [from] przez [pts] do przecięcia [to]. */
    private class Piece(val from: Int, val to: Int, val pts: FloatArray)

    /** Null = gumka nie zmienia wypełnienia. */
    fun erase(f: FillEl, cx: Float, cy: Float, r: Float): Erased? {
        if (r <= 0f || !f.bounds.intersects(cx - r, cy - r, cx + r, cy + r)) return null
        val r2 = r * r
        val crossings = ArrayList<Crossing>()
        val pieces = ArrayList<Piece>()
        val kept = ArrayList<FloatArray>()
        var changed = false

        fun cross(x: Float, y: Float): Int {
            crossings.add(Crossing(x, y, atan2((y - cy).toDouble(), (x - cx).toDouble())))
            return crossings.size - 1
        }

        for (ring in f.rings) {
            val n = ring.size / 2
            if (n < 3) continue
            val inside = BooleanArray(n) { val dx = ring[2 * it] - cx; val dy = ring[2 * it + 1] - cy; dx * dx + dy * dy < r2 }
            // Zdarzenia na obwodzie po kolei: wyjście z koła zaczyna kawałek, wejście go kończy; między nimi wierzchołki.
            val events = ArrayList<Int>()
            val exits = ArrayList<Boolean>()
            val between = ArrayList<FloatList>()
            var cur = FloatList()
            for (i in 0 until n) {
                val j = (i + 1) % n
                val ax = ring[2 * i]; val ay = ring[2 * i + 1]
                val bx = ring[2 * j]; val by = ring[2 * j + 1]
                if (!inside[i]) { cur.add(ax); cur.add(ay) }
                val dx = bx - ax; val dy = by - ay
                val ox = ax - cx; val oy = ay - cy
                val qa = dx * dx + dy * dy
                if (qa == 0f) continue
                val qb = 2f * (ox * dx + oy * dy)
                val qc = ox * ox + oy * oy - r2
                val disc = qb * qb - 4f * qa * qc
                if (inside[i] == inside[j] && (inside[i] || disc <= 0f)) continue
                val sq = sqrt(maxOf(disc, 0f))
                val t1 = (-qb - sq) / (2f * qa); val t2 = (-qb + sq) / (2f * qa)
                fun at(t: Float) = cross(ax + dx * t.coerceIn(0f, 1f), ay + dy * t.coerceIn(0f, 1f))
                when {
                    inside[i] && !inside[j] -> { events.add(at(t2)); exits.add(true); between.add(cur); cur = FloatList() }
                    !inside[i] && inside[j] -> { events.add(at(t1)); exits.add(false); between.add(cur); cur = FloatList() }
                    t1 > 0f && t2 < 1f && t1 < t2 -> {
                        events.add(at(t1)); exits.add(false); between.add(cur); cur = FloatList()
                        events.add(at(t2)); exits.add(true); between.add(cur); cur = FloatList()
                    }
                }
            }
            if (events.isEmpty()) {
                if (inside[0]) changed = true else kept.add(ring)
                continue
            }
            changed = true
            // Wierzchołki po ostatnim zdarzeniu należą do kawałka zaczętego tym zdarzeniem i kończą się na pierwszym.
            val head = between[0]
            for (q in 0 until head.size) cur.add(head[q])
            between[0] = cur
            val m = events.size
            for (k in 0 until m) {
                if (!exits[k]) continue
                val e = (k + 1) % m
                pieces.add(Piece(events[k], events[e], between[e].toArray()))
                crossings[events[k]].piece = pieces.size - 1
                crossings[events[e]].piece = pieces.size - 1
            }
        }

        if (crossings.isEmpty()) {
            // Koło w całości w wypełnieniu: wycina w nim dziurę. Okrąg może dotykać brzegu w punkcie (gumka styka się
            // z krawędzią), więc rozstrzyga większość z trzech punktów pod nieosiowymi kątami.
            val votes = doubleArrayOf(0.3, 2.4, 4.5).count { f.containsPoint(cx + r * cos(it).toFloat(), cy + r * sin(it).toFloat()) }
            if (votes >= 2) { kept.add(circle(cx, cy, r)); changed = true }
            if (!changed) return null
            return Erased(kept, dirty(cx, cy, r))
        }

        // Łuki między kolejnymi przecięciami leżą na zmianę w wypełnieniu i poza nim; o parzystości rozstrzyga najdłuższy.
        val order = crossings.indices.sortedBy { crossings[it].angle }
        val k = order.size
        fun span(i: Int): Double {
            val a = crossings[order[i]].angle; val b = crossings[order[(i + 1) % k]].angle
            return if (k == 1) 2 * PI else (b - a + 4 * PI) % (2 * PI)
        }
        var longest = 0
        for (i in 1 until k) if (span(i) > span(longest)) longest = i
        val mid = crossings[order[longest]].angle + span(longest) / 2
        val longestIn = f.containsPoint(cx + r * cos(mid).toFloat(), cy + r * sin(mid).toFloat())
        val arcFrom = ArrayList<Int>(); val arcTo = ArrayList<Int>()
        for (i in 0 until k) {
            if (((i - longest) % 2 == 0) != longestIn) continue
            val a = order[i]; val b = order[(i + 1) % k]
            arcFrom.add(a); arcTo.add(b)
            crossings[a].arc = arcFrom.size - 1
            crossings[b].arc = arcFrom.size - 1
        }

        // Każde przecięcie ma jeden kawałek i jeden łuk, więc idąc na przemian wraca się do początku.
        val usedPiece = BooleanArray(pieces.size)
        val usedArc = BooleanArray(arcFrom.size)
        for (start in pieces.indices) {
            if (usedPiece[start]) continue
            val out = FloatList()
            var node = pieces[start].from
            var viaPiece = true
            var guard = 0
            while (guard++ < 2 * (pieces.size + arcFrom.size) + 2) {
                val c = crossings[node]
                if (viaPiece) {
                    val pi = c.piece
                    if (pi < 0 || usedPiece[pi]) break
                    usedPiece[pi] = true
                    val p = pieces[pi]
                    out.add(c.x); out.add(c.y)
                    if (p.from == node) {
                        for (q in 0 until p.pts.size step 2) { out.add(p.pts[q]); out.add(p.pts[q + 1]) }
                        node = p.to
                    } else {
                        for (q in p.pts.size - 2 downTo 0 step 2) { out.add(p.pts[q]); out.add(p.pts[q + 1]) }
                        node = p.from
                    }
                } else {
                    val ai = c.arc
                    if (ai < 0 || usedArc[ai]) break
                    usedArc[ai] = true
                    out.add(c.x); out.add(c.y)
                    val a = arcFrom[ai]; val b = arcTo[ai]
                    val forward = a == node
                    val a0 = crossings[a].angle
                    val sweep = (crossings[b].angle - a0 + 4 * PI) % (2 * PI)
                    val steps = ceil(sweep / (2 * PI / ARC_STEPS)).toInt()
                    for (s in 1 until steps) {
                        val t = if (forward) s.toDouble() / steps else 1.0 - s.toDouble() / steps
                        val ang = a0 + sweep * t
                        out.add(cx + r * cos(ang).toFloat()); out.add(cy + r * sin(ang).toFloat())
                    }
                    node = if (forward) b else a
                }
                viaPiece = !viaPiece
            }
            val ring = tidy(out.toArray(), cx, cy, r + 2f)
            if (ring.size >= 6 && abs(FloodFill.area(ring)) >= MIN_AREA) kept.add(ring)
        }
        return Erased(kept, dirty(cx, cy, r))
    }

    /**
     * Usuwa wierzchołki prawie współliniowe w okolicy gumki. Każdy krok zostawia na brzegach śladu okruchy łuków; bez
     * tego liczba wierzchołków rosłaby z długością pociągnięcia, a z nią koszt kolejnych kroków i rysowania.
     */
    private fun tidy(ring: FloatArray, cx: Float, cy: Float, reach: Float): FloatArray {
        val n = ring.size / 2
        if (n <= 4) return ring
        val reach2 = reach * reach
        val out = FloatList(ring.size)
        out.add(ring[0], ring[1])
        for (i in 1 until n) {
            val x = ring[2 * i]; val y = ring[2 * i + 1]
            val dx = x - cx; val dy = y - cy
            if (dx * dx + dy * dy <= reach2) {
                val px = out[out.size - 2]; val py = out[out.size - 1]
                val j = (i + 1) % n
                val nx = ring[2 * j]; val ny = ring[2 * j + 1]
                val cxv = nx - px; val cyv = ny - py
                val len = sqrt(cxv * cxv + cyv * cyv)
                val off = if (len < 1e-6f) sqrt((x - px) * (x - px) + (y - py) * (y - py)) else abs((x - px) * cyv - (y - py) * cxv) / len
                if (off < TIDY_EPS) continue
            }
            out.add(x, y)
        }
        return out.toArray()
    }

    private fun dirty(cx: Float, cy: Float, r: Float) = Bounds(cx - r, cy - r, cx + r, cy + r).inflate(1f)

    private fun circle(cx: Float, cy: Float, r: Float) = FloatArray(2 * ARC_STEPS) {
        val a = 2 * PI * (it / 2) / ARC_STEPS
        if (it % 2 == 0) cx + r * cos(a).toFloat() else cy + r * sin(a).toFloat()
    }
}

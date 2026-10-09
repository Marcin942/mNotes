package com.mdev.mnotes.core.edit

import com.mdev.mnotes.core.geom.GeomMath
import com.mdev.mnotes.core.model.BoxElement
import com.mdev.mnotes.core.model.Element
import com.mdev.mnotes.core.model.FillEl
import com.mdev.mnotes.core.model.ImageEl
import com.mdev.mnotes.core.model.Stroke
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Wiaderko (narzędzie „Wypełnienie”): jak w Paincie, ale na wektorach. Strona jest rasteryzowana do siatki,
 * kreski i ramki są ścianami, a obszar spójny z punktem dotknięcia zamienia się z powrotem w kontury
 * (pierścienie do reguły parzysto-nieparzystej: wyspy wewnątrz obszaru wychodzą jako dziury).
 * Krawędź strony też jest ścianą, więc dotknięcie w nieograniczonym miejscu wypełnia całą stronę poza zamkniętymi kształtami.
 *
 * Z domykaniem szpar ściany są pogrubione o [wallPad], więc drobne niedomknięcia nie przeciekają. Pogrubione ściany
 * wyznaczają tylko, KTÓRY obszar dotknięto; pas zapasu (wolny od prawdziwych linii, zajęty przez pogrubienie) jest
 * potem rozdzielany między sąsiednie obszary według odległości. Dzięki temu wypełnienie dochodzi do samych linii także
 * w ostrych kątach i zakamarkach, a przez szparę nie wychodzi otoczka na zewnątrz kształtu.
 */
object FloodFill {

    /** Docelowa liczba komórek na dłuższym boku strony. */
    const val CELLS_LONG_SIDE = 1200
    const val MAX_CELLS = 2_000_000


    /** O tyle komórek wypełnienie wchodzi pod linie, żeby między nim a kreską nie było szczeliny (kreska leży nad nim). */
    private const val UNDERLAP = 3

    /**
     * Kontury obszaru wokół (x, y) na stronie [pageW]×[pageH] (pt). Null, gdy punkt leży poza stroną albo na linii
     * (na linii nie ma czego wypełniać). Istniejące wypełnienia i zablokowane obrazy (tło strony) nie są ścianami.
     * [closeGaps] domyka szpary węższe niż ok. 2 pt.
     */
    fun fill(elements: List<Element>, pageW: Float, pageH: Float, x: Float, y: Float, closeGaps: Boolean = false, maxCells: Int = MAX_CELLS): List<FloatArray>? {
        if (pageW <= 0f || pageH <= 0f || x < 0f || y < 0f || x >= pageW || y >= pageH) return null
        var cell = max(pageW, pageH) / CELLS_LONG_SIDE
        cell = max(cell, sqrt(pageW * pageH / maxCells))
        val gw = ceil(pageW / cell).toInt().coerceAtLeast(1)
        val gh = ceil(pageH / cell).toInt().coerceAtLeast(1)
        val wall = BooleanArray(gw * gh)
        for (e in elements) rasterize(e, wall, gw, gh, cell, 0f)

        val sx = min(gw - 1, (x / cell).toInt()); val sy = min(gh - 1, (y / cell).toInt())
        val s = sy * gw + sx
        if (wall[s]) return null
        val region = if (!closeGaps) flood(wall, gw, gh, s) else {
            val thick = wall.copyOf()
            for (e in elements) rasterize(e, thick, gw, gh, cell, wallPad(cell))
            bridged(wall, thick, gw, gh, s)
        }
        repeat(UNDERLAP) { grow(region, wall, gw, gh) }
        return trace(region, gw, gh).mapNotNull { ring ->
            for (i in ring.indices step 2) {
                ring[i] = min(ring[i] * cell, pageW)
                ring[i + 1] = min(ring[i + 1] * cell, pageH)
            }
            val s = simplifyRing(ring, cell * 0.7f)
            if (s.size >= 6 && abs(area(s)) >= cell * cell * 2f) s else null
        }
    }

    /** O tyle ściana jest szersza od kreski, żeby wąskie szpary między liniami (np. niedomknięty rysunek) nie przeciekały. */
    private fun wallPad(cell: Float) = max(cell, 1f)

    private fun rasterize(e: Element, wall: BooleanArray, gw: Int, gh: Int, cell: Float, pad: Float) {
        when (e) {
            is FillEl -> return
            is Stroke -> {
                val p = e.pts
                val n = e.count
                if (n == 0) return
                val half = e.width * 0.5f
                // Co najmniej 0,75 komórki: cieńsza kreska ukośna zostawiałaby dziury między komórkami, przez które przecieka wypełnienie.
                val minR = max(0.5f, cell * 0.75f)
                fun rad(i: Int) = max(half * p[3 * i + 2], minR) + pad
                if (n == 1) { segment(wall, gw, gh, cell, p[0], p[1], p[0], p[1], rad(0)); return }
                for (i in 0 until n - 1) {
                    segment(wall, gw, gh, cell, p[3 * i], p[3 * i + 1], p[3 * i + 3], p[3 * i + 4], max(rad(i), rad(i + 1)))
                }
            }
            is BoxElement -> {
                if (e is ImageEl && e.locked) return
                val b = e.bounds
                val x0 = max(0, floor(b.l / cell).toInt()); val x1 = min(gw - 1, floor(b.r / cell).toInt())
                val y0 = max(0, floor(b.t / cell).toInt()); val y1 = min(gh - 1, floor(b.b / cell).toInt())
                for (cy in y0..y1) for (cx in x0..x1) {
                    if (e.containsPoint((cx + 0.5f) * cell, (cy + 0.5f) * cell, cell * 0.5f)) wall[cy * gw + cx] = true
                }
            }
        }
    }

    /** Zaznacza komórki, których środek leży bliżej odcinka niż [r]. */
    private fun segment(wall: BooleanArray, gw: Int, gh: Int, cell: Float, ax: Float, ay: Float, bx: Float, by: Float, r: Float) {
        val x0 = max(0, floor((min(ax, bx) - r) / cell).toInt()); val x1 = min(gw - 1, floor((max(ax, bx) + r) / cell).toInt())
        val y0 = max(0, floor((min(ay, by) - r) / cell).toInt()); val y1 = min(gh - 1, floor((max(ay, by) + r) / cell).toInt())
        val r2 = r * r
        for (cy in y0..y1) for (cx in x0..x1) {
            val i = cy * gw + cx
            if (wall[i]) continue
            if (GeomMath.distSqPointSegment((cx + 0.5f) * cell, (cy + 0.5f) * cell, ax, ay, bx, by) <= r2) wall[i] = true
        }
    }

    /**
     * Obszar przy domkniętych szparach. [thin] to prawdziwe linie, [thick] pogrubione. Rdzeń to obszar wolny od [thick]
     * spójny z dotknięciem; komórki pasa zapasu (wolne w [thin], zajęte w [thick]) dostaje ten obszar, który jest do nich
     * najbliżej (BFS z wielu źródeł naraz), więc ostry kąt należy do obszaru, w którym leży, a szpara dzieli się po połowie.
     */
    private fun bridged(thin: BooleanArray, thick: BooleanArray, gw: Int, gh: Int, s0: Int): BooleanArray {
        val n = thin.size
        var s = s0
        if (thick[s]) {
            // Dotknięcie w samym zakamarku (w pasie zapasu): bierzemy najbliższy obszar osiągalny bez przekraczania linii.
            s = nearestFree(thin, thick, gw, gh, s)
            // Malutki zamknięty obszar, cały węższy od zapasu: wypełniamy go po prawdziwych liniach.
            if (s < 0) return flood(thin, gw, gh, s0)
        }
        val core = flood(thick, gw, gh, s)
        // 1 = nasz obszar, 2 = inny, 0 = jeszcze nieprzydzielona komórka pasa zapasu.
        val own = ByteArray(n)
        val queue = IntArray(n)
        var tail = 0
        for (i in 0 until n) if (!thick[i]) own[i] = if (core[i]) 1 else 2
        fun isBand(j: Int) = thick[j] && !thin[j]
        for (i in 0 until n) {
            if (own[i].toInt() == 0) continue
            val cx = i % gw; val cy = i / gw
            if ((cx > 0 && isBand(i - 1)) || (cx < gw - 1 && isBand(i + 1)) || (cy > 0 && isBand(i - gw)) || (cy < gh - 1 && isBand(i + gw))) queue[tail++] = i
        }
        var head = 0
        while (head < tail) {
            val i = queue[head++]
            val o = own[i]
            val cx = i % gw; val cy = i / gw
            fun visit(j: Int) { if (own[j].toInt() == 0 && isBand(j)) { own[j] = o; queue[tail++] = j } }
            if (cx > 0) visit(i - 1)
            if (cx < gw - 1) visit(i + 1)
            if (cy > 0) visit(i - gw)
            if (cy < gh - 1) visit(i + gw)
        }
        val region = BooleanArray(n)
        for (i in 0 until n) if (own[i].toInt() == 1) region[i] = true
        return region
    }

    /** Najbliższa (BFS po komórkach wolnych od prawdziwych linii) komórka wolna także od pogrubionych ścian; -1, gdy brak. */
    private fun nearestFree(thin: BooleanArray, thick: BooleanArray, gw: Int, gh: Int, s: Int): Int {
        val seen = BooleanArray(thin.size)
        val queue = IntArray(thin.size)
        var head = 0; var tail = 0
        queue[tail++] = s; seen[s] = true
        while (head < tail) {
            val i = queue[head++]
            if (!thick[i]) return i
            val cx = i % gw; val cy = i / gw
            fun visit(j: Int) { if (!seen[j] && !thin[j]) { seen[j] = true; queue[tail++] = j } }
            if (cx > 0) visit(i - 1)
            if (cx < gw - 1) visit(i + 1)
            if (cy > 0) visit(i - gw)
            if (cy < gh - 1) visit(i + gw)
        }
        return -1
    }

    /** Wypełnienie 4-spójne od komórki [s] po komórkach bez ścian. */
    private fun flood(wall: BooleanArray, gw: Int, gh: Int, s: Int): BooleanArray {
        val region = BooleanArray(gw * gh)
        var stack = IntArray(1024)
        var top = 0
        fun push(i: Int) {
            if (top == stack.size) stack = stack.copyOf(stack.size * 2)
            stack[top++] = i
        }
        region[s] = true
        push(s)
        while (top > 0) {
            val i = stack[--top]
            val cx = i % gw; val cy = i / gw
            if (cx > 0 && !wall[i - 1] && !region[i - 1]) { region[i - 1] = true; push(i - 1) }
            if (cx < gw - 1 && !wall[i + 1] && !region[i + 1]) { region[i + 1] = true; push(i + 1) }
            if (cy > 0 && !wall[i - gw] && !region[i - gw]) { region[i - gw] = true; push(i - gw) }
            if (cy < gh - 1 && !wall[i + gw] && !region[i + gw]) { region[i + gw] = true; push(i + gw) }
        }
        return region
    }

    /** Poszerza obszar o jedną komórkę, ale tylko w ściany (pod linie), nigdy w sąsiednie wolne obszary. */
    private fun grow(region: BooleanArray, wall: BooleanArray, gw: Int, gh: Int) {
        val add = ArrayList<Int>()
        for (i in region.indices) {
            if (!wall[i] || region[i]) continue
            val cx = i % gw; val cy = i / gw
            if ((cx > 0 && region[i - 1]) || (cx < gw - 1 && region[i + 1]) ||
                (cy > 0 && region[i - gw]) || (cy < gh - 1 && region[i + gw])) add.add(i)
        }
        for (i in add) region[i] = true
    }

    /**
     * Obrys obszaru po krawędziach komórek: każda krawędź między komórką obszaru a resztą to skierowany odcinek
     * (obszar po prawej stronie w układzie ekranu), a odcinki łączą się w zamknięte pierścienie (współrzędne w komórkach).
     */
    internal fun trace(region: BooleanArray, gw: Int, gh: Int): List<FloatArray> {
        val vw = gw + 1
        // Bity kierunków wychodzących z wierzchołka: 1 = +x, 2 = +y, 4 = -x, 8 = -y.
        val out = ByteArray(vw * (gh + 1))
        fun inR(cx: Int, cy: Int) = cx in 0 until gw && cy in 0 until gh && region[cy * gw + cx]
        fun mark(vx: Int, vy: Int, bit: Int) { val v = vy * vw + vx; out[v] = (out[v].toInt() or bit).toByte() }
        for (cy in 0 until gh) for (cx in 0 until gw) {
            if (!region[cy * gw + cx]) continue
            if (!inR(cx, cy - 1)) mark(cx, cy, 1)
            if (!inR(cx + 1, cy)) mark(cx + 1, cy, 2)
            if (!inR(cx, cy + 1)) mark(cx + 1, cy + 1, 4)
            if (!inR(cx - 1, cy)) mark(cx, cy + 1, 8)
        }
        val dx = intArrayOf(1, 0, -1, 0); val dy = intArrayOf(0, 1, 0, -1)
        val rings = ArrayList<FloatArray>()
        for (start in out.indices) while (out[start].toInt() != 0) {
            val pts = com.mdev.mnotes.core.geom.FloatList(64)
            var v = start
            var dir = -1
            while (true) {
                val m = out[v].toInt()
                if (m == 0) break
                // W „szczypcach” (dwa wyjścia) skręcamy w prawo, więc stykające się rogiem komórki dają osobne pierścienie.
                val nd = if (dir < 0) Integer.numberOfTrailingZeros(m)
                else intArrayOf((dir + 1) % 4, dir, (dir + 3) % 4).firstOrNull { m and (1 shl it) != 0 } ?: break
                out[v] = (m and (1 shl nd).inv()).toByte()
                if (nd != dir) pts.add((v % vw).toFloat(), (v / vw).toFloat())
                dir = nd
                v += dy[nd] * vw + dx[nd]
                if (v == start) break
            }
            if (pts.size >= 6) rings.add(pts.toArray())
        }
        return rings
    }

    /** Douglas–Peucker dla zamkniętego pierścienia (pary x, y), bez powtórzonego ostatniego punktu. */
    internal fun simplifyRing(ring: FloatArray, eps: Float): FloatArray {
        val n = ring.size / 2
        if (n < 4) return ring
        // Dzielimy pierścień w punkcie 0 i w najdalszym od niego, każdą połowę upraszczamy osobno.
        var far = 0; var best = -1f
        for (i in 1 until n) {
            val d = (ring[2 * i] - ring[0]).let { it * it } + (ring[2 * i + 1] - ring[1]).let { it * it }
            if (d > best) { best = d; far = i }
        }
        val keep = BooleanArray(n + 1)
        keep[0] = true; keep[far] = true; keep[n] = true
        fun x(i: Int) = ring[2 * (i % n)]
        fun y(i: Int) = ring[2 * (i % n) + 1]
        val stack = ArrayDeque<Long>()
        stack.addLast((0L shl 32) or far.toLong())
        stack.addLast((far.toLong() shl 32) or n.toLong())
        val e2 = eps * eps
        while (stack.isNotEmpty()) {
            val s = stack.removeLast()
            val a = (s ushr 32).toInt(); val b = (s and 0xffffffffL).toInt()
            if (b - a < 2) continue
            var idx = -1; var dmax = e2
            for (i in a + 1 until b) {
                val d = GeomMath.distSqPointSegment(x(i), y(i), x(a), y(a), x(b), y(b))
                if (d > dmax) { dmax = d; idx = i }
            }
            if (idx >= 0) {
                keep[idx] = true
                stack.addLast((a.toLong() shl 32) or idx.toLong())
                stack.addLast((idx.toLong() shl 32) or b.toLong())
            }
        }
        val res = com.mdev.mnotes.core.geom.FloatList(64)
        for (i in 0 until n) if (keep[i]) res.add(x(i), y(i))
        return res.toArray()
    }

    /** Pole ze znakiem (wzór sznurowadłowy). */
    fun area(ring: FloatArray): Float {
        val n = ring.size / 2
        var s = 0f
        var j = n - 1
        for (i in 0 until n) {
            s += ring[2 * j] * ring[2 * i + 1] - ring[2 * i] * ring[2 * j + 1]
            j = i
        }
        return s * 0.5f
    }
}

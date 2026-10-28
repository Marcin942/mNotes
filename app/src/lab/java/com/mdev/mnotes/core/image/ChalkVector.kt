package com.mdev.mnotes.core.image

import kotlin.math.hypot
import kotlin.math.max

/** Kreska z kredy jako łamana w pikselach obrazu (x0, y0, x1, y1, …) i jej grubość w pikselach. */
class ChalkLine(val pts: FloatArray, val width: Float) {
    val size get() = pts.size / 2
    fun length(): Float {
        var s = 0f
        for (i in 1 until size) s += hypot(pts[2 * i] - pts[2 * i - 2], pts[2 * i + 1] - pts[2 * i - 1])
        return s
    }
}

/**
 * Wektoryzacja kredy do edytowalnych kresek (K2): maska krycia → ścienianie Zhang-Suen do szkieletu 1 px →
 * łamane między końcami i rozgałęzieniami → porządki w węzłach → uproszczenie Douglasa-Peuckera.
 *
 * Porządki (faza 2, B2-6), bo szkielet kredy rwał się na drobne łamane: sąsiedztwo m (piksel po skosie liczy się tylko,
 * gdy nie ma drogi przez wspólnego sąsiada w pionie/poziomie), więc schodki szkieletu nie są rozgałęzieniami; krótkie
 * ostrogi (widełki na końcach grubej kreski) znikają; w skupisku węzłów ścieżki łączą się parami po kącie (prosta
 * przez skrzyżowanie zostaje jedną łamaną). Grubość liczona osobno dla każdej łamanej z odległości od brzegu maski.
 */
object ChalkVector {

    /** Największe odchylenie od prostej (stopnie), przy którym dwie ścieżki w węźle są jedną kreską. */
    const val JOIN_DEGREES = 40.0

    fun vectorize(img: Raster, alphaMin: Int = 128, epsilon: Float = 1.2f, minLength: Float = 6f): List<ChalkLine> =
        skeleton(img, alphaMin).map { ChalkLine(simplify(it.pts, epsilon), it.width) }.filter { it.length() >= minLength }

    /**
     * Łamane szkieletu po porządkach w węzłach i łączeniu przerw, piksel po pikselu (bez uproszczenia), z grubością
     * każdej. Wspólny początek łamanych [vectorize] i gładkich krzywych [ChalkCurves.paths] (2b-B4).
     */
    fun skeleton(img: Raster, alphaMin: Int = 128): List<ChalkLine> {
        val w = img.w; val h = img.h
        val mask = BooleanArray(w * h) { Raster.alpha(img.px[it]) >= alphaMin }
        if (mask.none { it }) return emptyList()
        fillHoles(mask, w, h)
        val skel = thin(mask, w, h)
        val dist = distance(mask, w, h)
        return bridge(join(trace(skel, w, h), w, dist), w, dist).map { ChalkLine(it, widthOf(it, w, dist)) }
    }

    /** Największa dziura w kredzie (px), którą zamykamy przed ścienianiem, i największa szczelina grubości 1–2 px. */
    const val HOLE_AREA = 12
    const val SLIT_AREA = 48

    /**
     * Zamyka w [mask] drobne dziury (2b-B4): kreda jest porowata, a ścienianie obchodzi każdą dziurę pętlą, więc z jednej
     * kreski robiły się kwadraciki na łamanej i kółeczka z ostrzami na krzywej. Dziura to składowa tła (sąsiedztwo 4)
     * niedotykająca brzegu obrazu; zamykamy ją, gdy ma ≤ [HOLE_AREA] px albo gdy każdy jej piksel styka się z kredą
     * (szczelina grubości 1–2 px, np. między dwiema nachodzącymi kreskami) i ma ≤ [SLIT_AREA] px. Wnętrza liter (o, e, φ)
     * są większe i zostają.
     */
    fun fillHoles(mask: BooleanArray, w: Int, h: Int) {
        val seen = BooleanArray(w * h)
        val queue = IntArray(w * h)
        for (start in mask.indices) {
            if (mask[start] || seen[start]) continue
            var head = 0; var tail = 0
            queue[tail++] = start; seen[start] = true
            var border = false; var thin = true
            while (head < tail) {
                val p = queue[head++]
                val x = p % w; val y = p / w
                if (x == 0 || y == 0 || x == w - 1 || y == h - 1) border = true
                if (thin && !touchesInk(mask, w, h, x, y)) thin = false
                if (x > 0 && !mask[p - 1] && !seen[p - 1]) { seen[p - 1] = true; queue[tail++] = p - 1 }
                if (x < w - 1 && !mask[p + 1] && !seen[p + 1]) { seen[p + 1] = true; queue[tail++] = p + 1 }
                if (y > 0 && !mask[p - w] && !seen[p - w]) { seen[p - w] = true; queue[tail++] = p - w }
                if (y < h - 1 && !mask[p + w] && !seen[p + w]) { seen[p + w] = true; queue[tail++] = p + w }
            }
            if (!border && (tail <= HOLE_AREA || (thin && tail <= SLIT_AREA))) for (k in 0 until tail) mask[queue[k]] = true
        }
    }

    private fun touchesInk(mask: BooleanArray, w: Int, h: Int, x: Int, y: Int): Boolean {
        for (dy in -1..1) for (dx in -1..1) {
            val nx = x + dx; val ny = y + dy
            if ((dx != 0 || dy != 0) && nx in 0 until w && ny in 0 until h && mask[ny * w + nx]) return true
        }
        return false
    }

    /**
     * Odległość każdego piksela maski od najbliższego tła (fazowana 3-4, w pikselach); poza obrazem jest tło. Kreska
     * grubości t ma na osi odległość ok. (t + 1) / 2, stąd grubość łamanej = 2·średnia − 1.
     */
    fun distance(mask: BooleanArray, w: Int, h: Int, edgeIsBackground: Boolean = true): FloatArray {
        val big = Int.MAX_VALUE / 4
        val d = IntArray(w * h) { if (mask[it]) big else 0 }
        val outside = if (edgeIsBackground) 0 else big
        fun at(x: Int, y: Int) = if (x < 0 || y < 0 || x >= w || y >= h) outside else d[y * w + x]
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            if (d[i] == 0) continue
            d[i] = minOf(d[i], at(x - 1, y) + 3, at(x, y - 1) + 3, at(x - 1, y - 1) + 4, at(x + 1, y - 1) + 4)
        }
        for (y in h - 1 downTo 0) for (x in w - 1 downTo 0) {
            val i = y * w + x
            if (d[i] == 0) continue
            d[i] = minOf(d[i], at(x + 1, y) + 3, at(x, y + 1) + 3, at(x + 1, y + 1) + 4, at(x - 1, y + 1) + 4)
        }
        return FloatArray(w * h) { d[it] / 3f }
    }

    /** Grubość łamanej [pts] (piksele szkieletu) z odległości od brzegu maski. */
    private fun widthOf(pts: FloatArray, w: Int, dist: FloatArray): Float {
        var s = 0f; var n = 0
        for (i in 0 until pts.size / 2) {
            val p = pts[2 * i + 1].toInt() * w + pts[2 * i].toInt()
            if (p in dist.indices) { s += dist[p]; n++ }
        }
        return if (n == 0) 1f else (2f * s / n - 1f).coerceIn(1f, 40f)
    }
    /** Ścienianie Zhang-Suen: zdejmuje brzegowe piksele w dwóch podprzejściach, aż nic się nie zmienia. */
    fun thin(src: BooleanArray, w: Int, h: Int): BooleanArray {
        val m = src.copyOf()
        val remove = ArrayList<Int>()
        fun px(x: Int, y: Int) = x in 0 until w && y in 0 until h && m[y * w + x]
        var changed = true
        while (changed) {
            changed = false
            for (pass in 0..1) {
                remove.clear()
                for (y in 0 until h) for (x in 0 until w) {
                    if (!m[y * w + x]) continue
                    val p2 = px(x, y - 1); val p3 = px(x + 1, y - 1); val p4 = px(x + 1, y); val p5 = px(x + 1, y + 1)
                    val p6 = px(x, y + 1); val p7 = px(x - 1, y + 1); val p8 = px(x - 1, y); val p9 = px(x - 1, y - 1)
                    val ring = booleanArrayOf(p2, p3, p4, p5, p6, p7, p8, p9)
                    val b = ring.count { it }
                    if (b < 2 || b > 6) continue
                    var a = 0
                    for (i in 0 until 8) if (!ring[i] && ring[(i + 1) % 8]) a++
                    if (a != 1) continue
                    if (pass == 0) { if (p2 && p4 && p6) continue; if (p4 && p6 && p8) continue }
                    else { if (p2 && p4 && p8) continue; if (p2 && p6 && p8) continue }
                    remove.add(y * w + x)
                }
                if (remove.isNotEmpty()) { changed = true; for (i in remove) m[i] = false }
            }
        }
        return m
    }

    private val DX = intArrayOf(1, 1, 0, -1, -1, -1, 0, 1)
    private val DY = intArrayOf(0, 1, 1, 1, 0, -1, -1, -1)

    /**
     * Sąsiedzi piksela [p] szkieletu w sąsiedztwie m: cztery w pionie i poziomie oraz skośne tylko wtedy, gdy oba
     * piksele pośrednie są puste. Bez tego schodek szkieletu (piksel z sąsiadem z boku i po skosie) miał stopień 3.
     */
    private fun mNeighbours(skel: BooleanArray, w: Int, h: Int, p: Int, out: IntArray): Int {
        fun on(x: Int, y: Int) = x in 0 until w && y in 0 until h && skel[y * w + x]
        val x = p % w; val y = p / w
        var n = 0
        for (k in 0 until 8) {
            val nx = x + DX[k]; val ny = y + DY[k]
            if (!on(nx, ny)) continue
            if (DX[k] != 0 && DY[k] != 0 && (on(nx, y) || on(x, ny))) continue
            out[n++] = ny * w + nx
        }
        return n
    }

    /** Łamane szkieletu: od każdego końca i rozgałęzienia wzdłuż pikseli o dwóch sąsiadach; na końcu zamknięte pętle. */
    fun trace(skel: BooleanArray, w: Int, h: Int): List<FloatArray> {
        val nb = IntArray(8)
        fun degree(p: Int) = mNeighbours(skel, w, h, p, nb)
        val seen = HashSet<Long>()
        fun edge(a: Int, b: Int): Long = if (a < b) a.toLong() * skel.size + b else b.toLong() * skel.size + a
        val out = ArrayList<FloatArray>()
        val nodes = skel.indices.filter { skel[it] && degree(it) != 2 }

        fun walk(start: Int, first: Int): FloatArray {
            val xs = ArrayList<Float>()
            xs.add((start % w).toFloat()); xs.add((start / w).toFloat())
            var prev = start; var cur = first
            seen.add(edge(prev, cur))
            val here = IntArray(8)
            while (true) {
                xs.add((cur % w).toFloat()); xs.add((cur / w).toFloat())
                val n = mNeighbours(skel, w, h, cur, here)
                if (n != 2) break
                var next = -1
                for (k in 0 until n) {
                    val q = here[k]
                    if (q != prev && !seen.contains(edge(cur, q))) { next = q; break }
                }
                if (next < 0) break
                seen.add(edge(cur, next))
                prev = cur; cur = next
                if (cur == start) { xs.add((cur % w).toFloat()); xs.add((cur / w).toFloat()); break }
            }
            return xs.toFloatArray()
        }

        val around = IntArray(8)
        for (node in nodes) {
            val n = mNeighbours(skel, w, h, node, around)
            val next = around.copyOf(n)
            for (q in next) if (!seen.contains(edge(node, q))) out.add(walk(node, q))
            if (n == 0) out.add(floatArrayOf((node % w).toFloat(), (node / w).toFloat(), (node % w).toFloat(), (node / w).toFloat()))
        }
        // pętle bez końców (np. litera O): każdy piksel ma dwóch sąsiadów
        for (p in skel.indices) {
            if (!skel[p]) continue
            val n = mNeighbours(skel, w, h, p, around)
            val next = around.copyOf(n)
            for (q in next) if (!seen.contains(edge(p, q))) out.add(walk(p, q))
        }
        return out
    }

    /**
     * Porządki na łamanych z [trace]: (1) ostroga, czyli krótka ścieżka od rozgałęzienia do wolnego końca (krótsza niż
     * grubość kreski w węźle), znika; (2) węzły połączone ścieżką krótszą niż ta grubość tworzą jedno skupisko (gruby
     * krzyż daje po ścienianiu dwa węzły i mostek); (3) w skupisku ścieżki łączą się parami, najpierw te, które idą
     * najbardziej prosto (odchylenie < [JOIN_DEGREES]); przy dwóch ścieżkach zawsze. [dist] z [distance].
     */
    fun join(paths: List<FloatArray>, w: Int, dist: FloatArray): List<FloatArray> {
        fun pix(p: FloatArray, end: Int): Int { val i = if (end == 0) 0 else p.size / 2 - 1; return p[2 * i + 1].toInt() * w + p[2 * i].toInt() }
        fun len(p: FloatArray): Float { var s = 0f; for (i in 1 until p.size / 2) s += hypot(p[2 * i] - p[2 * i - 2], p[2 * i + 1] - p[2 * i - 1]); return s }
        fun reach(px: Int) = max(3f, 2f * dist.getOrElse(px) { 1f })
        val closed = paths.map { it.size >= 4 && pix(it, 0) == pix(it, 1) }
        var live = paths.indices.filter { !closed[it] }.toMutableSet()
        fun ends(): Map<Int, Int> { val c = HashMap<Int, Int>(); for (i in live) for (e in 0..1) c.merge(pix(paths[i], e), 1, Int::plus); return c }

        // (1) ostrogi: wolny koniec po jednej stronie, rozgałęzienie po drugiej, krótsze niż grubość w węźle
        val count = ends()
        live = live.filterTo(HashSet()) { i ->
            val p = paths[i]
            val a = count.getValue(pix(p, 0)); val b = count.getValue(pix(p, 1))
            val spur = (a == 1 && b >= 3 && len(p) < reach(pix(p, 1))) || (b == 1 && a >= 3 && len(p) < reach(pix(p, 0)))
            !spur
        }
        // (2) skupiska węzłów: krótki mostek między dwoma rozgałęzieniami znika, a jego końce to jeden węzeł
        val parent = HashMap<Int, Int>()
        fun find(x: Int): Int { var r = x; while (parent[r] != null && parent[r] != r) r = parent.getValue(r); return r }
        val deg = ends()
        for (i in live.toList()) {
            val p = paths[i]
            val a = pix(p, 0); val b = pix(p, 1)
            if (deg.getValue(a) >= 3 && deg.getValue(b) >= 3 && len(p) < max(reach(a), reach(b))) {
                live.remove(i)
                parent[find(a)] = find(b)
            }
        }
        // (3) pary w każdym skupisku: kierunek ścieżki to wektor od końca do punktu ~6 px dalej
        val byNode = HashMap<Int, MutableList<Pair<Int, Int>>>()
        for (i in live) for (e in 0..1) byNode.getOrPut(find(pix(paths[i], e))) { ArrayList() }.add(i to e)
        fun dir(i: Int, e: Int): FloatArray {
            val p = paths[i]; val n = p.size / 2
            val k = minOf(6, n - 1)
            val a = if (e == 0) 0 else n - 1; val b = if (e == 0) k else n - 1 - k
            val dx = p[2 * b] - p[2 * a]; val dy = p[2 * b + 1] - p[2 * a + 1]
            val l = max(1e-3f, hypot(dx, dy))
            return floatArrayOf(dx / l, dy / l)
        }
        val link = HashMap<Pair<Int, Int>, Pair<Int, Int>>()
        for ((_, list) in byNode) {
            if (list.size < 2) continue
            if (list.size == 2 && list[0].first != list[1].first) { link[list[0]] = list[1]; link[list[1]] = list[0]; continue }
            // prosta przez węzeł: kierunki obu ścieżek od węzła są przeciwne (iloczyn skalarny bliski −1)
            val pairs = ArrayList<Triple<Double, Pair<Int, Int>, Pair<Int, Int>>>()
            for (x in list.indices) for (y in x + 1 until list.size) {
                if (list[x].first == list[y].first) continue
                val d1 = dir(list[x].first, list[x].second); val d2 = dir(list[y].first, list[y].second)
                val dev = Math.toDegrees(kotlin.math.acos((-(d1[0] * d2[0] + d1[1] * d2[1])).toDouble().coerceIn(-1.0, 1.0)))
                if (dev < JOIN_DEGREES) pairs += Triple(dev, list[x], list[y])
            }
            pairs.sortBy { it.first }
            for ((_, a, b) in pairs) if (a !in link && b !in link) { link[a] = b; link[b] = a }
        }
        // łańcuchy: od końca bez pary przez kolejne pary; to, co zostało, to pętle
        val used = HashSet<Int>()
        val out = ArrayList<FloatArray>()
        fun chain(first: Int, fromEnd: Int) {
            val xs = ArrayList<Float>()
            var i = first; var e = fromEnd
            while (i !in used) {
                used += i
                val p = paths[i]; val n = p.size / 2
                val order = if (e == 0) 0 until n else (n - 1 downTo 0)
                for (k in order) { xs += p[2 * k]; xs += p[2 * k + 1] }
                val nextEnd = link[i to 1 - e] ?: break
                i = nextEnd.first; e = nextEnd.second
            }
            out += xs.toFloatArray()
        }
        for (i in live.sorted()) for (e in 0..1) if (i !in used && (i to e) !in link) chain(i, e)
        for (i in live.sorted()) if (i !in used) chain(i, 0)
        for (i in paths.indices) if (closed[i]) out += paths[i]
        return out
    }

    /**
     * Łączy przerwane kreski (2b-B2): kreda na tablicy ma przerwy, więc jedna linia rozpadała się na kawałki. Koniec
     * jednej łamanej i koniec drugiej łączą się, gdy są bliżej niż dwie grubości kreski (najwyżej [BRIDGE_MAX] px) i oba
     * końce patrzą na siebie (odchylenie każdego od odcinka łączącego < [BRIDGE_DEGREES]°). Kreski obok siebie albo pod
     * kątem prostym się nie łączą; najkrótsze przerwy najpierw. Łamane zamknięte zostają bez zmian.
     */
    fun bridge(paths: List<FloatArray>, w: Int, dist: FloatArray): List<FloatArray> {
        val open = paths.indices.filter { paths[it].size >= 4 && !(paths[it][0] == paths[it][paths[it].size - 2] && paths[it][1] == paths[it][paths[it].size - 1]) }
        if (open.size < 2) return paths
        fun end(i: Int, e: Int): Int = if (e == 0) 0 else paths[i].size / 2 - 1
        fun px(i: Int, e: Int) = paths[i][2 * end(i, e)]
        fun py(i: Int, e: Int) = paths[i][2 * end(i, e) + 1]
        // kierunek „na zewnątrz” końca: od punktu ~6 px w głąb łamanej do końca
        fun out(i: Int, e: Int): FloatArray {
            val n = paths[i].size / 2; val k = minOf(6, n - 1)
            val inner = if (e == 0) k else n - 1 - k
            val dx = px(i, e) - paths[i][2 * inner]; val dy = py(i, e) - paths[i][2 * inner + 1]
            val l = max(1e-3f, hypot(dx, dy))
            return floatArrayOf(dx / l, dy / l)
        }
        fun width(i: Int, e: Int) = 2f * dist.getOrElse(py(i, e).toInt() * w + px(i, e).toInt()) { 1f } - 1f
        val cosMax = kotlin.math.cos(Math.toRadians(BRIDGE_DEGREES)).toFloat()
        val cands = ArrayList<Triple<Float, Pair<Int, Int>, Pair<Int, Int>>>()
        for (a in open.indices) for (b in a + 1 until open.size) for (e in 0..1) for (f in 0..1) {
            val i = open[a]; val j = open[b]
            val gx = px(j, f) - px(i, e); val gy = py(j, f) - py(i, e)
            val gap = hypot(gx, gy)
            val limit = minOf(BRIDGE_MAX, max(3f, 2f * max(width(i, e), width(j, f))))
            if (gap > limit) continue
            if (gap > 1.5f) {
                val oi = out(i, e); val oj = out(j, f)
                if ((oi[0] * gx + oi[1] * gy) / gap < cosMax || -(oj[0] * gx + oj[1] * gy) / gap < cosMax) continue
            }
            cands += Triple(gap, i to e, j to f)
        }
        if (cands.isEmpty()) return paths
        cands.sortBy { it.first }
        val link = HashMap<Pair<Int, Int>, Pair<Int, Int>>()
        for ((_, a, b) in cands) if (a !in link && b !in link) {
            // bez pętli: nie łączymy, jeśli a i b są już w jednym łańcuchu
            var cur = b.first; var from = b.second; var loop = false
            while (true) { val nx = link[cur to 1 - from] ?: break; if (nx.first == a.first) { loop = true; break }; cur = nx.first; from = nx.second }
            if (loop || a.first == b.first) continue
            link[a] = b; link[b] = a
        }
        val used = HashSet<Int>()
        val result = ArrayList<FloatArray>()
        fun chain(first: Int, fromEnd: Int) {
            val xs = ArrayList<Float>()
            var i = first; var e = fromEnd
            while (i !in used) {
                used += i
                val p = paths[i]; val n = p.size / 2
                for (k in if (e == 0) 0 until n else (n - 1 downTo 0)) { xs += p[2 * k]; xs += p[2 * k + 1] }
                val next = link[i to 1 - e] ?: break
                i = next.first; e = next.second
            }
            result += xs.toFloatArray()
        }
        for (i in open) for (e in 0..1) if (i !in used && (i to e) !in link) chain(i, e)
        for (i in paths.indices) if (i !in used) result += paths[i]
        return result
    }

    /** Najdłuższa przerwa w kresce, którą łączymy (px wyniku), i największe odchylenie końców od siebie. */
    const val BRIDGE_MAX = 12f
    const val BRIDGE_DEGREES = 35.0

    /** Douglas-Peucker: zostawia punkty odległe od cięciwy o więcej niż [eps] pikseli. */
    fun simplify(pts: FloatArray, eps: Float): FloatArray {
        val n = pts.size / 2
        if (n <= 2) return pts
        val keep = BooleanArray(n)
        keep[0] = true; keep[n - 1] = true
        val stack = ArrayDeque<IntArray>()
        stack.addLast(intArrayOf(0, n - 1))
        while (stack.isNotEmpty()) {
            val (a, b) = stack.removeLast()
            var best = -1; var dist = eps
            val ax = pts[2 * a]; val ay = pts[2 * a + 1]; val bx = pts[2 * b]; val by = pts[2 * b + 1]
            val len = hypot(bx - ax, by - ay)
            for (i in a + 1 until b) {
                val px = pts[2 * i]; val py = pts[2 * i + 1]
                val d = if (len < 1e-6f) hypot(px - ax, py - ay) else kotlin.math.abs((bx - ax) * (ay - py) - (ax - px) * (by - ay)) / len
                if (d > dist) { dist = d; best = i }
            }
            if (best >= 0) { keep[best] = true; stack.addLast(intArrayOf(a, best)); stack.addLast(intArrayOf(best, b)) }
        }
        val out = ArrayList<Float>()
        for (i in 0 until n) if (keep[i]) { out.add(pts[2 * i]); out.add(pts[2 * i + 1]) }
        return out.toFloatArray()
    }
}

/**
 * Kilka zdjęć tej samej tablicy (K3): po wyprostowaniu do wspólnego prostokąta mediana każdego kanału usuwa to, co
 * zasłaniało tablicę tylko na części zdjęć (wykładowca, ręka). Różnica dwóch tablic pokazuje, co dopisano.
 */
object BoardStack {

    /** Mediana pikseli; obrazy muszą mieć ten sam rozmiar. */
    fun median(images: List<Raster>): Raster {
        require(images.isNotEmpty()) { "brak zdjęć" }
        val w = images[0].w; val h = images[0].h
        require(images.all { it.w == w && it.h == h }) { "zdjęcia muszą mieć ten sam rozmiar" }
        val n = images.size
        val buf = IntArray(n)
        val out = IntArray(w * h)
        fun med(shift: Int, i: Int): Int {
            for (k in 0 until n) buf[k] = (images[k].px[i] shr shift) and 255
            buf.sort()
            return if (n % 2 == 1) buf[n / 2] else (buf[n / 2 - 1] + buf[n / 2] + 1) / 2
        }
        for (i in 0 until w * h) out[i] = Raster.argb(med(24, i), med(16, i), med(8, i), med(0, i))
        return Raster(w, h, out)
    }

    /** Prostuje każde zdjęcie jego czworokątem do rozmiaru pierwszego i liczy medianę. */
    fun alignedMedian(photos: List<Raster>, quads: List<Quad>): Raster {
        require(photos.size == quads.size && photos.isNotEmpty())
        val size = quads[0].rectSize()
        return median(photos.indices.map { BoardGeometry.warp(photos[it], quads[it], size) })
    }

    /** Co przybyło na [after] względem [before] (krycie kredy): piksele, w których krycie wzrosło o więcej niż [delta]. */
    fun added(before: Raster, after: Raster, delta: Int = 64): Raster {
        require(before.w == after.w && before.h == after.h)
        return Raster(after.w, after.h, IntArray(after.px.size) { i ->
            if (Raster.alpha(after.px[i]) - Raster.alpha(before.px[i]) > delta) after.px[i] else 0
        })
    }
}

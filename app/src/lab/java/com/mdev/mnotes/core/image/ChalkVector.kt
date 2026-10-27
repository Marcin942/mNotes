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
 * łamane między końcami i rozgałęzieniami → uproszczenie Douglasa-Peuckera. Grubość kreski = pole maski / długość
 * szkieletu.
 */
object ChalkVector {

    fun vectorize(img: Raster, alphaMin: Int = 128, epsilon: Float = 1.2f, minLength: Float = 6f): List<ChalkLine> {
        val w = img.w; val h = img.h
        val mask = BooleanArray(w * h) { Raster.alpha(img.px[it]) >= alphaMin }
        val area = mask.count { it }
        if (area == 0) return emptyList()
        val skel = thin(mask, w, h)
        val paths = trace(skel, w, h)
        val skelLen = max(1, skel.count { it })
        val width = (area.toFloat() / skelLen).coerceIn(1f, 40f)
        return paths.map { ChalkLine(simplify(it, epsilon), width) }.filter { it.length() >= minLength }
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

    /** Łamane szkieletu: od każdego końca i rozgałęzienia wzdłuż pikseli o dwóch sąsiadach; na końcu zamknięte pętle. */
    fun trace(skel: BooleanArray, w: Int, h: Int): List<FloatArray> {
        fun on(x: Int, y: Int) = x in 0 until w && y in 0 until h && skel[y * w + x]
        fun degree(p: Int): Int { val x = p % w; val y = p / w; var d = 0; for (k in 0 until 8) if (on(x + DX[k], y + DY[k])) d++; return d }
        val seen = HashSet<Long>()
        fun edge(a: Int, b: Int): Long = if (a < b) a.toLong() * skel.size + b else b.toLong() * skel.size + a
        val out = ArrayList<FloatArray>()
        val nodes = skel.indices.filter { skel[it] && degree(it) != 2 }

        fun walk(start: Int, first: Int): FloatArray {
            val xs = ArrayList<Float>()
            xs.add((start % w).toFloat()); xs.add((start / w).toFloat())
            var prev = start; var cur = first
            seen.add(edge(prev, cur))
            while (true) {
                xs.add((cur % w).toFloat()); xs.add((cur / w).toFloat())
                if (degree(cur) != 2) break
                val cx = cur % w; val cy = cur / w
                var next = -1
                for (k in 0 until 8) {
                    val nx = cx + DX[k]; val ny = cy + DY[k]
                    if (!on(nx, ny)) continue
                    val q = ny * w + nx
                    if (q != prev && !seen.contains(edge(cur, q))) { next = q; break }
                }
                if (next < 0) break
                seen.add(edge(cur, next))
                prev = cur; cur = next
                if (cur == start) { xs.add((cur % w).toFloat()); xs.add((cur / w).toFloat()); break }
            }
            return xs.toFloatArray()
        }

        for (n in nodes) {
            val x = n % w; val y = n / w
            for (k in 0 until 8) {
                val nx = x + DX[k]; val ny = y + DY[k]
                if (!on(nx, ny)) continue
                val q = ny * w + nx
                if (!seen.contains(edge(n, q))) out.add(walk(n, q))
            }
            if (degree(n) == 0) out.add(floatArrayOf(x.toFloat(), y.toFloat(), x.toFloat(), y.toFloat()))
        }
        // pętle bez końców (np. litera O): każdy piksel ma dwóch sąsiadów
        for (p in skel.indices) {
            if (!skel[p]) continue
            val x = p % w; val y = p / w
            for (k in 0 until 8) {
                val nx = x + DX[k]; val ny = y + DY[k]
                if (!on(nx, ny)) continue
                val q = ny * w + nx
                if (!seen.contains(edge(p, q))) out.add(walk(p, q))
            }
        }
        return out
    }

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

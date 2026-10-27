package com.mdev.mnotes.core.geom

import kotlin.math.floor

/**
 * Siatka kubełków nad prostokątami (np. elementami strony): zapytanie o obszar odwiedza tylko te, które mogą go
 * dotykać, zamiast całej listy. Na gęstej stronie (tysiące kresek) gumka i lasso sprawdzają kilka, nie wszystkie.
 * Prostokąty obejmujące bardzo wiele kubełków (długi zakreślacz, wielkie zdjęcie) leżą na osobnej liście.
 */
class BoundsGrid(private val bounds: List<Bounds>, private val cell: Float = 48f) {
    private val buckets = HashMap<Long, IntArray>()
    private val sizes = HashMap<Long, Int>()
    private var big = IntArray(8)
    private var bigN = 0
    private val stamp = IntArray(bounds.size)
    private var gen = 0

    init {
        for (i in bounds.indices) {
            val b = bounds[i]
            if (b.isEmpty) continue
            val x0 = cellOf(b.l); val x1 = cellOf(b.r); val y0 = cellOf(b.t); val y1 = cellOf(b.b)
            if ((x1 - x0 + 1).toLong() * (y1 - y0 + 1) > MAX_CELLS) { addBig(i); continue }
            for (cy in y0..y1) for (cx in x0..x1) add(key(cx, cy), i)
        }
    }

    private fun cellOf(v: Float): Int = floor(v / cell).toInt()
    private fun key(cx: Int, cy: Int): Long = (cx.toLong() shl 32) xor (cy.toLong() and 0xFFFFFFFFL)

    private fun add(k: Long, i: Int) {
        val n = sizes[k] ?: 0
        var arr = buckets[k]
        if (arr == null) { arr = IntArray(4); buckets[k] = arr } else if (n == arr.size) { arr = arr.copyOf(n * 2); buckets[k] = arr }
        arr[n] = i
        sizes[k] = n + 1
    }

    private fun addBig(i: Int) {
        if (bigN == big.size) big = big.copyOf(bigN * 2)
        big[bigN++] = i
    }

    /** Odwiedza (każdy raz) indeksy prostokątów przecinających [q]. Kolejność nieokreślona. */
    fun query(q: Bounds, visit: (Int) -> Unit) {
        if (q.isEmpty) return
        gen++
        for (k in 0 until bigN) { val i = big[k]; stamp[i] = gen; if (bounds[i].intersects(q)) visit(i) }
        val x0 = cellOf(q.l); val x1 = cellOf(q.r); val y0 = cellOf(q.t); val y1 = cellOf(q.b)
        if ((x1 - x0 + 1).toLong() * (y1 - y0 + 1) > MAX_QUERY_CELLS) {
            // Zapytanie wielkości strony: taniej przejrzeć wszystko po kolei.
            for (i in bounds.indices) if (!bounds[i].isEmpty && stamp[i] != gen && bounds[i].intersects(q)) { stamp[i] = gen; visit(i) }
            return
        }
        for (cy in y0..y1) for (cx in x0..x1) {
            val k = key(cx, cy)
            val arr = buckets[k] ?: continue
            val n = sizes[k] ?: 0
            for (j in 0 until n) {
                val i = arr[j]
                if (stamp[i] == gen) continue
                stamp[i] = gen
                if (bounds[i].intersects(q)) visit(i)
            }
        }
    }

    private companion object {
        const val MAX_CELLS = 64L
        const val MAX_QUERY_CELLS = 400L
    }
}

package com.mdev.mnotes.data

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Układ gwiazdek na okładkach (3c-A14): [CoverStyle.STARS] drobne rozsiane, [CoverStyle.STARS_BIG] większe
 * pięcioramienne, [CoverStyle.CONSTELLATIONS] gwiazdozbiory (Wielki Wóz i Kasjopeja) na tle drobnych kropek.
 * Logika czysta i deterministyczna (stałe ziarno), więc każda okładka wygląda zawsze tak samo; rysuje ją `CoverArt`.
 *
 * Współrzędne: x jako ułamek szerokości, y jako ułamek wysokości okładki, promień [Star.r] jako ułamek szerokości.
 * Pas grzbietu (x < [SPINE]) zostaje pusty, żeby gwiazdki nie wchodziły w zawias.
 */
object CoverStars {
    enum class Shape { DOT, SPARK, STAR }

    /** [a] to jasność względna 0..1 (mnożona przez krycie wzoru). */
    class Star(val x: Float, val y: Float, val r: Float, val shape: Shape, val rot: Float, val a: Float)

    class Layout(val stars: List<Star>, val lines: List<FloatArray>)

    const val SPINE = 0.13f

    fun layout(style: Int): Layout = synchronized(cache) { cache.getOrPut(style) { build(style) } }

    private val cache = HashMap<Int, Layout>()

    private fun build(style: Int): Layout = when (style) {
        CoverStyle.STARS -> Layout(scatter(Rng(11), 7, 10, 1f) { rng ->
            if (rng.next() < 0.18f) Shape.SPARK to rng.range(0.022f, 0.034f) else Shape.DOT to rng.range(0.005f, 0.012f)
        }, emptyList())
        CoverStyle.STARS_BIG -> {
            val big = scatter(Rng(23), 4, 5, 0.8f) { rng -> Shape.STAR to rng.range(0.03f, 0.065f) }
            val dust = scatter(Rng(29), 6, 8, 0.55f) { rng -> Shape.DOT to rng.range(0.004f, 0.008f) }
                .filter { d -> big.none { b -> near(b, d, b.r * 1.6f) } }
            Layout(big + dust, emptyList())
        }
        CoverStyle.CONSTELLATIONS -> constellations()
        else -> Layout(emptyList(), emptyList())
    }

    /** Jedna gwiazdka w każdej komórce siatki [cols]×[rows] (z prawdopodobieństwem [fill]) — rozsiane, bez zbitek. */
    private fun scatter(rng: Rng, cols: Int, rows: Int, fill: Float, pick: (Rng) -> Pair<Shape, Float>): List<Star> {
        val out = ArrayList<Star>()
        val cw = (1f - SPINE) / cols; val ch = 1f / rows
        for (j in 0 until rows) for (i in 0 until cols) {
            val keep = rng.next() < fill
            val (shape, r) = pick(rng)
            val x = SPINE + (i + rng.range(0.18f, 0.82f)) * cw
            val y = (j + rng.range(0.15f, 0.85f)) * ch
            val rot = rng.range(0f, (2 * PI / 5).toFloat())
            val a = rng.range(0.55f, 1f)
            if (keep) out += Star(x, y, r, shape, rot, a)
        }
        return out
    }

    private fun near(a: Star, b: Star, d: Float): Boolean {
        val dx = a.x - b.x; val dy = (a.y - b.y) * 1.35f
        return dx * dx + dy * dy < d * d
    }

    /** Wielki Wóz u dołu, Kasjopeja nad etykietą tytułu; kształty z mapy nieba, uproszczone. */
    private fun constellations(): Layout {
        val dipper = listOf(0.20f to 0.70f, 0.32f to 0.68f, 0.43f to 0.72f, 0.53f to 0.77f, 0.56f to 0.88f, 0.76f to 0.90f, 0.78f to 0.79f)
        val dipperEdges = listOf(0 to 1, 1 to 2, 2 to 3, 3 to 4, 4 to 5, 5 to 6, 6 to 3)
        val cassiopeia = listOf(0.46f to 0.08f, 0.56f to 0.15f, 0.66f to 0.10f, 0.76f to 0.16f, 0.86f to 0.07f)
        val casEdges = listOf(0 to 1, 1 to 2, 2 to 3, 3 to 4)
        val rng = Rng(37)
        val stars = ArrayList<Star>()
        val lines = ArrayList<FloatArray>()
        for ((pts, edges) in listOf(dipper to dipperEdges, cassiopeia to casEdges)) {
            for ((x, y) in pts) stars += Star(x, y, rng.range(0.026f, 0.036f), Shape.SPARK, 0f, rng.range(0.85f, 1f))
            for ((p, q) in edges) lines += floatArrayOf(pts[p].first, pts[p].second, pts[q].first, pts[q].second)
        }
        val figures = stars.toList()
        stars += scatter(Rng(41), 8, 11, 0.75f) { r -> Shape.DOT to r.range(0.003f, 0.007f) }
            .filter { d -> figures.none { f -> near(f, d, 0.05f) } }
            .map { Star(it.x, it.y, it.r, it.shape, 0f, it.a * 0.7f) }
        return Layout(stars, lines)
    }

    /** Wierzchołki iskierki (4 ramiona) albo gwiazdy (5 ramion) o środku ([cx], [cy]) i promieniu [r], w pikselach. */
    fun outline(shape: Shape, cx: Float, cy: Float, r: Float, rot: Float): FloatArray {
        val (arms, inner) = when (shape) { Shape.SPARK -> 4 to 0.28f; else -> 5 to 0.45f }
        val out = FloatArray(arms * 4)
        for (k in 0 until arms * 2) {
            val ang = rot - PI.toFloat() / 2 + k * PI.toFloat() / arms
            val rr = if (k % 2 == 0) r else r * inner
            out[2 * k] = cx + cos(ang) * rr; out[2 * k + 1] = cy + sin(ang) * rr
        }
        return out
    }

    /** Mały generator liniowy (stałe ziarno — ten sam układ na każdym urządzeniu i w każdej wersji). */
    private class Rng(seed: Int) {
        private var s = seed.toLong() * 2654435761L + 1
        fun next(): Float { s = (s * 6364136223846793005L + 1442695040888963407L); return ((s ushr 40) and 0xFFFFFF).toFloat() / 0x1000000 }
        fun range(a: Float, b: Float) = a + (b - a) * next()
    }
}

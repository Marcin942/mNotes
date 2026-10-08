package com.mdev.mnotes.core

import com.mdev.mnotes.core.pen.StrokeBuilder
import com.mdev.mnotes.core.model.PenKind
import com.mdev.mnotes.core.shapes.ShapeKind
import com.mdev.mnotes.core.shapes.ShapeRecognizer
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * Rozpoznawanie na wejściu przypominającym prawdziwe pismo na śliskim ekranie: drżenie, wygięte boki,
 * zaokrąglone rogi, krzywe kąty, nadlot/niedolot przy domykaniu i dryf rysika w trakcie przytrzymania.
 * Czysto syntetyczne dane (PenAndShapesTest) tego nie sprawdzają.
 */
class HandDrawnShapesTest {

    /** Próbki ręcznie rysowanej łamanej przez wierzchołki [v]: wygięcie boków, drżenie, zaokrąglenie rogów, dryf na końcu. */
    private fun hand(rnd: Random, v: List<Pair<Float, Float>>, closeLoop: Boolean, drift: Float = 5f, bend: Float = 0.04f, jitter: Float = 0.7f): FloatArray {
        val pts = ArrayList<Pair<Float, Float>>()
        val corners = if (closeLoop) v + v[0] else v
        for (i in 0 until corners.size - 1) {
            val (x0, y0) = corners[i]; val (x1, y1) = corners[i + 1]
            val len = hypot(x1 - x0, y1 - y0)
            val n = (len / 2.5f).toInt().coerceAtLeast(6)
            val nx = -(y1 - y0) / len; val ny = (x1 - x0) / len
            val b = (rnd.nextFloat() * 2f - 1f) * bend * len
            for (k in 0 until n) {
                val t = k / n.toFloat()
                val arc = 4f * t * (1f - t) * b
                pts.add((x0 + (x1 - x0) * t + nx * arc + (rnd.nextFloat() - 0.5f) * 2 * jitter) to
                    (y0 + (y1 - y0) * t + ny * arc + (rnd.nextFloat() - 0.5f) * 2 * jitter))
            }
        }
        pts.add(corners.last())
        // Zaokrąglenie rogów: krótka średnia krocząca (ręka nie zatrzymuje się idealnie w rogu).
        val sm = ArrayList<Pair<Float, Float>>()
        for (i in pts.indices) {
            var sx = 0f; var sy = 0f; var c = 0
            for (j in (i - 2).coerceAtLeast(0)..(i + 2).coerceAtMost(pts.size - 1)) { sx += pts[j].first; sy += pts[j].second; c++ }
            sm.add(sx / c to sy / c)
        }
        // Dryf: rysik stoi w miejscu i powoli ucieka.
        val (lx, ly) = sm.last()
        for (k in 1..30) sm.add((lx + drift * k / 30f + rnd.nextFloat() - 0.5f) to (ly - drift * 0.6f * k / 30f + rnd.nextFloat() - 0.5f))
        val out = FloatArray(2 * sm.size)
        for (i in sm.indices) { out[2 * i] = sm[i].first; out[2 * i + 1] = sm[i].second }
        return out
    }

    /** Wierzchołki zaburzone o [wob] (krzywe kąty, nierówne boki), obrócone o [rot] i przeskalowane. */
    private fun wob(rnd: Random, base: List<Pair<Float, Float>>, wob: Float, rot: Float, scale: Float): List<Pair<Float, Float>> {
        val cx = base.sumOf { it.first.toDouble() }.toFloat() / base.size
        val cy = base.sumOf { it.second.toDouble() }.toFloat() / base.size
        val c = cos(rot); val s = sin(rot)
        return base.map { (x, y) ->
            val dx = (x - cx) * scale + (rnd.nextFloat() - 0.5f) * 2 * wob
            val dy = (y - cy) * scale + (rnd.nextFloat() - 0.5f) * 2 * wob
            (300f + dx * c - dy * s) to (300f + dx * s + dy * c)
        }
    }

    private fun rate(name: String, trials: Int = 60, accept: (FloatArray) -> Boolean, gen: (Random) -> FloatArray): Float {
        var ok = 0
        for (seed in 0 until trials) {
            val xy = gen(Random(seed * 7919 + name.hashCode()))
            if (accept(xy)) ok++ else println("MISS $name seed=$seed -> ${ShapeRecognizer.recognize(xy, xy.size / 2)?.kind}")
        }
        val r = ok / trials.toFloat()
        println("RATE $name = $r")
        return r
    }

    private fun isPoly(xy: FloatArray, vararg kinds: ShapeKind): Boolean {
        val s = ShapeRecognizer.recognize(xy, xy.size / 2) ?: return false
        return s.kind in kinds
    }

    private val square = listOf(-50f to -50f, 50f to -50f, 50f to 50f, -50f to 50f)
    private val rect = listOf(-80f to -45f, 80f to -45f, 80f to 45f, -80f to 45f)
    private val triangle = listOf(0f to -60f, 60f to 45f, -60f to 45f)
    private val tri2 = listOf(-70f to 40f, 70f to 40f, -10f to -55f)
    // Znak pierwiastka: krótki ząbek w dół, długi skos w górę, poziom (kreska nadkreślenia).
    private val root = listOf(-60f to 5f, -45f to 25f, -20f to -35f, 80f to -35f)
    private val rootLong = listOf(-70f to 0f, -55f to 20f, -25f to -40f, 90f to -40f)
    private val zed = listOf(-60f to -40f, 60f to -40f, -60f to 40f, 60f to 40f)
    private val ell = listOf(-60f to -50f, -60f to 50f, 60f to 50f)
    private val vee = listOf(-60f to -40f, 0f to 40f, 60f to -40f)

    @Test fun handDrawnSquares() {
        val r = rate("square", accept = { isPoly(it, ShapeKind.RECT) }) { rnd ->
            hand(rnd, wob(rnd, square, 5f, rnd.nextFloat() * 0.2f - 0.1f, 0.6f + rnd.nextFloat() * 1.2f), true)
        }
        assertTrue("kwadraty: $r", r >= 0.9f)
    }

    @Test fun handDrawnRectangles() {
        val r = rate("rect", accept = { isPoly(it, ShapeKind.RECT, ShapeKind.POLYLINE) }) { rnd ->
            hand(rnd, wob(rnd, rect, 5f, rnd.nextFloat() * 0.3f - 0.15f, 0.6f + rnd.nextFloat()), true)
        }
        assertTrue("prostokąty: $r", r >= 0.9f)
    }

    @Test fun handDrawnTriangles() {
        val r = rate("triangle", accept = { isPoly(it, ShapeKind.TRIANGLE) }) { rnd ->
            val base = if (rnd.nextBoolean()) triangle else tri2
            hand(rnd, wob(rnd, base, 6f, rnd.nextFloat() * 6f, 0.6f + rnd.nextFloat() * 1.2f), true)
        }
        assertTrue("trójkąty: $r", r >= 0.9f)
    }

    @Test fun handDrawnRoots() {
        val r = rate("root", accept = { isPoly(it, ShapeKind.POLYLINE) }) { rnd ->
            val base = if (rnd.nextBoolean()) root else rootLong
            hand(rnd, wob(rnd, base, 4f, rnd.nextFloat() * 0.16f - 0.08f, 0.6f + rnd.nextFloat()), false)
        }
        assertTrue("pierwiastki: $r", r >= 0.9f)
    }

    @Test fun handDrawnZLVPolylines() {
        for ((name, base) in listOf("Z" to zed, "L" to ell, "V" to vee)) {
            val r = rate(name, accept = { isPoly(it, ShapeKind.POLYLINE, ShapeKind.ARROW) }) { rnd ->
                hand(rnd, wob(rnd, base, 4f, rnd.nextFloat() * 0.2f - 0.1f, 0.6f + rnd.nextFloat()), false)
            }
            assertTrue("$name: $r", r >= 0.9f)
        }
    }

    @Test fun handDrawnCirclesAreNotPolygons() {
        val r = rate("circle", accept = { isPoly(it, ShapeKind.ELLIPSE) }) { rnd ->
            val n = 90
            val rad = 40f + rnd.nextFloat() * 50f
            val sq = 0.7f + rnd.nextFloat() * 0.5f
            val pts = ArrayList<Pair<Float, Float>>()
            val start = rnd.nextFloat() * 6.28f
            val over = 1.05f + rnd.nextFloat() * 0.08f
            for (i in 0 until n) {
                val a = start + 2f * PI.toFloat() * over * i / n
                val rr = rad * (1f + 0.04f * sin(2.1f * a + 1f))
                pts.add((300f + rr * cos(a) + (rnd.nextFloat() - 0.5f)) to (300f + rr * sq * sin(a) + (rnd.nextFloat() - 0.5f)))
            }
            val out = FloatArray(2 * pts.size)
            for (i in pts.indices) { out[2 * i] = pts[i].first; out[2 * i + 1] = pts[i].second }
            out
        }
        assertTrue("koła/elipsy: $r", r >= 0.85f)
    }

    @Test fun handDrawnArcsStaySmooth() {
        val r = rate("arc", accept = { ShapeRecognizer.recognize(it, it.size / 2) == null }) { rnd ->
            val n = 70
            val rad = 60f + rnd.nextFloat() * 40f
            val sweep = (0.6f + rnd.nextFloat() * 0.9f) * PI.toFloat()
            val pts = FloatArray(2 * n)
            for (i in 0 until n) {
                val a = sweep * i / n
                pts[2 * i] = 300f + rad * cos(a) + (rnd.nextFloat() - 0.5f)
                pts[2 * i + 1] = 300f + rad * sin(a) + (rnd.nextFloat() - 0.5f)
            }
            pts
        }
        assertTrue("łuki: $r", r >= 0.85f)
    }

    /** Wejście z prawdziwego StrokeBuilder (wygładzony filtr) — rozpoznawanie dostaje próbki surowe, nie zaokrąglone. */
    @Test fun builderRawPointsKeepCornersSharp() {
        val rnd = Random(3)
        val xy = hand(rnd, wob(rnd, square, 4f, 0.05f, 1.2f), true)
        val b = StrokeBuilder()
        b.begin(PenKind.BALLPOINT, 0xFF000000.toInt(), 1.4f, 0.9f)
        var t = 0L
        for (i in 0 until xy.size / 2) { b.add(xy[2 * i], xy[2 * i + 1], 0.5f, t); t += 4 }
        val s = ShapeRecognizer.recognize(b.raw, b.rawCount)
        assertTrue("kind=${s?.kind}", s?.kind == ShapeKind.RECT)
    }
}

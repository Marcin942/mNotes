package com.mdev.mnotes.core.image

import com.mdev.mnotes.core.model.ImageCrop
import com.mdev.mnotes.core.model.ImageEl
import com.mdev.mnotes.core.model.Stroke
import com.mdev.mnotes.core.shapes.Bezier
import com.mdev.mnotes.core.shapes.CurveFit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** 2b-B4: kreda → gładkie krzywe Béziera; kształty syntetyczne z chropowatym brzegiem jak kreda. */
class ChalkCurvesTest {

    /** Kreda wzdłuż łamanej [line] (gęste punkty x, y …): piksel, gdy jest bliżej niż [half] ± losowy brzeg. */
    private fun chalk(w: Int, h: Int, line: FloatArray, half: Float, seed: Long = 7): Raster {
        val r = Raster(w, h)
        val rnd = java.util.Random(seed)
        val ink = Raster.argb(255, 235, 235, 230)
        for (y in 0 until h) for (x in 0 until w) {
            val d = CurveMetrics.distToPolyline(x + 0.5f, y + 0.5f, line)
            if (d <= half + (rnd.nextFloat() - 0.5f) * 0.9f) r[x, y] = ink
        }
        return r
    }

    private fun param(n: Int, f: (Double) -> Pair<Double, Double>) = FloatArray(2 * (n + 1)) { i ->
        val p = f((i / 2).toDouble() / n); (if (i % 2 == 0) p.first else p.second).toFloat()
    }

    private val circle = param(400) { t -> (60 + 40 * cos(2 * PI * t)) to (60 + 40 * sin(2 * PI * t)) }
    private val sine = param(400) { t -> (10 + 220 * t) to (50 + 25 * sin(2 * PI * (220 * t) / 110)) }
    // S: pionowa sinusoida, najciaśniejsze łuki r ≈ 8 px
    private val ess = param(300) { t -> (45 - 20 * sin(2 * PI * t)) to (12 + 80 * t) }
    private val sigma = floatArrayOf(70f, 15f, 20f, 15f, 50f, 55f, 20f, 95f, 72f, 95f)

    private fun dense(poly: FloatArray): FloatArray {
        val out = ArrayList<Float>()
        for (i in 1 until poly.size / 2) {
            val ax = poly[2 * i - 2]; val ay = poly[2 * i - 1]; val bx = poly[2 * i]; val by = poly[2 * i + 1]
            val k = (hypot(bx - ax, by - ay) * 2).toInt().coerceAtLeast(1)
            for (j in 0 until k) { out += ax + (bx - ax) * j / k; out += ay + (by - ay) * j / k }
        }
        out += poly[poly.size - 2]; out += poly[poly.size - 1]
        return out.toFloatArray()
    }

    private fun one(img: Raster): Pair<ChalkPath, FloatArray> {
        val paths = ChalkCurves.paths(img)
        assertEquals("ścieżek: ${paths.map { it.length() }}", 1, paths.size)
        val c = ChalkCurves.fit(paths[0], ChalkCurves.TOL_2400)
        assertNotNull(c)
        return paths[0] to c!!
    }

    /** Największy kąt (stopnie) między uchwytami po obu stronach kotwic, które nie są narożnikami. */
    private fun maxKink(c: FloatArray, skipCorners: Boolean = true): Double {
        var worst = 0.0
        val n = c.size / 2
        val anchors = (3 until n - 1 step 3).toMutableList()
        if (CurveFit.closed(c)) anchors += 0
        for (a in anchors) {
            val before = if (a == 0) n - 2 else a - 1; val after = a + 1
            val ix = c[2 * a] - c[2 * before]; val iy = c[2 * a + 1] - c[2 * before + 1]
            val ox = c[2 * after] - c[2 * a]; val oy = c[2 * after + 1] - c[2 * a + 1]
            val deg = Math.toDegrees(kotlin.math.acos(((ix * ox + iy * oy) / (hypot(ix, iy) * hypot(ox, oy))).toDouble().coerceIn(-1.0, 1.0)))
            if (skipCorners && deg >= 20.0) continue
            worst = maxOf(worst, deg)
        }
        return worst
    }

    @Test
    fun circleBecomesOneClosedSmoothCurve() {
        val (path, c) = one(chalk(120, 120, circle, 2.5f))
        assertTrue("pętla", path.closed)
        assertEquals("narożniki ${path.corners.toList()}", 0, path.corners.size)
        assertTrue("krzywa zamknięta", CurveFit.closed(c))
        assertTrue("kotwic ${Bezier.anchors(c)}", Bezier.anchors(c) <= 8)
        val poly = CurveMetrics.curvePolyline(c, 0.5f)
        var worst = 0.0
        // szkielet ma indeksy pikseli, a kreda była rysowana po środkach pikseli: środek okręgu w (59,5; 59,5)
        for (i in 0 until poly.size / 2) worst = maxOf(worst, abs(hypot(poly[2 * i] - 59.5, poly[2 * i + 1] - 59.5) - 40.0))
        assertTrue("odchyłka od okręgu $worst px", worst < 1.2)
        assertTrue("załamanie ${maxKink(c, skipCorners = false)}°", maxKink(c, skipCorners = false) < 3.0)
    }

    @Test
    fun sineHasFewNodesAndNoTeeth() {
        val img = chalk(240, 100, sine, 2f)
        val (path, c) = one(img)
        assertFalse(path.closed)
        assertEquals("narożniki ${path.corners.toList()}", 0, path.corners.size)
        val raw = ChalkVector.skeleton(img).single().pts
        val old = CurveMetrics.Sum().also { CurveMetrics.addPolyline(it, raw, ChalkVector.simplify(raw, 1.2f)) }
        val new = CurveMetrics.Sum().also { CurveMetrics.addCurve(it, raw, c) }
        assertTrue("węzły/100 px: łamana ${old.nodesPer100}, krzywa ${new.nodesPer100}", new.nodesPer100 * 3 <= old.nodesPer100)
        assertEquals("zęby krzywej", 0, new.teeth)
        assertTrue("skręt na gładkim ${new.maxSmooth}°", new.maxSmooth < 25.0)
        // krzywa leży na prawdziwej sinusoidzie
        val poly = CurveMetrics.curvePolyline(c, 0.5f)
        var s = 0.0
        for (i in 0 until poly.size / 2) s += CurveMetrics.distToPolyline(poly[2 * i] + 0.5f, poly[2 * i + 1] + 0.5f, sine)
        assertTrue("średnio od sinusoidy ${s / (poly.size / 2)} px", s / (poly.size / 2) < 0.6)
    }

    @Test
    fun letterSIsSmoothWithoutCorners() {
        val (path, c) = one(chalk(100, 110, ess, 2.5f))
        assertEquals("narożniki ${path.corners.toList()}", 0, path.corners.size)
        assertTrue("załamanie ${maxKink(c)}°", maxKink(c, skipCorners = false) < 3.0)
        assertTrue("kotwic ${Bezier.anchors(c)}", Bezier.anchors(c) <= 7)
    }

    @Test
    fun sigmaKeepsItsSharpCorners() {
        // Zewnętrzne narożniki ∑ (127°) zawsze; środkowy (74°) szkielet zaokrągla — na 3 z 4 ziaren szumu jest ostry,
        // na czwartym zostaje łukiem (gładko, bez zęba). Fałszywych narożników nie ma żadnych.
        val want = listOf(20f to 15f, 50f to 55f, 20f to 95f)
        var middle = 0
        for (seed in 1L..4L) {
            val img = chalk(100, 110, dense(sigma), 2f, seed)
            val path = ChalkCurves.paths(img).single()
            val c = ChalkCurves.fit(path, ChalkCurves.TOL_2400)!!
            val corners = CurveMetrics.cornerAnchors(c)
            fun has(x: Float, y: Float) = (0 until corners.size / 2).any { hypot(corners[2 * it] - x, corners[2 * it + 1] - y) <= 4f }
            assertTrue("ziarno $seed: ostre kotwice ${corners.toList()}", has(20f, 15f) && has(20f, 95f))
            if (has(50f, 55f)) middle++
            for (k in 0 until corners.size / 2) assertTrue("ziarno $seed: fałszywy narożnik (${corners[2 * k]}, ${corners[2 * k + 1]})",
                want.any { (x, y) -> hypot(corners[2 * k] - x, corners[2 * k + 1] - y) <= 4f })
            // proste ramiona zostają proste, a poza narożnikami krzywa jest gładka
            val poly = CurveMetrics.curvePolyline(c, 0.5f)
            for (i in 0 until poly.size / 2) assertTrue("(${poly[2 * i]}, ${poly[2 * i + 1]})", CurveMetrics.distToPolyline(poly[2 * i], poly[2 * i + 1], sigma) < 2.5f)
            assertTrue("ziarno $seed: załamanie poza narożnikami ${maxKink(c)}°", maxKink(c) < 3.0)
        }
        assertTrue("środkowy narożnik na $middle z 4", middle >= 3)
    }

    @Test
    fun arcsAndSmallLoopsGetNoCorners() {
        val shapes = listOf(
            Triple(40, param(200) { t -> (20 + 5 * cos(2 * PI * t)) to (20 + 5 * sin(2 * PI * t)) }, 1.4f),
            Triple(40, param(200) { t -> (20 + 6 * cos(2 * PI * t)) to (20 + 6 * sin(2 * PI * t)) }, 1.6f),
            Triple(40, param(200) { t -> (20 + 12 * cos(2 * PI * t)) to (20 + 12 * sin(2 * PI * t)) }, 2f),
            Triple(110, ess, 2.5f),
            Triple(110, param(300) { t -> (55 + 40 * cos(PI * t)) to (60 - 40 * sin(PI * t)) }, 3f),
        )
        for ((i, s) in shapes.withIndex()) for (seed in 1L..4L) {
            val paths = ChalkCurves.paths(chalk(s.first, s.first, s.second, s.third, seed))
            assertEquals("kształt $i, ziarno $seed: narożniki ${paths.map { it.corners.toList() }}", 0, paths.sumOf { it.corners.size })
        }
    }

    @Test
    fun lAndVHaveOneCorner() {
        for (seed in 1L..4L) {
            for (line in listOf(floatArrayOf(15f, 10f, 15f, 45f, 50f, 45f), floatArrayOf(10f, 10f, 28f, 50f, 46f, 10f))) {
                val path = ChalkCurves.paths(chalk(60, 60, dense(line), 1.6f, seed)).single()
                assertEquals("ziarno $seed ${line.toList()}", 1, path.corners.size)
                // ostry V (40°): szkielet kończy się przed wierzchołkiem, więc narożnik leży do kilku px w głąb
                val d = hypot(path.pts[2 * path.corners[0]] - line[2], path.pts[2 * path.corners[0] + 1] - line[3])
                assertTrue("ziarno $seed: narożnik $d px od wierzchołka", d <= 7f)
            }
        }
    }

    @Test
    fun staircaseOfAShallowLineIsStraightened() {
        // linia o nachyleniu 1/7: szkielet to schodki co 7 px
        val line = floatArrayOf(10f, 20f, 160f, 41.4f)
        val img = chalk(170, 60, dense(line), 1.6f)
        val (_, c) = one(img)
        assertTrue("kotwic ${Bezier.anchors(c)}", Bezier.anchors(c) <= 3)
        val raw = ChalkVector.skeleton(img).single().pts
        val sum = CurveMetrics.Sum().also { CurveMetrics.addCurve(it, raw, c) }
        assertTrue("skręt ${sum.maxSmooth}°", sum.maxSmooth < 5.0)
    }

    @Test
    fun binomialKeepsEndsAndCornersInPlace() {
        val p = floatArrayOf(0f, 0f, 1f, 1f, 2f, 0f, 3f, 1f, 4f, 0f, 5f, 1f, 6f, 0f)
        val s = ChalkCurves.binomial(p, false, intArrayOf(3))
        assertEquals(0f, s[0]); assertEquals(0f, s[1])
        assertEquals(3f, s[6]); assertEquals(1f, s[7])
        assertEquals(6f, s[12]); assertEquals(0f, s[13])
        // obok końca okno 1‑2‑1: (1 + 2·1 + 0) / 4 dla y punktu 1
        assertEquals(0.5f, s[3], 1e-6f)
    }

    @Test
    fun loopCutByTheCropBecomesOneOpenRun() {
        val ring = FloatArray(200) { i -> val t = 2 * PI * (i / 2) / 100; (if (i % 2 == 0) 50 + 20 * cos(t) else 50 + 20 * sin(t)).toFloat() }
        val path = ChalkPath(ring, IntArray(0), 3f, true)
        val runs = ChalkStrokes.visibleRuns(path) { x, _ -> x < 50f }
        assertEquals(1, runs.size)
        assertFalse(runs[0].closed)
        // lewa połowa okręgu od brzegu do brzegu
        val r = runs[0].pts
        assertEquals(50f, r[0], 0.05f); assertEquals(50f, r[r.size - 2], 0.05f)
        assertTrue(r.indices.filter { it % 2 == 0 }.all { r[it] <= 50.01f })
    }

    @Test
    fun curvesLieOnTheChalkOfTheStraightenedPhoto() {
        val photo = Synthetic.photo()
        val res = BoardPipeline.run(photo)
        val el = ImageEl(1, "a.jpg", 200f, 150f, 400f, 300f)
        val strokes = ChalkStrokes.toCurves(res.image, res.source!!, el, 0x336699).filterIsInstance<Stroke>()
        assertTrue("kresek ${strokes.size}", strokes.isNotEmpty())
        assertTrue(strokes.all { it.ctrl != null && !it.poly })
        val toBoard = Homography.of(Synthetic.QUAD, Quad.of(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f))!!
        for (s in strokes) for (i in 0 until s.pts.size / 3) {
            val b = toBoard.map(s.pts[3 * i].toDouble() - 0.5, s.pts[3 * i + 1].toDouble() - 0.5)
            assertTrue("punkt (${s.pts[3 * i]}, ${s.pts[3 * i + 1]}) → tablica (${b[0]}, ${b[1]})", abs(b[1] - 0.5) < 0.03 && b[0] in 0.15..0.85)
        }
        // w zeszycie widać tylko lewą połowę zdjęcia: krzywe kończą się na brzegu wycinka
        val left = ImageEl(2, "a.jpg", 100f, 150f, 200f, 300f, crop = ImageCrop(0f, 0f, 0.5f, 1f))
        val cut = ChalkStrokes.toCurves(res.image, res.source!!, left, 0).filterIsInstance<Stroke>()
        assertTrue(cut.isNotEmpty())
        for (s in cut) for (i in 0 until s.pts.size / 3) assertTrue("x ${s.pts[3 * i]}", s.pts[3 * i] <= 202f)
    }
}

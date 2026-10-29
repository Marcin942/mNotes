package com.mdev.mnotes.core

import com.mdev.mnotes.core.geom.Construct
import com.mdev.mnotes.core.geom.Construct.P
import com.mdev.mnotes.core.geom.GeoSnap
import com.mdev.mnotes.core.geom.GeoTool
import com.mdev.mnotes.core.geom.GeoTool.In
import com.mdev.mnotes.core.geom.GeoTool.Input
import com.mdev.mnotes.core.geom.GeoTool.Kind
import com.mdev.mnotes.core.geom.Live
import com.mdev.mnotes.core.model.Element
import com.mdev.mnotes.core.model.ElementsEdit
import com.mdev.mnotes.core.model.Ids
import com.mdev.mnotes.core.model.PageContent
import com.mdev.mnotes.core.model.PenKind
import com.mdev.mnotes.core.model.Stroke
import com.mdev.mnotes.core.model.TextBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** 3c-B2: przyciąganie punktów Geometrii (przecięcia, środki, punkty na kształcie) i nowe konstrukcje. */
class GeoSnapTest {
    private val page = "p"
    private fun poly(vararg xy: Float, id: Int = Ids.next()) = Stroke(id, PenKind.BALLPOINT, 0, 1.2f, FloatArray(xy.size / 2 * 3) { k -> if (k % 3 == 2) 1f else xy[k / 3 * 2 + k % 3] }, poly = true)
    private fun circle(cx: Float, cy: Float, r: Float, id: Int = Ids.next()) = Stroke(id, PenKind.BALLPOINT, 0, 1.2f,
        FloatArray(3 * 49) { k -> val a = 2 * PI * (k / 3) / 48; when (k % 3) { 0 -> cx + r * cos(a).toFloat(); 1 -> cy + r * sin(a).toFloat(); else -> 1f } }, ellipse = true)
    private fun dot(x: Float, y: Float) = Stroke(Ids.next(), PenKind.BALLPOINT, 0, 3f, floatArrayOf(x, y, 1f))
    private fun dots(els: List<Element>) = els.filterIsInstance<Stroke>().filter { it.count == 1 }
    private fun near(p: P, x: Float, y: Float, eps: Float = 0.05f) { assertEquals(x, p.x, eps); assertEquals(y, p.y, eps) }
    private fun replaced(c: PageContent, e: Element) = c.apply(ElementsEdit.replace(c, mapOf(e.id to e)))

    @Test fun snapKindsAndPriority() {
        val seg = poly(0f, 0f, 100f, 0f); val c = circle(200f, 0f, 30f)
        val els = listOf(seg, c, dot(0f, 100f))
        assertEquals(GeoSnap.Kind.MID, GeoSnap.find(els, 52f, 6f, 10f)!!.kind)
        near(GeoSnap.find(els, 52f, 6f, 10f)!!.p, 50f, 0f)
        assertEquals(GeoSnap.Kind.VERTEX, GeoSnap.find(els, 97f, 3f, 10f)!!.kind)
        assertEquals(GeoSnap.Kind.CENTER, GeoSnap.find(els, 204f, 3f, 10f)!!.kind)
        assertEquals(GeoSnap.Kind.DOT, GeoSnap.find(els, 3f, 96f, 10f)!!.kind)
        // Na odcinku tylko tuż przy nim (połowa zasięgu); dalej punkt jest wolny.
        val on = GeoSnap.find(els, 25f, 3f, 10f)!!
        assertEquals(GeoSnap.Kind.ON, on.kind); near(on.p, 25f, 0f)
        assertNull(GeoSnap.find(els, 25f, 8f, 10f))
        // Punkt na okręgu leży dokładnie na nim.
        val oc = GeoSnap.find(els, 200f, 33f, 10f)!!
        assertEquals(GeoSnap.Kind.ON, oc.kind); assertEquals(30f, oc.p.dist(P(200f, 0f)), 1e-3f)
        // Kropka minimalnie dalej wygrywa ze środkiem odcinka (waga rodzaju).
        val tie = listOf(poly(-50f, 0f, 50f, 0f), dot(0f, 7f))
        assertEquals(GeoSnap.Kind.DOT, GeoSnap.find(tie, 0f, 3f, 10f)!!.kind)
    }

    @Test fun intersectionsOfSegmentsAndCircles() {
        val a = poly(0f, 0f, 100f, 100f); val b = poly(0f, 100f, 100f, 0f)
        val x = GeoSnap.find(listOf(a, b), 54f, 47f, 12f)!!
        assertEquals(GeoSnap.Kind.CROSS, x.kind); near(x.p, 50f, 50f)
        // Przedłużenie odcinka nie daje przecięcia (punkt wspólny poza końcem drugiego).
        val short = poly(0f, 100f, 40f, 60f)
        assertTrue(GeoSnap.find(listOf(a, short), 51f, 49f, 12f)?.kind != GeoSnap.Kind.CROSS)
        // Okrąg i prosta, dwa okręgi.
        val c1 = circle(0f, 0f, 50f); val line = poly(-100f, 30f, 100f, 30f)
        near(GeoSnap.find(listOf(c1, line), 42f, 28f, 12f)!!.p, 40f, 30f, 0.1f)
        val c2 = circle(60f, 0f, 50f)
        val xc = GeoSnap.find(listOf(c1, c2), 31f, -38f, 12f)!!
        assertEquals(GeoSnap.Kind.CROSS, xc.kind); near(xc.p, 30f, -40f, 0.1f)
        // Sąsiednie boki łamanej przecinają się we wspólnym wierzchołku — to wierzchołek, nie „przecięcie”.
        val tri = poly(0f, 0f, 60f, 0f, 0f, 40f, 0f, 0f)
        assertEquals(GeoSnap.Kind.VERTEX, GeoSnap.find(listOf(tri), 59f, 2f, 10f)!!.kind)
    }

    @Test fun snapResolvesAfterTheShapesMove() {
        val a = poly(0f, 0f, 100f, 100f); val b = poly(0f, 100f, 100f, 0f)
        val x = GeoSnap.find(listOf(a, b), 50f, 50f, 12f)!!
        val b2 = poly(0f, 60f, 100f, 60f, id = b.id)
        near(GeoSnap.resolve(x, mapOf(a.id to a, b.id to b2))!!, 60f, 60f)
        // Okrąg rozciągnięty w kreskę bez kształtu okręgu — zależność się zrywa.
        val c = circle(0f, 0f, 40f); val ln = poly(-100f, 0f, 100f, 0f)
        val m = GeoSnap.find(listOf(ln, c), 38f, 2f, 10f)!!
        assertEquals(GeoSnap.Kind.CROSS, m.kind)
        assertNotNull(GeoSnap.resolve(m, mapOf(ln.id to ln, c.id to c)))
        assertNull(GeoSnap.resolve(m, mapOf(ln.id to ln, c.id to poly(0f, 0f, 5f, 5f, id = c.id))))
        // Środek odcinka i punkt na odcinku idą za odcinkiem.
        val s = poly(0f, 0f, 100f, 0f)
        val mid = GeoSnap.find(listOf(s), 50f, 2f, 10f)!!; val on = GeoSnap.find(listOf(s), 20f, 1f, 10f)!!
        val s2 = poly(0f, 0f, 0f, 200f, id = s.id)
        near(GeoSnap.resolve(mid, mapOf(s.id to s2))!!, 0f, 100f)
        near(GeoSnap.resolve(on, mapOf(s.id to s2))!!, 0f, 40f)
    }

    @Test fun liveCircleCentredOnAnIntersectionFollowsTheLines() {
        // Okrąg cyrklem ze środkiem w przecięciu dwóch odcinków: przesunięcie odcinka przenosi okrąg (NAU-3 + 3c-B2).
        val live = Live()
        val a = poly(0f, 0f, 100f, 100f); val b = poly(0f, 100f, 100f, 0f); val d = dot(80f, 50f)
        var content = PageContent.of(listOf(a, b, d))
        val kind = Kind.COMPASS
        val picks = listOf(GeoTool.pickFrom(content.elements, 52f, 49f, 12f, In.POINT)!!, GeoTool.pickFrom(content.elements, 81f, 51f, 12f, In.POINT)!!)
        assertEquals(listOf(a.id, b.id), picks[0].ids.sorted().let { if (a.id < b.id) it else it.reversed() })
        val out = GeoTool.build(kind, picks.map { it.input }, 0, 1.2f, names = false)
        content = content.apply(ElementsEdit.add(content, out))
        val ids = picks.flatMap { it.ids }.distinct()
        live.add(page, ids, out.map { it.id }) { cur ->
            val byId = cur.associateBy { it.id }
            GeoTool.build(kind, picks.mapIndexed { i, pk -> GeoTool.resolvePick(kind.inputs[i], pk, byId) ?: return@add null }, 0, 1.2f, names = false)
        }
        val before = content
        val after = replaced(before, poly(0f, 60f, 100f, 60f, id = b.id))
        val fin = after.apply(live.follow(page, before, after)!!)
        val centre = dots(fin.elements).first { it.id in out.map { o -> o.id } }
        assertEquals(60f, centre.bounds.cx, 0.1f); assertEquals(60f, centre.bounds.cy, 0.1f)
    }

    @Test fun divideRegularAndSegment() {
        val l = Input.Ln(Construct.Line(P(0f, 0f), P(90f, 0f)))
        val thirds = dots(GeoTool.build(Kind.DIVIDE, listOf(l), 0, 1f, prm = GeoTool.Params(parts = 3)))
        assertEquals(listOf(30f, 60f), thirds.map { it.bounds.cx }.sorted().map { Math.round(it).toFloat() })
        // Podział na 2 to środek z nazwą M; na więcej części — same kropki bez nazw.
        assertEquals("M", GeoTool.build(Kind.DIVIDE, listOf(l), 0, 1f).filterIsInstance<TextBox>().single().text)
        assertTrue(GeoTool.build(Kind.DIVIDE, listOf(l), 0, 1f, prm = GeoTool.Params(parts = 5)).none { it is TextBox })
        val hex = GeoTool.build(Kind.REGULAR, listOf(l), 0, 1f, prm = GeoTool.Params(sides = 6)).filterIsInstance<Stroke>().single()
        assertTrue(hex.closed); assertEquals(6, hex.vertexCount)
        for (i in 0 until 6) assertEquals(90f, P(hex.pts[3 * i], hex.pts[3 * i + 1]).dist(P(hex.pts[3 * i + 3], hex.pts[3 * i + 4])), 0.05f)
        // Nad odcinkiem (y w dół: mniejsze y), dla A z lewej i B z prawej.
        val ys = (0 until hex.count).map { hex.pts[3 * it + 1] }
        assertEquals(-155.88f, ys.min(), 0.05f); assertEquals(0f, ys.max(), 1e-3f)
        val tri = GeoTool.build(Kind.REGULAR, listOf(l), 0, 1f, prm = GeoTool.Params(sides = 3)).filterIsInstance<Stroke>().single()
        assertNotNull(GeoTool.pick(listOf(tri), 45f, -20f, 4f, In.TRIANGLE))
        val seg = GeoTool.build(Kind.SEGMENT, listOf(Input.Point(P(0f, 0f)), Input.Point(P(30f, 40f))), 0, 1f).filterIsInstance<Stroke>().single()
        assertEquals(2, seg.count); assertEquals(50f, P(seg.pts[0], seg.pts[1]).dist(P(seg.pts[3], seg.pts[4])), 1e-3f)
        assertTrue(GeoTool.build(Kind.SEGMENT, listOf(Input.Point(P(0f, 0f)), Input.Point(P(0.2f, 0f))), 0, 1f).isEmpty())
    }

    @Test fun tangentAtCopyRadiusAndAngleAt() {
        val c = Input.Circ(Construct.Circle(P(0f, 0f), 50f))
        val tan = GeoTool.build(Kind.TANGENT_AT, listOf(c, Input.Point(P(0f, -60f))), 0, 1f, names = false)
        val t = dots(tan).single(); assertEquals(0f, t.bounds.cx, 1e-3f); assertEquals(-50f, t.bounds.cy, 1e-3f)
        val line = tan.filterIsInstance<Stroke>().single { it.count == 2 && it.line == com.mdev.mnotes.core.pen.LineStyle.SOLID }
        assertEquals(line.pts[1], line.pts[4], 1e-3f) // styczna w (0, −50) jest pozioma
        assertTrue(GeoTool.build(Kind.TANGENT_AT, listOf(c, Input.Point(P(0f, 0f))), 0, 1f).isEmpty())
        val cr = GeoTool.build(Kind.COPY_RADIUS, listOf(Input.Ln(Construct.Line(P(0f, 0f), P(30f, 40f))), Input.Point(P(200f, 200f))), 0, 1f, names = false)
        val ring = cr.filterIsInstance<Stroke>().single { it.ellipse }
        assertEquals(50f, P(ring.pts[0], ring.pts[1]).dist(P(200f, 200f)), 0.01f)
        // Kąt 60° przy wierzchołku A ramienia AB: półprosta pod 60° (w górę ekranu) i podpis miary.
        val arm = Input.Ln(Construct.Line(P(0f, 0f), P(100f, 0f)))
        val ang = GeoTool.build(Kind.ANGLE_AT, listOf(arm, Input.Point(P(0f, 0f))), 0, 1f, prm = GeoTool.Params(angle = 60f))
        assertEquals("60°", ang.filterIsInstance<TextBox>().single().text)
        val ray = ang.filterIsInstance<Stroke>().first { it.count == 2 }
        assertEquals(-60.0, Math.toDegrees(kotlin.math.atan2((ray.pts[4] - ray.pts[1]).toDouble(), (ray.pts[3] - ray.pts[0]).toDouble())), 0.05)
        assertEquals(100f, P(ray.pts[0], ray.pts[1]).dist(P(ray.pts[3], ray.pts[4])), 0.05f)
    }

    @Test fun translateAndDilation() {
        val tri = poly(0f, 0f, 20f, 0f, 0f, 10f, 0f, 0f)
        val moved = GeoTool.build(Kind.TRANSLATE, listOf(Input.Point(P(0f, 0f)), Input.Point(P(50f, -20f)), Input.Obj(tri)), 0, 1f).single() as Stroke
        assertEquals(50f, moved.pts[0], 1e-3f); assertEquals(-20f, moved.pts[1], 1e-3f)
        assertTrue(moved.id != tri.id)
        val half = GeoTool.build(Kind.HOMOTHETY, listOf(Input.Point(P(0f, 0f)), Input.Obj(tri)), 0, 1f, prm = GeoTool.Params(scale = -0.5f)).single() as Stroke
        assertEquals(-10f, half.pts[3], 1e-3f); assertEquals(-5f, half.pts[7], 1e-3f)
    }

    @Test fun newHintsSayWhatThePointIsFor() {
        assertEquals("Wskaż początek wektora", GeoTool.hint(Kind.TRANSLATE, 0))
        assertEquals("Wskaż koniec wektora", GeoTool.hint(Kind.TRANSLATE, 1))
        assertEquals("Wskaż obiekt do przekształcenia", GeoTool.hint(Kind.TRANSLATE, 2))
        assertEquals("Wskaż wierzchołek kąta", GeoTool.hint(Kind.ANGLE_AT, 1))
        assertEquals("Pick the centre", GeoTool.hint(Kind.HOMOTHETY, 0, pl = false))
        // Każda konstrukcja z liczbą ma swój parametr; listwa grupuje rodzaje po kolei (bez przeplatania grup).
        assertEquals(GeoTool.Param.PARTS, Kind.DIVIDE.param)
        val groups = Kind.entries.map { it.group }
        assertEquals(groups.sorted(), groups)
        assertTrue(abs(GeoTool.Params().scale - 2f) < 1e-6f)
    }
}

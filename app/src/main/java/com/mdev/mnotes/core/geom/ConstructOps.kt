package com.mdev.mnotes.core.geom

import com.mdev.mnotes.core.geom.Construct.Circle
import com.mdev.mnotes.core.geom.Construct.Line
import com.mdev.mnotes.core.geom.Construct.P
import com.mdev.mnotes.core.model.Element
import com.mdev.mnotes.core.model.Ids
import com.mdev.mnotes.core.model.PenKind
import com.mdev.mnotes.core.model.Stroke
import com.mdev.mnotes.core.pen.LineStyle
import com.mdev.mnotes.core.shapes.ShapeBox
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/**
 * Narzędzie „Konstrukcja” (L1): z zaznaczonych kształtów (rozpoznane proste, trójkąty, okręgi, punkty) wylicza
 * konstrukcje, które mają sens dla tego zestawu, i ich wynik jako zwykłe kreski (proste z uchwytami, okręgi jako
 * elipsy z ramką, punkty jako kropki). Kreski pomocnicze są przerywane, wynik ciągły.
 */
object ConstructOps {
    /** Rozpoznane wejścia: nazwane elementy zaznaczenia. */
    class Inputs(val points: List<P>, val lines: List<Line>, val circles: List<Circle>, val triangles: List<Triple<P, P, P>>, val others: List<Element>)

    class Op(val id: String, val pl: String, val en: String, val build: (color: Int, width: Float) -> List<Element>, val replaces: Boolean = false)

    fun inputs(els: List<Element>): Inputs {
        val pts = ArrayList<P>(); val lines = ArrayList<Line>(); val circles = ArrayList<Circle>(); val tris = ArrayList<Triple<P, P, P>>(); val rest = ArrayList<Element>()
        for (e in els) {
            if (e !is Stroke) { rest.add(e); continue }
            val p = e.pts; val n = e.count
            val f = if (e.ellipse) ShapeBox.frameOf(e) else null
            when {
                n >= 1 && e.bounds.w < 6f + e.width && e.bounds.h < 6f + e.width -> pts.add(P(e.bounds.cx, e.bounds.cy))
                f != null && abs(f.hw - f.hh) < 0.08f * max(f.hw, f.hh) -> circles.add(Circle(P(f.cx, f.cy), (f.hw + f.hh) / 2f))
                e.poly && n == 2 -> lines.add(Line(P(p[0], p[1]), P(p[3], p[4])))
                e.poly && e.closed && e.vertexCount == 3 -> tris.add(Triple(P(p[0], p[1]), P(p[3], p[4]), P(p[6], p[7])))
                else -> rest.add(e)
            }
        }
        return Inputs(pts, lines, circles, tris, rest)
    }

    /** Konstrukcje dostępne dla zaznaczenia [els] (puste, gdy nie ma żadnej). */
    fun ops(els: List<Element>): List<Op> {
        val i = inputs(els)
        val out = ArrayList<Op>()
        for (t in i.triangles.take(1)) {
            val (a, b, c) = t
            out += Op("circum", "Okrąg opisany", "Circumcircle", { col, w -> listOfNotNull(Construct.circleThrough(a, b, c)?.let { circle(it, col, w) }) + listOfNotNull(Construct.circumcenter(a, b, c)?.let { dot(it, col, w) }) })
            out += Op("in", "Okrąg wpisany", "Incircle", { col, w -> Construct.incircle(a, b, c)?.let { listOf(circle(it, col, w), dot(it.c, col, w)) } ?: emptyList() })
            out += Op("medians", "Środkowe i środek ciężkości", "Medians and centroid", { col, w ->
                listOf(line(Line(a, Construct.midpoint(b, c)), col, w, true), line(Line(b, Construct.midpoint(c, a)), col, w, true), line(Line(c, Construct.midpoint(a, b)), col, w, true), dot(Construct.centroid(a, b, c), col, w))
            })
            out += Op("altitudes", "Wysokości i ortocentrum", "Altitudes and orthocenter", { col, w ->
                val h = Construct.orthocenter(a, b, c)
                listOf(line(Line(a, Construct.project(a, Line(b, c))), col, w, true), line(Line(b, Construct.project(b, Line(c, a))), col, w, true), line(Line(c, Construct.project(c, Line(a, b))), col, w, true)) + listOfNotNull(h?.let { dot(it, col, w) })
            })
            out += Op("bisectors", "Symetralne boków", "Perpendicular bisectors", { col, w ->
                val len = max(a.dist(b), max(b.dist(c), c.dist(a)))
                listOf(line(Construct.bisector(a, b, len), col, w, true), line(Construct.bisector(b, c, len), col, w, true), line(Construct.bisector(c, a, len), col, w, true))
            })
        }
        if (i.points.size >= 3) {
            val (a, b, c) = i.points
            out += Op("circle3", "Okrąg przez 3 punkty", "Circle through 3 points", { col, w -> listOfNotNull(Construct.circleThrough(a, b, c)?.let { circle(it, col, w) }) })
        }
        if (i.lines.size >= 2) out += Op("xll", "Punkt przecięcia prostych", "Intersection of lines", { col, w -> listOfNotNull(Construct.intersect(i.lines[0], i.lines[1])?.let { dot(it, col, w) }) })
        if (i.lines.isNotEmpty() && i.circles.isNotEmpty()) out += Op("xlc", "Przecięcie prostej i okręgu", "Line–circle intersection", { col, w -> Construct.intersect(i.lines[0], i.circles[0]).map { dot(it, col, w) } })
        if (i.circles.size >= 2) out += Op("xcc", "Przecięcie okręgów", "Circle intersection", { col, w -> Construct.intersect(i.circles[0], i.circles[1]).map { dot(it, col, w) } })
        if (i.lines.size == 1 && i.points.isEmpty() && i.circles.isEmpty() && i.triangles.isEmpty() && i.others.isEmpty()) {
            val l = i.lines[0]
            out += Op("mid", "Środek i symetralna odcinka", "Midpoint and bisector", { col, w -> listOf(dot(Construct.midpoint(l.a, l.b), col, w), line(Construct.bisector(l.a, l.b, l.a.dist(l.b)), col, w, true)) })
        }
        if (i.circles.size == 1 && i.lines.isEmpty() && i.points.isEmpty()) out += Op("center", "Środek okręgu", "Circle centre", { col, w -> listOf(dot(i.circles[0].c, col, w)) })
        // Odbicia: prosta (albo punkt) jako oś, reszta zaznaczenia odbija się jako kopia.
        val movable = els.filter { e -> !(e is Stroke && e.poly && e.count == 2 && i.lines.isNotEmpty() && sameLine(e, i.lines[0])) }
        if (i.lines.size == 1 && movable.isNotEmpty()) {
            val m = reflection(i.lines[0])
            out += Op("mirror", "Odbij względem prostej (kopia)", "Reflect across the line (copy)", { _, _ -> movable.map { it.transformed(m).withId(Ids.next()) } })
        }
        if (i.points.size == 1) {
            val p = i.points[0]
            val rest = els.filter { e -> !(e is Stroke && e.bounds.w < 6f + e.width && e.bounds.h < 6f + e.width) }
            if (rest.isNotEmpty()) {
                out += Op("pmirror", "Odbij względem punktu (kopia)", "Reflect through the point (copy)", { _, _ -> rest.map { it.transformed(Affine.scale(-1f, -1f, p.x, p.y)).withId(Ids.next()) } })
                out += Op("homothety2", "Jednokładność ×2 względem punktu", "Homothety ×2 about the point", { _, _ -> rest.map { it.transformed(Affine.scale(2f, 2f, p.x, p.y)).withId(Ids.next()) } })
            }
        }
        return out
    }

    private fun sameLine(s: Stroke, l: Line) = s.pts[0] == l.a.x && s.pts[1] == l.a.y && s.pts[3] == l.b.x && s.pts[4] == l.b.y

    /** Odbicie względem prostej jako przekształcenie afiniczne. */
    fun reflection(l: Line): Affine {
        val dx = l.b.x - l.a.x; val dy = l.b.y - l.a.y
        val len = hypot(dx, dy).coerceAtLeast(1e-6f)
        val ux = dx / len; val uy = dy / len
        val a = 2 * ux * ux - 1; val b = 2 * ux * uy; val d = 2 * uy * uy - 1
        // x' = M·x + t, gdzie t = p − M·p dla punktu p na prostej.
        val tx = l.a.x - (a * l.a.x + b * l.a.y); val ty = l.a.y - (b * l.a.x + d * l.a.y)
        return Affine(a, b, b, d, tx, ty)
    }

    private fun line(l: Line, color: Int, width: Float, helper: Boolean = false) =
        Stroke(Ids.next(), PenKind.BALLPOINT, color, if (helper) width * 0.7f else width, floatArrayOf(l.a.x, l.a.y, 1f, l.b.x, l.b.y, 1f), poly = true, line = if (helper) LineStyle.DASHED else LineStyle.SOLID)

    private fun circle(c: Circle, color: Int, width: Float): Stroke {
        val n = 48
        val pts = FloatArray(3 * (n + 1))
        for (k in 0..n) { val a = 2 * PI * k / n; pts[3 * k] = c.c.x + c.r * cos(a).toFloat(); pts[3 * k + 1] = c.c.y + c.r * sin(a).toFloat(); pts[3 * k + 2] = 1f }
        return Stroke(Ids.next(), PenKind.BALLPOINT, color, width, pts, ellipse = true)
    }

    private fun dot(p: P, color: Int, width: Float) = Stroke(Ids.next(), PenKind.BALLPOINT, color, max(3f, width * 2.5f), floatArrayOf(p.x, p.y, 1f))
}

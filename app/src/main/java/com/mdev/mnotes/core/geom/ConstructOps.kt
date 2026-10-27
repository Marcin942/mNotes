package com.mdev.mnotes.core.geom

import com.mdev.mnotes.core.geom.Construct.Circle
import com.mdev.mnotes.core.geom.Construct.Line
import com.mdev.mnotes.core.geom.Construct.P
import com.mdev.mnotes.core.model.Element
import com.mdev.mnotes.core.model.Ids
import com.mdev.mnotes.core.model.PenKind
import com.mdev.mnotes.core.model.Stroke
import com.mdev.mnotes.core.model.TextBox
import com.mdev.mnotes.core.pen.LineStyle
import com.mdev.mnotes.core.shapes.ShapeBox
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
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

    /**
     * Konstrukcje dostępne dla zaznaczenia [els] (puste, gdy nie ma żadnej). [names] = podpisz punkty (A, B, C, O, H, …)
     * małymi polami tekstu obok kropek (A2-9: bez nazw trudno o konstrukcji mówić); [pl] wybiera S (PL) albo G (EN) dla
     * środka ciężkości.
     */
    fun ops(els: List<Element>, names: Boolean = true, pl: Boolean = true): List<Op> {
        val i = inputs(els)
        val out = ArrayList<Op>()
        // Wierzchołki podpisujemy tylko raz: gdy w zaznaczeniu są już pola „A”/„B”/„C”, zostawiamy je.
        val nameVertices = names && i.others.none { it is TextBox && it.text.trim() in VERTEX_NAMES }
        fun draw(base: List<Line>, f: Draw.() -> Unit): (Int, Float) -> List<Element> = { col, w -> Draw(col, w, base, names).apply(f).done() }
        for (t in i.triangles.take(1)) {
            val (a, b, c) = t
            val sides = listOf(Line(a, b), Line(b, c), Line(c, a))
            fun Draw.vertices() { if (nameVertices) { name(a, "A"); name(b, "B"); name(c, "C") } }
            out += Op("circum", "Okrąg opisany", "Circumcircle", draw(sides) {
                vertices()
                Construct.circleThrough(a, b, c)?.let { circle(it) }
                Construct.circumcenter(a, b, c)?.let { dot(it, "O") }
            })
            out += Op("in", "Okrąg wpisany", "Incircle", draw(sides) {
                vertices()
                Construct.incircle(a, b, c)?.let { circle(it); dot(it.c, "I") }
            })
            out += Op("medians", "Środkowe i środek ciężkości", "Medians and centroid", draw(sides) {
                vertices()
                line(Line(a, Construct.midpoint(b, c)), true); line(Line(b, Construct.midpoint(c, a)), true); line(Line(c, Construct.midpoint(a, b)), true)
                dot(Construct.centroid(a, b, c), if (pl) "S" else "G")
            })
            out += Op("altitudes", "Wysokości i ortocentrum", "Altitudes and orthocenter", draw(sides) {
                vertices()
                line(Line(a, Construct.project(a, Line(b, c))), true); line(Line(b, Construct.project(b, Line(c, a))), true); line(Line(c, Construct.project(c, Line(a, b))), true)
                Construct.orthocenter(a, b, c)?.let { dot(it, "H") }
            })
            out += Op("bisectors", "Symetralne boków", "Perpendicular bisectors", draw(sides) {
                vertices()
                val len = max(a.dist(b), max(b.dist(c), c.dist(a)))
                line(Construct.bisector(a, b, len), true); line(Construct.bisector(b, c, len), true); line(Construct.bisector(c, a, len), true)
            })
        }
        if (i.points.size >= 3) {
            val (a, b, c) = i.points
            out += Op("circle3", "Okrąg przez 3 punkty", "Circle through 3 points", draw(emptyList()) {
                Construct.circleThrough(a, b, c)?.let { circle(it); if (nameVertices) { name(a, "A", 4f); name(b, "B", 4f); name(c, "C", 4f) } }
            })
        }
        val given = i.lines.take(2)
        if (i.lines.size >= 2) out += Op("xll", "Punkt przecięcia prostych", "Intersection of lines", draw(given) { Construct.intersect(i.lines[0], i.lines[1])?.let { dot(it, "P") } })
        if (i.lines.isNotEmpty() && i.circles.isNotEmpty()) out += Op("xlc", "Przecięcie prostej i okręgu", "Line–circle intersection", draw(given) {
            around(i.circles[0]); Construct.intersect(i.lines[0], i.circles[0]).forEachIndexed { k, p -> dot(p, PAIR[k]) }
        })
        if (i.circles.size >= 2) out += Op("xcc", "Przecięcie okręgów", "Circle intersection", draw(emptyList()) {
            around(i.circles[0]); around(i.circles[1]); Construct.intersect(i.circles[0], i.circles[1]).forEachIndexed { k, p -> dot(p, PAIR[k]) }
        })
        if (i.lines.size == 1 && i.points.isEmpty() && i.circles.isEmpty() && i.triangles.isEmpty() && i.others.isEmpty()) {
            val l = i.lines[0]
            out += Op("mid", "Środek i symetralna odcinka", "Midpoint and bisector", draw(listOf(l)) {
                dot(Construct.midpoint(l.a, l.b), "M"); line(Construct.bisector(l.a, l.b, l.a.dist(l.b)), true)
            })
        }
        if (i.circles.size == 1 && i.lines.isEmpty() && i.points.isEmpty()) out += Op("center", "Środek okręgu", "Circle centre", draw(emptyList()) { dot(i.circles[0].c, "O") })
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

    /**
     * Czy zamiast konstrukcji pokazać wskazówkę: zaznaczenie nie daje żadnej, ale są w nim odręczne kreski — pewnie
     * kształt narysowany bez przytrzymania, którego narzędzie nie rozpoznaje jako trójkąta, prostej ani okręgu (A2-9).
     */
    fun needsHint(els: List<Element>): Boolean =
        ops(els, names = false).isEmpty() && els.any { it is Stroke && !it.poly && !it.ellipse && it.ctrl == null && it.count > 2 }

    const val LABEL_PT = 11f
    private val VERTEX_NAMES = setOf("A", "B", "C")
    private val PAIR = listOf("P", "Q")

    /** Wynik jednej konstrukcji: kreski i kropki oraz geometria, której nazwy punktów mają nie zasłaniać. */
    private class Draw(val col: Int, val w: Float, base: List<Line>, val names: Boolean) {
        val out = ArrayList<Element>()
        val segs = ArrayList<Line>(base)
        val circs = ArrayList<Circle>()
        private val marks = ArrayList<Triple<String, P, Float>>()
        fun line(l: Line, helper: Boolean) { out += ConstructOps.line(l, col, w, helper); segs += l }
        fun circle(c: Circle) { out += ConstructOps.circle(c, col, w); circs += c }
        /** Okrąg z zaznaczenia (nie rysujemy go, ale nazwa nie może na nim leżeć). */
        fun around(c: Circle) { circs += c }
        fun dot(p: P, name: String) { out += ConstructOps.dot(p, col, w); marks += Triple(name, p, dotSize(w)) }
        /** Nazwa istniejącego punktu (wierzchołek, kropka z zaznaczenia o średnicy [dot]). */
        fun name(p: P, name: String, dot: Float = 0f) { marks += Triple(name, p, dot) }
        fun done(): List<Element> {
            if (!names) return out
            val tol = max(1.5f, w)
            // Punkty, które wypadły w tym samym miejscu (np. ortocentrum = wierzchołek kąta prostego), dostają jedną
            // nazwę „A=H” zamiast dwóch pól na sobie.
            val used = BooleanArray(marks.size)
            for (k in marks.indices) {
                if (used[k]) continue
                val (n, p, d) = marks[k]
                var text = n; var dot = d
                for (j in k + 1 until marks.size) if (!used[j] && marks[j].second.dist(p) <= tol) { used[j] = true; text += "=" + marks[j].first; dot = max(dot, marks[j].third) }
                out += label(text, p, busyAt(p, segs, circs, tol), col, dot)
            }
            return out
        }
    }

    /** Kierunki (radiany) linii wychodzących z punktu [p]: odcinki przez niego lub z końcem w nim, styczne okręgów. */
    fun busyAt(p: P, segs: List<Line>, circs: List<Circle>, tol: Float): List<Float> {
        val out = ArrayList<Float>()
        for (s in segs) {
            val len = s.a.dist(s.b)
            if (len < 1e-3f) continue
            if (kotlin.math.sqrt(GeomMath.distSqPointSegment(p.x, p.y, s.a.x, s.a.y, s.b.x, s.b.y)) > tol) continue
            val ab = atan2(s.b.y - s.a.y, s.b.x - s.a.x)
            when {
                p.dist(s.a) <= tol -> out += ab
                p.dist(s.b) <= tol -> out += ab + PI.toFloat()
                else -> { out += ab; out += ab + PI.toFloat() }
            }
        }
        for (c in circs) if (abs(p.dist(c.c) - c.r) <= tol) {
            val t = atan2(p.y - c.c.y, p.x - c.c.x) + (PI / 2).toFloat()
            out += t; out += t + PI.toFloat()
        }
        return out
    }

    /**
     * Środek pola z nazwą punktu [p]: w połowie największej wolnej luki między kierunkami [busy], w odległości [dist];
     * bez zajętych kierunków — w prawo i w górę (jak zwykle podpisuje się punkt na kartce).
     */
    fun labelAt(p: P, busy: List<Float>, dist: Float): P {
        if (busy.isEmpty()) return P(p.x + dist * 0.7071f, p.y - dist * 0.7071f)
        val twoPi = (2 * PI).toFloat()
        val a = busy.map { ((it % twoPi) + twoPi) % twoPi }.sorted()
        var best = -1f; var mid = 0f
        for (k in a.indices) {
            val from = a[k]; val to = if (k + 1 < a.size) a[k + 1] else a[0] + twoPi
            if (to - from > best) { best = to - from; mid = from + (to - from) / 2f }
        }
        return P(p.x + dist * cos(mid), p.y + dist * sin(mid))
    }

    private fun label(name: String, p: P, busy: List<Float>, color: Int, dot: Float): TextBox {
        val tw = name.length * LABEL_PT * 0.62f + 3f; val th = LABEL_PT * 1.3f
        val at = labelAt(p, busy, dot / 2f + 0.5f * hypot(tw, th) + 1.5f)
        return TextBox(Ids.next(), at.x, at.y, tw, th, 0f, name, LABEL_PT, color, style = TextBox.STYLE_ITALIC, align = TextBox.ALIGN_CENTER)
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

    private fun dotSize(width: Float) = max(3f, width * 2.5f)

    private fun dot(p: P, color: Int, width: Float) = Stroke(Ids.next(), PenKind.BALLPOINT, color, dotSize(width), floatArrayOf(p.x, p.y, 1f))
}

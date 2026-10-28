package com.mdev.mnotes.core.geom

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Konstrukcje geometryczne 2D (L1), napisane od zera według znanych wzorów (pomysł z pracy licencjackiej „Route
 * Editor”, bez jej kodu): okrąg przez trzy punkty, przecięcia, punkty szczególne trójkąta i przekształcenia.
 * Wszystko w punktach strony (pt), y w dół jak na ekranie — wzory od tego nie zależą.
 */
object Construct {
    data class P(val x: Float, val y: Float) {
        operator fun plus(o: P) = P(x + o.x, y + o.y)
        operator fun minus(o: P) = P(x - o.x, y - o.y)
        operator fun times(k: Float) = P(x * k, y * k)
        fun dist(o: P) = hypot(x - o.x, y - o.y)
    }

    /** Prosta przez [a] i [b] (jako odcinek do rysowania: od [a] do [b]). */
    data class Line(val a: P, val b: P)

    data class Circle(val c: P, val r: Float)

    private const val EPS = 1e-6

    /**
     * 2c-A8: próg „prawie równoległe / prawie współliniowe” jako sinus kąta (ok. 0,03°), nie bezwzględne 1e-6 przy
     * wielkościach rzędu 10⁴ pt² — dawniej przecięcie prostych pod 0,01° lądowało setki tysięcy pt dalej.
     */
    const val SIN_EPS = 5e-4

    /** Najdalej tyle przekątnych strony od jej środka może wypaść wynik konstrukcji (dalej to szum, nie punkt). */
    const val FAR_DIAGONALS = 3f

    /** Czy [p] leży dalej niż [FAR_DIAGONALS] przekątnych od środka strony [w]×[h]. */
    fun tooFar(p: P, w: Float, h: Float): Boolean = hypot(p.x - w / 2f, p.y - h / 2f) > FAR_DIAGONALS * hypot(w, h)

    /** Czy trzy punkty są prawie współliniowe: wysokość trójkąta względem najdłuższego boku poniżej [SIN_EPS]. */
    fun nearlyCollinear(a: P, b: P, c: P): Boolean {
        val l = maxOf(a.dist(b), b.dist(c), c.dist(a)).toDouble()
        return l < 1e-6 || abs(cross(b - a, c - a)) < SIN_EPS * l * l
    }

    private fun cross(a: P, b: P) = a.x.toDouble() * b.y - a.y.toDouble() * b.x

    /** Okrąg przez trzy punkty; null, gdy są współliniowe. */
    fun circleThrough(a: P, b: P, c: P): Circle? {
        val o = circumcenter(a, b, c) ?: return null
        return Circle(o, o.dist(a))
    }

    /** Środek okręgu opisanego (przecięcie symetralnych). */
    fun circumcenter(a: P, b: P, c: P): P? {
        val ax = a.x.toDouble(); val ay = a.y.toDouble(); val bx = b.x.toDouble(); val by = b.y.toDouble(); val cx = c.x.toDouble(); val cy = c.y.toDouble()
        if (nearlyCollinear(a, b, c)) return null
        val d = 2 * (ax * (by - cy) + bx * (cy - ay) + cx * (ay - by))
        val a2 = ax * ax + ay * ay; val b2 = bx * bx + by * by; val c2 = cx * cx + cy * cy
        return P(((a2 * (by - cy) + b2 * (cy - ay) + c2 * (ay - by)) / d).toFloat(), ((a2 * (cx - bx) + b2 * (ax - cx) + c2 * (bx - ax)) / d).toFloat())
    }

    fun centroid(a: P, b: P, c: P) = P((a.x + b.x + c.x) / 3f, (a.y + b.y + c.y) / 3f)

    /** Ortocentrum: H = A + B + C − 2·O (prosta Eulera), dla współliniowych null. */
    fun orthocenter(a: P, b: P, c: P): P? {
        val o = circumcenter(a, b, c) ?: return null
        return P(a.x + b.x + c.x - 2 * o.x, a.y + b.y + c.y - 2 * o.y)
    }

    /** Okrąg wpisany: środek ważony długościami przeciwległych boków, promień = pole / połowa obwodu. */
    fun incircle(a: P, b: P, c: P): Circle? {
        val la = b.dist(c); val lb = c.dist(a); val lc = a.dist(b)
        val per = la + lb + lc
        val area = abs(cross(b - a, c - a)).toFloat() / 2f
        if (per < EPS || nearlyCollinear(a, b, c)) return null
        val i = P((la * a.x + lb * b.x + lc * c.x) / per, (la * a.y + lb * b.y + lc * c.y) / per)
        return Circle(i, 2f * area / per)
    }

    /** Przecięcie prostych (nie odcinków); null dla równoległych. */
    fun intersect(l1: Line, l2: Line): P? {
        val r = l1.b - l1.a; val s = l2.b - l2.a
        val den = cross(r, s)
        if (abs(den) < SIN_EPS * hypot(r.x, r.y) * hypot(s.x, s.y) || abs(den) < EPS) return null
        val t = cross(l2.a - l1.a, s) / den
        return P((l1.a.x + r.x * t).toFloat(), (l1.a.y + r.y * t).toFloat())
    }

    /** Przecięcia prostej z okręgiem (0, 1 albo 2 punkty). */
    fun intersect(l: Line, c: Circle): List<P> {
        val d = l.b - l.a
        val f = l.a - c.c
        val a = d.x.toDouble() * d.x + d.y.toDouble() * d.y
        if (a < EPS) return emptyList()
        val b = 2 * (f.x.toDouble() * d.x + f.y.toDouble() * d.y)
        val cc = f.x.toDouble() * f.x + f.y.toDouble() * f.y - c.r.toDouble() * c.r
        val disc = b * b - 4 * a * cc
        if (disc < -EPS * a) return emptyList()
        val sq = sqrt(maxOf(0.0, disc))
        val t1 = (-b - sq) / (2 * a); val t2 = (-b + sq) / (2 * a)
        val p1 = P((l.a.x + d.x * t1).toFloat(), (l.a.y + d.y * t1).toFloat())
        if (sq < 1e-9) return listOf(p1)
        return listOf(p1, P((l.a.x + d.x * t2).toFloat(), (l.a.y + d.y * t2).toFloat()))
    }

    /** Punkty styczności stycznych z [p] do okręgu [c] (0 — punkt wewnątrz, 1 — na okręgu, 2 — na zewnątrz). */
    fun tangents(p: P, c: Circle): List<P> {
        val d = p.dist(c.c)
        if (d < c.r - 1e-4f) return emptyList()
        if (d <= c.r + 1e-4f) return listOf(p)
        // Punkty styczności to przecięcie z okręgiem o średnicy PO (kąt prosty w punkcie styczności).
        val m = midpoint(p, c.c)
        return intersect(Circle(m, d / 2f), c)
    }

    /** Przecięcia dwóch okręgów (0, 1 albo 2 punkty; współśrodkowe — brak). */
    fun intersect(c1: Circle, c2: Circle): List<P> {
        val d = c1.c.dist(c2.c).toDouble()
        if (d < EPS * maxOf(1f, c1.r, c2.r) || d > c1.r + c2.r + 1e-4 || d < abs(c1.r - c2.r) - 1e-4) return emptyList()
        val a = (c1.r.toDouble() * c1.r - c2.r.toDouble() * c2.r + d * d) / (2 * d)
        val h = sqrt(maxOf(0.0, c1.r.toDouble() * c1.r - a * a))
        val ux = (c2.c.x - c1.c.x) / d; val uy = (c2.c.y - c1.c.y) / d
        val mx = c1.c.x + a * ux; val my = c1.c.y + a * uy
        val p1 = P((mx - h * uy).toFloat(), (my + h * ux).toFloat())
        if (h < 1e-6) return listOf(p1)
        return listOf(p1, P((mx + h * uy).toFloat(), (my - h * ux).toFloat()))
    }

    /** Punkt na odcinku (t ∈ [0, 1] od a do b) i na okręgu (t ∈ [0, 1] pełny obrót od kierunku osi x). */
    fun pointOn(l: Line, t: Float) = l.a + (l.b - l.a) * t
    fun pointOn(c: Circle, t: Float): P { val a = 2 * Math.PI * t; return P(c.c.x + c.r * cos(a).toFloat(), c.c.y + c.r * sin(a).toFloat()) }

    /** Rzut prostopadły punktu na prostą (spodek wysokości). */
    fun project(p: P, l: Line): P {
        val d = l.b - l.a
        val len2 = d.x.toDouble() * d.x + d.y.toDouble() * d.y
        if (len2 < EPS) return l.a
        val t = ((p.x - l.a.x).toDouble() * d.x + (p.y - l.a.y).toDouble() * d.y) / len2
        return P((l.a.x + d.x * t).toFloat(), (l.a.y + d.y * t).toFloat())
    }

    fun reflect(p: P, l: Line): P { val q = project(p, l); return q * 2f - p }
    fun reflect(p: P, center: P): P = center * 2f - p
    fun rotate(p: P, center: P, rad: Float): P {
        val c = cos(rad); val s = sin(rad); val d = p - center
        return P(center.x + d.x * c - d.y * s, center.y + d.x * s + d.y * c)
    }
    fun homothety(p: P, center: P, k: Float): P = center + (p - center) * k
    fun translate(p: P, v: P): P = p + v

    /** Środek odcinka i symetralna (jako odcinek długości [len] przez środek). */
    fun midpoint(a: P, b: P) = P((a.x + b.x) / 2f, (a.y + b.y) / 2f)
    fun bisector(a: P, b: P, len: Float): Line {
        val m = midpoint(a, b); val d = b - a; val l = hypot(d.x, d.y).coerceAtLeast(1e-6f)
        val n = P(-d.y / l, d.x / l)
        return Line(m - n * (len / 2f), m + n * (len / 2f))
    }

    /** Kąt przy wierzchołku [v] między ramionami do [a] i [b] (stopnie, 0–180). */
    fun angleDeg(a: P, v: P, b: P): Float {
        val ux = a.x - v.x; val uy = a.y - v.y; val wx = b.x - v.x; val wy = b.y - v.y
        val l = hypot(ux, uy) * hypot(wx, wy)
        if (l < 1e-9f) return 0f
        return Math.toDegrees(kotlin.math.acos(((ux * wx + uy * wy) / l).toDouble().coerceIn(-1.0, 1.0))).toFloat()
    }

    /** Kąt między dwiema prostymi: wierzchołek (przecięcie) i ramiona do dalszych końców odcinków. */
    class Corner(val v: P, val a: P, val b: P) { val deg: Float get() = angleDeg(a, v, b) }

    /**
     * Kąt, jaki tworzą odcinki [l1] i [l2] (A2-13: odczyt kąta przez zaznaczenie): wierzchołek w przecięciu prostych,
     * ramiona w stronę dalszych końców — dla odcinków ze wspólnym końcem to po prostu kąt między nimi. Null dla równoległych.
     */
    fun corner(l1: Line, l2: Line): Corner? {
        val v = intersect(l1, l2) ?: return null
        fun far(l: Line) = if (l.a.dist(v) >= l.b.dist(v)) l.a else l.b
        return Corner(v, far(l1), far(l2))
    }

    /** Dwusieczna kąta przy wierzchołku [v] między ramionami do [a] i [b] (półprosta długości [len]). */
    fun angleBisector(a: P, v: P, b: P, len: Float): Line {
        val u = (a - v).let { it * (1f / hypot(it.x, it.y).coerceAtLeast(1e-6f)) }
        val w = (b - v).let { it * (1f / hypot(it.x, it.y).coerceAtLeast(1e-6f)) }
        val s = u + w
        val l = hypot(s.x, s.y).coerceAtLeast(1e-6f)
        return Line(v, v + s * (len / l))
    }
}

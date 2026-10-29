package com.mdev.mnotes.core.geom

import com.mdev.mnotes.core.geom.Construct.Circle
import com.mdev.mnotes.core.geom.Construct.Line
import com.mdev.mnotes.core.geom.Construct.P
import com.mdev.mnotes.core.model.Element
import com.mdev.mnotes.core.model.Stroke
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Przyciąganie punktu narzędzia Geometria (3c-B2): punktem konstrukcji może być kropka, wierzchołek, środek odcinka,
 * środek okręgu, przecięcie dwóch kształtów albo punkt na odcinku lub okręgu — nie tylko kropka i wierzchołek jak
 * dawniej. Każdy punkt pamięta, z czego powstał ([Ref]), więc żywa konstrukcja (NAU-3) liczy go na nowo, gdy kształt
 * się przesunie: symetralna zbudowana na przecięciu dwóch okręgów idzie za okręgami.
 */
object GeoSnap {
    /** Rodzaj punktu; kolejność to pierwszeństwo przy równej odległości (kropka przed przecięciem, przecięcie przed środkiem). */
    enum class Kind(val pl: String, val en: String, val weight: Float, val reach: Float = 1f) {
        DOT("punkt", "point", 0.7f),
        VERTEX("wierzchołek", "vertex", 0.7f),
        CROSS("przecięcie", "intersection", 0.85f),
        MID("środek odcinka", "midpoint", 1f),
        CENTER("środek okręgu", "centre", 1f),
        // tylko blisko kształtu (wolny punkt obok odcinka ma zostać wolny) i tylko gdy obok nie ma nic innego
        ON("na kształcie", "on the shape", 1f, 0.5f),
    }

    /**
     * Część elementu, z której wzięto punkt. [part]: -1 kropka (środek), 0… wierzchołek, [CENTER] środek okręgu,
     * [MID] + i środek boku i, [SEG] + i bok i jako prosta, [RING] okrąg jako całość.
     */
    data class Ref(val id: Int, val part: Int)

    const val CENTER = -2
    const val RING = -3
    const val MID = 1_000_000
    const val SEG = 2_000_000

    /**
     * Punkt przyciągania: miejsce [p], rodzaj, odniesienia (jedno; dwa dla przecięcia) i [arg] — które z dwóch przecięć
     * (0/1) albo położenie na kształcie (część boku 0–1, kąt na okręgu w radianach).
     */
    class Snap(val p: P, val kind: Kind, val refs: List<Ref>, val arg: Float = 0f)

    private fun line(s: Stroke, i: Int) = Line(P(s.pts[3 * i], s.pts[3 * i + 1]), P(s.pts[3 * i + 3], s.pts[3 * i + 4]))
    private fun segOk(s: Stroke) = s.poly && !s.ellipse && s.count >= 2

    /** Odcinek albo okrąg wzięty do przecięć i punktów „na kształcie”. */
    private class Shape(val ref: Ref, val line: Line?, val circle: Circle?)

    private fun segDist(p: P, l: Line) = sqrt(GeomMath.distSqPointSegment(p.x, p.y, l.a.x, l.a.y, l.b.x, l.b.y))

    /**
     * Najlepszy punkt pod ([x], [y]) w zasięgu [tol] (pt) wśród elementów strony [els]; null — w zasięgu nic nie ma
     * (wtedy punkt to samo miejsce dotknięcia). Wygrywa najmniejsza odległość ważona rodzajem ([Kind.weight]), więc
     * kropka minimalnie dalej wygrywa ze środkiem odcinka, a „na kształcie” tylko wtedy, gdy w zasięgu nie ma żadnego
     * innego punktu (inaczej przy wierzchołku wygrywałby rzut na bok, zawsze bliższy).
     */
    fun find(els: List<Element>, x: Float, y: Float, tol: Float, on: Boolean = true): Snap? {
        val t = P(x, y)
        var best: Snap? = null; var bs = Float.MAX_VALUE
        fun offer(s: Snap) {
            val d = s.p.dist(t)
            if (d > tol * s.kind.reach) return
            // zapas 5 % zasięgu: dwa punkty w tym samym miejscu (środek odcinka = przecięcie przekątnych) rozstrzyga rodzaj
            val sc = (d + 0.05f * tol) * s.kind.weight
            if (sc < bs) { bs = sc; best = s }
        }
        val near = ArrayList<Shape>()
        for (e in els.asReversed()) {
            val s = e as? Stroke ?: continue
            if (!s.bounds.inflate(tol).contains(x, y)) continue
            if (GeoTool.isDot(s)) { offer(Snap(P(s.bounds.cx, s.bounds.cy), Kind.DOT, listOf(Ref(s.id, -1)))); continue }
            val c = GeoTool.circleOf(s)
            if (c != null) {
                offer(Snap(c.c, Kind.CENTER, listOf(Ref(s.id, CENTER))))
                if (kotlin.math.abs(c.c.dist(t) - c.r) <= tol) near += Shape(Ref(s.id, RING), null, c)
                continue
            }
            if (!segOk(s)) continue
            val last = s.count - 1
            for (i in 0..last) {
                // wierzchołek zamykający (powtórzony pierwszy) to ten sam punkt — pomijamy go
                if (i == last && s.closed) break
                offer(Snap(P(s.pts[3 * i], s.pts[3 * i + 1]), Kind.VERTEX, listOf(Ref(s.id, i))))
            }
            for (i in 0 until last) {
                val l = line(s, i)
                if (l.a.dist(l.b) < 1f) continue
                offer(Snap(Construct.midpoint(l.a, l.b), Kind.MID, listOf(Ref(s.id, MID + i))))
                if (segDist(t, l) <= tol) near += Shape(Ref(s.id, SEG + i), l, null)
            }
        }
        // Przecięcia tylko kształtów przechodzących w zasięgu: punkt wspólny leży na obu, więc oba muszą być blisko.
        for (i in near.indices) for (j in i + 1 until near.size) {
            val a = near[i]; val b = near[j]
            if (a.ref.id == b.ref.id && a.line != null && b.line != null && kotlin.math.abs(a.ref.part - b.ref.part) == 1) continue
            crossings(a, b).forEachIndexed { k, p -> if (inside(a, p, tol) && inside(b, p, tol)) offer(Snap(p, Kind.CROSS, listOf(a.ref, b.ref), k.toFloat())) }
        }
        if (on && best == null) for (sh in near) {
            val l = sh.line
            if (l != null) {
                val len2 = (l.b.x - l.a.x) * (l.b.x - l.a.x) + (l.b.y - l.a.y) * (l.b.y - l.a.y)
                val u = (((t.x - l.a.x) * (l.b.x - l.a.x) + (t.y - l.a.y) * (l.b.y - l.a.y)) / len2).coerceIn(0f, 1f)
                offer(Snap(Construct.pointOn(l, u), Kind.ON, listOf(sh.ref), u))
            } else {
                val c = sh.circle!!
                val a = atan2(t.y - c.c.y, t.x - c.c.x)
                offer(Snap(P(c.c.x + c.r * kotlin.math.cos(a), c.c.y + c.r * kotlin.math.sin(a)), Kind.ON, listOf(sh.ref), a))
            }
        }
        return best
    }

    private fun crossings(a: Shape, b: Shape): List<P> = when {
        a.line != null && b.line != null -> listOfNotNull(Construct.intersect(a.line, b.line))
        a.line != null -> Construct.intersect(a.line, b.circle!!)
        b.line != null -> Construct.intersect(b.line, a.circle!!)
        else -> Construct.intersect(a.circle!!, b.circle!!)
    }

    /** Przecięcie odcinków liczy się tylko na odcinkach (z małym zapasem), nie na ich przedłużeniach daleko obok. */
    private fun inside(s: Shape, p: P, tol: Float): Boolean = s.line == null || segDist(p, s.line) <= tol * 0.25f

    /** Kształt [ref] z bieżącej wersji elementu [el]; null — element nie jest już tym kształtem. */
    private fun shapeOf(el: Element, ref: Ref): Shape? {
        val s = el as? Stroke ?: return null
        return when {
            ref.part == RING -> GeoTool.circleOf(s)?.let { Shape(ref, null, it) }
            ref.part >= SEG -> { val i = ref.part - SEG; if (segOk(s) && i + 1 < s.count) Shape(ref, line(s, i), null) else null }
            else -> null
        }
    }

    /**
     * NAU-3: punkt [snap] policzony na nowo z bieżących elementów [byId]; null — któregoś elementu brak albo zmienił
     * się w coś innego (zależność się zrywa). Przecięcie, które zniknęło (okręgi się rozeszły), też daje null.
     */
    fun resolve(snap: Snap, byId: Map<Int, Element>): P? {
        val r0 = snap.refs[0]
        val el = byId[r0.id] ?: return null
        return when (snap.kind) {
            Kind.CROSS -> {
                val a = shapeOf(el, r0) ?: return null
                val b = shapeOf(byId[snap.refs[1].id] ?: return null, snap.refs[1]) ?: return null
                val ps = crossings(a, b)
                ps.getOrNull(snap.arg.toInt()) ?: ps.firstOrNull()
            }
            Kind.ON -> {
                val sh = shapeOf(el, r0) ?: return null
                sh.line?.let { Construct.pointOn(it, snap.arg) } ?: sh.circle!!.let { c -> P(c.c.x + c.r * kotlin.math.cos(snap.arg), c.c.y + c.r * kotlin.math.sin(snap.arg)) }
            }
            Kind.CENTER -> GeoTool.circleOf(el as? Stroke ?: return null)?.c
            Kind.MID -> {
                val s = el as? Stroke ?: return null; val i = r0.part - MID
                if (segOk(s) && i in 0 until s.count - 1) line(s, i).let { Construct.midpoint(it.a, it.b) } else null
            }
            Kind.DOT, Kind.VERTEX -> (GeoTool.resolve(GeoTool.In.POINT, el, r0.part) as? GeoTool.Input.Point)?.p
        }
    }
}

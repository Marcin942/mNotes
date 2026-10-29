package com.mdev.mnotes.core.geom

import com.mdev.mnotes.core.edit.EraseMath
import com.mdev.mnotes.core.geom.Construct.Circle
import com.mdev.mnotes.core.geom.Construct.Line
import com.mdev.mnotes.core.geom.Construct.P
import com.mdev.mnotes.core.model.BoxElement
import com.mdev.mnotes.core.model.Element
import com.mdev.mnotes.core.model.Ids
import com.mdev.mnotes.core.model.Stroke
import com.mdev.mnotes.core.shapes.ShapeBox
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

/**
 * Narzędzie „Geometria” (2b-A8): konstrukcje jak cyrklem i linijką. Wybiera się konstrukcję, potem wskazuje na stronie jej
 * wejścia po kolei (okrąg, odcinek, punkt…) z podpowiedzią „wskaż drugi okrąg”. Wynik to zwykłe elementy (pomocnicze
 * przerywane, nazwy punktów jak w A2-9) z tego samego kodu co menu „Konstrukcja ▸” ([ConstructOps]).
 */
object GeoTool {
    enum class In { POINT, LINE, CIRCLE, TRIANGLE, OBJECT }

    /** Liczba dobierana chipami nad płótnem: kąt (°), liczba części odcinka, liczba boków, skala jednokładności. */
    enum class Param { NONE, ANGLE, PARTS, SIDES, SCALE }

    /** [group]: grupa na listwie (okręgi i punkty, proste i kąty, przecięcia, trójkąt i wielokąty, przekształcenia). */
    enum class Kind(val inputs: List<In>, val pl: String, val en: String, val group: Int, val param: Param = Param.NONE) {
        COMPASS(listOf(In.POINT, In.POINT), "Okrąg: środek i punkt na nim", "Circle: centre and a point on it", 0),
        CIRCLE3(listOf(In.POINT, In.POINT, In.POINT), "Okrąg przez 3 punkty", "Circle through 3 points", 0),
        COPY_RADIUS(listOf(In.LINE, In.POINT), "Okrąg o promieniu odcinka (przeniesienie cyrklem)", "Circle with a segment's radius (compass transfer)", 0),
        CENTER(listOf(In.CIRCLE), "Środek okręgu", "Circle centre", 0),
        DIVIDE(listOf(In.LINE), "Podział odcinka na równe części", "Divide a segment into equal parts", 0, Param.PARTS),
        SEGMENT(listOf(In.POINT, In.POINT), "Odcinek przez dwa punkty", "Segment through two points", 1),
        BISECTOR(listOf(In.LINE), "Symetralna odcinka", "Perpendicular bisector", 1),
        PERP(listOf(In.LINE, In.POINT), "Prostopadła przez punkt", "Perpendicular through a point", 1),
        PARALLEL(listOf(In.LINE, In.POINT), "Równoległa przez punkt", "Parallel through a point", 1),
        TANGENT_AT(listOf(In.CIRCLE, In.POINT), "Styczna w punkcie okręgu", "Tangent at a point of a circle", 1),
        ANGLE(listOf(In.LINE, In.LINE), "Kąt: łuk i miara", "Angle: arc and value", 1),
        ANGLE_AT(listOf(In.LINE, In.POINT), "Kąt o danej mierze przy punkcie", "Angle of a given size at a point", 1, Param.ANGLE),
        ANGLE_BISECTOR(listOf(In.LINE, In.LINE), "Dwusieczna kąta", "Angle bisector", 1),
        XLL(listOf(In.LINE, In.LINE), "Przecięcie prostych", "Intersection of lines", 2),
        XLC(listOf(In.LINE, In.CIRCLE), "Przecięcie prostej i okręgu", "Line–circle intersection", 2),
        XCC(listOf(In.CIRCLE, In.CIRCLE), "Przecięcie okręgów i prosta przez punkty", "Circle intersection and the line through it", 2),
        TANGENTS(listOf(In.POINT, In.CIRCLE), "Styczne z punktu do okręgu", "Tangents from a point to a circle", 2),
        CIRCUM(listOf(In.TRIANGLE), "Okrąg opisany", "Circumcircle", 3),
        INCIRCLE(listOf(In.TRIANGLE), "Okrąg wpisany", "Incircle", 3),
        ORTHO(listOf(In.TRIANGLE), "Wysokości i ortocentrum", "Altitudes and orthocentre", 3),
        CENTROID(listOf(In.TRIANGLE), "Środkowe i środek ciężkości", "Medians and centroid", 3),
        REGULAR(listOf(In.LINE), "Wielokąt foremny na odcinku", "Regular polygon on a segment", 3, Param.SIDES),
        REFLECT(listOf(In.LINE, In.OBJECT), "Odbicie względem prostej", "Reflection across a line", 4),
        ROTATE(listOf(In.POINT, In.OBJECT), "Obrót wokół punktu", "Rotation about a point", 4, Param.ANGLE),
        PREFLECT(listOf(In.POINT, In.OBJECT), "Odbicie względem punktu", "Reflection through a point", 4),
        TRANSLATE(listOf(In.POINT, In.POINT, In.OBJECT), "Przesunięcie o wektor", "Translation by a vector", 4),
        HOMOTHETY(listOf(In.POINT, In.OBJECT), "Jednokładność (skala)", "Dilation (scale)", 4, Param.SCALE),
    }

    /**
     * Liczby konstrukcji z [Param]: kąt w stopniach (obrót, kąt przy punkcie), liczba części odcinka, liczba boków
     * wielokąta, skala jednokładności (ujemna — po drugiej stronie środka).
     */
    data class Params(val angle: Float = 90f, val parts: Int = 2, val sides: Int = 6, val scale: Float = 2f)

    const val MAX_PARTS = 12
    const val MAX_SIDES = 12

    /** Wskazane wejście: punkt (istniejący albo dowolne miejsce), prosta, okrąg, trójkąt albo obiekt do przekształcenia. */
    sealed class Input {
        class Point(val p: P) : Input()
        class Ln(val l: Line) : Input()
        class Circ(val c: Circle) : Input()
        class Tri(val a: P, val b: P, val c: P) : Input()
        class Obj(val el: Element) : Input()
    }

    /** Podpowiedź do kroku [step] konstrukcji [k] („Wskaż drugi okrąg”). */
    /** 2c-A8: dymek przy chybieniu — czego pod palcem zabrakło, a potem zwykła podpowiedź kroku. */
    fun missHint(k: Kind, step: Int, pl: Boolean = true): String {
        val miss = when (k.inputs.getOrNull(step)) {
            In.LINE -> if (pl) "Tu nie ma odcinka ani prostej" else "No segment or line here"
            In.CIRCLE -> if (pl) "Tu nie ma okręgu (narysuj go i przytrzymaj pióro)" else "No circle here (draw one and hold the pen)"
            In.TRIANGLE -> if (pl) "Tu nie ma trójkąta (narysuj go i przytrzymaj pióro)" else "No triangle here (draw one and hold the pen)"
            In.OBJECT -> if (pl) "Tu nie ma obiektu" else "No object here"
            In.POINT, null -> return hint(k, step, pl)
        }
        return "$miss · ${hint(k, step, pl)}"
    }

    fun hint(k: Kind, step: Int, pl: Boolean = true): String {
        val want = k.inputs.getOrNull(step) ?: return if (pl) "Gotowe" else "Done"
        // 3c-B2: wejścia, których rola nie wynika z typu („punkt” to tu środek, wierzchołek albo koniec wektora)
        val own = when (k to step) {
            Kind.COPY_RADIUS to 0 -> "Wskaż odcinek — promień" to "Pick the segment — the radius"
            Kind.COPY_RADIUS to 1 -> "Wskaż środek okręgu" to "Pick the centre"
            Kind.TANGENT_AT to 1 -> "Wskaż punkt na okręgu" to "Pick a point on the circle"
            Kind.ANGLE_AT to 0 -> "Wskaż ramię kąta (odcinek)" to "Pick the arm (a segment)"
            Kind.ANGLE_AT to 1 -> "Wskaż wierzchołek kąta" to "Pick the vertex"
            Kind.TRANSLATE to 0 -> "Wskaż początek wektora" to "Pick where the vector starts"
            Kind.TRANSLATE to 1 -> "Wskaż koniec wektora" to "Pick where the vector ends"
            Kind.HOMOTHETY to 0, Kind.ROTATE to 0, Kind.PREFLECT to 0 -> "Wskaż środek przekształcenia" to "Pick the centre"
            else -> null
        }
        if (own != null) return if (pl) own.first else own.second
        val nth = k.inputs.take(step).count { it == want }
        val ord = if (pl) listOf("", "drugi ", "trzeci ") else listOf("", "second ", "third ")
        val o = ord.getOrElse(nth) { "" }
        return when (want) {
            In.POINT -> if (pl) "Wskaż ${o}punkt (albo dotknij miejsca)" else "Pick the ${o}point (or tap a spot)"
            In.LINE -> if (pl) "Wskaż ${if (nth == 0) "" else o}odcinek albo prostą" else "Pick the ${o}segment or line"
            In.CIRCLE -> if (pl) "Wskaż ${o}okrąg" else "Pick the ${o}circle"
            In.TRIANGLE -> if (pl) "Wskaż trójkąt" else "Pick the triangle"
            In.OBJECT -> if (pl) "Wskaż obiekt do przekształcenia" else "Pick the object to transform"
        }.replace("  ", " ")
    }

    internal fun circleOf(e: Stroke): Circle? {
        if (!e.ellipse) return null
        val f = ShapeBox.frameOf(e) ?: return null
        return if (abs(f.hw - f.hh) < 0.08f * max(f.hw, f.hh)) Circle(P(f.cx, f.cy), (f.hw + f.hh) / 2f) else null
    }

    internal fun isDot(e: Stroke) = e.count >= 1 && e.bounds.w < 6f + e.width && e.bounds.h < 6f + e.width

    private fun segDist(p: P, a: P, b: P) = kotlin.math.sqrt(GeomMath.distSqPointSegment(p.x, p.y, a.x, a.y, b.x, b.y))

    /**
     * Wejście typu [want] pod dotknięciem ([x], [y]) w tolerancji [tol] (pt) wśród elementów strony [els] (najwyżej leżące
     * pierwsze). Punkt: kropka albo wierzchołek w zasięgu, inaczej samo miejsce dotknięcia; null — nic pasującego.
     */
    fun pick(els: List<Element>, x: Float, y: Float, tol: Float, want: In): Input? = pickFrom(els, x, y, tol, want)?.input

    /**
     * NAU-3: wskazane wejście razem z elementem, z którego je odczytano ([id]; null — wolne miejsce) i jego częścią
     * ([part]: wierzchołek punktu, początek boku prostej; -1 — cały element). Z tego [resolve] odczyta wejście ponownie,
     * gdy element się przesunie.
     */
    class Picked(val input: Input, val id: Int?, val part: Int, val snap: GeoSnap.Snap? = null) {
        /** Elementy, od których wejście zależy (przecięcie — dwa); puste — wolne miejsce strony. */
        val ids: List<Int> get() = snap?.refs?.map { it.id }?.distinct() ?: listOfNotNull(id)
    }

    /**
     * NAU-3: wejście [pk] (typu [want]) odczytane z bieżących elementów [byId]; wolne miejsce zostaje, jak było. Null —
     * zależność się zerwała (brak elementu, okrąg rozciągnięty w elipsę, przecięcie zniknęło).
     */
    fun resolvePick(want: In, pk: Picked, byId: Map<Int, Element>): Input? {
        pk.snap?.let { s -> return GeoSnap.resolve(s, byId)?.let { Input.Point(it) } }
        val id = pk.id ?: return pk.input
        return resolve(want, byId[id] ?: return null, pk.part)
    }

    fun pickFrom(els: List<Element>, x: Float, y: Float, tol: Float, want: In): Picked? {
        val t = P(x, y)
        val strokes = els.asReversed().filterIsInstance<Stroke>()
        when (want) {
            // 3c-B2: kropka, wierzchołek, środek odcinka lub okręgu, przecięcie albo punkt na kształcie ([GeoSnap]).
            In.POINT -> {
                val s = GeoSnap.find(els, x, y, tol) ?: return Picked(Input.Point(t), null, -1)
                return Picked(Input.Point(s.p), s.refs[0].id, s.refs[0].part, s)
            }
            In.LINE -> {
                var best: Line? = null; var bd = tol; var from: Stroke? = null; var part = -1
                for (s in strokes) if (s.poly && !s.ellipse && s.count >= 2) for (i in 0 until s.count - 1) {
                    val a = P(s.pts[3 * i], s.pts[3 * i + 1]); val b = P(s.pts[3 * i + 3], s.pts[3 * i + 4])
                    if (a.dist(b) < 1f) continue
                    val d = segDist(t, a, b)
                    if (d <= bd) { bd = d; best = Line(a, b); from = s; part = i }
                }
                return best?.let { Picked(Input.Ln(it), from?.id, part) }
            }
            In.CIRCLE -> {
                var best: Stroke? = null; var bd = tol
                for (s in strokes) { val c = circleOf(s) ?: continue; val d = abs(c.c.dist(t) - c.r); if (d <= bd) { bd = d; best = s } }
                // Dotknięcie w środku okręgu też go wskazuje (najmniejszy, który zawiera punkt).
                if (best == null) best = strokes.filter { s -> circleOf(s)?.let { it.c.dist(t) < it.r } == true }.minByOrNull { circleOf(it)!!.r }
                return best?.let { s -> Picked(Input.Circ(circleOf(s)!!), s.id, -1) }
            }
            In.TRIANGLE -> {
                for (s in strokes) {
                    val tr = triangleOf(s) ?: continue
                    val near = segDist(t, tr.a, tr.b) <= tol || segDist(t, tr.b, tr.c) <= tol || segDist(t, tr.c, tr.a) <= tol
                    if (near || inside(t, tr.a, tr.b, tr.c)) return Picked(tr, s.id, -1)
                }
                return null
            }
            In.OBJECT -> {
                for (e in els.asReversed()) {
                    if (e.locked || !e.bounds.inflate(tol).contains(x, y)) continue
                    val hit = if (e is BoxElement) e.containsPoint(x, y, tol) else EraseMath.hitsElement(e, x, y, tol)
                    if (hit) return Picked(Input.Obj(e), e.id, -1)
                }
                return null
            }
        }
    }

    private fun triangleOf(s: Stroke): Input.Tri? {
        if (!s.poly || s.ellipse || s.vertexCount != 3 || !s.closed) return null
        return Input.Tri(P(s.pts[0], s.pts[1]), P(s.pts[3], s.pts[4]), P(s.pts[6], s.pts[7]))
    }

    /**
     * NAU-3: wejście typu [want] odczytane na nowo z bieżącej wersji elementu [el] (część [part] jak w [Picked]);
     * null — element nie jest już tym, czym był (okrąg rozciągnięty w elipsę, brak wierzchołka), zależność się zrywa.
     */
    fun resolve(want: In, el: Element, part: Int): Input? {
        if (want == In.OBJECT) return Input.Obj(el)
        val s = el as? Stroke ?: return null
        return when (want) {
            In.POINT -> when {
                part < 0 -> if (isDot(s)) Input.Point(P(s.bounds.cx, s.bounds.cy)) else null
                part < s.count -> Input.Point(P(s.pts[3 * part], s.pts[3 * part + 1]))
                else -> null
            }
            In.LINE -> if (s.poly && !s.ellipse && part >= 0 && part + 1 < s.count) Input.Ln(Line(P(s.pts[3 * part], s.pts[3 * part + 1]), P(s.pts[3 * part + 3], s.pts[3 * part + 4]))) else null
            In.CIRCLE -> circleOf(s)?.let { Input.Circ(it) }
            In.TRIANGLE -> triangleOf(s)
            In.OBJECT -> Input.Obj(el)
        }
    }

    private fun inside(p: P, a: P, b: P, c: P): Boolean {
        fun s(u: P, v: P) = (v.x - u.x) * (p.y - u.y) - (v.y - u.y) * (p.x - u.x)
        val d1 = s(a, b); val d2 = s(b, c); val d3 = s(c, a)
        return (d1 >= 0 && d2 >= 0 && d3 >= 0) || (d1 <= 0 && d2 <= 0 && d3 <= 0)
    }

    /** Powód braku wyniku (albo wyniku daleko poza stroną) dla dymku, jak [ConstructOps.failOf] (2c-A8). */
    fun failOf(k: Kind): ConstructOps.Fail = when (k) {
        Kind.XLL -> ConstructOps.Fail.PARALLEL
        Kind.CIRCLE3, Kind.CIRCUM, Kind.INCIRCLE, Kind.ORTHO -> ConstructOps.Fail.COLLINEAR
        else -> ConstructOps.Fail.NONE
    }

    /** Wynik konstrukcji [k] z wejść [ins] w kolorze [col] i grubości [w]; pusta lista — konstrukcja nie ma rozwiązania. */
    fun build(k: Kind, ins: List<Input>, col: Int, w: Float, names: Boolean = true, pl: Boolean = true, angle: Float = 90f, prm: Params = Params(angle = angle)): List<Element> {
        fun pt(i: Int) = (ins[i] as Input.Point).p
        fun ln(i: Int) = (ins[i] as Input.Ln).l
        fun cir(i: Int) = (ins[i] as Input.Circ).c
        fun tri() = ins[0] as Input.Tri
        fun obj(i: Int) = (ins[i] as Input.Obj).el
        val deg = prm.angle
        return when (k) {
            Kind.COPY_RADIUS -> {
                val l = ln(0); val c = pt(1); val r = l.a.dist(l.b)
                if (r < 1f) emptyList() else ConstructOps.compose(col, w, names, listOf(l)) { circle(Circle(c, r)); dot(c, "O") }
            }
            Kind.DIVIDE -> {
                val l = ln(0); val n = prm.parts.coerceIn(2, MAX_PARTS)
                if (l.a.dist(l.b) < 1f) emptyList() else ConstructOps.compose(col, w, names, listOf(l)) {
                    for (j in 1 until n) dot(Construct.pointOn(l, j / n.toFloat()), if (n == 2) "M" else null)
                }
            }
            Kind.SEGMENT -> { val a = pt(0); val b = pt(1); if (a.dist(b) < 1f) emptyList() else ConstructOps.compose(col, w, names) { line(Line(a, b), false) } }
            Kind.TANGENT_AT -> {
                val c = cir(0); val d = pt(1) - c.c; val len = hypot(d.x, d.y)
                if (len < 1e-3f || c.r < 1f) emptyList() else {
                    // punkt obok okręgu rzutujemy na okrąg (styczna jest w punkcie okręgu najbliższym wskazaniu)
                    val q = c.c + d * (c.r / len); val n = P(-d.y / len, d.x / len); val half = max(c.r * 1.2f, 30f)
                    ConstructOps.compose(col, w, names) { around(c); line(Line(c.c, q), true); line(Line(q - n * half, q + n * half), false); dot(q, "T") }
                }
            }
            Kind.ANGLE_AT -> {
                val l = ln(0); val v = pt(1)
                val far = if (l.a.dist(v) >= l.b.dist(v)) l.a else l.b
                val u = far.dist(v)
                if (u < 1f || abs(deg) < 0.01f || abs(deg) >= 360f) emptyList() else {
                    // y w dół: obrót o −α na ekranie idzie przeciwnie do wskazówek zegara, jak kąt na kartce
                    val rot = Construct.rotate(far, v, (-deg * PI / 180).toFloat())
                    val end = v + (rot - v) * (max(u, 40f) / u)
                    ConstructOps.compose(col, w, names) { line(Line(v, end), false) } + ConstructOps.angleMark(Construct.Corner(v, far, end), col, w, pl)
                }
            }
            Kind.REGULAR -> {
                val l = ln(0); val n = prm.sides.coerceIn(3, MAX_SIDES)
                if (l.a.dist(l.b) < 1f) emptyList() else {
                    // od boku AB z obrotem o kąt zewnętrzny 360°/n — wielokąt nad odcinkiem (dla A z lewej, B z prawej)
                    val ext = (-2 * PI / n).toFloat()
                    val ps = ArrayList<P>(n).apply { add(l.a); add(l.b) }
                    var dir = l.b - l.a
                    while (ps.size < n) { dir = Construct.rotate(dir, P(0f, 0f), ext); ps += ps.last() + dir }
                    ConstructOps.compose(col, w, names) { poly(ps) }
                }
            }
            Kind.TRANSLATE -> { val v = pt(1) - pt(0); if (hypot(v.x, v.y) < 0.5f) emptyList() else listOf(obj(2).transformed(Affine.translation(v.x, v.y)).withId(Ids.next())) }
            Kind.HOMOTHETY -> { val c = pt(0); val s = prm.scale; if (abs(s) < 1e-3f) emptyList() else listOf(obj(1).transformed(Affine.scale(s, s, c.x, c.y)).withId(Ids.next())) }
            Kind.COMPASS -> {
                val c = pt(0); val r = c.dist(pt(1))
                if (r < 1f) emptyList() else ConstructOps.compose(col, w, names) { circle(Circle(c, r)); dot(c, "O") }
            }
            Kind.CIRCLE3 -> Construct.circleThrough(pt(0), pt(1), pt(2))?.let { cc ->
                ConstructOps.compose(col, w, names) { circle(cc); dot(cc.c, "O"); name(pt(0), "A", 4f); name(pt(1), "B", 4f); name(pt(2), "C", 4f) }
            } ?: emptyList()
            Kind.CENTER -> ConstructOps.compose(col, w, names) { around(cir(0)); dot(cir(0).c, "O") }
            Kind.BISECTOR -> { val l = ln(0); ConstructOps.compose(col, w, names, listOf(l)) { dot(Construct.midpoint(l.a, l.b), "M"); line(Construct.bisector(l.a, l.b, l.a.dist(l.b)), true) } }
            Kind.PERP -> {
                val l = ln(0); val p = pt(1)
                val f = Construct.project(p, l)
                val d = l.b - l.a; val len = hypot(d.x, d.y).coerceAtLeast(1e-6f)
                val n = P(-d.y / len, d.x / len)
                val half = max(p.dist(f) * 1.2f, len * 0.5f)
                ConstructOps.compose(col, w, names, listOf(l)) { line(Line(f - n * half, f + n * half), true); dot(f, "F") }
            }
            Kind.PARALLEL -> {
                val l = ln(0); val p = pt(1)
                val d = l.b - l.a
                ConstructOps.compose(col, w, names, listOf(l)) { line(Line(p - d * 0.6f, p + d * 0.6f), true) }
            }
            Kind.ANGLE -> Construct.corner(ln(0), ln(1))?.let { ConstructOps.angleMark(it, col, w, pl) } ?: emptyList()
            Kind.ANGLE_BISECTOR -> Construct.corner(ln(0), ln(1))?.let { cr ->
                val len = max(cr.a.dist(cr.v), cr.b.dist(cr.v))
                ConstructOps.compose(col, w, names, listOf(ln(0), ln(1))) { line(Construct.angleBisector(cr.a, cr.v, cr.b, len), true) } + ConstructOps.angleMark(cr, col, w, pl)
            } ?: emptyList()
            Kind.XLL -> Construct.intersect(ln(0), ln(1))?.let { p -> ConstructOps.compose(col, w, names, listOf(ln(0), ln(1))) { dot(p, "P") } } ?: emptyList()
            Kind.XLC -> {
                val ps = Construct.intersect(ln(0), cir(1))
                if (ps.isEmpty()) emptyList() else ConstructOps.compose(col, w, names, listOf(ln(0))) { around(cir(1)); ps.forEachIndexed { i, p -> dot(p, listOf("P", "Q")[i]) } }
            }
            Kind.XCC -> {
                val ps = Construct.intersect(cir(0), cir(1))
                if (ps.isEmpty()) emptyList() else ConstructOps.compose(col, w, names) {
                    around(cir(0)); around(cir(1))
                    if (ps.size == 2) { val d = ps[1] - ps[0]; line(Line(ps[0] - d * 0.35f, ps[1] + d * 0.35f), false) }
                    ps.forEachIndexed { i, p -> dot(p, listOf("P", "Q")[i]) }
                }
            }
            Kind.CIRCUM, Kind.INCIRCLE, Kind.ORTHO, Kind.CENTROID -> {
                val t = tri()
                val id = when (k) { Kind.CIRCUM -> "circum"; Kind.INCIRCLE -> "in"; Kind.ORTHO -> "altitudes"; else -> "medians" }
                val tr = com.mdev.mnotes.core.model.Stroke(Ids.next(), com.mdev.mnotes.core.model.PenKind.BALLPOINT, 0, 1f,
                    floatArrayOf(t.a.x, t.a.y, 1f, t.b.x, t.b.y, 1f, t.c.x, t.c.y, 1f, t.a.x, t.a.y, 1f), poly = true)
                ConstructOps.ops(listOf(tr), names, pl).firstOrNull { it.id == id }?.build?.invoke(col, w) ?: emptyList()
            }
            Kind.REFLECT -> listOf((ins[1] as Input.Obj).el.transformed(ConstructOps.reflection(ln(0))).withId(Ids.next()))
            Kind.ROTATE -> { val c = pt(0); listOf((ins[1] as Input.Obj).el.transformed(Affine.rotation((deg * PI / 180).toFloat(), c.x, c.y)).withId(Ids.next())) }
            Kind.PREFLECT -> { val c = pt(0); listOf((ins[1] as Input.Obj).el.transformed(Affine.scale(-1f, -1f, c.x, c.y)).withId(Ids.next())) }
            Kind.TANGENTS -> {
                val p = pt(0); val c = cir(1)
                val ts = Construct.tangents(p, c)
                if (ts.size < 2) emptyList() else ConstructOps.compose(col, w, names) {
                    around(c)
                    for ((i, t) in ts.withIndex()) { line(Line(p, p + (t - p) * 1.3f), false); dot(t, listOf("P", "Q")[i]) }
                }
            }
        }
    }
}

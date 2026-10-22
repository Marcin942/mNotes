package com.mdev.mnotes.core.edit

import com.mdev.mnotes.core.geom.GeomMath
import com.mdev.mnotes.core.model.BoxElement
import com.mdev.mnotes.core.model.Element
import com.mdev.mnotes.core.model.FillEl
import com.mdev.mnotes.core.model.ImageEl
import com.mdev.mnotes.core.model.PenKind
import com.mdev.mnotes.core.model.Stroke
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Wiaderko (narzędzie „Wypełnienie”): jak w Paincie, ale na wektorach. Strona jest rasteryzowana do siatki,
 * kreski i ramki są ścianami, a obszar spójny z punktem dotknięcia zamienia się z powrotem w kontury
 * (pierścienie do reguły parzysto-nieparzystej: wyspy wewnątrz obszaru wychodzą jako dziury).
 * Krawędź strony też jest ścianą, więc dotknięcie w nieograniczonym miejscu wypełnia całą stronę poza zamkniętymi kształtami.
 *
 * Z domykaniem szpar ściany są pogrubione o [wallPad], więc drobne niedomknięcia nie przeciekają. Pogrubione ściany
 * wyznaczają tylko, KTÓRY obszar dotknięto; pas zapasu (wolny od prawdziwych linii, zajęty przez pogrubienie) jest
 * potem rozdzielany między sąsiednie obszary według odległości. Dzięki temu wypełnienie dochodzi do samych linii także
 * w ostrych kątach i zakamarkach, a przez szparę nie wychodzi otoczka na zewnątrz kształtu.
 */
object FloodFill {

    /** Docelowa liczba komórek na dłuższym boku strony. */
    const val CELLS_LONG_SIDE = 1200
    const val MAX_CELLS = 2_000_000


    /** O tyle komórek wypełnienie wchodzi pod linie, żeby między nim a kreską nie było szczeliny (kreska leży nad nim). */
    private const val UNDERLAP = 3

    /**
     * Wąska szczelina między dwiema liniami (ostry klin) bywa węższa niż komórka: nie ma w niej środka komórki, więc zwykły
     * obszar się w niej kończy. Wypełnienie idzie wtedy dalej po brzegowych komórkach kresek (dalej niż [UNDERLAP]), ale
     * nie głębiej w grubą kreskę, bo tam nie ma papieru do pokrycia.
     */
    private const val SLIVER_REACH = 24

    /** Wolna kępa poza rdzeniem nie większa niż tyle komórek jest ziarnem rastra, a nie osobnym obszarem. */
    private const val DUST_CELLS = 40

    /** Odchylenie kanału (0..255) od bieli albo czerni, przy którym zakreślacz jeszcze uznajemy za niewidoczny. */
    private const val INVISIBLE_TOL = 4

    /** Rozstaw próbek przy wygładzaniu konturu, w komórkach. */
    private const val SMOOTH_STEP = 0.5f

    /** Odchylenie standardowe wygładzania, w próbkach (3 próbki = 1,5 komórki). */
    private const val SMOOTH_SIGMA = 3f

    /** Najdalsze przesunięcie wierzchołka przez wygładzanie, w komórkach: mniej niż [UNDERLAP], więc skuteczność się nie zmienia. */
    private const val SMOOTH_MAX_SHIFT = 0.6f

    /**
     * Kontury obszaru wokół (x, y) na stronie [pageW]×[pageH] (pt). Null, gdy punkt leży poza stroną albo na linii
     * (na linii nie ma czego wypełniać). Istniejące wypełnienia i zablokowane obrazy (tło strony) nie są ścianami.
     * [closeGaps] domyka szpary węższe niż ok. 2 pt. [darkPaper] mówi, jak strona rysuje zakreślacz (patrz [invisible]).
     */
    fun fill(elements: List<Element>, pageW: Float, pageH: Float, x: Float, y: Float, closeGaps: Boolean = false, darkPaper: Boolean = false, maxCells: Int = MAX_CELLS, longSide: Int = CELLS_LONG_SIDE): List<FloatArray>? {
        if (pageW <= 0f || pageH <= 0f || x < 0f || y < 0f || x >= pageW || y >= pageH) return null
        var cell = max(pageW, pageH) / longSide
        cell = max(cell, sqrt(pageW * pageH / maxCells))
        val gw = ceil(pageW / cell).toInt().coerceAtLeast(1)
        val gh = ceil(pageH / cell).toInt().coerceAtLeast(1)
        val wall = BooleanArray(gw * gh)
        @Suppress("NAME_SHADOWING") val elements = elements.filterNot { invisible(it, darkPaper) }
        for (e in elements) rasterize(e, wall, gw, gh, cell, 0f)
        // Komórki głęboko pod grubą kreską: tam wypełnienie nie szuka szczelin (patrz SLIVER_REACH).
        val deep = BooleanArray(gw * gh)
        for (e in elements) rasterize(e, deep, gw, gh, cell, 0f, inset = cell)

        val sx = min(gw - 1, (x / cell).toInt()); val sy = min(gh - 1, (y / cell).toInt())
        val s = sy * gw + sx
        if (wall[s]) return null
        val region = if (!closeGaps) flood(wall, gw, gh, s) else {
            val thick = wall.copyOf()
            for (e in elements) rasterize(e, thick, gw, gh, cell, wallPad(cell))
            bridged(wall, thick, gw, gh, s)
        }
        underlap(region, wall, deep, gw, gh)
        return trace(region, gw, gh).mapNotNull { ring ->
            val smooth = smoothRing(ring, gw, gh)
            for (i in smooth.indices step 2) {
                smooth[i] = min(smooth[i] * cell, pageW)
                smooth[i + 1] = min(smooth[i + 1] * cell, pageH)
            }
            val s = simplifyRing(smooth, cell * 0.08f)
            if (s.size >= 6 && abs(area(s)) >= cell * cell * 2f) s else null
        }
    }

    /**
     * Zakreślacz, którego nie widać: na jasnym papierze kolor się mnoży, więc biały nic nie zmienia, a na ciemnym
     * rozjaśnia („screen”), więc nic nie zmienia czarny. Taka kreska nie może być ścianą, bo wypełnienie urywałoby się
     * na niewidocznej granicy.
     */
    private fun invisible(e: Element, darkPaper: Boolean): Boolean {
        if (e !is Stroke || e.pen != PenKind.HIGHLIGHTER) return false
        val r = e.color shr 16 and 0xFF; val g = e.color shr 8 and 0xFF; val b = e.color and 0xFF
        return if (darkPaper) max(r, max(g, b)) <= INVISIBLE_TOL else min(r, min(g, b)) >= 255 - INVISIBLE_TOL
    }

    /** O tyle ściana jest szersza od kreski, żeby wąskie szpary między liniami (np. niedomknięty rysunek) nie przeciekały. */
    private fun wallPad(cell: Float) = max(cell, 1f)

    /** [inset] > 0 zaznacza tylko rdzeń kresek: komórki głębiej niż [inset] pod brzegiem prawdziwej kreski. */
    private fun rasterize(e: Element, wall: BooleanArray, gw: Int, gh: Int, cell: Float, pad: Float, inset: Float = 0f) {
        when (e) {
            is FillEl -> return
            is Stroke -> {
                val p = e.pts
                val n = e.count
                if (n == 0) return
                val half = e.width * 0.5f
                // Co najmniej 0,75 komórki: cieńsza kreska ukośna zostawiałaby dziury między komórkami, przez które przecieka wypełnienie.
                val minR = if (inset > 0f) 0f else max(0.5f, cell * 0.75f)
                fun rad(i: Int) = max(half * p[3 * i + 2] - inset, minR) + pad
                if (n == 1) { if (rad(0) > 0f) segment(wall, gw, gh, cell, p[0], p[1], p[0], p[1], rad(0)); return }
                for (i in 0 until n - 1) {
                    val r = max(rad(i), rad(i + 1))
                    if (r > 0f) segment(wall, gw, gh, cell, p[3 * i], p[3 * i + 1], p[3 * i + 3], p[3 * i + 4], r)
                }
            }
            is BoxElement -> {
                if (e is ImageEl && e.locked) return
                val b = e.bounds
                val x0 = max(0, floor(b.l / cell).toInt()); val x1 = min(gw - 1, floor(b.r / cell).toInt())
                val y0 = max(0, floor(b.t / cell).toInt()); val y1 = min(gh - 1, floor(b.b / cell).toInt())
                for (cy in y0..y1) for (cx in x0..x1) {
                    if (e.containsPoint((cx + 0.5f) * cell, (cy + 0.5f) * cell, cell * 0.5f)) wall[cy * gw + cx] = true
                }
            }
        }
    }

    /** Zaznacza komórki, których środek leży bliżej odcinka niż [r]. */
    private fun segment(wall: BooleanArray, gw: Int, gh: Int, cell: Float, ax: Float, ay: Float, bx: Float, by: Float, r: Float) {
        val x0 = max(0, floor((min(ax, bx) - r) / cell).toInt()); val x1 = min(gw - 1, floor((max(ax, bx) + r) / cell).toInt())
        val y0 = max(0, floor((min(ay, by) - r) / cell).toInt()); val y1 = min(gh - 1, floor((max(ay, by) + r) / cell).toInt())
        val r2 = r * r
        for (cy in y0..y1) for (cx in x0..x1) {
            val i = cy * gw + cx
            if (wall[i]) continue
            if (GeomMath.distSqPointSegment((cx + 0.5f) * cell, (cy + 0.5f) * cell, ax, ay, bx, by) <= r2) wall[i] = true
        }
    }

    /**
     * Obszar przy domkniętych szparach. [thin] to prawdziwe linie, [thick] pogrubione. Rdzeń to obszar wolny od [thick]
     * spójny z dotknięciem; komórki pasa zapasu (wolne w [thin], zajęte w [thick]) dostaje ten obszar, który jest do nich
     * najbliżej (BFS z wielu źródeł naraz), więc ostry kąt należy do obszaru, w którym leży, a szpara dzieli się po połowie.
     */
    private fun bridged(thin: BooleanArray, thick: BooleanArray, gw: Int, gh: Int, s0: Int): BooleanArray {
        val n = thin.size
        var s = s0
        if (thick[s]) {
            // Dotknięcie w samym zakamarku (w pasie zapasu): bierzemy najbliższy obszar osiągalny bez przekraczania linii.
            s = nearestFree(thin, thick, gw, gh, s)
            // Malutki zamknięty obszar, cały węższy od zapasu: wypełniamy go po prawdziwych liniach.
            if (s < 0) return flood(thin, gw, gh, s0)
        }
        val core = flood(thick, gw, gh, s)
        // 1 = nasz obszar, 2 = inny, 0 = jeszcze nieprzydzielona komórka pasa zapasu.
        val own = ByteArray(n)
        val queue = IntArray(n)
        var tail = 0
        for (i in 0 until n) if (!thick[i]) own[i] = if (core[i]) 1 else 2
        // Drobne wolne kępki poza rdzeniem (zwykle ziarna rastra w zwężającym się klinie) nie są osobnym obszarem:
        // są pasem zapasu i dostaje je najbliższy obszar, inaczej urywałyby kolor przed czubkiem kąta.
        val dust = BooleanArray(n)
        markDust(thick, core, own, gw, gh, dust)
        fun isBand(j: Int) = (thick[j] && !thin[j]) || dust[j]
        // 8-spójnie, jak samo wypełnienie: ostry czubek kąta łączy się z resztą obszaru tylko rogami komórek.
        for (i in 0 until n) {
            if (own[i].toInt() == 0) continue
            val cx = i % gw; val cy = i / gw
            var next = false
            for (dy in -1..1) for (dx in -1..1) {
                val x = cx + dx; val y = cy + dy
                if (x in 0 until gw && y in 0 until gh && isBand(y * gw + x)) next = true
            }
            if (next) queue[tail++] = i
        }
        var head = 0
        while (head < tail) {
            val i = queue[head++]
            val o = own[i]
            val cx = i % gw; val cy = i / gw
            for (dy in -1..1) for (dx in -1..1) {
                val x = cx + dx; val y = cy + dy
                if (x !in 0 until gw || y !in 0 until gh) continue
                val j = y * gw + x
                if (own[j].toInt() == 0 && isBand(j)) { own[j] = o; queue[tail++] = j }
            }
        }
        val region = BooleanArray(n)
        for (i in 0 until n) if (own[i].toInt() == 1) region[i] = true
        return region
    }

    /** Zaznacza w [dust] komórki wolnych kęp (8-spójnych) poza [core], które mają najwyżej [DUST_CELLS] komórek, i zeruje ich [own]. */
    private fun markDust(thick: BooleanArray, core: BooleanArray, own: ByteArray, gw: Int, gh: Int, dust: BooleanArray) {
        val n = thick.size
        val seen = BooleanArray(n)
        val queue = IntArray(n)
        for (s in 0 until n) {
            if (thick[s] || core[s] || seen[s]) continue
            var head = 0; var tail = 0
            seen[s] = true; queue[tail++] = s
            while (head < tail) {
                val i = queue[head++]
                val cx = i % gw; val cy = i / gw
                for (dy in -1..1) for (dx in -1..1) {
                    val x = cx + dx; val y = cy + dy
                    if (x !in 0 until gw || y !in 0 until gh) continue
                    val j = y * gw + x
                    if (!thick[j] && !core[j] && !seen[j]) { seen[j] = true; queue[tail++] = j }
                }
            }
            if (tail <= DUST_CELLS) for (k in 0 until tail) { dust[queue[k]] = true; own[queue[k]] = 0 }
        }
    }

    /** Najbliższa (BFS po komórkach wolnych od prawdziwych linii) komórka wolna także od pogrubionych ścian; -1, gdy brak. */
    private fun nearestFree(thin: BooleanArray, thick: BooleanArray, gw: Int, gh: Int, s: Int): Int {
        val seen = BooleanArray(thin.size)
        val queue = IntArray(thin.size)
        var head = 0; var tail = 0
        queue[tail++] = s; seen[s] = true
        while (head < tail) {
            val i = queue[head++]
            if (!thick[i]) return i
            val cx = i % gw; val cy = i / gw
            fun visit(j: Int) { if (!seen[j] && !thin[j]) { seen[j] = true; queue[tail++] = j } }
            if (cx > 0) visit(i - 1)
            if (cx < gw - 1) visit(i + 1)
            if (cy > 0) visit(i - gw)
            if (cy < gh - 1) visit(i + gw)
        }
        return -1
    }

    /**
     * Wypełnienie 8-spójne od komórki [s] po komórkach bez ścian. Ściany mają promień co najmniej 0,75 komórki, więc żadna
     * linia nie zostawia przekątnej szpary, którą przeciekałoby wypełnienie, a wąskie szczeliny łączące się tylko rogami
     * komórek (ostre klinowate końce) są wypełniane do końca.
     */
    private fun flood(wall: BooleanArray, gw: Int, gh: Int, s: Int): BooleanArray {
        val region = BooleanArray(gw * gh)
        var stack = IntArray(1024)
        var top = 0
        fun push(i: Int) {
            if (top == stack.size) stack = stack.copyOf(stack.size * 2)
            stack[top++] = i
        }
        region[s] = true
        push(s)
        while (top > 0) {
            val i = stack[--top]
            val cx = i % gw; val cy = i / gw
            for (dy in -1..1) {
                val ny = cy + dy
                if (ny < 0 || ny >= gh) continue
                for (dx in -1..1) {
                    val nx = cx + dx
                    if (nx < 0 || nx >= gw) continue
                    val j = ny * gw + nx
                    if (!wall[j] && !region[j]) { region[j] = true; push(j) }
                }
            }
        }
        return region
    }

    /**
     * Poszerza obszar w ściany (pod linie), nigdy w sąsiednie wolne obszary. Każdą komórkę ściany w zasięgu [UNDERLAP]
     * dostaje najbliższy wolny obszar (BFS z wielu źródeł naraz), więc kolor kończy się w połowie kreski, a nie wychodzi
     * spod niej po drugiej stronie ani nie biegnie wzdłuż kreski poza róg kształtu.
     *
     * Dalej, do [SLIVER_REACH], obszar idzie tylko komórkami szczeliny: brzegowymi komórkami kresek (poza [deep]), które
     * nie stykają się bokiem z innym wolnym obszarem. Tak dochodzi do końca wąskiego, ostrego klina, a nie pełznie po
     * kresce wystającej z kształtu (jej komórki zawsze stykają się z papierem po obu stronach).
     */
    private fun underlap(region: BooleanArray, wall: BooleanArray, deep: BooleanArray, gw: Int, gh: Int) {
        val n = region.size
        // 1 = nasz obszar, 2 = inny wolny obszar, 0 = komórka ściany jeszcze bez właściciela.
        val own = ByteArray(n)
        val depth = ByteArray(n)
        val queue = IntArray(n)
        var tail = 0
        // Nasz obszar wchodzi do kolejki pierwszy, więc remis (środek kreski o nieparzystej szerokości) jest nasz.
        for (ours in booleanArrayOf(true, false)) for (i in 0 until n) {
            if (wall[i] || region[i] != ours) continue
            own[i] = if (ours) 1 else 2
            val cx = i % gw; val cy = i / gw
            if ((cx > 0 && wall[i - 1]) || (cx < gw - 1 && wall[i + 1]) || (cy > 0 && wall[i - gw]) || (cy < gh - 1 && wall[i + gw])) queue[tail++] = i
        }
        var head = 0
        while (head < tail) {
            val i = queue[head++]
            val nd = depth[i] + 1
            if (nd > UNDERLAP) continue
            val o = own[i]
            val cx = i % gw; val cy = i / gw
            fun visit(j: Int) { if (own[j].toInt() == 0) { own[j] = o; depth[j] = nd.toByte(); queue[tail++] = j } }
            if (cx > 0) visit(i - 1)
            if (cx < gw - 1) visit(i + 1)
            if (cy > 0) visit(i - gw)
            if (cy < gh - 1) visit(i + gw)
        }
        fun touchesOther(j: Int): Boolean {
            val cx = j % gw; val cy = j / gw
            fun other(k: Int) = !wall[k] && !region[k]
            return (cx > 0 && other(j - 1)) || (cx < gw - 1 && other(j + 1)) || (cy > 0 && other(j - gw)) || (cy < gh - 1 && other(j + gw))
        }
        // Komórka, którą wcześniej dostał inny obszar, jest szczeliną tylko wtedy, gdy inny obszar leży po obu jej stronach
        // (szew między dwiema zbiegającymi się kreskami). Środek zwykłej kreski ma nasz obszar z jednej strony: tam
        // zabranie komórki dałoby postrzępiony brzeg.
        fun reachesOther(j: Int, dx: Int, dy: Int): Boolean {
            var x = j % gw; var y = j / gw
            for (s in 1..UNDERLAP + 1) {
                x += dx; y += dy
                if (x < 0 || y < 0 || x >= gw || y >= gh) return false
                val k = y * gw + x
                if (own[k].toInt() == 1) return false
                if (!wall[k]) return true
            }
            return false
        }
        fun betweenOthers(j: Int) =
            (reachesOther(j, 1, 0) && reachesOther(j, -1, 0)) || (reachesOther(j, 0, 1) && reachesOther(j, 0, -1)) ||
                (reachesOther(j, 1, 1) && reachesOther(j, -1, -1)) || (reachesOther(j, 1, -1) && reachesOther(j, -1, 1))
        // Szczeliny: od komórek obszaru (także tych spod linii) dalej, z odległością liczoną od nowa w głąb ściany.
        tail = 0
        for (i in 0 until n) {
            if (own[i].toInt() != 1) continue
            region[i] = true
            if (wall[i]) queue[tail++] = i
        }
        val sliver = BooleanArray(n)
        head = 0
        while (head < tail) {
            val i = queue[head++]
            val nd = depth[i] + 1
            if (nd > SLIVER_REACH) continue
            val cx = i % gw; val cy = i / gw
            fun visit(j: Int) {
                if (!wall[j] || region[j] || sliver[j] || deep[j] || touchesOther(j)) return
                if (own[j].toInt() == 2 && !betweenOthers(j)) return
                sliver[j] = true; region[j] = true; depth[j] = nd.toByte(); queue[tail++] = j
            }
            if (cx > 0) visit(i - 1)
            if (cx < gw - 1) visit(i + 1)
            if (cy > 0) visit(i - gw)
            if (cy < gh - 1) visit(i + gw)
        }
    }

    /**
     * Obrys obszaru po krawędziach komórek: każda krawędź między komórką obszaru a resztą to skierowany odcinek
     * (obszar po prawej stronie w układzie ekranu), a odcinki łączą się w zamknięte pierścienie (współrzędne w komórkach).
     */
    internal fun trace(region: BooleanArray, gw: Int, gh: Int): List<FloatArray> {
        val vw = gw + 1
        // Bity kierunków wychodzących z wierzchołka: 1 = +x, 2 = +y, 4 = -x, 8 = -y.
        val out = ByteArray(vw * (gh + 1))
        fun inR(cx: Int, cy: Int) = cx in 0 until gw && cy in 0 until gh && region[cy * gw + cx]
        fun mark(vx: Int, vy: Int, bit: Int) { val v = vy * vw + vx; out[v] = (out[v].toInt() or bit).toByte() }
        for (cy in 0 until gh) for (cx in 0 until gw) {
            if (!region[cy * gw + cx]) continue
            if (!inR(cx, cy - 1)) mark(cx, cy, 1)
            if (!inR(cx + 1, cy)) mark(cx + 1, cy, 2)
            if (!inR(cx, cy + 1)) mark(cx + 1, cy + 1, 4)
            if (!inR(cx - 1, cy)) mark(cx, cy + 1, 8)
        }
        val dx = intArrayOf(1, 0, -1, 0); val dy = intArrayOf(0, 1, 0, -1)
        val rings = ArrayList<FloatArray>()
        for (start in out.indices) while (out[start].toInt() != 0) {
            val pts = com.mdev.mnotes.core.geom.FloatList(64)
            var v = start
            var dir = -1
            while (true) {
                val m = out[v].toInt()
                if (m == 0) break
                // W „szczypcach” (dwa wyjścia) skręcamy w prawo, więc stykające się rogiem komórki dają osobne pierścienie.
                val nd = if (dir < 0) Integer.numberOfTrailingZeros(m)
                else intArrayOf((dir + 1) % 4, dir, (dir + 3) % 4).firstOrNull { m and (1 shl it) != 0 } ?: break
                out[v] = (m and (1 shl nd).inv()).toByte()
                if (nd != dir) pts.add((v % vw).toFloat(), (v / vw).toFloat())
                dir = nd
                v += dy[nd] * vw + dx[nd]
                if (v == start) break
            }
            if (pts.size >= 6) rings.add(pts.toArray())
        }
        return rings
    }

    /**
     * Wygładza schodkowy pierścień z [trace] (współrzędne w komórkach): próbkuje go gęsto, rozmywa po łuku (Gauss) i
     * zostawia każdy punkt najdalej [SMOOTH_MAX_SHIFT] od oryginału, więc kształt (i skuteczność wypełnienia) się nie zmienia,
     * znikają tylko schodki. Punkty na krawędzi strony stoją w miejscu, żeby prosty brzeg i rogi strony zostały proste.
     */
    internal fun smoothRing(ring: FloatArray, gw: Int, gh: Int): FloatArray {
        val n = ring.size / 2
        if (n < 4) return ring
        val xs = com.mdev.mnotes.core.geom.FloatList(256)
        for (i in 0 until n) {
            val ax = ring[2 * i]; val ay = ring[2 * i + 1]
            val k = (i + 1) % n
            val bx = ring[2 * k]; val by = ring[2 * k + 1]
            val parts = max(1, ceil(sqrt((bx - ax) * (bx - ax) + (by - ay) * (by - ay)) / SMOOTH_STEP).toInt())
            for (q in 0 until parts) {
                val t = q.toFloat() / parts
                xs.add(ax + (bx - ax) * t, ay + (by - ay) * t)
            }
        }
        val src = xs.toArray()
        val m = src.size / 2
        val radius = ceil(SMOOTH_SIGMA * 3f).toInt()
        if (m <= 2 * radius + 1) return ring
        val w = FloatArray(radius + 1) { kotlin.math.exp(-(it * it) / (2f * SMOOTH_SIGMA * SMOOTH_SIGMA)) }
        var wsum = w[0]
        for (j in 1..radius) wsum += 2f * w[j]
        val out = FloatArray(src.size)
        val eps = 0.001f
        for (i in 0 until m) {
            val px = src[2 * i]; val py = src[2 * i + 1]
            if (px <= eps || py <= eps || px >= gw - eps || py >= gh - eps) { out[2 * i] = px; out[2 * i + 1] = py; continue }
            var sx = w[0] * px; var sy = w[0] * py
            for (j in 1..radius) {
                val a = (i - j + m) % m; val b = (i + j) % m
                sx += w[j] * (src[2 * a] + src[2 * b]); sy += w[j] * (src[2 * a + 1] + src[2 * b + 1])
            }
            var dx = sx / wsum - px; var dy = sy / wsum - py
            val d = sqrt(dx * dx + dy * dy)
            if (d > SMOOTH_MAX_SHIFT) { dx *= SMOOTH_MAX_SHIFT / d; dy *= SMOOTH_MAX_SHIFT / d }
            out[2 * i] = px + dx; out[2 * i + 1] = py + dy
        }
        return out
    }

    /** Douglas–Peucker dla zamkniętego pierścienia (pary x, y), bez powtórzonego ostatniego punktu. */
    internal fun simplifyRing(ring: FloatArray, eps: Float): FloatArray {
        val n = ring.size / 2
        if (n < 4) return ring
        // Dzielimy pierścień w punkcie 0 i w najdalszym od niego, każdą połowę upraszczamy osobno.
        var far = 0; var best = -1f
        for (i in 1 until n) {
            val d = (ring[2 * i] - ring[0]).let { it * it } + (ring[2 * i + 1] - ring[1]).let { it * it }
            if (d > best) { best = d; far = i }
        }
        val keep = BooleanArray(n + 1)
        keep[0] = true; keep[far] = true; keep[n] = true
        fun x(i: Int) = ring[2 * (i % n)]
        fun y(i: Int) = ring[2 * (i % n) + 1]
        val stack = ArrayDeque<Long>()
        stack.addLast((0L shl 32) or far.toLong())
        stack.addLast((far.toLong() shl 32) or n.toLong())
        val e2 = eps * eps
        while (stack.isNotEmpty()) {
            val s = stack.removeLast()
            val a = (s ushr 32).toInt(); val b = (s and 0xffffffffL).toInt()
            if (b - a < 2) continue
            var idx = -1; var dmax = e2
            for (i in a + 1 until b) {
                val d = GeomMath.distSqPointSegment(x(i), y(i), x(a), y(a), x(b), y(b))
                if (d > dmax) { dmax = d; idx = i }
            }
            if (idx >= 0) {
                keep[idx] = true
                stack.addLast((a.toLong() shl 32) or idx.toLong())
                stack.addLast((idx.toLong() shl 32) or b.toLong())
            }
        }
        val res = com.mdev.mnotes.core.geom.FloatList(64)
        for (i in 0 until n) if (keep[i]) res.add(x(i), y(i))
        return res.toArray()
    }

    /** Pole ze znakiem (wzór sznurowadłowy). */
    fun area(ring: FloatArray): Float {
        val n = ring.size / 2
        var s = 0f
        var j = n - 1
        for (i in 0 until n) {
            s += ring[2 * j] * ring[2 * i + 1] - ring[2 * i] * ring[2 * j + 1]
            j = i
        }
        return s * 0.5f
    }
}

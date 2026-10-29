// Algorytmy kredy na podstawie programu kreda — autor i licencja do potwierdzenia przed publikacją (app/src/kreda/NOTICE.md).

package com.mdev.mnotes.core.image

import kotlin.math.max
import kotlin.math.min

/**
 * Kreda na przezroczystym tle: wyrównanie oświetlenia, wyostrzenie, kolor do alfy (próg/pełne, auto-próg z MAD),
 * gumka wielokątna. Odpowiedniki `flatten`, `sharpen`, `process`, `auto_cut`, `polygon_mask` z kreda.py.
 */
object Chalk {
    const val FLATTEN_RADIUS = 0.012f
    const val FLATTEN_GAIN = 2.0f
    const val AUTO_CUT_K = 4.5f
    const val AUTO_CUT_MIN = 0.10f
    const val AUTO_CUT_MAX = 0.50f
    const val SHARPEN_RADIUS = 0.0006f
    const val SHARPEN_AMOUNT = 1.0f

    /**
     * Usuwa nierówne oświetlenie: tło szacowane na ćwiartce rozmiaru (otwarcie dla ciemnej tablicy, domknięcie dla
     * białej, potem rozmycie), wynik wygląda jak kreda na równo czarnym (albo pisak na równo białym) tle.
     */
    fun flatten(src: Raster, black: Boolean): Raster {
        val sw = max(1, src.w / 4); val sh = max(1, src.h / 4)
        val small = src.downscale(sw, sh)
        val r = max(3, (FLATTEN_RADIUS * src.w / 4).toInt())
        val bgs = Array(3) { c ->
            val p = small.plane { ((it shr (16 - 8 * c)) and 255) / 255f }
            val m = if (black) BoardFilters.open(p, sw, sh, r) else BoardFilters.close(p, sw, sh, r)
            BoardFilters.upscale(BoardFilters.boxBlur(m, sw, sh, r), sw, sh, src.w, src.h)
        }
        val out = IntArray(src.px.size)
        for (i in out.indices) {
            val c = src.px[i]
            val bR = bgs[0][i]; val bG = bgs[1][i]; val bB = bgs[2][i]
            val denom = max((bR + bG + bB) / 3f, 16f / 255f)
            val k = FLATTEN_GAIN / denom
            val sr = Raster.red(c) / 255f; val sg = Raster.green(c) / 255f; val sb = Raster.blue(c) / 255f
            out[i] = if (black) Raster.rgbF((sr - bR) * k, (sg - bG) * k, (sb - bB) * k, Raster.alpha(c) / 255f)
            else Raster.rgbF(1 - (bR - sr) * k, 1 - (bG - sg) * k, 1 - (bB - sb) * k, Raster.alpha(c) / 255f)
        }
        return Raster(src.w, src.h, out)
    }

    /** Maska wyostrzająca kanały koloru: src + amount·(src − rozmyte). */
    fun sharpen(src: Raster, amount: Float = SHARPEN_AMOUNT): Raster {
        val r = max(1, (SHARPEN_RADIUS * src.w + 0.5f).toInt())
        val blurred = Array(3) { c -> BoardFilters.boxBlur(src.plane { ((it shr (16 - 8 * c)) and 255) / 255f }, src.w, src.h, r) }
        val out = IntArray(src.px.size)
        for (i in out.indices) {
            val c = src.px[i]
            fun ch(k: Int, v: Int) = v / 255f + amount * (v / 255f - blurred[k][i])
            out[i] = Raster.rgbF(ch(0, Raster.red(c)), ch(1, Raster.green(c)), ch(2, Raster.blue(c)), Raster.alpha(c) / 255f)
        }
        return Raster(src.w, src.h, out)
    }

    /** Jasność kredy względem tła: maks. kanał na czarnym, maks. (1 − kanał) na białym. */
    private fun strength(c: Int, black: Boolean): Float =
        if (black) Raster.maxRgb(c) else 1f - Raster.minRgb(c)

    /**
     * Próg tła z ziarna samego obrazu, w [0.10, 0.50]: K × σ szumu, a po wyrównaniu oświetlenia ([flattened]) jeszcze
     * plus mediana. Jak w kreda.py σ jest odporne (z percentyli, nie z odchylenia), ale liczone z górnej połowy rozkładu:
     * po wyrównaniu część tła jest obcięta do zera i zwykłe MAD zaniżało σ, przez co szum przechodził jako mgła. Bez
     * wyrównania tło nie leży przy zerze, a mediana nasycała próg i gubiła słabą kredę: wtedy K × σ jak w kreda.py.
     * Liczone po [IntArray] bez listy pośredniej (12 Mpx to ~4 mln pudełek `Integer`).
     */
    fun autoCut(src: Raster, black: Boolean, flattened: Boolean = true): Float {
        val px = src.px
        var visible = 0
        for (c in px) if (Raster.alpha(c) > 127) visible++
        if (visible == 0) return AUTO_CUT_MIN
        val step = max(1, visible / 400_000)
        val v = FloatArray((visible + step - 1) / step)
        var seen = 0; var n = 0
        for (c in px) {
            if (Raster.alpha(c) <= 127) continue
            if (seen++ % step == 0) v[n++] = strength(c, black)
        }
        v.sort()
        fun pct(p: Double) = v[((v.size - 1) * p).toInt()]
        val med = pct(0.5)
        val sigma = if (med > 1f / 255f) pct(0.8413) - med else pct(0.9772) / 2f
        return ((if (flattened) med else 0f) + AUTO_CUT_K * sigma).coerceIn(AUTO_CUT_MIN, AUTO_CUT_MAX)
    }

    /**
     * Kolor do alfy względem czarnego albo białego tła. Poniżej [cut] piksel znika, powyżej [solid] jest pełny;
     * kolor kredy odtwarzany jak w GIMP-ie (dzielenie przez krycie), a z [tint] wszystkie kreski dostają ten kolor.
     */
    fun toAlpha(src: Raster, black: Boolean, cut: Float, solid: Float, tint: Int? = null, low: Float? = null, cutMap: FloatArray? = null): Raster {
        val out = IntArray(src.px.size)
        // Histereza (2b-B2): piksel jaśniejszy od [low], ale ciemniejszy od [cut], zostaje tylko w plamie, która ma
        // choć jeden piksel ≥ [cut]; w takiej plamie krycie liczy się od [low]. Blade fragmenty liter wracają,
        // odosobnione smugi po gąbce nie. Z [cutMap] (próg lokalny, 2c-B9) oba progi są na piksel, [low] w tej samej proporcji.
        val lowFrac = if (low != null && low < cut) low / cut else null
        val keep = if (lowFrac != null) connectedToStrong(src, black, cut, low!!, cutMap) else null
        for (i in out.indices) {
            val c = src.px[i]
            val a = strength(c, black)
            val ci = cutMap?.get(i) ?: cut
            val s = max(solid, ci + 1e-3f)
            val from = if (keep != null && keep[i]) ci * lowFrac!! else ci
            val alpha = ((a - from) / (s - from)).coerceIn(0f, 1f) * (Raster.alpha(c) / 255f)
            if (alpha <= 0f) { out[i] = 0; continue }
            out[i] = if (tint != null) (Raster.q(alpha) shl 24) or (tint and 0xFFFFFF) else {
                val safe = max(a, 1e-4f)
                fun col(v: Int) = if (black) v / 255f / safe else 1f - (1f - v / 255f) / safe
                Raster.rgbF(col(Raster.red(c)), col(Raster.green(c)), col(Raster.blue(c)), alpha)
            }
        }
        return Raster(src.w, src.h, out)
    }

    /**
     * Piksele ≥ [low] połączone (8-sąsiedztwo) z jakimkolwiek pikselem ≥ [cut]; widoczne tylko te nieprzezroczyste.
     * Z [cutMap] progi są na piksel: `cutMap[i]` i `cutMap[i] · low / cut`.
     */
    fun connectedToStrong(src: Raster, black: Boolean, cut: Float, low: Float, cutMap: FloatArray? = null): BooleanArray {
        val w = src.w; val h = src.h; val n = w * h
        val a = FloatArray(n) { if (Raster.alpha(src.px[it]) > 127) strength(src.px[it], black) else 0f }
        val frac = low / cut
        fun strongAt(i: Int) = cutMap?.get(i) ?: cut
        fun lowAt(i: Int) = cutMap?.let { it[i] * frac } ?: low
        val keep = BooleanArray(n)
        val queue = IntArray(n)
        var head = 0; var tail = 0
        for (i in 0 until n) if (a[i] >= strongAt(i)) { keep[i] = true; queue[tail++] = i }
        while (head < tail) {
            val p = queue[head++]
            val x = p % w; val y = p / w
            for (dy in -1..1) for (dx in -1..1) {
                val nx = x + dx; val ny = y + dy
                if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                val q = ny * w + nx
                if (!keep[q] && a[q] >= lowAt(q)) { keep[q] = true; queue[tail++] = q }
            }
        }
        return keep
    }

    /**
     * Próg lokalny (2c-B9): średnia + [LOCAL_K]·σ siły kredy w oknie [LOCAL_WINDOW] szerokości, przycięta do
     * [LOCAL_FLOOR]·[cut] … [cut]. Tylko obniża globalny próg tam, gdzie tło w oknie jest równe i ciemne (blade litery na
     * szarej tablicy); gdzie jest gęste pismo albo smugi, σ jest duże i zostaje [cut].
     */
    fun localCut(src: Raster, black: Boolean, cut: Float): FloatArray {
        val w = src.w; val h = src.h
        val a = FloatArray(w * h) { if (Raster.alpha(src.px[it]) > 127) strength(src.px[it], black) else 0f }
        val r = max(2, (LOCAL_WINDOW * w / 2).toInt())
        val mean = BoardFilters.boxBlur(a, w, h, r)
        for (i in a.indices) a[i] *= a[i]
        val sq = BoardFilters.boxBlur(a, w, h, r)
        // 3c-B3 (K1): ręczny próg może być niższy niż AUTO_CUT_MIN — wtedy zakres byłby pusty i coerceIn rzucał.
        val lo = min(cut, max(AUTO_CUT_MIN, LOCAL_FLOOR * cut))
        return FloatArray(w * h) { i -> (mean[i] + LOCAL_K * kotlin.math.sqrt(max(0f, sq[i] - mean[i] * mean[i]))).coerceIn(lo, cut) }
    }

    const val LOCAL_WINDOW = 1f / 16f
    const val LOCAL_K = 2f
    const val LOCAL_FLOOR = 0.6f

    /**
     * Usuwa jasne pasy grubsze niż kreda (2b-B2): listwę między tablicami, ramę, kawałki ściany przy brzegu kadru.
     * Rdzeń to piksele dalej od tła niż [maxHalf]; znika tylko rdzeń **długi** (rozpiętość ≥ [FRAME_EXTENT] dłuższego
     * boku obrazu), razem z otoczką do [maxHalf]. Zwarte grube plamy (pogrubione, rozmyte litery) zostają, cienkie kreski
     * dotykające pasa też. Pierwsza wersja bez warunku długości zjadała pogrubione litery na rozmytym zdjęciu.
     */
    fun dropThick(img: Raster, maxHalf: Float): Raster {
        val w = img.w; val h = img.h; val n = w * h
        val mask = BooleanArray(n) { Raster.alpha(img.px[it]) > 128 }
        val d = ChalkVector.distance(mask, w, h)
        val core = longCores(BooleanArray(n) { d[it] > maxHalf }, w, h, (FRAME_EXTENT * max(w, h)).toInt())
        if (core.none { it }) return img
        val toCore = ChalkVector.distance(BooleanArray(n) { !core[it] }, w, h, edgeIsBackground = false)
        val out = img.px.copyOf()
        for (i in 0 until n) if (out[i] != 0 && (core[i] || toCore[i] <= maxHalf + 1.5f)) out[i] = 0
        return Raster(w, h, out)
    }

    /**
     * Usuwa proste listwy i krawędzie ram cieńsze od progu [dropThick] (3c-B1): prawie poziomy (pionowy) pas, który bez
     * przerw dłuższych niż [RAIL_GAP] boku ciągnie się przez co najmniej [RAIL_SPAN] szerokości (wysokości) wyniku (listwa idzie od brzegu do brzegu; kreska podziału kredą rzadko).
     * Kandydaci z głosowania Hougha (±[RAIL_TILT]°, tylko piksele kryjące), potem sprawdzenie wzdłuż prostej. Odręczna
     * kreska kredą tak długa i tak prosta nie bywa; przerywana też nie przechodzi. Znika pas z otoczką 2 px.
     */
    fun dropRails(img: Raster): Raster {
        val w = img.w; val h = img.h
        val solid = BooleanArray(w * h) { Raster.alpha(img.px[it]) > 128 }
        val out = img.px.copyOf()
        val a = railsAlong(solid, out, w, h, horizontal = true)
        val b = railsAlong(solid, out, w, h, horizontal = false)
        return if (a + b == 0) img else Raster(w, h, out)
    }

    /** Listwy w jednym kierunku; zwraca liczbę usuniętych. [solid] i [out] zmieniają się na miejscu. */
    private fun railsAlong(solid: BooleanArray, out: IntArray, w: Int, h: Int, horizontal: Boolean): Int {
        val len = if (horizontal) w else h
        val across = if (horizontal) h else w
        fun at(u: Int, v: Int) = if (horizontal) v * w + u else u * w + v
        val steps = (2 * RAIL_TILT / RAIL_STEP).toInt() + 1
        val tans = FloatArray(steps) { Math.tan(Math.toRadians(-RAIL_TILT + it * RAIL_STEP)).toFloat() }
        val pad = (len * tans[steps - 1]).toInt() + 2
        val bins = across + 2 * pad
        val votes = IntArray(steps * bins)
        var count = 0
        for (v in 0 until across) for (u in 0 until len) if (solid[at(u, v)]) {
            count++
            for (s in 0 until steps) {
                val b = (v - u * tans[s]).toInt() + pad
                if (b in 0 until bins) votes[s * bins + b]++
            }
        }
        if (count == 0 || count > 0.3 * w * h) return 0
        val need = (RAIL_SPAN * len).toInt()
        val cand = (votes.indices).filter { votes[it] >= need }.sortedByDescending { votes[it] }.take(400)
        val gap = max(4, (RAIL_GAP * len).toInt())
        val maxThick = max(6, (RAIL_THICK * across).toInt())
        var removed = 0
        for (c in cand) {
            if (removed >= 12) break
            val t = tans[c / bins]; val v0 = c % bins - pad
            fun line(u: Int, d: Int) = (v0 + u * t).toInt() + d
            fun hit(u: Int, d: Int): Boolean { val v = line(u, d); return v in 0 until across && solid[at(u, v)] }
            // Odcinki w paśmie ±1 z przerwami ≤ gap; liczą się tylko długie ([RAIL_PIECE]): listwa przerwana półką, kablem
            // albo ręką to kilka długich kawałków na jednej prostej, a pismo takich nie ma.
            val runs = ArrayList<IntArray>()
            // 3c-B3 (K4): odcinek zamyka dopiero przerwa dłuższa niż gap albo koniec (dawniej każdy pusty piksel).
            var s = -1; var last = -1
            for (u in 0..len) {
                val on = u < len && (hit(u, -1) || hit(u, 0) || hit(u, 1))
                if (on && s >= 0 && u - last - 1 <= gap) { last = u; continue }
                if (s >= 0 && (on || u == len || u - last - 1 > gap)) {
                    if (last - s + 1 >= RAIL_PIECE * len) runs += intArrayOf(s, last)
                    s = -1
                }
                if (on) { s = u; last = u }
            }
            if (runs.sumOf { it[1] - it[0] + 1 } < need) continue
            // Grubość: kolejne przesunięcia od prostej, na których pas pokrywa co najmniej połowę odcinków.
            fun cover(d: Int): Float {
                var n = 0; var all = 0
                for (r in runs) for (u in r[0]..r[1]) { all++; if (hit(u, d)) n++ }
                return n.toFloat() / all
            }
            var lo = 0; var hi = 0
            while (lo > -maxThick && cover(lo - 1) >= 0.5f) lo--
            while (hi < maxThick && cover(hi + 1) >= 0.5f) hi++
            if (hi - lo + 1 >= maxThick || (cover(0) < 0.5f && cover(-1) < 0.5f && cover(1) < 0.5f)) continue
            for (r in runs) for (u in r[0]..r[1]) for (d in lo - 2..hi + 2) {
                val v = line(u, d)
                if (v in 0 until across) { val i = at(u, v); out[i] = 0; solid[i] = false }
            }
            removed++
        }
        return removed
    }

    /** Listwa ciągnie się przez co najmniej taki ułamek boku wyniku. */
    const val RAIL_SPAN = 0.7f
    /** Najkrótszy kawałek listwy (ułamek boku), który liczy się do [RAIL_SPAN]. */
    const val RAIL_PIECE = 0.04f
    /** Najdłuższa przerwa w listwie (ułamek boku): zatarcie, kreda na listwie. Przerywana kreska ma dłuższe. */
    const val RAIL_GAP = 0.006f
    /** Listwa grubsza niż taki ułamek drugiego boku to już nie listwa (zostaje dla [dropThick]). */
    const val RAIL_THICK = 0.03f
    /** Pochylenie listwy po prostowaniu (stopnie) i krok szukania. */
    const val RAIL_TILT = 4.0
    const val RAIL_STEP = 0.2

    /**
     * Usuwa z pasa przy brzegu wyniku ([EDGE_STRIP] boku) kawałki ramy (3c-B1): spójne plamy kryjące, które w całości
     * leżą w pasie przy jednym brzegu i ciągną się wzdłuż niego (długość ≥ 3 × grubość). Litera ucięta kadrem sterczy
     * w poprzek brzegu albo wychodzi poza pas, więc zostaje.
     */
    fun dropEdgeScraps(img: Raster): Raster {
        val w = img.w; val h = img.h; val n = w * h
        val solid = BooleanArray(n) { Raster.alpha(img.px[it]) > 128 }
        val sx = max(3, (EDGE_STRIP * w).toInt()); val sy = max(3, (EDGE_STRIP * h).toInt())
        val seen = BooleanArray(n); val queue = IntArray(n)
        val out = img.px.copyOf()
        var changed = false
        for (start in 0 until n) {
            if (!solid[start] || seen[start]) continue
            var head = 0; var tail = 0
            queue[tail++] = start; seen[start] = true
            var x0 = w; var y0 = h; var x1 = -1; var y1 = -1
            while (head < tail) {
                val p = queue[head++]; val x = p % w; val y = p / w
                x0 = min(x0, x); x1 = max(x1, x); y0 = min(y0, y); y1 = max(y1, y)
                for (dy in -1..1) for (dx in -1..1) {
                    val nx = x + dx; val ny = y + dy
                    if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                    val q = ny * w + nx
                    if (solid[q] && !seen[q]) { seen[q] = true; queue[tail++] = q }
                }
            }
            val bw = x1 - x0 + 1; val bh = y1 - y0 + 1
            val scrap = (x1 < sx || x0 >= w - sx) && bh >= 3 * bw || (y1 < sy || y0 >= h - sy) && bw >= 3 * bh
            if (!scrap) continue
            changed = true
            for (i in 0 until tail) {
                val p = queue[i]; val x = p % w; val y = p / w
                for (dy in -2..2) for (dx in -2..2) {
                    val nx = x + dx; val ny = y + dy
                    if (nx in 0 until w && ny in 0 until h) out[ny * w + nx] = 0
                }
            }
        }
        return if (changed) Raster(w, h, out) else img
    }

    /** Pas przy brzegu wyniku (ułamek boku), w którym szukane są kawałki ramy. */
    const val EDGE_STRIP = 0.012f

    /**
     * Usuwa drobinki (2b-B2): spójne (8-sąsiedztwo) plamki kryjące (krycie > 50 %) mniejsze niż [minArea] px razem z ich
     * półprzezroczystą otoczką. To ziarno tablicy i pył kredy; kropka nad „i” albo przecinek dziesiętny są większe.
     */
    fun despeckle(img: Raster, minArea: Int): Raster {
        val w = img.w; val h = img.h; val n = w * h
        val solid = BooleanArray(n) { Raster.alpha(img.px[it]) > 128 }
        val seen = BooleanArray(n)
        val queue = IntArray(n)
        val out = img.px.copyOf()
        var changed = false
        for (start in 0 until n) {
            if (!solid[start] || seen[start]) continue
            var head = 0; var tail = 0
            queue[tail++] = start; seen[start] = true
            while (head < tail) {
                val p = queue[head++]
                val x = p % w; val y = p / w
                for (dy in -1..1) for (dx in -1..1) {
                    val nx = x + dx; val ny = y + dy
                    if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                    val q = ny * w + nx
                    if (solid[q] && !seen[q]) { seen[q] = true; queue[tail++] = q }
                }
            }
            if (tail >= minArea) continue
            changed = true
            for (i in 0 until tail) {
                val p = queue[i]; val x = p % w; val y = p / w
                for (dy in -1..1) for (dx in -1..1) {
                    val nx = x + dx; val ny = y + dy
                    if (nx in 0 until w && ny in 0 until h && !solid[ny * w + nx]) out[ny * w + nx] = 0
                }
                out[p] = 0
            }
        }
        return if (changed) Raster(w, h, out) else img
    }

    /** Najmniejsza plamka kredy przy szerokości wyniku [w]: 6 px przy 1600 px, skalowane z polem obrazu, co najmniej 3. */
    fun speckArea(w: Int): Int = max(3, (SPECK_AREA * (w / 1600f) * (w / 1600f)).toInt())

    const val SPECK_AREA = 6f

    /** Z [core] zostają tylko spójne kawałki (8-sąsiedztwo) o rozpiętości (szer. albo wys. ramki) co najmniej [minExtent]. */
    private fun longCores(core: BooleanArray, w: Int, h: Int, minExtent: Int): BooleanArray {
        val out = BooleanArray(core.size)
        val seen = BooleanArray(core.size)
        val queue = IntArray(core.size)
        for (start in core.indices) {
            if (!core[start] || seen[start]) continue
            var head = 0; var tail = 0
            queue[tail++] = start; seen[start] = true
            var x0 = w; var y0 = h; var x1 = 0; var y1 = 0
            while (head < tail) {
                val p = queue[head++]
                val x = p % w; val y = p / w
                x0 = min(x0, x); x1 = max(x1, x); y0 = min(y0, y); y1 = max(y1, y)
                for (dy in -1..1) for (dx in -1..1) {
                    val nx = x + dx; val ny = y + dy
                    if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                    val q = ny * w + nx
                    if (core[q] && !seen[q]) { seen[q] = true; queue[tail++] = q }
                }
            }
            if (max(x1 - x0, y1 - y0) + 1 >= minExtent) for (i in 0 until tail) out[queue[i]] = true
        }
        return out
    }

    /** Rdzeń dłuższy niż ten ułamek boku obrazu to listwa, rama albo ściana, a nie litera. */
    const val FRAME_EXTENT = 0.08f

    /** Połówka grubości, powyżej której jasny obszar to rama albo listwa, nie kreda: 0,4 % szerokości wyniku, co najmniej 4 px. */
    fun thickLimit(w: Int): Float = max(4f, w * THICK_FRACTION)

    const val THICK_FRACTION = 0.004f

    /** Próg dociągania bladej kredy jako ułamek progu tła. */
    const val FAINT_FRACTION = 0.6f

    /** Czy punkt (x, y) leży w wielokącie (reguła parzystości przecięć). */
    fun inside(poly: FloatArray, x: Float, y: Float): Boolean {
        var hit = false
        val n = poly.size / 2
        var j = n - 1
        for (i in 0 until n) {
            val xi = poly[2 * i]; val yi = poly[2 * i + 1]; val xj = poly[2 * j]; val yj = poly[2 * j + 1]
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) hit = !hit
            j = i
        }
        return hit
    }

    /** Gumka wielokątna: piksele w którymkolwiek z [polygons] (x0, y0, x1, y1…) stają się przezroczyste. */
    fun erase(src: Raster, polygons: List<FloatArray>): Raster {
        if (polygons.isEmpty()) return src
        val out = src.px.copyOf()
        for (poly in polygons) {
            if (poly.size < 6) continue
            var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE; var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
            for (i in 0 until poly.size / 2) {
                minX = min(minX, poly[2 * i]); maxX = max(maxX, poly[2 * i])
                minY = min(minY, poly[2 * i + 1]); maxY = max(maxY, poly[2 * i + 1])
            }
            for (y in max(0, minY.toInt())..min(src.h - 1, maxY.toInt())) for (x in max(0, minX.toInt())..min(src.w - 1, maxX.toInt())) {
                if (inside(poly, x + 0.5f, y + 0.5f)) out[y * src.w + x] = 0
            }
        }
        return Raster(src.w, src.h, out)
    }
}

/** Ustawienia oczyszczania: każdy krok osobno do wyłączenia (decyzja planu nocy: pełna customizacja). */
data class BoardOptions(
    val straighten: Boolean = true,
    val crop: Boolean = true,
    val flatten: Boolean = true,
    val sharpen: Boolean = true,
    val autoCut: Boolean = true,
    val cut: Float = 0.25f,
    val solid: Float = 0.85f,
    /** null: kolory kredy z oryginału; inaczej jeden kolor wszystkich kresek (np. biały albo grafit na jasną stronę). */
    val tint: Int? = null,
    /** null: wykryj (ciemna tablica albo biała); true: kreda na ciemnym; false: pisak na białym. */
    val black: Boolean? = null,
    val maxSide: Int = 2400,
    /** Dociągaj bladą kredę połączoną z wyraźną (histereza, 2b-B2). */
    val faint: Boolean = true,
    /** Próg lokalny: w spokojnych miejscach tablicy niższy niż globalny, do 0,6 × (2c-B9, [Chalk.localCut]). */
    val localCut: Boolean = false,
    /** Usuń jasne pasy grubsze niż kreda: listwy, ramy, ścianę przy brzegu (2b-B2). */
    val frames: Boolean = true,
    /** Usuń drobinki mniejsze niż kropka (ziarno tablicy, pył kredy) (2b-B2). */
    val specks: Boolean = true,
    /** „Edytowalne kreski” jako gładkie krzywe Béziera; wyłączone — łamane po pikselach szkieletu jak dawniej (2b-B4). */
    val curves: Boolean = true,
    /** Papier strony, na którą trafi wynik (ARGB); kreski dostają do niego kontrast ≥ 3:1 ([ChalkInk], 2c-B4). null: bez zmian. */
    val paper: Int? = null,
)

/**
 * Wynik potoku: obraz kredy na przezroczystym tle i użyte parametry (do pokazania w panelu i powtórzenia). [quad] jest
 * w pikselach zdjęcia po pomniejszeniu do `maxSide`; [source] mówi, skąd w zdjęciu pochodzi każdy piksel [image].
 */
class BoardResult(val image: Raster, val quad: Quad?, val black: Boolean, val cut: Float, val source: BoardSource? = null)

/**
 * Tablica po krokach geometrii i filtrów (prostowanie, kadr, wyrównanie, wyostrzenie), przed kolorem do alfy. To
 * najdroższa część potoku: panel liczy ją raz, a suwaki progu, krycia i koloru wołają tylko [BoardPipeline.finish].
 *
 * Mapowanie wstecz ([toPhoto]): piksel wyniku → kadr ([cropX], [cropY]) → prostowanie ([warp]: prostokąt → czworokąt
 * w pikselach pomniejszonego zdjęcia [fitW]×[fitH]) → ułamek szerokości i wysokości zdjęcia. Dzięki temu kreski z
 * wektoryzacji (K2) leżą na kredzie w zdjęciu, a nie na wyprostowanym i przyciętym obrazie rozciągniętym na zdjęcie.
 */
class BoardSource(
    val image: Raster,
    val black: Boolean,
    val quad: Quad?,
    val flattened: Boolean,
    val fitW: Int,
    val fitH: Int,
    val cropX: Int = 0,
    val cropY: Int = 0,
    val warp: Homography? = null,
    /** Przy kilku zdjęciach (K3): które było odniesieniem (do niego odnosi się [toPhoto]) i które wypadły. */
    val ref: Int = 0,
    val skipped: List<Int> = emptyList(),
) {
    /** Auto-próg zależy tylko od obrazu po filtrach, więc liczy się raz na źródło (nie przy każdym ruchu suwaka). */
    val autoCut: Float by lazy { Chalk.autoCut(image, black, flattened) }

    /**
     * Punkt wyniku w układzie indeksów pikseli (środek piksela i to i) jako (u, v) ∈ [0, 1]² zdjęcia: 0, 0 to lewy
     * górny róg zdjęcia, 1, 1 prawy dolny. Bez prostowania i kadru: u = (x + 0.5) / [fitW], jak dawne `toStrokes`.
     */
    fun toPhoto(x: Float, y: Float): FloatArray {
        var px = x + cropX; var py = y + cropY
        warp?.let { val p = it.map(px.toDouble(), py.toDouble()); px = p[0].toFloat(); py = p[1].toFloat() }
        return floatArrayOf((px + 0.5f) / fitW, (py + 0.5f) / fitH)
    }
}

object BoardPipeline {
    /**
     * Pełny potok kredy: pomniejszenie → prostowanie (narożniki [quad] albo zgadnięte) → kadr do tablicy → wyrównanie
     * oświetlenia → wyostrzenie → kolor do alfy → gumka. Kolejność jak w kreda.py (`build_board_source`, `recompute`).
     * [quad] jest w pikselach [photo]; wielokąty gumki [erase] w pikselach wyniku (użytkownik maluje je na podglądzie).
     */
    fun run(photo: Raster, opt: BoardOptions = BoardOptions(), quad: Quad? = null, erase: List<FloatArray> = emptyList()): BoardResult =
        finish(prepare(photo, opt, quad), opt, erase)

    /** Kroki geometrii i filtrów; wynik zależy od [photo], [quad] i przełączników kroków, nie od progu, krycia ani koloru. */
    fun prepare(photo: Raster, opt: BoardOptions = BoardOptions(), quad: Quad? = null): BoardSource {
        var img = photo.fit(opt.maxSide)
        val fitW = img.w; val fitH = img.h
        val k = img.w.toFloat() / photo.w
        val black = opt.black ?: !looksWhite(img)
        var used: Quad? = null
        var back: Homography? = null
        if (opt.straighten) {
            // 3c-B3 (K3): skrzyżowane narożniki (niewypukły czworokąt) dawały prawie pusty obraz — wtedy zgadnięte.
            val q = quad?.scaled(k)?.takeIf { it.isConvex() } ?: BoardGeometry.guessQuad(img, black)
            used = q
            // 3c-B3 (K5): cały kadr („tablicy nie znaleziono”) nie przepróbkowuje zdjęcia o piksel mniejszego.
            if (!q.isFull(img.w, img.h)) {
                val (w, h) = q.rectSize()
                back = Homography.of(Quad.of(0f, 0f, w - 1f, 0f, w - 1f, h - 1f, 0f, h - 1f), q)
                img = BoardGeometry.warp(img, q)
            }
        }
        var cx = 0; var cy = 0
        if (opt.crop) cropToBoardAt(img, black).let { (cut, x, y) -> img = cut; cx = x; cy = y }
        if (opt.flatten) img = Chalk.flatten(img, black)
        if (opt.sharpen) img = Chalk.sharpen(img)
        return BoardSource(img, black, used, opt.flatten, fitW, fitH, cx, cy, back)
    }

    /** Kolor do alfy i gumka na gotowym [src]: tanie, więc podgląd liczy to przy każdym ruchu suwaka. */
    fun finish(src: BoardSource, opt: BoardOptions = BoardOptions(), erase: List<FloatArray> = emptyList()): BoardResult {
        val cut = if (opt.autoCut) src.autoCut else opt.cut
        val map = if (opt.localCut) Chalk.localCut(src.image, src.black, cut) else null
        var out = Chalk.toAlpha(src.image, src.black, cut, opt.solid, opt.tint, low = if (opt.faint) cut * Chalk.FAINT_FRACTION else null, cutMap = map)
        if (opt.frames) out = Chalk.dropEdgeScraps(Chalk.dropRails(Chalk.dropThick(out, Chalk.thickLimit(out.w))))
        if (opt.specks) out = Chalk.despeckle(out, Chalk.speckArea(out.w))
        if (erase.isNotEmpty()) out = Chalk.erase(out, erase)
        opt.paper?.let { out = ChalkInk.fitImage(out, it) }
        return BoardResult(out, src.quad, src.black, cut, src)
    }

    /** Kilka zdjęć tej samej tablicy w pamięci (testy); aplikacja woła [prepareMany] z wczytywaniem po jednym. */
    fun runMany(photos: List<Raster>, opt: BoardOptions = BoardOptions()): BoardResult {
        require(photos.isNotEmpty()) { "brak zdjęć" }
        if (photos.size == 1) return run(photos[0], opt)
        return finish(prepareMany(photos.size, opt) { photos[it] }!!, opt)
    }

    /**
     * Kilka zdjęć tej samej tablicy (K3): każde wyprostowane własnymi narożnikami do rozmiaru zdjęcia odniesienia,
     * mediana usuwa to, co zasłaniało tablicę tylko na części zdjęć, potem reszta potoku jak dla jednego zdjęcia.
     *
     * [load] wczytuje zdjęcie i, i od razu po wyprostowaniu zostaje tylko jego klatka: w pamięci nie siedzi 6 pełnych
     * zdjęć naraz. Zdjęcie, na którym tablicy nie znaleziono (narożniki = cały kadr), wypada, jeśli na innych ją
     * znaleziono: jego klatka nie byłaby wyrównana i mediana dałaby „duchy” ([BoardSource.skipped]). Gdy tablicy nie
     * znaleziono nigdzie, wszystkie idą całe, jak przy wyłączonym prostowaniu (statyw). null, gdy zdjęcia nie da się wczytać.
     */
    fun prepareMany(count: Int, opt: BoardOptions = BoardOptions(), load: (Int) -> Raster?): BoardSource? {
        require(count > 0) { "brak zdjęć" }
        var black = opt.black
        val frames = ArrayList<Raster>()
        val skipped = ArrayList<Int>()
        // zdjęcia bez znalezionej tablicy, zanim wiadomo, czy na którymkolwiek ją znaleziono
        val whole = ArrayList<Pair<Int, Raster>>()
        var ref = -1; var refQuad: Quad? = null; var refSize = 0 to 0; var refFit = 0 to 0
        for (i in 0 until count) {
            val img = load(i)?.fit(opt.maxSide) ?: return null
            val b = black ?: !looksWhite(img)
            black = b
            val q = if (opt.straighten) BoardGeometry.guessQuad(img, b) else Quad.full(img.w, img.h)
            if (opt.straighten && q.isFull(img.w, img.h)) {
                if (ref >= 0) skipped += i else whole += i to img
                continue
            }
            if (ref < 0) {
                ref = i; refQuad = q; refSize = q.rectSize(); refFit = img.w to img.h
                skipped += whole.map { it.first }
                whole.clear()
            }
            frames += BoardGeometry.warp(img, q, refSize)
        }
        if (ref < 0) {
            val (first, img) = whole[0]
            // 3c-B3 (K5, K6): klatki całych zdjęć bez przepróbkowania i bez drugiej kopii, gdy rozmiar się zgadza.
            ref = first; refQuad = Quad.full(img.w, img.h); refSize = img.w to img.h; refFit = img.w to img.h
            for ((_, w) in whole) frames += if (w.w to w.h == refSize) w else BoardGeometry.warp(w, Quad.full(w.w, w.h), refSize)
            whole.clear()
        }
        val merged = BoardStack.median(frames)
        frames.clear()
        // 3c-B3 (K2): wyprostowana tablica bywa dłuższa niż maxSide; drugie pomniejszenie rozjechałoby `back` z wynikiem.
        val s = prepare(merged, opt.copy(straighten = false, black = black, maxSide = max(opt.maxSide, max(merged.w, merged.h))))
        val (w, h) = refSize
        val back = Homography.of(Quad.of(0f, 0f, w - 1f, 0f, w - 1f, h - 1f, 0f, h - 1f), refQuad!!)
        return BoardSource(s.image, s.black, refQuad, s.flattened, refFit.first, refFit.second, s.cropX, s.cropY, back, ref, skipped)
    }

    /** Biała tablica (pisak): jasne, mało nasycone tło przeważa w środku obrazu. */
    fun looksWhite(img: Raster): Boolean {
        val s = img.fit(120)
        var light = 0; var total = 0
        for (y in s.h / 5 until s.h * 4 / 5) for (x in s.w / 5 until s.w * 4 / 5) {
            total++
            if (Raster.luma(s[x, y]) > 0.6f) light++
        }
        return total > 0 && light > total / 2
    }

    /** Kadr do prostokąta otaczającego tablicę; poza tablicą przezroczysto (rama, ściana, półka). */
    fun cropToBoard(img: Raster, black: Boolean): Raster = cropToBoardAt(img, black).first

    /** To samo z lewym górnym rogiem kadru w pikselach [img] (do mapowania wyniku z powrotem na zdjęcie). */
    fun cropToBoardAt(img: Raster, black: Boolean): Triple<Raster, Int, Int> {
        val small = img.fit(BoardGeometry.WORK)
        val mask = BoardGeometry.boardMask(small, black) ?: return Triple(img, 0, 0)
        if (mask.count { it } < 0.1 * mask.size) return Triple(img, 0, 0)
        var x0 = small.w; var y0 = small.h; var x1 = 0; var y1 = 0
        for (p in mask.indices) if (mask[p]) {
            val x = p % small.w; val y = p / small.w
            x0 = min(x0, x); y0 = min(y0, y); x1 = max(x1, x); y1 = max(y1, y)
        }
        val k = img.w.toFloat() / small.w
        val cx0 = (x0 * k).toInt().coerceIn(0, img.w - 1); val cy0 = (y0 * k).toInt().coerceIn(0, img.h - 1)
        val cx1 = ((x1 + 1) * k).toInt().coerceIn(cx0 + 1, img.w); val cy1 = ((y1 + 1) * k).toInt().coerceIn(cy0 + 1, img.h)
        val out = img.crop(cx0, cy0, cx1, cy1)
        for (y in 0 until out.h) for (x in 0 until out.w) {
            val sx = ((cx0 + x) / k).toInt().coerceIn(0, small.w - 1); val sy = ((cy0 + y) / k).toInt().coerceIn(0, small.h - 1)
            if (!mask[sy * small.w + sx]) out[x, y] = out[x, y] and 0x00FFFFFF
        }
        return Triple(out, cx0, cy0)
    }
}

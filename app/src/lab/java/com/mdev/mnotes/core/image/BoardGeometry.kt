package com.mdev.mnotes.core.image

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Cztery narożniki tablicy w kolejności: lewy górny, prawy górny, prawy dolny, lewy dolny (x, y w pikselach). */
class Quad(val pts: FloatArray) {
    init { require(pts.size == 8) { "czworokąt ma 4 punkty" } }

    fun x(i: Int) = pts[2 * i]
    fun y(i: Int) = pts[2 * i + 1]
    fun scaled(k: Float) = Quad(FloatArray(8) { pts[it] * k })

    /** Każdy narożnik przesunięty o [d] pikseli w stronę środka czworokąta. */
    fun inset(d: Float): Quad {
        val cx = (x(0) + x(1) + x(2) + x(3)) / 4f; val cy = (y(0) + y(1) + y(2) + y(3)) / 4f
        return Quad(FloatArray(8) { i ->
            val p = i / 2
            val dx = cx - x(p); val dy = cy - y(p)
            val len = hypot(dx, dy).coerceAtLeast(1e-3f)
            if (i % 2 == 0) x(p) + dx / len * d else y(p) + dy / len * d
        })
    }
    private fun side(i: Int, j: Int) = hypot(x(j) - x(i), y(j) - y(i))

    /** Rozmiar prostokąta po wyprostowaniu: dłuższe z przeciwległych boków, bez powiększania. */
    fun rectSize(): Pair<Int, Int> =
        max(8, max(side(0, 1), side(3, 2)).roundToInt()) to max(8, max(side(0, 3), side(1, 2)).roundToInt())

    /** Czy to [full] dla obrazu [w]×[h], czyli „tablicy nie znaleziono” w [BoardGeometry.guessQuad]. */
    fun isFull(w: Int, h: Int): Boolean {
        val f = full(w, h).pts
        return pts.indices.all { abs(pts[it] - f[it]) < 0.5f }
    }

    /** Czy czworokąt jest wypukły (wszystkie iloczyny wektorowe kolejnych boków mają ten sam znak). */
    fun isConvex(): Boolean {
        var pos = 0; var neg = 0
        for (i in 0 until 4) {
            val a = i; val b = (i + 1) % 4; val c = (i + 2) % 4
            val cross = (x(b) - x(a)) * (y(c) - y(b)) - (y(b) - y(a)) * (x(c) - x(b))
            if (cross > 0) pos++ else if (cross < 0) neg++
        }
        return pos == 4 || neg == 4
    }

    companion object {
        fun of(vararg xy: Float) = Quad(xy)
        fun full(w: Int, h: Int) = Quad(floatArrayOf(0f, 0f, w - 1f, 0f, w - 1f, h - 1f, 0f, h - 1f))

        /**
         * Narożniki chmury punktów (x, y) jak `_order_quad` w kreda.py: lewy górny ma najmniejsze x + y, prawy dolny
         * największe, prawy górny największe x − y, lewy dolny najmniejsze.
         */
        fun ofExtremes(xs: IntArray, ys: IntArray, n: Int): Quad {
            var tl = 0; var br = 0; var tr = 0; var bl = 0
            for (i in 1 until n) {
                val s = xs[i] + ys[i]; val d = xs[i] - ys[i]
                if (s < xs[tl] + ys[tl]) tl = i
                if (s > xs[br] + ys[br]) br = i
                if (d > xs[tr] - ys[tr]) tr = i
                if (d < xs[bl] - ys[bl]) bl = i
            }
            return of(xs[tl].toFloat(), ys[tl].toFloat(), xs[tr].toFloat(), ys[tr].toFloat(), xs[br].toFloat(), ys[br].toFloat(), xs[bl].toFloat(), ys[bl].toFloat())
        }
    }
}

/** Przekształcenie rzutowe 3×3 (h[8] = 1): (x, y) → ((h0x + h1y + h2)/d, (h3x + h4y + h5)/d), d = h6x + h7y + 1. */
class Homography(val h: DoubleArray) {
    fun map(x: Double, y: Double): DoubleArray {
        val d = h[6] * x + h[7] * y + 1.0
        return doubleArrayOf((h[0] * x + h[1] * y + h[2]) / d, (h[3] * x + h[4] * y + h[5]) / d)
    }

    companion object {
        /** Z czterech par punktów: src[i] → dst[i]; null, gdy punkty są współliniowe. */
        fun of(src: Quad, dst: Quad): Homography? {
            val a = Array(8) { DoubleArray(9) }
            for (i in 0 until 4) {
                val x = src.x(i).toDouble(); val y = src.y(i).toDouble()
                val u = dst.x(i).toDouble(); val v = dst.y(i).toDouble()
                a[2 * i] = doubleArrayOf(x, y, 1.0, 0.0, 0.0, 0.0, -u * x, -u * y, u)
                a[2 * i + 1] = doubleArrayOf(0.0, 0.0, 0.0, x, y, 1.0, -v * x, -v * y, v)
            }
            val sol = solve(a) ?: return null
            return Homography(DoubleArray(9) { if (it < 8) sol[it] else 1.0 })
        }

        /** Eliminacja Gaussa z wyborem elementu głównego; macierz rozszerzona n×(n+1). */
        private fun solve(a: Array<DoubleArray>): DoubleArray? {
            val n = a.size
            for (c in 0 until n) {
                var p = c
                for (r in c + 1 until n) if (abs(a[r][c]) > abs(a[p][c])) p = r
                if (abs(a[p][c]) < 1e-12) return null
                val t = a[p]; a[p] = a[c]; a[c] = t
                for (r in 0 until n) if (r != c) {
                    val f = a[r][c] / a[c][c]
                    if (f != 0.0) for (k in c..n) a[r][k] -= f * a[c][k]
                }
            }
            return DoubleArray(n) { a[it][n] / a[it][it] }
        }
    }
}

object BoardGeometry {

    /** Prostuje czworokąt [quad] do prostokąta o rozmiarze z jego boków; próbkowanie dwuliniowe. */
    fun warp(src: Raster, quad: Quad, size: Pair<Int, Int> = quad.rectSize()): Raster {
        val (w, h) = size
        val dst = Quad.of(0f, 0f, w - 1f, 0f, w - 1f, h - 1f, 0f, h - 1f)
        val back = Homography.of(dst, quad) ?: return src.copy()
        val out = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val p = back.map(x.toDouble(), y.toDouble())
            out[y * w + x] = sample(src, p[0].toFloat(), p[1].toFloat())
        }
        return Raster(w, h, out)
    }

    /** Próbka dwuliniowa w (x, y); poza obrazem przezroczysty piksel. */
    fun sample(r: Raster, x: Float, y: Float): Int {
        if (x < -0.5f || y < -0.5f || x > r.w - 0.5f || y > r.h - 0.5f) return 0
        val fx = x.coerceIn(0f, r.w - 1f); val fy = y.coerceIn(0f, r.h - 1f)
        val x0 = fx.toInt(); val y0 = fy.toInt()
        val x1 = min(r.w - 1, x0 + 1); val y1 = min(r.h - 1, y0 + 1)
        val tx = fx - x0; val ty = fy - y0
        val c00 = r[x0, y0]; val c10 = r[x1, y0]; val c01 = r[x0, y1]; val c11 = r[x1, y1]
        fun ch(shift: Int): Int {
            val a = ((c00 shr shift) and 255) * (1 - tx) + ((c10 shr shift) and 255) * tx
            val b = ((c01 shr shift) and 255) * (1 - tx) + ((c11 shr shift) and 255) * tx
            return (a * (1 - ty) + b * ty + 0.5f).toInt().coerceIn(0, 255)
        }
        return (ch(24) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /** Szerokość roboczego podglądu przy szukaniu tablicy (jak 1000 px w kreda.py, mniej, bo telefon). */
    const val WORK = 400

    /** Najmniejsza różnica jasności tła tablicy i otoczenia, przy której maska ma sens. */
    const val MIN_CONTRAST = 0.12

    /**
     * Maska „to jest tablica” w obrazie [r] (już pomniejszonym): tło (otwarcie usuwa kredę) ciemniejsze od progu Otsu
     * i mało nasycone; zostaje największa plama z wypełnionymi dziurami. Dla białej tablicy odwrotnie.
     */
    fun boardMask(r: Raster, black: Boolean): BooleanArray? {
        val w = r.w; val h = r.h
        val v = r.plane { Raster.maxRgb(it) }
        val rad = max(2, w / 70)
        val bg = if (black) BoardFilters.open(v, w, h, rad) else BoardFilters.close(v, w, h, rad)
        val sat = r.plane { Raster.maxRgb(it) - Raster.minRgb(it) }
        val satBg = BoardFilters.minMax(sat, w, h, rad, false)
        val thr = BoardFilters.otsu(bg)
        val mask = BooleanArray(w * h) { (if (black) bg[it] < thr else bg[it] >= thr) && satBg[it] < 0.35f }
        // Otsu dzieli na dwie części nawet sam szum (zdjęcie, na którym tablica wypełnia kadr): bez wyraźnej różnicy
        // jasności między „tablicą” a resztą nie ma czego wycinać.
        var inS = 0.0; var inN = 0; var outS = 0.0; var outN = 0
        for (i in mask.indices) if (mask[i]) { inS += bg[i]; inN++ } else { outS += bg[i]; outN++ }
        if (inN == 0 || outN == 0 || abs(outS / outN - inS / inN) < MIN_CONTRAST) return null
        val blob = BoardFilters.largestFilled(mask, w, h) ?: return null
        return if (blob.count { it } < 0.03 * w * h) null else blob
    }

    /**
     * Narożniki tablicy na zdjęciu: maska na podglądzie [WORK] px (bez ściany w kolorze tablicy, [clipToContent]),
     * narożniki ze skrajnych punktów plamy, przeliczone
     * na pełny rozmiar. Gdy nic sensownego nie wyszło, cały obraz (użytkownik i tak może przesunąć narożniki).
     */
    fun guessQuad(src: Raster, black: Boolean = true): Quad {
        val small = src.fit(WORK)
        val k = src.w.toFloat() / small.w
        val found = boardMask(small, black) ?: return Quad.full(src.w, src.h)
        val mask = clipToContent(small, found, black) ?: found
        val n = mask.count { it }
        val xs = IntArray(n); val ys = IntArray(n)
        var i = 0
        for (p in mask.indices) if (mask[p]) { xs[i] = p % small.w; ys[i] = p / small.w; i++ }
        // Skrajne piksele plamy leżą na samej krawędzi tablicy; wcięcie o 1.5 piksela podglądu odcina pasek ściany,
        // który po prostowaniu dawałby jasną ramkę dookoła kredy.
        val q = Quad.ofExtremes(xs, ys, n).inset(1.5f).scaled(k)
        return if (q.isConvex()) q else Quad.full(src.w, src.h)
    }

    /** Wiersz (kolumna) „ma pismo”, gdy ma co najmniej tyle śladów co typowy zapisany wiersz (75. percentyl). */
    const val INK_ROW = 0.2

    /** Przerwa między liniami tekstu (ułamek boku), przez którą odcinek pisma jeszcze się nie rozpada. */
    const val INK_GAP = 0.05

    /** Odcinek z pismem liczy się do zakresu, gdy ma co najmniej tyle masy śladów co najcięższy (osobne bloki wzorów tak, smugi nie). */
    const val INK_BLOCK = 0.10

    /** Margines wokół zakresu pisma: ułamek boku podglądu (skrajne znaki i krawędź tablicy tuż za nimi zostają). */
    const val CONTENT_MARGIN = 0.025f

    /** Prosty odcinek śladu dłuższy niż tyle boku to listwa, rama albo noga stojaka, nie pismo. */
    const val RAIL_MIN = 0.12

    /** Najwyższe nasycenie śladu pisma (max − min RGB); biała i szara kreda ma kilka setnych, skóra i ubrania więcej. */
    const val INK_SAT = 0.2f

    /** Bok maski przesuwa się dopiero o tyle boku podglądu; mniej to szum, a dobra maska zostaje. */
    const val CLIP_MIN = 0.02

    /** Bezpiecznik: za przesuniętym bokiem może zostać najwyżej tyle śladów pisma (inaczej bok zostaje, jak był). */
    const val INK_OUTSIDE = 0.03

    /** Ślady pisma na podglądzie ([ink]) i ich liczba w każdym wierszu ([rows]) i kolumnie ([cols]). */
    class Ink(val ink: BooleanArray, val rows: IntArray, val cols: IntArray) {
        val total = rows.sum()
    }

    /**
     * Pismo na podglądzie: cienkie (top-hat), prawie bezbarwne ([INK_SAT]) ślady jaśniejsze od otoczenia (dla białej
     * tablicy ciemniejsze) bez długich poziomych i pionowych odcinków ([RAIL_MIN]: listwy, półka na kredę, ramy).
     */
    fun inkMap(r: Raster, mask: BooleanArray, black: Boolean): Ink? {
        val w = r.w; val h = r.h
        val v = r.plane { Raster.maxRgb(it) }
        val rad = max(2, w / 70)
        val bg = if (black) BoardFilters.open(v, w, h, rad) else BoardFilters.close(v, w, h, rad)
        val sat = r.plane { Raster.maxRgb(it) - Raster.minRgb(it) }
        val hat = FloatArray(w * h) { if (mask[it] && sat[it] < INK_SAT) abs(v[it] - bg[it]) else 0f }
        val inMask = FloatArray(mask.count { it }).also { a -> var j = 0; for (i in mask.indices) if (mask[i]) a[j++] = hat[i] }
        if (inMask.isEmpty()) return null
        val thr = max(0.04f, BoardFilters.median(inMask) + 4f * BoardFilters.madSigma(inMask))
        val ink = BooleanArray(w * h) { hat[it] > thr }
        dropRuns(ink, w, h, horizontal = true, (RAIL_MIN * w).toInt())
        dropRuns(ink, w, h, horizontal = false, (RAIL_MIN * h).toInt())
        val cols = IntArray(w); val rows = IntArray(h)
        for (y in 0 until h) for (x in 0 until w) if (ink[y * w + x]) { cols[x]++; rows[y]++ }
        return Ink(ink, rows, cols)
    }

    /**
     * Czyści w [ink] proste odcinki (wiersze albo kolumny) dłuższe niż [min] pikseli. Odcinek liczy się w paśmie 3 px
     * (linia ± 1), bo rama na zdjęciu bywa lekko pochylona.
     */
    private fun dropRuns(ink: BooleanArray, w: Int, h: Int, horizontal: Boolean, min: Int) {
        val lines = if (horizontal) h else w
        val len = if (horizontal) w else h
        fun at(l: Int, i: Int) = if (horizontal) l * w + i else i * w + l
        val source = ink.copyOf()
        fun band(l: Int, i: Int) = (max(0, l - 1)..min(lines - 1, l + 1)).any { source[at(it, i)] }
        for (l in 0 until lines) {
            var start = -1
            for (i in 0..len) {
                if (i < len && band(l, i)) { if (start < 0) start = i; continue }
                if (start >= 0 && i - start >= min) for (j in start until i) ink[at(l, j)] = false
                start = -1
            }
        }
    }

    /**
     * Zakres pisma (x0, y0, x1, y1 na podglądzie): w pionie i w poziomie osobno odcinki wierszy z pismem ([INK_ROW])
     * sklejane przez przerwy do [INK_GAP], od pierwszego do ostatniego odcinka z masą ≥ [INK_BLOCK] najcięższego;
     * rozproszone ślady przy brzegu maski odpadają. Margines [CONTENT_MARGIN]. `null`, gdy śladów prawie nie ma.
     */
    fun contentBox(r: Raster, mask: BooleanArray, black: Boolean): IntArray? = inkMap(r, mask, black)?.let { boxOf(it, r.w, r.h) }

    private fun boxOf(ink: Ink, w: Int, h: Int): IntArray? {
        if (ink.total < 0.002 * w * h) return null
        val (x0, x1) = inkSpan(ink.cols, (INK_GAP * w).toInt())
        val (y0, y1) = inkSpan(ink.rows, (INK_GAP * h).toInt())
        val m = (CONTENT_MARGIN * max(w, h)).roundToInt().coerceAtLeast(1)
        return intArrayOf(max(0, x0 - m), max(0, y0 - m), min(w - 1, x1 + m), min(h - 1, y1 + m))
    }

    /** Zakres profilu [counts] (wygładzonego oknem 5): odcinki ≥ [INK_ROW] typowej wartości, przerwy ≤ [gap], [INK_BLOCK]. */
    private fun inkSpan(counts: IntArray, gap: Int): Pair<Int, Int> {
        val n = counts.size
        val smooth = FloatArray(n) { i -> (max(0, i - 2)..min(n - 1, i + 2)).sumOf { counts[it] }.toFloat() / 5f }
        val nonzero = smooth.filter { it > 0f }.sorted()
        val on = (INK_ROW * nonzero[(nonzero.size * 3 / 4).coerceAtMost(nonzero.size - 1)]).toFloat()
        // Odcinki: (początek, koniec, masa śladów razem z przerwami w środku).
        val spans = mutableListOf<Triple<Int, Int, Int>>()
        var start = -1; var mass = 0; var lastOn = -1
        for (i in 0 until n) {
            if (smooth[i] < on) continue
            if (start >= 0 && i - lastOn > gap) { spans += Triple(start, lastOn, mass); start = -1 }
            if (start < 0) { start = i; mass = counts[i] } else for (j in lastOn + 1..i) mass += counts[j]
            lastOn = i
        }
        if (start >= 0) spans += Triple(start, lastOn, mass)
        val heaviest = spans.maxOf { it.third }
        val kept = spans.filter { it.third >= INK_BLOCK * heaviest }
        return kept.first().first to kept.last().second
    }

    /**
     * Maska bez ściany, ekranu i głów, które mają kolor tablicy i łączą się z nią (zdjęcia 20260525_*, 20260324_154544):
     * przycięta do [clipEdges]. `null`, gdy nic się nie zmienia albo zostałoby za mało.
     */
    fun clipToContent(r: Raster, mask: BooleanArray, black: Boolean): BooleanArray? {
        val w = r.w
        val (x0, y0, x1, y1) = clipEdges(r, mask, black)?.toList() ?: return null
        val clipped = BooleanArray(mask.size) { mask[it] && it % w in x0..x1 && it / w in y0..y1 }
        return if (clipped.count { it } < 0.03 * mask.size) null else clipped
    }

    /**
     * Granice przycięcia (x0, y0, x1, y1 na podglądzie): bok przesuwa się do [contentBox] tylko wtedy, gdy maska dochodzi
     * tym bokiem do brzegu zdjęcia (tablica nie ma tam w kadrze własnej krawędzi, więc „tablicą” staje się ściana), ruch
     * to ≥ [CLIP_MIN] boku, a za nowym bokiem zostaje ≤ [INK_OUTSIDE] śladów pisma. Bok kończący się w kadrze to prawdziwa
     * krawędź tablicy i zostaje razem z perspektywą. `null`, gdy żaden bok się nie przesuwa.
     */
    fun clipEdges(r: Raster, mask: BooleanArray, black: Boolean): IntArray? {
        val w = r.w; val h = r.h
        val ink = inkMap(r, mask, black) ?: return null
        val box = boxOf(ink, w, h) ?: return null
        var mx0 = w; var my0 = h; var mx1 = -1; var my1 = -1
        for (i in mask.indices) if (mask[i]) {
            val x = i % w; val y = i / w
            if (x < mx0) mx0 = x; if (x > mx1) mx1 = x; if (y < my0) my0 = y; if (y > my1) my1 = y
        }
        val limit = INK_OUTSIDE * ink.total
        val minX = (CLIP_MIN * w).toInt(); val minY = (CLIP_MIN * h).toInt()
        val x0 = if (mx0 <= 1 && box[0] >= minX && (0 until box[0]).sumOf { ink.cols[it] } <= limit) box[0] else 0
        val x1 = if (mx1 >= w - 2 && box[2] <= w - 1 - minX && (box[2] + 1 until w).sumOf { ink.cols[it] } <= limit) box[2] else w - 1
        val y0 = if (my0 <= 1 && box[1] >= minY && (0 until box[1]).sumOf { ink.rows[it] } <= limit) box[1] else 0
        val y1 = if (my1 >= h - 2 && box[3] <= h - 1 - minY && (box[3] + 1 until h).sumOf { ink.rows[it] } <= limit) box[3] else h - 1
        return if (x0 == 0 && y0 == 0 && x1 == w - 1 && y1 == h - 1) null else intArrayOf(x0, y0, x1, y1)
    }

    /**
     * Czy to zdjęcie ciemnej tablicy (propozycja „Oczyść tablicę”): w środkowej części obrazu przeważa ciemne, mało
     * nasycone tło. Zrzuty ekranu i kartki dają false.
     */
    fun looksLikeDarkBoard(src: Raster): Boolean {
        val s = src.fit(160)
        val bg = BoardFilters.open(s.plane { Raster.maxRgb(it) }, s.w, s.h, max(1, s.w / 60))
        var dark = 0; var total = 0
        for (y in s.h / 5 until s.h * 4 / 5) for (x in s.w / 5 until s.w * 4 / 5) {
            val c = s[x, y]
            total++
            if (bg[y * s.w + x] < 0.42f && Raster.maxRgb(c) - Raster.minRgb(c) < 0.3f) dark++
        }
        return total > 0 && dark >= 0.55 * total
    }
}

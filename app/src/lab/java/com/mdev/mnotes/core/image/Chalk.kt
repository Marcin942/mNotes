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
     * Próg tła z ziarna samego obrazu, w [0.10, 0.50]: mediana + K × σ szumu. Jak w kreda.py σ jest odporne (z
     * percentyli, nie z odchylenia), ale liczone z górnej połowy rozkładu: po wyrównaniu oświetlenia część tła jest
     * obcięta do zera i zwykłe MAD zaniżało σ, przez co szum przechodził jako półprzezroczysta mgła.
     */
    fun autoCut(src: Raster, black: Boolean): Float {
        val visible = src.px.filter { Raster.alpha(it) > 127 }
        if (visible.isEmpty()) return AUTO_CUT_MIN
        val step = max(1, visible.size / 400_000)
        val v = FloatArray((visible.size + step - 1) / step) { strength(visible[it * step], black) }
        v.sort()
        fun pct(p: Double) = v[((v.size - 1) * p).toInt()]
        val med = pct(0.5)
        val sigma = if (med > 1f / 255f) pct(0.8413) - med else pct(0.9772) / 2f
        return (med + AUTO_CUT_K * sigma).coerceIn(AUTO_CUT_MIN, AUTO_CUT_MAX)
    }

    /**
     * Kolor do alfy względem czarnego albo białego tła. Poniżej [cut] piksel znika, powyżej [solid] jest pełny;
     * kolor kredy odtwarzany jak w GIMP-ie (dzielenie przez krycie), a z [tint] wszystkie kreski dostają ten kolor.
     */
    fun toAlpha(src: Raster, black: Boolean, cut: Float, solid: Float, tint: Int? = null): Raster {
        val s = max(solid, cut + 1e-3f)
        val out = IntArray(src.px.size)
        for (i in out.indices) {
            val c = src.px[i]
            val a = strength(c, black)
            val alpha = ((a - cut) / (s - cut)).coerceIn(0f, 1f) * (Raster.alpha(c) / 255f)
            if (alpha <= 0f) { out[i] = 0; continue }
            out[i] = if (tint != null) (Raster.q(alpha) shl 24) or (tint and 0xFFFFFF) else {
                val safe = max(a, 1e-4f)
                fun col(v: Int) = if (black) v / 255f / safe else 1f - (1f - v / 255f) / safe
                Raster.rgbF(col(Raster.red(c)), col(Raster.green(c)), col(Raster.blue(c)), alpha)
            }
        }
        return Raster(src.w, src.h, out)
    }

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
)

/** Wynik potoku: obraz kredy na przezroczystym tle i użyte parametry (do pokazania w panelu i powtórzenia). */
class BoardResult(val image: Raster, val quad: Quad?, val black: Boolean, val cut: Float)

object BoardPipeline {
    /**
     * Pełny potok kredy: pomniejszenie → prostowanie (narożniki [quad] albo zgadnięte) → kadr do tablicy → wyrównanie
     * oświetlenia → wyostrzenie → kolor do alfy → gumka. Kolejność jak w kreda.py (`build_board_source`, `recompute`).
     * [quad] jest w pikselach [photo]; wielokąty gumki [erase] w pikselach wyniku (użytkownik maluje je na podglądzie).
     */
    fun run(photo: Raster, opt: BoardOptions = BoardOptions(), quad: Quad? = null, erase: List<FloatArray> = emptyList()): BoardResult {
        var img = photo.fit(opt.maxSide)
        val k = img.w.toFloat() / photo.w
        val black = opt.black ?: !looksWhite(img)
        var used: Quad? = null
        if (opt.straighten) {
            used = quad?.scaled(k) ?: BoardGeometry.guessQuad(img, black)
            img = BoardGeometry.warp(img, used)
        }
        if (opt.crop) img = cropToBoard(img, black)
        if (opt.flatten) img = Chalk.flatten(img, black)
        if (opt.sharpen) img = Chalk.sharpen(img)
        val cut = if (opt.autoCut) Chalk.autoCut(img, black) else opt.cut
        var out = Chalk.toAlpha(img, black, cut, opt.solid, opt.tint)
        if (erase.isNotEmpty()) out = Chalk.erase(out, erase)
        return BoardResult(out, used, black, cut)
    }

    /**
     * Kilka zdjęć tej samej tablicy (K3): każde wyprostowane własnymi narożnikami do rozmiaru pierwszego, mediana
     * usuwa to, co zasłaniało tablicę tylko na części zdjęć, potem reszta potoku jak dla jednego zdjęcia.
     */
    fun runMany(photos: List<Raster>, opt: BoardOptions = BoardOptions()): BoardResult {
        require(photos.isNotEmpty()) { "brak zdjęć" }
        if (photos.size == 1) return run(photos[0], opt)
        val fitted = photos.map { it.fit(opt.maxSide) }
        val black = opt.black ?: !looksWhite(fitted[0])
        val quads = fitted.map { if (opt.straighten) BoardGeometry.guessQuad(it, black) else Quad.full(it.w, it.h) }
        val merged = BoardStack.alignedMedian(fitted, quads)
        val res = run(merged, opt.copy(straighten = false, black = black))
        return BoardResult(res.image, quads[0], black, res.cut)
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
    fun cropToBoard(img: Raster, black: Boolean): Raster {
        val small = img.fit(BoardGeometry.WORK)
        val mask = BoardGeometry.boardMask(small, black) ?: return img
        if (mask.count { it } < 0.1 * mask.size) return img
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
        return out
    }
}

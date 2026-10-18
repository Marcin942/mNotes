package com.mdev.mnotes.core.model

import com.mdev.mnotes.core.geom.Affine
import com.mdev.mnotes.core.geom.Bounds
import com.mdev.mnotes.core.pen.LineStyle
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Identyfikatory elementów żyją tylko w pamięci (nie są zapisywane do pliku). */
object Ids {
    private val counter = AtomicInteger(1)
    fun next(): Int = counter.getAndIncrement()
}

/**
 * Rodzaje piór. Kody są częścią formatu pliku — nie zmieniać.
 * Liczbowe strojenie (zakresy, taper, wygładzanie) żyje w `PenSpec`, nie tutaj.
 */
enum class PenKind(val code: Int) {
    BALLPOINT(0),
    FOUNTAIN(1),
    BRUSH(2),
    HIGHLIGHTER(3),
    PENCIL(4);

    companion object {
        fun fromCode(code: Int): PenKind = entries.firstOrNull { it.code == code } ?: BALLPOINT
    }
}

/** Element strony: niezmienny, więc bezpiecznie współdzielony między wątkami renderowania. */
sealed class Element {
    abstract val id: Int
    abstract val bounds: Bounds
    abstract val locked: Boolean

    abstract fun withId(newId: Int): Element
    abstract fun withLocked(value: Boolean): Element

    /** Zwraca kopię przekształconą macierzą [m]; id zostaje zachowane. */
    abstract fun transformed(m: Affine): Element
}

class Stroke(
    override val id: Int,
    val pen: PenKind,
    val color: Int,
    /** Grubość maksymalna (przy współczynniku 1), w pt. */
    val width: Float,
    /**
     * Trójki x, y, p w punktach strony; p to GOTOWY współczynnik grubości (0..1) po krzywej nacisku,
     * prędkości i zbieganiu końcówek — rysowanie liczy tylko `width * p`.
     */
    val pts: FloatArray,
    override val locked: Boolean = false,
    /**
     * Łamana z uchwytami: każdy punkt [pts] to wierzchołek, odcinki są proste (kształt z „rysuj i przytrzymaj”).
     * Zamknięta, gdy pierwszy punkt = ostatni. Wierzchołki można po fakcie przesuwać (NoteView).
     */
    val poly: Boolean = false,
    /**
     * Elipsa z „rysuj i przytrzymaj” (49 punktów, punkt 0 na osi głównej, co 1/4 kolejna oś): kształt zachowuje
     * proporcje do zmiany szerokości i wysokości, a uchwyty ramki (NoteView) rozciągają go niezależnie po osiach.
     */
    val ellipse: Boolean = false,
    /** Linia ciągła, przerywana albo kropkowana; wzór liczy się przy rysowaniu ([StrokeDash]), punkty są te same. */
    val line: LineStyle = LineStyle.SOLID,
) : Element() {

    val count: Int get() = pts.size / 3

    /** Liczba przesuwalnych wierzchołków: zamknięta łamana nie liczy powtórzonego pierwszego punktu. */
    val vertexCount: Int get() = if (closed) count - 1 else count

    val closed: Boolean get() = poly && count > 3 && pts[0] == pts[3 * count - 3] && pts[1] == pts[3 * count - 2]

    /** Kopia z wierzchołkiem [i] przesuniętym na (x, y); w zamkniętej łamanej przesuwa też powtórzony pierwszy punkt. */
    fun withVertex(i: Int, x: Float, y: Float): Stroke {
        val out = pts.copyOf()
        out[3 * i] = x; out[3 * i + 1] = y
        if (closed && i == 0) { out[3 * count - 3] = x; out[3 * count - 2] = y }
        return Stroke(id, pen, color, width, out, locked, poly, ellipse, line)
    }

    override val bounds: Bounds = run {
        var l = Float.POSITIVE_INFINITY; var t = Float.POSITIVE_INFINITY
        var r = Float.NEGATIVE_INFINITY; var b = Float.NEGATIVE_INFINITY
        var i = 0
        while (i < pts.size) {
            val x = pts[i]; val y = pts[i + 1]
            if (x < l) l = x
            if (x > r) r = x
            if (y < t) t = y
            if (y > b) b = y
            i += 3
        }
        if (l > r) Bounds.EMPTY else Bounds(l, t, r, b).inflate(width * 0.5f + 0.5f)
    }

    /** Zakreślacz leży we własnej warstwie i miesza się z papierem mnożeniem (patrz renderer). */
    val multiply: Boolean get() = pen == PenKind.HIGHLIGHTER

    override fun withId(newId: Int): Stroke = Stroke(newId, pen, color, width, pts, locked, poly, ellipse, line)
    override fun withLocked(value: Boolean): Stroke = Stroke(id, pen, color, width, pts, value, poly, ellipse, line)

    fun withStyle(color: Int = this.color, width: Float = this.width, pen: PenKind = this.pen, line: LineStyle = this.line): Stroke =
        Stroke(id, pen, color, width, pts, locked, poly, ellipse, line)

    override fun transformed(m: Affine): Stroke {
        val out = FloatArray(pts.size)
        var i = 0
        while (i < pts.size) {
            val x = pts[i]; val y = pts[i + 1]
            out[i] = m.mapX(x, y)
            out[i + 1] = m.mapY(x, y)
            out[i + 2] = pts[i + 2]
            i += 3
        }
        return Stroke(id, pen, color, width * m.scaleFactor, out, locked, poly, ellipse, line)
    }
}

/**
 * Wypełnienie z narzędzia „Wypełnienie” (wiaderko): obszar ograniczony konturami [rings] (pary x, y w pt strony),
 * wypełniany regułą parzysto-nieparzystą, więc pierścień wewnątrz pierścienia to dziura. [hl] = jak zakreślacz
 * (półprzezroczyste, mnożenie z papierem); inaczej kolor jednolity.
 */
class FillEl(
    override val id: Int,
    val color: Int,
    val hl: Boolean,
    val rings: List<FloatArray>,
    override val locked: Boolean = false,
) : Element() {

    override val bounds: Bounds = run {
        var l = Float.POSITIVE_INFINITY; var t = Float.POSITIVE_INFINITY
        var r = Float.NEGATIVE_INFINITY; var b = Float.NEGATIVE_INFINITY
        for (ring in rings) {
            var i = 0
            while (i < ring.size) {
                val x = ring[i]; val y = ring[i + 1]
                if (x < l) l = x
                if (x > r) r = x
                if (y < t) t = y
                if (y > b) b = y
                i += 2
            }
        }
        if (l > r) Bounds.EMPTY else Bounds(l, t, r, b)
    }

    /** Liczba wszystkich wierzchołków (do lasso i limitów). */
    val vertexCount: Int get() = rings.sumOf { it.size / 2 }

    /** Czy punkt leży w wypełnieniu (parzysto-nieparzyście po wszystkich pierścieniach). */
    fun containsPoint(x: Float, y: Float): Boolean {
        if (!bounds.contains(x, y)) return false
        var inside = false
        for (ring in rings) {
            val n = ring.size / 2
            var j = n - 1
            for (i in 0 until n) {
                val xi = ring[2 * i]; val yi = ring[2 * i + 1]
                val xj = ring[2 * j]; val yj = ring[2 * j + 1]
                if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside
                j = i
            }
        }
        return inside
    }

    fun copy(id: Int = this.id, color: Int = this.color, hl: Boolean = this.hl, rings: List<FloatArray> = this.rings, locked: Boolean = this.locked) =
        FillEl(id, color, hl, rings, locked)

    override fun withId(newId: Int): FillEl = copy(id = newId)
    override fun withLocked(value: Boolean): FillEl = copy(locked = value)

    override fun transformed(m: Affine): FillEl = copy(rings = rings.map { ring ->
        val out = FloatArray(ring.size)
        var i = 0
        while (i < ring.size) {
            val x = ring[i]; val y = ring[i + 1]
            out[i] = m.mapX(x, y); out[i + 1] = m.mapY(x, y)
            i += 2
        }
        out
    })
}

/** Wspólna geometria elementów-ramek (tekst, obraz): środek, rozmiar, obrót. */
sealed class BoxElement : Element() {
    abstract val cx: Float
    abstract val cy: Float
    abstract val w: Float
    abstract val h: Float
    abstract val rot: Float

    override val bounds: Bounds
        get() {
            val c = abs(cos(rot)); val s = abs(sin(rot))
            val hw = (w * c + h * s) * 0.5f
            val hh = (w * s + h * c) * 0.5f
            return Bounds(cx - hw, cy - hh, cx + hw, cy + hh)
        }

    /** Czy punkt (x, y) leży w obróconym prostokącie (z marginesem [slop]). */
    fun containsPoint(x: Float, y: Float, slop: Float = 0f): Boolean {
        val dx = x - cx; val dy = y - cy
        val c = cos(-rot); val s = sin(-rot)
        val lx = dx * c - dy * s
        val ly = dx * s + dy * c
        return abs(lx) <= w * 0.5f + slop && abs(ly) <= h * 0.5f + slop
    }

    protected fun mapBox(m: Affine): BoxGeom {
        val c = cos(rot); val s = sin(rot)
        val ux = m.a * c + m.c * s
        val uy = m.b * c + m.d * s
        val vx = -m.a * s + m.c * c
        val vy = -m.b * s + m.d * c
        return BoxGeom(
            m.mapX(cx, cy), m.mapY(cx, cy),
            w * hypot(ux, uy), h * hypot(vx, vy),
            atan2(uy, ux),
            hypot(ux, uy),
        )
    }

    protected class BoxGeom(val cx: Float, val cy: Float, val w: Float, val h: Float, val rot: Float, val sx: Float)
}

class TextBox(
    override val id: Int,
    override val cx: Float,
    override val cy: Float,
    override val w: Float,
    override val h: Float,
    override val rot: Float,
    val text: String,
    /** Rozmiar czcionki w punktach. */
    val size: Float,
    val color: Int,
    val family: Int = FAMILY_SANS,
    val style: Int = 0,
    val align: Int = ALIGN_START,
    /** Kolor tła pola (ARGB); 0 = brak. */
    val fill: Int = 0,
    override val locked: Boolean = false,
    /** Skąd bierze się rozmiar pola: [FIT_HEIGHT] (stała szerokość, wysokość z tekstu), [FIT_AUTO] albo [FIT_FIXED]. */
    val fit: Int = FIT_HEIGHT,
    /** Mnożnik odstępu między wierszami (1 = domyślny dla czcionki); import z Samsung Notes używa 1,15. */
    val lineSpacing: Float = 1f,
) : BoxElement() {

    val bold: Boolean get() = style and STYLE_BOLD != 0
    val italic: Boolean get() = style and STYLE_ITALIC != 0
    val underline: Boolean get() = style and STYLE_UNDERLINE != 0
    val strike: Boolean get() = style and STYLE_STRIKE != 0

    override fun withId(newId: Int): TextBox = copy(id = newId)
    override fun withLocked(value: Boolean): TextBox = copy(locked = value)

    fun copy(
        id: Int = this.id, cx: Float = this.cx, cy: Float = this.cy, w: Float = this.w, h: Float = this.h,
        rot: Float = this.rot, text: String = this.text, size: Float = this.size, color: Int = this.color,
        family: Int = this.family, style: Int = this.style, align: Int = this.align, fill: Int = this.fill,
        locked: Boolean = this.locked, fit: Int = this.fit, lineSpacing: Float = this.lineSpacing,
    ) = TextBox(id, cx, cy, w, h, rot, text, size, color, family, style, align, fill, locked, fit, lineSpacing)

    override fun transformed(m: Affine): TextBox {
        val g = mapBox(m)
        // Rozmiar czcionki podąża za skalą wzdłuż osi tekstu; wysokość wynika z układu tekstu.
        return copy(cx = g.cx, cy = g.cy, w = g.w, h = h * g.sx, rot = g.rot, size = size * g.sx)
    }

    companion object {
        const val FAMILY_SANS = 0
        const val FAMILY_SERIF = 1
        const val FAMILY_MONO = 2
        const val FAMILY_HAND = 3

        const val STYLE_BOLD = 1
        const val STYLE_ITALIC = 2
        const val STYLE_UNDERLINE = 4
        const val STYLE_STRIKE = 8

        const val ALIGN_START = 0
        const val ALIGN_CENTER = 1
        const val ALIGN_END = 2

        /** Stała szerokość nadana ręcznie; wysokość wynika z tekstu. Tak działały pola przed wprowadzeniem trybów. */
        const val FIT_HEIGHT = 0
        /** Pole rośnie razem z tekstem aż do prawej krawędzi strony, potem zawija (domyślne dla nowych pól). */
        const val FIT_AUTO = 1
        /** Ręcznie ustawiona szerokość i wysokość; tekst, który się nie mieści, jest przycięty do ramki. */
        const val FIT_FIXED = 2
    }
}

/** Wycinek obrazu jako ułamki pliku (0..1): lewa, górna, prawa i dolna krawędź. */
class ImageCrop(val l: Float, val t: Float, val r: Float, val b: Float) {
    val isFull: Boolean get() = l <= 0f && t <= 0f && r >= 1f && b >= 1f
    val width: Float get() = r - l
    val height: Float get() = b - t

    override fun equals(other: Any?) = other is ImageCrop && l == other.l && t == other.t && r == other.r && b == other.b
    override fun hashCode() = ((l.hashCode() * 31 + t.hashCode()) * 31 + r.hashCode()) * 31 + b.hashCode()

    companion object {
        val FULL = ImageCrop(0f, 0f, 1f, 1f)
    }
}

class ImageEl(
    override val id: Int,
    /** Nazwa pliku zasobu w katalogu zeszytu. */
    val asset: String,
    override val cx: Float,
    override val cy: Float,
    override val w: Float,
    override val h: Float,
    override val rot: Float = 0f,
    val alpha: Float = 1f,
    override val locked: Boolean = false,
    /** Wycinek pliku, który obraz pokazuje (po przecięciu kawałki dzielą jeden plik). */
    val crop: ImageCrop = ImageCrop.FULL,
) : BoxElement() {

    override fun withId(newId: Int): ImageEl = copy(id = newId)
    override fun withLocked(value: Boolean): ImageEl = copy(locked = value)

    fun copy(
        id: Int = this.id, asset: String = this.asset, cx: Float = this.cx, cy: Float = this.cy,
        w: Float = this.w, h: Float = this.h, rot: Float = this.rot, alpha: Float = this.alpha,
        locked: Boolean = this.locked, crop: ImageCrop = this.crop,
    ) = ImageEl(id, asset, cx, cy, w, h, rot, alpha, locked, crop)

    override fun transformed(m: Affine): ImageEl {
        val g = mapBox(m)
        return copy(cx = g.cx, cy = g.cy, w = g.w, h = g.h, rot = g.rot)
    }
}

/**
 * Taśma do nauki (EDT-064): kolorowy prostokąt zakrywający to, co leży pod nim w kolejności rysowania.
 * [hidden] = nieprzezroczysta (zakrywa); odkryta jest tylko półprzezroczystym obrysem. Gumka jej nie rusza.
 */
class TapeEl(
    override val id: Int,
    override val cx: Float,
    override val cy: Float,
    override val w: Float,
    override val h: Float,
    override val rot: Float = 0f,
    val color: Int,
    val hidden: Boolean = true,
    override val locked: Boolean = false,
) : BoxElement() {

    override fun withId(newId: Int): TapeEl = copy(id = newId)
    override fun withLocked(value: Boolean): TapeEl = copy(locked = value)

    fun copy(
        id: Int = this.id, cx: Float = this.cx, cy: Float = this.cy, w: Float = this.w, h: Float = this.h,
        rot: Float = this.rot, color: Int = this.color, hidden: Boolean = this.hidden, locked: Boolean = this.locked,
    ) = TapeEl(id, cx, cy, w, h, rot, color, hidden, locked)

    override fun transformed(m: Affine): TapeEl {
        val g = mapBox(m)
        return copy(cx = g.cx, cy = g.cy, w = g.w, h = g.h, rot = g.rot)
    }
}

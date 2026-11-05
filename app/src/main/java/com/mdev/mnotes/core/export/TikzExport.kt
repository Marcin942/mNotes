package com.mdev.mnotes.core.export

import com.mdev.mnotes.core.geom.Bounds
import com.mdev.mnotes.core.model.Element
import com.mdev.mnotes.core.model.FillEl
import com.mdev.mnotes.core.model.ImageEl
import com.mdev.mnotes.core.model.PenKind
import com.mdev.mnotes.core.model.Stroke
import com.mdev.mnotes.core.model.TextBox
import com.mdev.mnotes.core.pen.LineStyle
import com.mdev.mnotes.core.pen.PenSpec
import com.mdev.mnotes.core.shapes.CurveFit
import com.mdev.mnotes.core.shapes.ShapeBox
import com.mdev.mnotes.core.text.MathText
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max

/**
 * FB13: rysunek ze strony jako kod TikZ do sprawozdania w LaTeX-u. Kształty z autokorekty wychodzą jako to, czym są
 * (`circle`, `ellipse`, `rectangle`, łamana), krzywe Béziera z tymi samymi uchwytami (`.. controls ..`), pismo odręczne
 * jako uproszczona łamana, a tekst z greką i indeksami wraca do zapisu LaTeX-a. Współrzędne w cm od lewego dolnego rogu
 * rysunku z osią y w górę, jak się pisze TikZ ręcznie, więc kod da się poprawiać; skala 1:1 z kartką.
 */
object TikzExport {
    /** cm na punkt strony (1 pt = 1/72 cala). */
    const val CM = 2.54 / 72.0
    /** Tolerancja upraszczania pisma (pt): poniżej grubości cienkiego pióra, więc litery zostają czytelne. */
    const val SIMPLIFY_PT = 0.35f
    /** Wypełnienie zamkniętej krzywej jak na ekranie (`PageRenderer.FILL_ALPHA`), żeby pod spodem było widać pismo. */
    private const val FILL_OPACITY = 0.28

    /** [code] — gotowy `tikzpicture`; [drawn] — elementy w kodzie; [skipped] — pominięte (taśma, pismo bez [freehand]). */
    class Result(val code: String, val drawn: Int, val skipped: Int)

    /** Kształt, który w TikZ ma własną postać (nie zwykła kreska odręczna). */
    fun isShape(s: Stroke) = s.poly || s.ellipse || s.ctrl != null

    /** [paper] — kolor tła pod rysunkiem (3c-A5: strona ciemna w eksporcie); null — bez tła, jak dotąd. */
    fun picture(els: List<Element>, freehand: Boolean = true, paper: Int? = null): Result {
        val use = els.filter { e -> if (e is Stroke) freehand || isShape(e) else e is TextBox || e is ImageEl || e is FillEl }
        var box = Bounds.EMPTY
        for (e in use) box = box.union(extent(e))
        val w = Writer(if (box.isEmpty) 0f else box.l, if (box.isEmpty) 0f else box.b)
        for (e in use) when (e) {
            is Stroke -> w.stroke(e)
            is TextBox -> w.text(e)
            is ImageEl -> w.image(e)
            is FillEl -> w.fill(e)
            else -> {}
        }
        val sb = StringBuilder()
        sb.append("% mNotes: rysunek w skali 1:1 (cm). Mniejszy: [scale=0.5, every node/.style={scale=0.5}]\n")
        sb.append("\\begin{tikzpicture}[line cap=round, line join=round]\n")
        for ((rgb, name) in w.colors) sb.append(String.format(Locale.ROOT, "\\definecolor{%s}{HTML}{%06X}\n", name, rgb))
        if (paper != null) {
            val pad = 0.3
            val bw = if (box.isEmpty) 0.0 else (box.r - box.l) * CM
            val bh = if (box.isEmpty) 0.0 else (box.b - box.t) * CM
            sb.append(String.format(Locale.ROOT, "\\definecolor{mnpaper}{HTML}{%06X}\n", paper and 0xFFFFFF))
            sb.append("\\fill[mnpaper] (${num(-pad)},${num(-pad)}) rectangle (${num(bw + pad)},${num(bh + pad)});\n")
        }
        sb.append(w.body)
        sb.append("\\end{tikzpicture}\n")
        return Result(sb.toString(), w.drawn, els.size - w.drawn)
    }

    /** Ramka samej geometrii: kreska bez poszerzenia o grubość pióra (jak w modelu), więc początek układu wypada na punkcie. */
    private fun extent(e: Element): Bounds {
        if (e !is Stroke || e.count == 0) return e.bounds
        var l = Float.POSITIVE_INFINITY; var t = Float.POSITIVE_INFINITY
        var r = Float.NEGATIVE_INFINITY; var b = Float.NEGATIVE_INFINITY
        for (k in 0 until e.count) {
            val x = e.pts[3 * k]; val y = e.pts[3 * k + 1]
            if (x < l) l = x
            if (x > r) r = x
            if (y < t) t = y
            if (y > b) b = y
        }
        return Bounds(l, t, r, b)
    }

    /** Plik `.tex` do skompilowania od razu (pdflatex): każdy rysunek to osobna strona `standalone`. */
    fun document(pictures: List<String>, title: String): String {
        val sb = StringBuilder()
        sb.append("% ").append(title.replace('\n', ' ')).append(" — eksport TikZ z mNotes\n")
        sb.append("\\documentclass[tikz,border=4pt]{standalone}\n\\usepackage[T1]{fontenc}\n\\begin{document}\n")
        for ((i, p) in pictures.withIndex()) sb.append("% strona ").append(i + 1).append('\n').append(p)
        sb.append("\\end{document}\n")
        return sb.toString()
    }

    /** Liczba w cm do kodu: dwa miejsca po przecinku (0,1 mm), bez zbędnych zer i bez „-0”. */
    fun num(v: Double): String {
        var s = String.format(Locale.ROOT, "%.2f", v)
        if ('.' in s) s = s.trimEnd('0').trimEnd('.')
        return if (s == "-0") "0" else s
    }

    /**
     * Tekst pola do LaTeX-a: znaki specjalne ucieczką, a to, co aplikacja zamieniła przy pisaniu (α, x², a₁, ≤, °),
     * z powrotem w trybie matematycznym — kolejne takie znaki w jednym `$…$`.
     */
    fun latexText(s: String): String {
        val sb = StringBuilder()
        var math = false
        fun open() { if (!math) { sb.append('$'); math = true } }
        var i = 0
        while (i < s.length) {
            val c = s[i]
            val cmd = MathText.latexOf(c)
            when {
                MathText.unSup(c) != null || MathText.unSub(c) != null -> {
                    open()
                    val up = MathText.unSup(c) != null
                    sb.append(if (up) "^{" else "_{")
                    while (i < s.length) { val u = (if (up) MathText.unSup(s[i]) else MathText.unSub(s[i])) ?: break; sb.append(u); i++ }
                    sb.append('}')
                    continue
                }
                cmd != null -> { open(); sb.append(cmd) }
                else -> {
                    if (math) { sb.append('$'); math = false }
                    sb.append(escape(c))
                }
            }
            i++
        }
        if (math) sb.append('$')
        return sb.toString()
    }

    private fun escape(c: Char): String = when (c) {
        '\\' -> "\\textbackslash{}"
        '{' -> "\\{"; '}' -> "\\}"; '$' -> "\\$"; '&' -> "\\&"; '%' -> "\\%"; '#' -> "\\#"; '_' -> "\\_"
        '^' -> "\\^{}"; '~' -> "\\~{}"; '\t' -> " "
        else -> c.toString()
    }

    /** Douglas–Peucker dla łamanej (pary x, y); końce zostają zawsze. */
    fun simplify(xy: FloatArray, eps: Float): FloatArray {
        val n = xy.size / 2
        if (n <= 2) return xy.copyOf()
        val keep = BooleanArray(n)
        keep[0] = true; keep[n - 1] = true
        val stack = ArrayDeque<Pair<Int, Int>>()
        stack.addLast(0 to n - 1)
        while (stack.isNotEmpty()) {
            val (a, b) = stack.removeLast()
            if (b - a < 2) continue
            val ax = xy[2 * a]; val ay = xy[2 * a + 1]; val dx = xy[2 * b] - ax; val dy = xy[2 * b + 1] - ay
            val len = hypot(dx, dy)
            var best = -1; var bd = eps
            for (k in a + 1 until b) {
                val px = xy[2 * k] - ax; val py = xy[2 * k + 1] - ay
                val d = if (len < 1e-6f) hypot(px, py) else abs(px * dy - py * dx) / len
                if (d > bd) { bd = d; best = k }
            }
            if (best >= 0) { keep[best] = true; stack.addLast(a to best); stack.addLast(best to b) }
        }
        val out = FloatArray(2 * keep.count { it })
        var j = 0
        for (k in 0 until n) if (keep[k]) { out[j++] = xy[2 * k]; out[j++] = xy[2 * k + 1] }
        return out
    }

    /** Zbiera kod rysunku; [ox], [oy] — lewy dolny róg rysunku w punktach strony. */
    private class Writer(val ox: Float, val oy: Float) {
        val body = StringBuilder()
        val colors = LinkedHashMap<Int, String>()
        var drawn = 0

        fun p(x: Float, y: Float) = "(" + num((x - ox) * CM) + "," + num((oy - y) * CM) + ")"

        fun color(argb: Int): String = when (val rgb = argb and 0xFFFFFF) {
            0 -> "black"
            0xFFFFFF -> "white"
            else -> colors.getOrPut(rgb) { "mn${colors.size + 1}" }
        }

        private fun opacity(argb: Int, pen: PenKind): Double {
            var a = (argb ushr 24) / 255.0
            if (pen == PenKind.HIGHLIGHTER) a *= PenSpec.of(PenKind.HIGHLIGHTER).alpha
            return a
        }

        private fun opts(s: Stroke, lw: Double): String {
            val o = arrayListOf(color(s.color), "line width=" + num(lw) + "pt")
            when (s.line) { LineStyle.DASHED -> o += "dashed"; LineStyle.DOTTED -> o += "dotted"; else -> {} }
            val a = opacity(s.color, s.pen)
            if (a < 0.995) o += "draw opacity=" + num(a)
            if (s.fill != 0) { o += "fill=" + color(s.fill); o += "fill opacity=" + num(FILL_OPACITY) }
            return o.joinToString(", ")
        }

        private fun cmd(s: Stroke) = if (s.fill != 0) "\\filldraw" else "\\draw"

        /** Punkty po kolei, po 6 w wierszu, żeby długa łamana dała się czytać w edytorze LaTeX-a. */
        private fun path(xy: FloatArray, closed: Boolean): String {
            val sb = StringBuilder()
            val n = xy.size / 2
            for (k in 0 until n) {
                if (k > 0) sb.append(if (k % 6 == 0) "\n  -- " else " -- ")
                sb.append(p(xy[2 * k], xy[2 * k + 1]))
            }
            if (closed) sb.append(" -- cycle")
            return sb.toString()
        }

        fun stroke(s: Stroke) {
            drawn++
            val c = s.ctrl
            when {
                c != null && c.size >= 8 -> bezier(s, c)
                s.ellipse -> ellipse(s)
                s.poly -> poly(s)
                else -> freehand(s)
            }
        }

        private fun bezier(s: Stroke, c: FloatArray) {
            val closed = CurveFit.closed(c)
            val segs = (c.size / 2 - 1) / 3
            val sb = StringBuilder(cmd(s)).append('[').append(opts(s, s.width.toDouble())).append("] ").append(p(c[0], c[1]))
            for (k in 0 until segs) {
                val o = 6 * k
                sb.append(if (k > 0) "\n  " else " ")
                sb.append(".. controls ").append(p(c[o + 2], c[o + 3])).append(" and ").append(p(c[o + 4], c[o + 5])).append(" .. ")
                sb.append(if (closed && k == segs - 1) "cycle" else p(c[o + 6], c[o + 7]))
            }
            body.append(sb).append(";\n")
        }

        private fun ellipse(s: Stroke) {
            val f = ShapeBox.frameOf(s.withLocked(false))
            if (f == null) { poly(s); return }
            val o = opts(s, s.width.toDouble())
            val c = p(f.cx, f.cy)
            if (abs(f.hw - f.hh) <= 0.01f * max(f.hw, f.hh)) {
                body.append(cmd(s)).append('[').append(o).append("] ").append(c).append(" circle [radius=").append(num((f.hw + f.hh) / 2 * CM)).append("];\n")
                return
            }
            // Strona ma oś y w dół, TikZ w górę: kąt zmienia znak; elipsa jest symetryczna, więc wystarczy (−90°, 90°].
            var deg = -Math.toDegrees(atan2(f.uy, f.ux).toDouble())
            while (deg > 90.0) deg -= 180.0
            while (deg <= -90.0) deg += 180.0
            val rot = if (abs(deg) < 0.05) "" else ", rotate around={" + num(deg) + ":" + c + "}"
            body.append(cmd(s)).append('[').append(o).append(rot).append("] ").append(c)
                .append(" ellipse [x radius=").append(num(f.hw * CM)).append(", y radius=").append(num(f.hh * CM)).append("];\n")
        }

        private fun poly(s: Stroke) {
            val n = s.count
            val pt = s.pts
            val o = opts(s, s.width.toDouble())
            val closed = s.closed
            // Prostokąt w osiach: `rectangle` czyta się od razu jako prostokąt i łatwo go poprawić o dwie liczby.
            if (closed && n == 5 && axisAligned(pt)) {
                body.append(cmd(s)).append('[').append(o).append("] ").append(p(pt[0], pt[1])).append(" rectangle ").append(p(pt[6], pt[7])).append(";\n")
                return
            }
            val m = if (closed) n - 1 else n
            val xy = FloatArray(2 * m) { i -> pt[3 * (i / 2) + i % 2] }
            body.append(cmd(s)).append('[').append(o).append("] ").append(path(xy, closed || s.fill != 0)).append(";\n")
        }

        private fun axisAligned(pt: FloatArray): Boolean {
            for (k in 0 until 4) {
                val dx = pt[3 * k + 3] - pt[3 * k]; val dy = pt[3 * k + 4] - pt[3 * k + 1]
                if (abs(dx) > 0.01f && abs(dy) > 0.01f) return false
            }
            return true
        }

        private fun freehand(s: Stroke) {
            val n = s.count
            if (n == 0) { drawn--; return }
            var pSum = 0.0
            for (k in 0 until n) pSum += s.pts[3 * k + 2]
            val lw = s.width * (pSum / n).coerceIn(0.05, 1.0)
            val xy = FloatArray(2 * n) { i -> s.pts[3 * (i / 2) + i % 2] }
            val simple = simplify(xy, SIMPLIFY_PT)
            if (simple.size <= 4 && hypot(simple[0] - simple[simple.size - 2], simple[1] - simple[simple.size - 1]) < 0.5f) {
                // Kropka (np. nad „i” albo punkt na wykresie): koło o średnicy kreski, nie odcinek zerowej długości.
                body.append("\\fill[").append(color(s.color)).append("] ").append(p(simple[0], simple[1])).append(" circle [radius=").append(num(lw / 2)).append("pt];\n")
                return
            }
            body.append(cmd(s)).append('[').append(opts(s, lw)).append("] ").append(path(simple, s.fill != 0)).append(";\n")
        }

        fun text(t: TextBox) {
            if (t.text.isBlank()) return
            drawn++
            val font = StringBuilder("\\fontsize{").append(num(t.size.toDouble())).append("}{").append(num(t.size * 1.2)).append("}\\selectfont")
            font.append(when (TextBox.generic(t.family)) { TextBox.FAMILY_SERIF -> "\\rmfamily"; TextBox.FAMILY_MONO -> "\\ttfamily"; else -> "\\sffamily" })
            if (t.bold) font.append("\\bfseries")
            if (t.italic) font.append("\\itshape")
            val o = arrayListOf(
                "text=" + color(t.color), "anchor=center", "inner sep=0pt", "align=left",
                "text width=" + num(t.w / t.stretch * CM) + "cm", "font=" + font,
            )
            // LP-4: tekst rozciągnięty w osi x łamie się na węższej szerokości i dostaje skalę poziomą
            if (t.stretch != 1f) o += "xscale=" + num(t.stretch.toDouble())
            val deg = -Math.toDegrees(t.rot.toDouble())
            if (abs(deg) >= 0.05) o += "rotate=" + num(deg)
            val txt = t.text.trimEnd().split('\n').joinToString("\\\\ ") { latexText(it) }
            body.append("\\node[").append(o.joinToString(", ")).append("] at ").append(p(t.cx, t.cy)).append(" {").append(txt).append("};\n")
        }

        fun image(e: ImageEl) {
            drawn++
            val b = e.bounds
            body.append("\\draw[gray, densely dashed] ").append(p(b.l, b.b)).append(" rectangle ").append(p(b.r, b.t))
                .append("; % obraz ").append(e.asset).append(": \\node at (…) {\\includegraphics[width=").append(num(b.w * CM)).append("cm]{plik}};\n")
        }

        fun fill(f: FillEl) {
            val rings = f.rings.filter { it.size >= 6 }
            if (rings.isEmpty()) return
            drawn++
            val o = arrayListOf(color(f.color), "even odd rule")
            if (f.hl) o += "fill opacity=" + num(PenSpec.of(PenKind.HIGHLIGHTER).alpha.toDouble())
            body.append("\\fill[").append(o.joinToString(", ")).append("] ")
            body.append(rings.joinToString("\n  ") { path(simplify(it, SIMPLIFY_PT), true) }).append(";\n")
        }
    }
}

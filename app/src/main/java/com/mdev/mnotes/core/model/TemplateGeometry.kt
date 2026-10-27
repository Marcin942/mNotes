package com.mdev.mnotes.core.model

import kotlin.math.max
import kotlin.math.min

/**
 * Geometria tła strony jako płaskie tablice prymitywów (w pt), gotowa do `drawLines`/`drawPoints`.
 * Dzięki temu rysowanie ekranu, eksport PDF i testy korzystają z tego samego źródła prawdy.
 */
class TemplateGeom(
    /** Cienkie linie: x1,y1,x2,y2 … */
    val lines: FloatArray,
    /** Linie wyróżnione (marginesy, podziały): x1,y1,x2,y2 … */
    val heavy: FloatArray,
    /** Kropki: x,y … */
    val dots: FloatArray,
    /** Puste kwadraty (pola wyboru): l,t,r,b … */
    val boxes: FloatArray,
) {
    val isEmpty: Boolean get() = lines.isEmpty() && heavy.isEmpty() && dots.isEmpty() && boxes.isEmpty()

    companion object {
        val NONE = TemplateGeom(FloatArray(0), FloatArray(0), FloatArray(0), FloatArray(0))
    }
}

object TemplateGeometry {

    private const val SQRT3_2 = 0.8660254f

    fun build(spec: PageSpec): TemplateGeom {
        if (spec.template == Template.BLANK) return TemplateGeom.NONE
        val sp = max(spec.spacing, 3f)
        return when (spec.template) {
            Template.BLANK -> TemplateGeom.NONE
            Template.LINED -> lined(spec, sp)
            Template.GRID -> grid(spec, sp)
            Template.DOTS -> dots(spec, sp)
            Template.ISO_DOTS -> isoDots(spec, sp)
            Template.ISO_GRID -> isoGrid(spec, sp)
            Template.CORNELL -> cornell(spec, sp)
            Template.STAFF -> staff(spec, sp)
            Template.CHECKLIST -> checklist(spec, sp)
            Template.MILLIMETER -> millimeter(spec, max(spec.spacing, 10f))
        }
    }

    /** Papier milimetrowy: grube linie co [sp] (domyślnie 1 cm), cienkie co [sp]/10, wyśrodkowane jak kratka. */
    private fun millimeter(s: PageSpec, sp: Float): TemplateGeom {
        val o = Out()
        val minor = sp / 10f
        val x0 = (s.w % sp) / 2f
        val y0 = (s.h % sp) / 2f
        var i = 0
        var x = x0
        while (x <= s.w - x0 + 0.01f) { if (i % 10 == 0) o.heavyLine(x, y0, x, s.h - y0) else o.line(x, y0, x, s.h - y0); x += minor; i++ }
        i = 0
        var y = y0
        while (y <= s.h - y0 + 0.01f) { if (i % 10 == 0) o.heavyLine(x0, y, s.w - x0, y) else o.line(x0, y, s.w - x0, y); y += minor; i++ }
        return o.done()
    }

    private class Out {
        var lines = FloatArray(64); var nl = 0
        var heavy = FloatArray(16); var nh = 0
        var dots = FloatArray(64); var nd = 0
        var boxes = FloatArray(16); var nb = 0

        fun line(x1: Float, y1: Float, x2: Float, y2: Float) {
            if (nl + 4 > lines.size) lines = lines.copyOf(lines.size * 2)
            lines[nl++] = x1; lines[nl++] = y1; lines[nl++] = x2; lines[nl++] = y2
        }

        fun heavyLine(x1: Float, y1: Float, x2: Float, y2: Float) {
            if (nh + 4 > heavy.size) heavy = heavy.copyOf(heavy.size * 2)
            heavy[nh++] = x1; heavy[nh++] = y1; heavy[nh++] = x2; heavy[nh++] = y2
        }

        fun dot(x: Float, y: Float) {
            if (nd + 2 > dots.size) dots = dots.copyOf(dots.size * 2)
            dots[nd++] = x; dots[nd++] = y
        }

        fun box(l: Float, t: Float, r: Float, b: Float) {
            if (nb + 4 > boxes.size) boxes = boxes.copyOf(boxes.size * 2)
            boxes[nb++] = l; boxes[nb++] = t; boxes[nb++] = r; boxes[nb++] = b
        }

        fun done() = TemplateGeom(lines.copyOf(nl), heavy.copyOf(nh), dots.copyOf(nd), boxes.copyOf(nb))
    }

    private fun lined(s: PageSpec, sp: Float): TemplateGeom {
        val o = Out()
        val top = sp * 2f
        var y = top
        while (y <= s.h - sp * 0.5f) {
            o.line(0f, y, s.w, y)
            y += sp
        }
        if (s.marginLine) {
            val mx = max(48f, sp * 3f)
            o.heavyLine(mx, 0f, mx, s.h)
        }
        return o.done()
    }

    /** Siatka wyśrodkowana, żeby nie zostawały niepełne kratki tylko po jednej stronie. */
    private fun grid(s: PageSpec, sp: Float): TemplateGeom {
        val o = Out()
        val x0 = (s.w % sp) / 2f
        val y0 = (s.h % sp) / 2f
        var x = x0
        while (x <= s.w + 0.01f) { o.line(x, 0f, x, s.h); x += sp }
        var y = y0
        while (y <= s.h + 0.01f) { o.line(0f, y, s.w, y); y += sp }
        return o.done()
    }

    private fun dots(s: PageSpec, sp: Float): TemplateGeom {
        val o = Out()
        val x0 = (s.w % sp) / 2f
        val y0 = (s.h % sp) / 2f
        var y = y0
        while (y <= s.h + 0.01f) {
            var x = x0
            while (x <= s.w + 0.01f) { o.dot(x, y); x += sp }
            y += sp
        }
        return o.done()
    }

    /** Siatka trójkątna (przesunięte rzędy) — do rysunku izometrycznego. */
    private fun isoDots(s: PageSpec, sp: Float): TemplateGeom {
        val o = Out()
        val rowH = sp * SQRT3_2
        var row = 0
        var y = rowH * 0.5f
        while (y <= s.h) {
            var x = if (row % 2 == 0) sp * 0.5f else sp
            while (x <= s.w) { o.dot(x, y); x += sp }
            y += rowH
            row++
        }
        return o.done()
    }

    /** Linie pionowe oraz dwie rodziny linii pod kątem ±30° od poziomu (papier izometryczny). */
    private fun isoGrid(s: PageSpec, sp: Float): TemplateGeom {
        val o = Out()
        val colW = sp * SQRT3_2 * 2f
        var x = colW / 2f
        while (x < s.w) { o.line(x, 0f, x, s.h); x += colW }
        val tan30 = 0.57735026f
        val step = sp * 2f
        // Rodzina w dół i w górę: y = y0 ± x * tan30, przycięta do strony.
        var c = -s.w * tan30
        while (c <= s.h) {
            clipLine(o, 0f, c, s.w, c + s.w * tan30, s.w, s.h)
            c += step
        }
        c = 0f
        while (c <= s.h + s.w * tan30) {
            clipLine(o, 0f, c, s.w, c - s.w * tan30, s.w, s.h)
            c += step
        }
        return o.done()
    }

    private fun cornell(s: PageSpec, sp: Float): TemplateGeom {
        val o = Out()
        val header = s.h * 0.11f
        val summary = s.h * 0.18f
        val cueW = s.w * 0.30f
        val bottom = s.h - summary
        o.heavyLine(0f, header, s.w, header)
        o.heavyLine(0f, bottom, s.w, bottom)
        o.heavyLine(cueW, header, cueW, bottom)
        var y = header + sp
        while (y < bottom - sp * 0.3f) {
            o.line(cueW, y, s.w, y)
            y += sp
        }
        return o.done()
    }

    /** Pięciolinie: [spacing] to odstęp między liniami jednej pięciolinii. */
    private fun staff(s: PageSpec, sp: Float): TemplateGeom {
        val o = Out()
        val systemH = sp * 4f
        val pitch = sp * 10f
        val side = min(s.w * 0.06f, 36f)
        var top = sp * 6f
        while (top + systemH <= s.h - sp * 3f) {
            for (i in 0..4) o.line(side, top + i * sp, s.w - side, top + i * sp)
            o.heavyLine(side, top, side, top + systemH)
            o.heavyLine(s.w - side, top, s.w - side, top + systemH)
            top += pitch
        }
        return o.done()
    }

    private fun checklist(s: PageSpec, sp: Float): TemplateGeom {
        val o = Out()
        val boxSize = sp * 0.62f
        val left = max(28f, sp)
        var y = sp * 2f
        while (y <= s.h - sp * 0.5f) {
            val bx = left
            o.box(bx, y - boxSize - sp * 0.12f, bx + boxSize, y - sp * 0.12f)
            o.line(bx + boxSize + sp * 0.5f, y, s.w - left, y)
            y += sp
        }
        return o.done()
    }

    /** Liang–Barsky: dodaje do [o] odcinek przycięty do prostokąta 0..w × 0..h. */
    private fun clipLine(o: Out, x1: Float, y1: Float, x2: Float, y2: Float, w: Float, h: Float) {
        var t0 = 0f
        var t1 = 1f
        val dx = x2 - x1
        val dy = y2 - y1
        val p = floatArrayOf(-dx, dx, -dy, dy)
        val q = floatArrayOf(x1, w - x1, y1, h - y1)
        for (i in 0 until 4) {
            if (p[i] == 0f) {
                if (q[i] < 0f) return
            } else {
                val r = q[i] / p[i]
                if (p[i] < 0f) {
                    if (r > t1) return
                    if (r > t0) t0 = r
                } else {
                    if (r < t0) return
                    if (r < t1) t1 = r
                }
            }
        }
        if (t1 - t0 < 1e-4f) return
        o.line(x1 + t0 * dx, y1 + t0 * dy, x1 + t1 * dx, y1 + t1 * dy)
    }

}

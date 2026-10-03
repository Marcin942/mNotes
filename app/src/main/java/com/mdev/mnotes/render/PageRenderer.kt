package com.mdev.mnotes.render

import android.graphics.Bitmap
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.os.Build
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.mdev.mnotes.core.geom.Bounds
import com.mdev.mnotes.core.geom.FloatList
import com.mdev.mnotes.core.model.Element
import com.mdev.mnotes.core.model.ImageEl
import com.mdev.mnotes.core.model.PageContent
import com.mdev.mnotes.core.model.PageSpec
import com.mdev.mnotes.core.model.PenKind
import com.mdev.mnotes.core.model.Stroke
import com.mdev.mnotes.core.model.TemplateGeom
import com.mdev.mnotes.core.model.TemplateGeometry
import com.mdev.mnotes.core.model.TapeEl
import com.mdev.mnotes.core.model.TextBox
import com.mdev.mnotes.core.pen.PenSpec
import com.mdev.mnotes.core.pen.StrokeOutline
import kotlin.math.max

/**
 * Rysowanie strony (papier, wzór, elementy) na dowolnym Canvasie w układzie strony (pt).
 * Instancja nie jest bezpieczna wątkowo — każdy wątek (UI, renderer, miniatury) ma własną.
 */
class PageRenderer(private val assets: Assets?) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val fl = FloatList()
    private val sm = FloatList()
    private val rect = RectF()
    private val multiply = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
    private val screen = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
    private val geoms = HashMap<PageSpec, TemplateGeom>()

    fun geom(spec: PageSpec): TemplateGeom {
        if (spec.template.code == 0) return TemplateGeom.NONE
        if (geoms.size > 24) geoms.clear()
        return geoms.getOrPut(spec) { TemplateGeometry.build(spec) }
    }

    /** Papier + wzór (lub tło PDF z [pdfBg], jeśli podane). [scale] = px na pt, służy do minimalnej grubości linii. */
    fun drawPaper(c: Canvas, spec: PageSpec, scale: Float, pdfBg: Bitmap? = null) {
        paint.reset(); paint.isAntiAlias = true
        paint.style = Paint.Style.FILL
        paint.color = spec.paper
        c.drawRect(0f, 0f, spec.w, spec.h, paint)
        if (spec.pdf != null) {
            if (pdfBg != null) {
                rect.set(0f, 0f, spec.w, spec.h)
                c.drawBitmap(pdfBg, null, rect, bmpPaint)
            }
            return
        }
        val g = geom(spec)
        if (g.isEmpty) return
        val lw = max(spec.lineWidth, 0.9f / scale)
        paint.color = spec.line
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.BUTT
        if (g.lines.isNotEmpty()) { paint.strokeWidth = lw; c.drawLines(g.lines, paint) }
        if (g.heavy.isNotEmpty()) { paint.strokeWidth = lw * 1.5f; c.drawLines(g.heavy, paint) }
        if (g.dots.isNotEmpty()) {
            paint.strokeWidth = max(lw * 2.6f, 1.4f / scale); paint.strokeCap = Paint.Cap.ROUND
            c.drawPoints(g.dots, paint)
        }
        if (g.boxes.isNotEmpty()) {
            paint.strokeWidth = lw * 1.2f
            var i = 0
            while (i + 3 < g.boxes.size) { c.drawRoundRect(g.boxes[i], g.boxes[i + 1], g.boxes[i + 2], g.boxes[i + 3], 2f, 2f, paint); i += 4 }
        }
    }

    /** Rysuje elementy od indeksu [from]; z [clip] pomija te, które go nie dotykają. */
    fun drawElements(c: Canvas, nb: String, content: PageContent, from: Int, clip: Bounds?, scale: Float, decodeImages: Boolean, dark: Boolean = false) {
        val els = content.elements
        for (i in from until els.size) {
            val e = els[i]
            if (clip != null && !e.bounds.intersects(clip)) continue
            drawElement(c, nb, e, scale, decodeImages, dark)
        }
    }

    fun drawElement(c: Canvas, nb: String, e: Element, scale: Float, decodeImages: Boolean, dark: Boolean = false) {
        when (e) {
            is Stroke -> drawStrokeData(c, e.pen, e.color, e.width, e.pts, e.count, scale, dark)
            is TextBox -> drawText(c, e)
            is ImageEl -> drawImage(c, nb, e, decodeImages)
            is TapeEl -> drawTape(c, e)
        }
    }

    /** Taśma: zakryta = pełny kolor, odkryta = półprzezroczysty obrys. */
    fun drawTape(c: Canvas, t: TapeEl) {
        val rgb = t.color and 0x00FFFFFF
        val rad = minOf(t.w, t.h) * 0.12f
        c.save()
        c.rotate(Math.toDegrees(t.rot.toDouble()).toFloat(), t.cx, t.cy)
        paint.reset(); paint.isAntiAlias = true
        paint.style = Paint.Style.FILL
        paint.color = if (t.hidden) rgb or (0xFF shl 24) else rgb or 0x2E000000
        c.drawRoundRect(t.cx - t.w / 2, t.cy - t.h / 2, t.cx + t.w / 2, t.cy + t.h / 2, rad, rad, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.2f
        paint.color = if (t.hidden) darken(rgb) else rgb or (0xB0 shl 24)
        c.drawRoundRect(t.cx - t.w / 2, t.cy - t.h / 2, t.cx + t.w / 2, t.cy + t.h / 2, rad, rad, paint)
        c.restore()
    }

    private fun darken(rgb: Int): Int {
        val r = ((rgb shr 16) and 0xFF) * 3 / 4; val g = ((rgb shr 8) and 0xFF) * 3 / 4; val b = (rgb and 0xFF) * 3 / 4
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    /**
     * Zakreślacz jak marker na papierze: na jasnym papierze prawdziwe „multiply” (wynik D·(1−α+α·C), tekst pod spodem
     * zostaje czytelny, kolor nie brudzi się przy nakładaniu), na ciemnym — „screen” (rozświetlenie).
     * Uwaga: [PorterDuff.Mode.MULTIPLY] to na Androidzie „modulate” (mnoży też alfę) i przy kryciu < 1 robi w bitmapie
     * dziurę, przez którą prześwituje tło — dlatego od API 29 używamy [BlendMode.MULTIPLY], a poniżej API 29 koloru
     * wstępnie rozjaśnionego i w pełni kryjącego (przy nieprzezroczystym papierze wynik jest identyczny).
     */
    private fun styleHighlighter(color: Int, alpha: Float, dark: Boolean) {
        if (dark) {
            paint.color = color
            paint.alpha = (alpha * 0.84f * 255f + 0.5f).toInt().coerceIn(0, 255)
            paint.xfermode = screen
        } else if (Build.VERSION.SDK_INT >= 29) {
            paint.color = color
            paint.alpha = (alpha * 255f + 0.5f).toInt().coerceIn(0, 255)
            paint.blendMode = BlendMode.MULTIPLY
        } else {
            fun ch(sh: Int) = (255f + ((color shr sh and 0xFF) - 255) * alpha + 0.5f).toInt().coerceIn(0, 255)
            paint.color = (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
            paint.xfermode = multiply
        }
    }

    /** Kreska z surowych danych (trójki x,y,p) — używana też do podglądu na żywo. [dark] = ciemny papier. */
    fun drawStrokeData(c: Canvas, pen: PenKind, color: Int, width: Float, pts: FloatArray, n: Int, scale: Float, dark: Boolean = false) {
        if (n <= 0) return
        val spec = PenSpec.of(pen)
        paint.reset(); paint.isAntiAlias = true
        val hl = pen == PenKind.HIGHLIGHTER
        if (hl) styleHighlighter(color, spec.alpha, dark)
        else {
            paint.color = color
            val a = (spec.alpha * 255f + 0.5f).toInt().coerceIn(0, 255)
            if (a < 255) paint.alpha = a
        }
        val minW = 0.9f / scale
        if (n == 1 || StrokeOutline.isConstant(pts, n)) {
            var avg = 0f
            for (i in 0 until n) avg += pts[3 * i + 2]
            val w = max(width * (avg / n), minW)
            if (n == 1) {
                paint.style = Paint.Style.FILL
                c.drawCircle(pts[0], pts[1], w * 0.5f, paint)
                return
            }
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = w
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeJoin = Paint.Join.ROUND
            path.rewind()
            path.moveTo(pts[0], pts[1])
            if (n == 2) path.lineTo(pts[3], pts[4])
            else {
                for (i in 1 until n - 1) {
                    val x0 = pts[3 * i]; val y0 = pts[3 * i + 1]
                    val x1 = pts[3 * i + 3]; val y1 = pts[3 * i + 4]
                    path.quadTo(x0, y0, (x0 + x1) * 0.5f, (y0 + y1) * 0.5f)
                }
                path.lineTo(pts[3 * n - 3], pts[3 * n - 2])
            }
            c.drawPath(path, paint)
        } else {
            StrokeOutline.build(pts, n, width, fl)
            StrokeOutline.smooth(fl, sm)
            if (sm.size < 6) return
            paint.style = Paint.Style.FILL
            path.rewind()
            path.fillType = Path.FillType.WINDING
            path.moveTo(sm.a[0], sm.a[1])
            var i = 2
            while (i + 3 < sm.size) { path.quadTo(sm.a[i], sm.a[i + 1], sm.a[i + 2], sm.a[i + 3]); i += 4 }
            path.close()
            c.drawPath(path, paint)
        }
    }

    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)

    fun typeface(family: Int, style: Int): Typeface {
        val st = (if (style and TextBox.STYLE_BOLD != 0) Typeface.BOLD else 0) or (if (style and TextBox.STYLE_ITALIC != 0) Typeface.ITALIC else 0)
        val base = when (family) {
            TextBox.FAMILY_SERIF -> Typeface.SERIF
            TextBox.FAMILY_MONO -> Typeface.MONOSPACE
            TextBox.FAMILY_HAND -> Typeface.create("cursive", Typeface.NORMAL)
            else -> Typeface.SANS_SERIF
        }
        return Typeface.create(base, st)
    }

    /** Układ tekstu pola — wspólny dla rysowania i dla pomiaru wysokości przy edycji. */
    fun layoutFor(t: TextBox, widthPt: Float = t.w): StaticLayout {
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG)
        tp.textSize = t.size
        tp.color = t.color
        tp.typeface = typeface(t.family, t.style)
        tp.isUnderlineText = t.underline
        tp.isStrikeThruText = t.strike
        val al = when (t.align) { TextBox.ALIGN_CENTER -> Layout.Alignment.ALIGN_CENTER; TextBox.ALIGN_END -> Layout.Alignment.ALIGN_OPPOSITE; else -> Layout.Alignment.ALIGN_NORMAL }
        val txt = if (t.text.isEmpty()) " " else t.text
        return StaticLayout.Builder.obtain(txt, 0, txt.length, tp, max(1, widthPt.toInt())).setAlignment(al).setIncludePad(false).build()
    }

    private fun drawText(c: Canvas, t: TextBox) {
        c.save()
        c.rotate(Math.toDegrees(t.rot.toDouble()).toFloat(), t.cx, t.cy)
        c.translate(t.cx - t.w * 0.5f, t.cy - t.h * 0.5f)
        if (t.fill != 0) {
            paint.reset(); paint.isAntiAlias = true; paint.color = t.fill
            c.drawRoundRect(0f, 0f, t.w, t.h, 4f, 4f, paint)
        }
        layoutFor(t).draw(c)
        c.restore()
    }

    private fun drawImage(c: Canvas, nb: String, im: ImageEl, decode: Boolean) {
        val bmp = assets?.bitmap(nb, im.asset, decode)
        c.save()
        c.rotate(Math.toDegrees(im.rot.toDouble()).toFloat(), im.cx, im.cy)
        rect.set(im.cx - im.w * 0.5f, im.cy - im.h * 0.5f, im.cx + im.w * 0.5f, im.cy + im.h * 0.5f)
        if (bmp != null) {
            bmpPaint.alpha = (im.alpha * 255f).toInt().coerceIn(0, 255)
            c.drawBitmap(bmp, null, rect, bmpPaint)
            bmpPaint.alpha = 255
        } else {
            paint.reset(); paint.isAntiAlias = true; paint.color = 0x22888888
            c.drawRect(rect, paint)
        }
        c.restore()
    }
}

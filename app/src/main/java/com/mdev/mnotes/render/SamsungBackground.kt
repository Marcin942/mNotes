package com.mdev.mnotes.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.RectF
import com.mdev.mnotes.core.model.ImageEl
import com.mdev.mnotes.core.samsung.ConvertedPage
import com.mdev.mnotes.core.samsung.SdBackground
import com.mdev.mnotes.core.samsung.SdBackgroundGeometry
import java.io.OutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Tło strony z Samsung Notes rysowane jedną funkcją: ta sama służy do pliku PDF, który leży pod stroną jak tło
 * zaimportowanego PDF-a (wierność oryginałowi bez sprawdzania każdego papieru osobno), i do podglądu w oknie importu.
 */
object SamsungBackground {
    /** Papier i wzór w układzie strony (pt). */
    fun draw(c: Canvas, bg: SdBackground) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.style = Paint.Style.FILL
        p.color = bg.paper
        c.drawRect(0f, 0f, bg.w, bg.h, p)
        p.style = Paint.Style.STROKE
        p.color = bg.line
        p.strokeWidth = bg.lineWidth
        p.strokeCap = Paint.Cap.BUTT
        val lines = SdBackgroundGeometry.lines(bg)
        if (lines.isNotEmpty()) c.drawLines(lines, p)
        val dots = SdBackgroundGeometry.dots(bg)
        if (dots.isNotEmpty()) {
            p.strokeWidth = bg.lineWidth * 2.6f
            p.strokeCap = Paint.Cap.ROUND
            c.drawPoints(dots, p)
        }
    }

    /**
     * Jeden PDF, po jednej stronie na każde tło z [backgrounds] (kolejność = numer strony). Strona PDF ma całkowity
     * rozmiar w pt, więc rysunek jest skalowany do dokładnych wymiarów tła; strona mNotes rozciąga PDF z powrotem do
     * własnego rozmiaru, dzięki czemu linie leżą tam, gdzie w oryginale.
     */
    fun writePdf(backgrounds: List<SdBackground>, out: OutputStream) {
        val doc = PdfDocument()
        try {
            backgrounds.forEachIndexed { i, bg ->
                val pw = bg.w.roundToInt().coerceAtLeast(1)
                val ph = bg.h.roundToInt().coerceAtLeast(1)
                val page = doc.startPage(PdfDocument.PageInfo.Builder(pw, ph, i + 1).create())
                page.canvas.scale(pw / bg.w, ph / bg.h)
                draw(page.canvas, bg)
                doc.finishPage(page)
            }
            doc.writeTo(out)
        } finally { doc.close() }
    }

    /**
     * Podgląd strony po konwersji (tło + wszystkie elementy) o szerokości [widthPx]; [renderer] rysuje elementy jak w
     * edytorze, a obrazy biorą pliki z [media] (klucze `sd:`).
     */
    fun preview(page: ConvertedPage, widthPx: Int, renderer: PageRenderer, media: Map<Int, ByteArray> = emptyMap()): Bitmap {
        val spec = page.spec
        val sc = widthPx / spec.w
        val bmp = Bitmap.createBitmap(widthPx.coerceAtLeast(1), (spec.h * sc).roundToInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.scale(sc, sc)
        val bg = page.background
        if (bg != null) draw(c, bg) else renderer.drawPaper(c, spec, sc)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        for (e in page.elements) {
            if (e !is ImageEl) { renderer.drawElement(c, "", e, sc, false, spec.darkPaper); continue }
            val img = SamsungImages.decode(media, e.asset, (max(e.w, e.h) * sc * 1.5f).toInt().coerceAtLeast(64)) ?: continue
            c.save()
            c.rotate(Math.toDegrees(e.rot.toDouble()).toFloat(), e.cx, e.cy)
            c.drawBitmap(img, null, RectF(e.cx - e.w / 2, e.cy - e.h / 2, e.cx + e.w / 2, e.cy + e.h / 2), paint)
            c.restore()
            img.recycle()
        }
        return bmp
    }
}

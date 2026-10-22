package com.mdev.mnotes.core.edit

import com.mdev.mnotes.core.model.ImageCrop
import com.mdev.mnotes.core.model.ImageEl
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Linia cięcia przez cały obraz: [vertical] = pionowa; [pos] to ułamek (0..1) szerokości (pionowa) albo wysokości (pozioma) widocznego obrazu. */
class CutLine(val vertical: Boolean, val pos: Float)

/**
 * Przecinanie obrazu (czysta logika): linie biegną równolegle do boków obrazu, także gdy ten jest obrócony.
 * Kawałki to komórki siatki wyznaczonej przez linie; każdy pokazuje własny wycinek tego samego pliku.
 */
object ImageCut {
    /** Najmniejszy kawałek (pt): cieńszych pasków nie da się uchwycić palcem, więc takie linie odrzucamy. */
    const val MIN_PIECE_PT = 14f

    /** Domyślny odstęp między kawałkami po cięciu (pt). */
    const val DEFAULT_GAP_PT = 10f

    private val SNAPS = floatArrayOf(0.25f, 1f / 3f, 0.5f, 2f / 3f, 0.75f)

    /** Pozycja linii względem obrazu: [px], [py] to punkt na stronie, wynik (u, v) w ułamkach szerokości i wysokości (0 = lewa/górna krawędź). */
    fun toLocal(img: ImageEl, px: Float, py: Float): FloatArray {
        val dx = px - img.cx; val dy = py - img.cy
        val c = cos(img.rot); val s = sin(img.rot)
        val lx = dx * c + dy * s
        val ly = -dx * s + dy * c
        return floatArrayOf(lx / img.w + 0.5f, ly / img.h + 0.5f)
    }

    /** Odwrotność [toLocal]: punkt strony dla ułamków (u, v). */
    fun toPage(img: ImageEl, u: Float, v: Float): FloatArray {
        val lx = (u - 0.5f) * img.w; val ly = (v - 0.5f) * img.h
        val c = cos(img.rot); val s = sin(img.rot)
        return floatArrayOf(img.cx + lx * c - ly * s, img.cy + lx * s + ly * c)
    }

    /** Przyciąga [pos] do połowy, trzecich i ćwiartek, gdy leży bliżej niż [tolPt]; [sizePt] to wymiar obrazu wzdłuż osi. */
    fun snap(pos: Float, sizePt: Float, tolPt: Float): Float {
        if (sizePt <= 0f) return pos
        val tol = tolPt / sizePt
        var best = pos; var bd = tol
        for (s in SNAPS) { val d = abs(pos - s); if (d <= bd) { bd = d; best = s } }
        return best
    }

    /** Czy [pos] daje kawałki nie mniejsze niż [MIN_PIECE_PT] po obu stronach. */
    fun fits(pos: Float, sizePt: Float): Boolean {
        val min = MIN_PIECE_PT / sizePt
        return pos >= min && pos <= 1f - min
    }

    /** Pozycja nowej linii: środek największej luki między brzegami i istniejącymi liniami tej osi; null, gdy nie mieści się już kawałek. */
    fun nextPos(img: ImageEl, vertical: Boolean, lines: List<CutLine>): Float? {
        val stops = listOf(0f) + lines.filter { it.vertical == vertical }.map { it.pos }.sorted() + 1f
        var at = 0f; var best = 0f
        for (i in 0 until stops.size - 1) {
            val g = stops[i + 1] - stops[i]
            if (g > best) { best = g; at = (stops[i] + stops[i + 1]) / 2f }
        }
        return at.takeIf { best > 0f && fits(at, if (vertical) img.w else img.h) && best / 2f >= MIN_PIECE_PT / (if (vertical) img.w else img.h) }
    }

    /** Linie posortowane po osiach, bez tych zbyt blisko brzegu albo siebie nawzajem (zostaje pierwsza z pary). */
    fun normalize(img: ImageEl, lines: List<CutLine>): List<CutLine> {
        val out = ArrayList<CutLine>()
        for (vertical in booleanArrayOf(true, false)) {
            val size = if (vertical) img.w else img.h
            val min = MIN_PIECE_PT / size
            var last = 0f
            for (l in lines.filter { it.vertical == vertical }.sortedBy { it.pos }) {
                if (l.pos - last < min || 1f - l.pos < min) continue
                out.add(l); last = l.pos
            }
        }
        return out
    }

    /**
     * Dzieli [img] liniami [lines] na kawałki w kolejności wierszami (od góry, od lewej). Kawałki dziedziczą krycie,
     * efekty, blokadę i obrót; [gap] rozsuwa je symetrycznie wokół dotychczasowego środka. Bez ważnych linii zwraca pustą listę.
     */
    fun split(img: ImageEl, lines: List<CutLine>, gap: Float, newId: () -> Int): List<ImageEl> {
        val norm = normalize(img, lines)
        val xs = listOf(0f) + norm.filter { it.vertical }.map { it.pos } + 1f
        val ys = listOf(0f) + norm.filter { !it.vertical }.map { it.pos } + 1f
        if (xs.size == 2 && ys.size == 2) return emptyList()
        val cols = xs.size - 1; val rows = ys.size - 1
        val c = cos(img.rot); val s = sin(img.rot)
        // odbity obraz dzieli się tak, jak jego odwrócony plik; kawałki wracają do odbicia na końcu
        val cr = CropGeom.unmirrored(img).crop
        val out = ArrayList<ImageEl>(cols * rows)
        for (r in 0 until rows) for (k in 0 until cols) {
            val u0 = xs[k]; val u1 = xs[k + 1]; val v0 = ys[r]; val v1 = ys[r + 1]
            val lx = ((u0 + u1) / 2f - 0.5f) * img.w + (k - (cols - 1) / 2f) * gap
            val ly = ((v0 + v1) / 2f - 0.5f) * img.h + (r - (rows - 1) / 2f) * gap
            out.add(
                CropGeom.remirrored(
                    ImageEl(
                        newId(), img.asset,
                        img.cx + lx * c - ly * s, img.cy + lx * s + ly * c,
                        (u1 - u0) * img.w, (v1 - v0) * img.h, img.rot, img.alpha, img.locked,
                        ImageCrop(cr.l + u0 * cr.width, cr.t + v0 * cr.height, cr.l + u1 * cr.width, cr.t + v1 * cr.height),
                        img.fx,
                    ),
                    img.mirror,
                ),
            )
        }
        return out
    }
}

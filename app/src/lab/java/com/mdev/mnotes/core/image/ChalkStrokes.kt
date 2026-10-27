package com.mdev.mnotes.core.image

import com.mdev.mnotes.core.model.Element
import com.mdev.mnotes.core.model.Ids
import com.mdev.mnotes.core.model.ImageEl
import com.mdev.mnotes.core.model.PenKind
import com.mdev.mnotes.core.model.Stroke
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/**
 * Kreski z kredy ułożone na zdjęciu w zeszycie (K2). Wynik potoku bywa wyprostowany i przycięty, więc każdy punkt
 * łamanej wraca przez [BoardSource.toPhoto] do ułamków pliku zdjęcia, a stamtąd jak w `PageRenderer.drawImage`:
 * wycinek pliku ([ImageEl.crop], także obrócony o `a`), odbicie, obrót i środek elementu. Punkty poza widocznym
 * wycinkiem (zdjęcie przycięte w zeszycie) rozcinają kreskę, żeby nic nie wystawało poza obraz.
 */
object ChalkStrokes {
    /** Zapas na krawędzi wycinka (ułamek), żeby kreska dochodząca do brzegu nie gubiła ostatniego punktu. */
    private const val EDGE = 0.01f

    /**
     * Punkt pliku (u, v) ∈ [0, 1]² w punktach strony albo null, gdy leży poza wycinkiem (z [clip] = false też poza
     * nim, do liczenia skali). [aspect] to szerokość pliku przez wysokość (obrót wycinka jest w pikselach pliku).
     */
    fun place(u: Float, v: Float, over: ImageEl, aspect: Float, clip: Boolean = true): FloatArray? {
        val c = over.crop
        var dx = (u - (c.l + c.r) / 2f) * aspect
        var dy = v - (c.t + c.b) / 2f
        if (c.a != 0f) {
            val ca = cos(-c.a); val sa = sin(-c.a)
            val rx = dx * ca - dy * sa
            dy = dx * sa + dy * ca; dx = rx
        }
        var lx = dx / (c.width * aspect)
        val ly = dy / c.height
        if (clip && (abs(lx) > 0.5f + EDGE || abs(ly) > 0.5f + EDGE)) return null
        if (over.mirror) lx = -lx
        val px = lx * over.w; val py = ly * over.h
        val cr = cos(over.rot); val sr = sin(over.rot)
        return floatArrayOf(over.cx + px * cr - py * sr, over.cy + px * sr + py * cr)
    }

    /** Łamane z [chalk] (wynik potoku ze źródłem [src]) jako kreski pióra nad zdjęciem [over]; grubość w pt z lokalnej skali. */
    fun toStrokes(chalk: Raster, src: BoardSource, over: ImageEl, color: Int, lines: List<ChalkLine> = ChalkVector.vectorize(chalk)): List<Element> {
        val aspect = src.fitW.toFloat() / src.fitH
        fun at(x: Float, y: Float): FloatArray? = src.toPhoto(x, y).let { place(it[0], it[1], over, aspect) }
        val out = ArrayList<Element>()
        for (l in lines) {
            val mid = l.size / 2
            val k = scaleAt(l.pts[2 * mid], l.pts[2 * mid + 1]) { x, y -> src.toPhoto(x, y).let { place(it[0], it[1], over, aspect, clip = false) } } ?: continue
            val width = (l.width * k).coerceIn(0.4f, 12f)
            val run = ArrayList<Float>()
            fun add(p: FloatArray) { run += p[0]; run += p[1]; run += 1f }
            fun flush() {
                if (run.size >= 6) out += Stroke(Ids.next(), PenKind.BALLPOINT, color or (0xFF shl 24), width, run.toFloatArray(), poly = true)
                run.clear()
            }
            at(l.pts[0], l.pts[1])?.let(::add)
            for (i in 1 until l.size) {
                val ax = l.pts[2 * i - 2]; val ay = l.pts[2 * i - 1]; val bx = l.pts[2 * i]; val by = l.pts[2 * i + 1]
                // Po uproszczeniu odcinek bywa długi (prosta kreska to dwa punkty), więc krawędź wycinka szukamy
                // wzdłuż odcinka co ~4 px wyniku, a miejsce przejścia dokładnie bisekcją.
                val steps = max(1, (hypot(bx - ax, by - ay) / 4f).toInt())
                var prevT = 0f
                var inside = run.isNotEmpty()
                for (k in 1..steps) {
                    val t = k.toFloat() / steps
                    val now = at(ax + (bx - ax) * t, ay + (by - ay) * t) != null
                    if (now != inside) {
                        var lo = prevT; var hi = t
                        repeat(12) { val m = (lo + hi) / 2; if ((at(ax + (bx - ax) * m, ay + (by - ay) * m) != null) == inside) lo = m else hi = m }
                        val edge = if (inside) lo else hi
                        at(ax + (bx - ax) * edge, ay + (by - ay) * edge)?.let(::add)
                        if (inside) flush()
                        inside = now
                    }
                    prevT = t
                }
                if (inside) at(bx, by)?.let(::add)
            }
            flush()
        }
        return out
    }

    /** Punkty strony na piksel wyniku w (x, y): średnia z kroku w prawo i w dół (prostowanie zmienia skalę w obrazie). */
    private fun scaleAt(x: Float, y: Float, at: (Float, Float) -> FloatArray?): Float? {
        val p = at(x, y) ?: return null
        val px = at(x + 1f, y); val py = at(x, y + 1f)
        val d = listOfNotNull(px?.let { hypot(it[0] - p[0], it[1] - p[1]) }, py?.let { hypot(it[0] - p[0], it[1] - p[1]) })
        return if (d.isEmpty()) null else d.average().toFloat()
    }
}

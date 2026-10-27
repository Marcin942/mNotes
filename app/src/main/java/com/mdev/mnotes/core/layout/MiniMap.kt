package com.mdev.mnotes.core.layout

import com.mdev.mnotes.core.geom.Bounds

/**
 * Mini-mapa strony (pomysł Fable nr 3): przy dużym powiększeniu w rogu widać całą bieżącą stronę z prostokątem
 * widocznego fragmentu; dotknięcie albo przeciągnięcie po mini-mapie przenosi widok w to miejsce strony.
 */
object MiniMap {
    /** Od ilu razy „100 %” mini-mapa się pokazuje. */
    const val MIN_ZOOM_FACTOR = 2.5f

    class Geometry(val box: Bounds, val scale: Float, val view: Bounds)

    /**
     * Mini-mapa strony [pw]×[ph] pt wpisana w pole najwyżej [maxW]×[maxH] px w prawym dolnym rogu widoku
     * [vw]×[vh] px z marginesem [margin]; [visible] = widoczny fragment strony w pt.
     */
    fun geometry(pw: Float, ph: Float, visible: Bounds, vw: Float, vh: Float, maxW: Float, maxH: Float, margin: Float): Geometry {
        val s = minOf(maxW / pw, maxH / ph)
        val w = pw * s; val h = ph * s
        val box = Bounds(vw - margin - w, vh - margin - h, vw - margin, vh - margin)
        val v = Bounds(
            box.l + visible.l.coerceIn(0f, pw) * s, box.t + visible.t.coerceIn(0f, ph) * s,
            box.l + visible.r.coerceIn(0f, pw) * s, box.t + visible.b.coerceIn(0f, ph) * s,
        )
        return Geometry(box, s, v)
    }

    /** Punkt strony (pt) pod punktem ekranu (x, y) na mini-mapie, przycięty do strony. */
    fun toPage(g: Geometry, x: Float, y: Float, pw: Float, ph: Float): FloatArray =
        floatArrayOf(((x - g.box.l) / g.scale).coerceIn(0f, pw), ((y - g.box.t) / g.scale).coerceIn(0f, ph))
}

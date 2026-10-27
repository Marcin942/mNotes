package com.mdev.mnotes.core.layout

import com.mdev.mnotes.core.geom.Bounds

/**
 * Mini-mapa strony (pomysł Fable nr 3): przy dużym powiększeniu w rogu widać całą bieżącą stronę z prostokątem
 * widocznego fragmentu; dotknięcie albo przeciągnięcie po mini-mapie przenosi widok w to miejsce strony.
 */
object MiniMap {
    /** Od ilu razy „100 %” mini-mapa się pokazuje. */
    const val MIN_ZOOM_FACTOR = 2.5f

    /**
     * Jak pasek przewijania: mapa stoi [HOLD_MS] po ostatnim ruchu widoku i gaśnie przez [FADE_MS]. Na stałe zasłaniała
     * róg, w którym przy dużym zoomie nie dało się pisać (przegląd Fable, A2-2).
     */
    const val HOLD_MS = 1200L
    const val FADE_MS = 180L

    /** Krycie mapy w chwili [now], gdy widok ruszył się ostatnio w [movedAt] (ms; 0 = jeszcze nie / schowana rysikiem). */
    fun alpha(now: Long, movedAt: Long): Float {
        if (movedAt <= 0L) return 0f
        val t = now - movedAt
        return when {
            t <= HOLD_MS -> 1f
            t >= HOLD_MS + FADE_MS -> 0f
            else -> 1f - (t - HOLD_MS).toFloat() / FADE_MS
        }
    }

    /** Dotknięcie w polu mapy przenosi widok tylko palcem i tylko gdy mapę widać; rysik zawsze pisze pod nią. */
    fun takesTouch(finger: Boolean, alpha: Float, inBox: Boolean): Boolean = finger && inBox && alpha > 0f

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

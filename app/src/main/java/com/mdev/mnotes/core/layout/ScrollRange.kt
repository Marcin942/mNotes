package com.mdev.mnotes.core.layout

/**
 * Zakres przesunięcia widoku (tx albo ty, px) dla treści długości [content] w oknie [view]: dłuższa przewija się od
 * brzegu do brzegu, krótsza stoi na środku. 3c-A4: [margin] dokłada zapas po obu stronach — pierwszą (i każdą) stronę
 * da się odsunąć dalej niż brzeg ekranu, np. żeby leworęczny nie opierał dłoni na początku kartki.
 */
object ScrollRange {
    /** Ułamek okna, o który wolno przewinąć poza dokument (3c-A4: „co najmniej pół ekranu”). */
    const val MARGIN = 0.5f

    fun of(content: Float, view: Float, margin: Float = 0f): FloatArray {
        val lo = if (content <= view) (view - content) / 2f else view - content
        val hi = if (content <= view) lo else 0f
        return floatArrayOf(lo - margin, hi + margin)
    }

    fun clamp(v: Float, content: Float, view: Float, margin: Float = 0f): Float {
        val r = of(content, view, margin)
        return v.coerceIn(r[0], r[1])
    }
}

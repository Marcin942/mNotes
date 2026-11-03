package com.mdev.mnotes.core.layout

/**
 * Zakres przesunięcia widoku (tx albo ty, px) dla treści długości [content] w oknie [view]: dłuższa przewija się od
 * brzegu do brzegu, krótsza stoi na środku. 3c-A4: [startReach] pozwala odsunąć **tylko początek** treści (lewy brzeg
 * pierwszej strony): jej początek może stanąć najdalej na [startReach] px od brzegu okna, np. na połowie ekranu, żeby
 * leworęczny nie opierał dłoni na początku kartki. Koniec treści i druga oś zostają bez zapasu.
 */
object ScrollRange {
    /** Ułamek okna, do którego wolno odsunąć początek dokumentu (3c-A4: lewy brzeg pierwszej strony na połowie ekranu). */
    const val MARGIN = 0.5f

    fun of(content: Float, view: Float, startReach: Float = 0f): FloatArray {
        val lo = if (content <= view) (view - content) / 2f else view - content
        val hi = if (content <= view) lo else 0f
        return floatArrayOf(lo, maxOf(hi, startReach))
    }

    fun clamp(v: Float, content: Float, view: Float, startReach: Float = 0f): Float {
        val r = of(content, view, startReach)
        return v.coerceIn(r[0], r[1])
    }
}

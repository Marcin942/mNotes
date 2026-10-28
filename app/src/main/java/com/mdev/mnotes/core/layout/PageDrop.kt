package com.mdev.mnotes.core.layout

import com.mdev.mnotes.core.geom.Affine
import com.mdev.mnotes.core.geom.Bounds

/** Upuszczanie przesuwanych elementów na inną stronę: wybór strony docelowej i przeliczenie współrzędnych. */
object PageDrop {

    /**
     * Strona, na którą trafi element po upuszczeniu: ta, na której leży środek obwiedni [moved]
     * (we współrzędnych strony [from]); gdy środek wypada poza wszystkimi stronami (przerwa, margines),
     * element zostaje na stronie [from].
     */
    fun target(layout: DocLayout, from: Int, moved: Bounds): Int {
        if (from !in 0 until layout.count || moved.isEmpty) return from
        val at = layout.pageAt(layout.left(from) + moved.cx, layout.top(from) + moved.cy)
        return if (at < 0) from else at
    }

    /** Przekształcenie [m] (we współrzędnych strony [from]) zapisane we współrzędnych strony [to]. */
    fun inTarget(layout: DocLayout, from: Int, to: Int, m: Affine): Affine =
        if (from == to) m else m.then(Affine.translation(layout.left(from) - layout.left(to), layout.top(from) - layout.top(to)))

    /**
     * 3c-A2: jak [inTarget], ale przy przenosinach na inną stronę obwiednia [b] (współrzędne strony [from], przed [m])
     * zostaje dosunięta w całości na stronę [to]. Część wystająca poza stronę jest przycinana, więc obiekt wyglądał,
     * jakby wsunął się pod sąsiednią stronę. Większa od strony przylega do lewego/górnego brzegu.
     */
    fun intoTarget(layout: DocLayout, from: Int, to: Int, m: Affine, b: Bounds): Affine {
        val t = inTarget(layout, from, to, m)
        if (from == to || b.isEmpty) return t
        val (dx, dy) = pushInside(t.mapBounds(b), layout.width(to), layout.height(to))
        return t.then(Affine.translation(dx, dy))
    }

    /** Przesunięcie (dx, dy), po którym obwiednia [r] leży na stronie [w]×[h] (większa przylega do lewego/górnego brzegu). */
    fun pushInside(r: Bounds, w: Float, h: Float): Pair<Float, Float> = fit(r.l, r.r, w) to fit(r.t, r.b, h)

    private fun fit(lo: Float, hi: Float, size: Float): Float = when {
        hi - lo >= size || lo < 0f -> -lo
        hi > size -> size - hi
        else -> 0f
    }
}

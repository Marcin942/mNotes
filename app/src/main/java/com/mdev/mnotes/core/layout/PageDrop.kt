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
}

package com.mdev.mnotes.core.edit

import com.mdev.mnotes.core.geom.Affine
import com.mdev.mnotes.core.geom.Bounds
import com.mdev.mnotes.core.model.Element
import com.mdev.mnotes.core.model.Ids
import com.mdev.mnotes.core.model.ImageEl

/**
 * Naklejki (F19): zaznaczony rysunek zapisany raz i wstawiany wiele razy (wzór chemiczny, schemat obwodu, symbol).
 * Naklejka trzyma elementy przesunięte tak, że lewy górny róg obwiedni to (0, 0); przy wstawianiu dostają nowe id
 * i lądują środkiem w punkcie wstawienia, w skali dopasowanej do strony.
 */
object Sticker {
    /** Elementy przesunięte do (0, 0) i rozmiar obwiedni (szerokość, wysokość w pt). */
    class Normalized(val elements: List<Element>, val w: Float, val h: Float)

    fun normalize(els: List<Element>): Normalized? {
        if (els.isEmpty()) return null
        var b = Bounds.EMPTY
        for (e in els) b = b.union(e.bounds)
        if (b.isEmpty) return null
        val m = Affine.translation(-b.l, -b.t)
        return Normalized(els.map { it.transformed(m).withLocked(false) }, b.w, b.h)
    }

    /** Skala, z jaką naklejka [w]×[h] zmieści się w [maxFrac] strony [pageW]×[pageH] (nigdy nie powiększa). */
    fun fitScale(w: Float, h: Float, pageW: Float, pageH: Float, maxFrac: Float = 0.8f): Float =
        minOf(1f, maxFrac * pageW / w.coerceAtLeast(1f), maxFrac * pageH / h.coerceAtLeast(1f))

    /**
     * Kopie elementów naklejki ze środkiem w ([cx], [cy]) i skalą [scale]; każda z nowym id. [asset] zamienia nazwy
     * plików obrazów (kopie w zasobach zeszytu).
     */
    fun place(n: Normalized, cx: Float, cy: Float, scale: Float = 1f, asset: (String) -> String = { it }): List<Element> {
        val m = Affine.scale(scale, scale).then(Affine.translation(cx - n.w * scale / 2f, cy - n.h * scale / 2f))
        return n.elements.map { e ->
            val t = e.transformed(m).withId(Ids.next())
            if (t is ImageEl) t.copy(asset = asset(t.asset)) else t
        }
    }

    /** Pliki obrazów, których naklejka potrzebuje (do skopiowania razem z nią). */
    fun assets(els: List<Element>): Set<String> = els.filterIsInstance<ImageEl>().map { it.asset }.toSet()
}

package com.mdev.mnotes.core.edit

import com.mdev.mnotes.core.geom.Affine
import com.mdev.mnotes.core.geom.Bounds
import com.mdev.mnotes.core.model.Element
import com.mdev.mnotes.core.model.FillEl
import com.mdev.mnotes.core.model.Ids
import com.mdev.mnotes.core.model.ImageEl
import com.mdev.mnotes.core.model.Stroke
import com.mdev.mnotes.core.model.TextBox

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

    /**
     * Średnia jasność (0–1) atramentu naklejki: kreski ważone wielkością i grubością, teksty liczbą znaków i rozmiarem,
     * wypełnienia polem; null, gdy naklejka to same obrazy.
     */
    fun inkLuminance(els: List<Element>): Float? {
        var sum = 0.0; var weight = 0.0
        fun add(argb: Int, w: Double) {
            val r = (argb shr 16 and 255) / 255.0; val g = (argb shr 8 and 255) / 255.0; val b = (argb and 255) / 255.0
            sum += (0.2126 * r + 0.7152 * g + 0.0722 * b) * w; weight += w
        }
        for (e in els) when (e) {
            is Stroke -> add(e.color, (e.bounds.w + e.bounds.h + 1f).toDouble() * e.width)
            is TextBox -> add(e.color, e.text.length.toDouble() * e.size)
            is FillEl -> add(e.color, (e.bounds.w * e.bounds.h).toDouble().coerceAtLeast(1.0))
            else -> {}
        }
        return if (weight <= 0.0) null else (sum / weight).toFloat()
    }

    /**
     * Tło podglądu naklejki (2b-A5): ciemne, gdy atrament jest jasny (biała kreda, biały tekst z ciemnej strony), jasne,
     * gdy ciemny; pośrednie kolory i same obrazy — jak motyw. Sama naklejka nie ma tła i zachowuje własne kolory.
     */
    fun darkBackdrop(inkLum: Float?, themeDark: Boolean): Boolean = when {
        inkLum == null -> themeDark
        inkLum > 0.62f -> true
        inkLum < 0.38f -> false
        else -> themeDark
    }
}

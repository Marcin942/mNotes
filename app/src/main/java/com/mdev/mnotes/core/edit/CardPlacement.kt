package com.mdev.mnotes.core.edit

import kotlin.math.max
import kotlin.math.min

/** Gdzie leży karta względem przycinanego obrazu: pod, nad, z prawej, z lewej albo w jednym z narożników widoku. */
enum class CardSide { BELOW, ABOVE, RIGHT, LEFT, BOTTOM_RIGHT, BOTTOM_LEFT, TOP_RIGHT, TOP_LEFT }

class CardSpot(val side: CardSide, val x: Int, val y: Int)

/**
 * Ustawia pływającą kartę ([w] × [h]) tak, by jak najmniej zasłaniała prostokąt [l], [t], [r], [b] (przycinany obraz)
 * w widoku [vw] × [vh]. Najpierw szuka miejsca bez zasłaniania (pod, nad, z prawej, z lewej), a gdy obraz zajmuje
 * prawie cały widok, bierze to z ośmiu miejsc, które zasłania najmniej. Dotychczasowa strona [prev] wygrywa, dopóki
 * nie zasłania więcej niż nowa o parę procent karty, więc karta nie skacze przy każdym ruchu ramki.
 */
object CardPlacement {
    fun place(l: Float, t: Float, r: Float, b: Float, w: Int, h: Int, vw: Int, vh: Int, gap: Float, prev: CardSide?): CardSpot {
        val maxX = max(0, vw - w); val maxY = max(0, vh - h)
        fun cx(x: Float) = x.toInt().coerceIn(0, maxX)
        fun cy(y: Float) = y.toInt().coerceIn(0, maxY)
        val midX = (l + r) * 0.5f - w / 2f; val midY = (t + b) * 0.5f - h / 2f
        val spots = CardSide.values().map {
            when (it) {
                CardSide.BELOW -> CardSpot(it, cx(midX), cy(b + gap))
                CardSide.ABOVE -> CardSpot(it, cx(midX), cy(t - gap - h))
                CardSide.RIGHT -> CardSpot(it, cx(r + gap), cy(midY))
                CardSide.LEFT -> CardSpot(it, cx(l - gap - w), cy(midY))
                CardSide.BOTTOM_RIGHT -> CardSpot(it, maxX, maxY)
                CardSide.BOTTOM_LEFT -> CardSpot(it, 0, maxY)
                CardSide.TOP_RIGHT -> CardSpot(it, maxX, 0)
                CardSide.TOP_LEFT -> CardSpot(it, 0, 0)
            }
        }
        fun covered(s: CardSpot) = covered(s, l, t, r, b, w, h)
        // pod, nad, z prawej, z lewej: pierwsze, które nie zasłania wcale, mieści się w widoku bez przesuwania i dotrzymuje odstępu
        val free = spots.take(4).filter { covered(it) == 0f && gapKept(it, l, t, r, b, w, h, gap) }
        val keep = prev?.let { p -> spots.first { it.side == p } }
        if (keep != null && covered(keep) == 0f && (keep in free || keep.side.ordinal >= 4)) return keep
        if (free.isNotEmpty()) return keep?.takeIf { it in free } ?: free.first()
        val best = spots.minByOrNull { covered(it) }!!
        if (keep != null && covered(keep) <= covered(best) + 0.02f * w * h) return keep
        return best
    }

    /** Jak [place], ale tylko wtedy, gdy karta nie zasłania obrazu wcale; inaczej null (wołający zostawia wtedy kartę tam, gdzie była). */
    fun free(l: Float, t: Float, r: Float, b: Float, w: Int, h: Int, vw: Int, vh: Int, gap: Float, prev: CardSide?): CardSpot? {
        val s = place(l, t, r, b, w, h, vw, vh, gap, prev)
        return s.takeIf { covered(it, l, t, r, b, w, h) == 0f }
    }

    private fun covered(s: CardSpot, l: Float, t: Float, r: Float, b: Float, w: Int, h: Int): Float {
        val ow = min(s.x + w.toFloat(), r) - max(s.x.toFloat(), l)
        val oh = min(s.y + h.toFloat(), b) - max(s.y.toFloat(), t)
        return if (ow > 0f && oh > 0f) ow * oh else 0f
    }

    /** Czy karta leży w odstępie od obrazu, a nie tylko go nie dotyka (po przesunięciu do widoku mogłaby się zbliżyć). */
    private fun gapKept(s: CardSpot, l: Float, t: Float, r: Float, b: Float, w: Int, h: Int, gap: Float): Boolean = when (s.side) {
        CardSide.BELOW -> s.y >= b + gap - 1f
        CardSide.ABOVE -> s.y + h <= t - gap + 1f
        CardSide.RIGHT -> s.x >= r + gap - 1f
        CardSide.LEFT -> s.x + w <= l - gap + 1f
        else -> true
    }
}

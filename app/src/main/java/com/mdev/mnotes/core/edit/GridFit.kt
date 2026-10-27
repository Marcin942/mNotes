package com.mdev.mnotes.core.edit

import com.mdev.mnotes.core.geom.Bounds
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Wyrównanie wstawionego zdjęcia do kratki (pomysł Fable nr 11, opcja): lewy górny róg na najbliższym węźle siatki,
 * szerokość jako wielokrotność oczka (proporcje zachowane), tak jak siatka strony (wyśrodkowana: pierwsza linia w
 * (w mod oczko) / 2).
 */
object GridFit {
    /** Prostokąt [r] dopasowany do siatki o oczku [sp] na stronie [pw]×[ph]; wysokość z proporcji [aspect] = w/h. */
    fun fit(r: Bounds, aspect: Float, sp: Float, pw: Float, ph: Float): Bounds {
        if (sp <= 0f) return r
        val x0 = (pw % sp) / 2f; val y0 = (ph % sp) / 2f
        val cells = max(1, (r.w / sp).roundToInt())
        var w = cells * sp
        var h = w / aspect.coerceAtLeast(1e-3f)
        // Nie większe niż strona (bez oczek, które się nie mieszczą).
        val maxCells = max(1, floor((pw - 2 * x0) / sp).toInt())
        if (cells > maxCells) { w = maxCells * sp; h = w / aspect }
        var l = x0 + ((r.l - x0) / sp).roundToInt() * sp
        var t = y0 + ((r.t - y0) / sp).roundToInt() * sp
        l = l.coerceIn(x0, max(x0, x0 + floor((pw - x0 - w - x0) / sp + 1e-3f) * sp))
        t = t.coerceIn(y0, max(y0, ph - h))
        return Bounds(l, t, l + w, t + h)
    }
}

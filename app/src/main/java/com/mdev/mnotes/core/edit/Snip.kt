package com.mdev.mnotes.core.edit

import com.mdev.mnotes.core.geom.Bounds
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Wycinek strony jako obraz (F14): zaznaczony fragment (ramka zaznaczenia z małym marginesem) rasteryzowany razem
 * z papierem, wzorem i tłem PDF — do wstawienia jako obraz w zeszycie albo do schowka systemu jako PNG.
 */
object Snip {
    /** Prostokąt strony (pt) i rozmiar bitmapy (px) przy skali [scale] px/pt. */
    class Plan(val rect: Bounds, val wPx: Int, val hPx: Int, val scale: Float)

    /**
     * Ramka [sel] poszerzona o [margin], przycięta do strony; skala domyślnie 3 px/pt (ostro po powiększeniu),
     * zmniejszana, gdy dłuższy bok przekroczyłby [maxPx]. Null, gdy wycinek jest pusty.
     */
    fun plan(sel: Bounds, pageW: Float, pageH: Float, margin: Float = 4f, scale: Float = 3f, maxPx: Int = 3000): Plan? {
        val r = Bounds(max(0f, sel.l - margin), max(0f, sel.t - margin), min(pageW, sel.r + margin), min(pageH, sel.b + margin))
        if (r.w < 1f || r.h < 1f) return null
        val sc = min(scale, maxPx / max(r.w, r.h))
        return Plan(r, ceil(r.w * sc).toInt().coerceAtLeast(1), ceil(r.h * sc).toInt().coerceAtLeast(1), sc)
    }

    /**
     * Środek wstawianego obrazu wycinka (rozmiar jak [rect]): obok z prawej, gdy jest miejsce, potem pod spodem, potem
     * po lewej; w ostateczności na wycinku z przesunięciem o [gap], żeby nie zasłonić oryginału co do piksela.
     */
    fun placement(rect: Bounds, pageW: Float, pageH: Float, gap: Float = 10f): FloatArray {
        val w = rect.w; val h = rect.h
        return when {
            rect.r + gap + w <= pageW -> floatArrayOf(rect.r + gap + w / 2f, rect.t + h / 2f)
            rect.b + gap + h <= pageH -> floatArrayOf(rect.l + w / 2f, rect.b + gap + h / 2f)
            rect.l - gap - w >= 0f -> floatArrayOf(rect.l - gap - w / 2f, rect.t + h / 2f)
            else -> floatArrayOf(min(pageW - w / 2f, rect.l + gap + w / 2f), min(pageH - h / 2f, rect.t + gap + h / 2f))
        }
    }
}

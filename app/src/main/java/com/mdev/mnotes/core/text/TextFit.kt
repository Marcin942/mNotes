package com.mdev.mnotes.core.text

import com.mdev.mnotes.core.model.TextBox
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Pomiar tekstu pola. Implementacja Androida (StaticLayout) leży w `render`, testy podstawiają prosty zamiennik. */
interface TextMeasure {
    /** Szerokość najdłuższego wiersza bez zawijania (pt). */
    fun naturalWidth(t: TextBox): Float

    /** Wysokość tekstu zawiniętego na szerokość [width] (pt), bez ramki wokół. */
    fun textHeight(t: TextBox, width: Float): Float
}

/**
 * Rozmiar pola tekstowego (tryby [TextBox.FIT_AUTO], [TextBox.FIT_HEIGHT], [TextBox.FIT_FIXED]).
 *
 * Lewy-górny róg pola (w jego własnym, obróconym układzie) zostaje na miejscu przy każdym dopasowaniu: tekst
 * rośnie w prawo i w dół, tak jak w edytorze tekstu. Logika czysta — testowana jednostkowo.
 */
object TextFit {
    /** Zapas nad i pod tekstem; ta sama wartość co w polach sprzed trybów, więc stare pliki wyglądają tak samo. */
    const val PAD_V = 8f

    /** Odstęp od krawędzi strony, do którego sięga auto-rozmiar (5 mm). */
    const val MARGIN = 14.17f

    /** Tyle miejsca na szerokość musi mieć nowe pole; gdy kliknięto bliżej prawej krawędzi, pole zaczyna się wcześniej. */
    const val MIN_ROOM = 96f

    enum class Side { TOP, RIGHT, BOTTOM, LEFT }

    fun minWidth(size: Float) = max(size * 2f, 24f)

    fun minHeight(size: Float) = size * 1.3f + PAD_V

    /** Wysokość pola dla tekstu zawiniętego na [width]. */
    fun boxHeight(m: TextMeasure, t: TextBox, width: Float) = max(m.textHeight(t, width), t.size * 1.3f) + PAD_V

    /** Lewa współrzędna x nowego pola przesunięta tak, by do prawej krawędzi zostało co najmniej [MIN_ROOM]. */
    fun placeX(x: Float, pageW: Float) = min(x, pageW - MARGIN - MIN_ROOM).coerceAtLeast(min(x, MARGIN))

    /**
     * Miejsce wzdłuż osi tekstu od punktu ([x], [y]) do granic strony z marginesem. Dla pola nieobróconego to po prostu
     * odległość do prawej krawędzi, dla obróconego — do krawędzi, w którą trafi promień.
     */
    fun room(x: Float, y: Float, rot: Float, pageW: Float, pageH: Float): Float {
        val ux = cos(rot); val uy = sin(rot)
        var r = Float.MAX_VALUE
        if (ux > EPS) r = min(r, (pageW - MARGIN - x) / ux) else if (ux < -EPS) r = min(r, (MARGIN - x) / ux)
        if (uy > EPS) r = min(r, (pageH - MARGIN - y) / uy) else if (uy < -EPS) r = min(r, (MARGIN - y) / uy)
        return if (r == Float.MAX_VALUE) pageW else max(0f, r)
    }

    /** Szerokość pola w trybie auto: naturalna szerokość tekstu, ale nie dalej niż do krawędzi strony. */
    fun autoWidth(t: TextBox, pageW: Float, pageH: Float, m: TextMeasure): Float {
        val (x, y) = corner(t)
        val minW = minWidth(t.size)
        val room = max(room(x, y, t.rot, pageW, pageH), minW)
        // +1: StaticLayout dostaje szerokość całkowitą, a wiersz o ułamek szerszy zawinąłby się niepotrzebnie
        val natural = ceil(m.naturalWidth(t)) + 1f
        return natural.coerceIn(minW, room)
    }

    /** Przelicza rozmiar pola wg jego trybu — po zmianie treści, stylu albo trybu. W trybie ręcznym nic nie zmienia. */
    fun refit(t: TextBox, pageW: Float, pageH: Float, m: TextMeasure): TextBox = when (t.fit) {
        TextBox.FIT_FIXED -> t
        TextBox.FIT_AUTO -> {
            val w = autoWidth(t, pageW, pageH, m)
            anchored(t, w, boxHeight(m, t, w))
        }
        else -> anchored(t, t.w, boxHeight(m, t, t.w))
    }

    /** Ustawia tryb i od razu dopasowuje pole. */
    fun withFit(t: TextBox, fit: Int, pageW: Float, pageH: Float, m: TextMeasure): TextBox = refit(t.copy(fit = fit), pageW, pageH, m)

    /**
     * Przeciągnięcie uchwytu boku do punktu ([px], [py]) na stronie. Prawy i lewy zmieniają szerokość (auto przechodzi
     * wtedy na stałą szerokość, a wysokość nadal wynika z tekstu), górny i dolny ustawiają wysokość na sztywno.
     */
    fun resizeSide(t: TextBox, side: Side, px: Float, py: Float, m: TextMeasure): TextBox {
        val ux = cos(t.rot); val uy = sin(t.rot)
        val dx = px - t.cx; val dy = py - t.cy
        val lx = dx * ux + dy * uy      // wzdłuż poziomej osi pola
        val ly = -dx * uy + dy * ux     // wzdłuż pionowej osi pola
        return when (side) {
            Side.RIGHT -> widthTo(t, lx + t.w / 2f, fromRight = true, m)
            Side.LEFT -> widthTo(t, t.w / 2f - lx, fromRight = false, m)
            Side.BOTTOM -> heightTo(t, ly + t.h / 2f, fromBottom = true)
            Side.TOP -> heightTo(t, t.h / 2f - ly, fromBottom = false)
        }
    }

    private fun widthTo(t: TextBox, wRaw: Float, fromRight: Boolean, m: TextMeasure): TextBox {
        val w = max(wRaw, minWidth(t.size))
        val fit = if (t.fit == TextBox.FIT_FIXED) TextBox.FIT_FIXED else TextBox.FIT_HEIGHT
        val h = if (fit == TextBox.FIT_FIXED) t.h else boxHeight(m, t, w)
        val dw = w - t.w
        val dh = h - t.h
        val ux = cos(t.rot); val uy = sin(t.rot)
        // przeciwna krawędź w poziomie zostaje, górna krawędź zostaje
        val sx = if (fromRight) dw / 2f else -dw / 2f
        val sy = dh / 2f
        return t.copy(
            cx = t.cx + ux * sx - uy * sy, cy = t.cy + uy * sx + ux * sy,
            w = w, h = h, fit = fit,
        )
    }

    private fun heightTo(t: TextBox, hRaw: Float, fromBottom: Boolean): TextBox {
        val h = max(hRaw, minHeight(t.size))
        val dh = h - t.h
        val s = if (fromBottom) dh / 2f else -dh / 2f
        val ux = cos(t.rot); val uy = sin(t.rot)
        return t.copy(cx = t.cx - uy * s, cy = t.cy + ux * s, h = h, fit = TextBox.FIT_FIXED)
    }

    /** Pole o nowych wymiarach z tym samym lewym-górnym rogiem. */
    private fun anchored(t: TextBox, w: Float, h: Float): TextBox {
        if (abs(w - t.w) < 1e-3f && abs(h - t.h) < 1e-3f) return t
        val ux = cos(t.rot); val uy = sin(t.rot)
        val dw = w - t.w; val dh = h - t.h
        return t.copy(
            cx = t.cx + ux * dw / 2f - uy * dh / 2f,
            cy = t.cy + uy * dw / 2f + ux * dh / 2f,
            w = w, h = h,
        )
    }

    /** Lewy-górny róg pola na stronie. */
    fun corner(t: TextBox): Pair<Float, Float> {
        val ux = cos(t.rot); val uy = sin(t.rot)
        return (t.cx - ux * t.w / 2f + uy * t.h / 2f) to (t.cy - uy * t.w / 2f - ux * t.h / 2f)
    }

    private const val EPS = 1e-4f
}

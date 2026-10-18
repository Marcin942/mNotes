package com.mdev.mnotes.core.samsung

import kotlin.math.floor

/**
 * Geometria tła Samsunga w pt, bez Androida, żeby dało się ją sprawdzić testem na JVM. Pierwsza linia leży w
 * ([SdBackground.offsetX], [SdBackground.offsetY]), kolejne co [SdBackground.pitchX] (pionowe, kropki) i [SdBackground.pitchY] (poziome) aż do krawędzi strony.
 */
object SdBackgroundGeometry {
    private const val MIN_PITCH = 2f
    private const val MAX_LINES = 4000

    private fun positions(offset: Float, pitch: Float, limit: Float): FloatArray {
        if (pitch < MIN_PITCH) return FloatArray(0)
        val n = floor((limit - offset) / pitch + 1e-4).toInt() + 1
        if (n <= 0) return FloatArray(0)
        return FloatArray(minOf(n, MAX_LINES)) { offset + it * pitch }
    }

    /** Odcinki jako czwórki x1, y1, x2, y2: linie poziome (LINES), do tego pionowe (GRID), linie pięciolinii (STAFF); kropki ich nie mają. */
    fun lines(bg: SdBackground): FloatArray {
        if (bg.kind == BgKind.DOTS) return FloatArray(0)
        val staff = bg.staff
        if (bg.kind == BgKind.STAFF) {
            if (staff == null || staff.lines < 1) return FloatArray(0)
            val out = ArrayList<Float>()
            for (g in positions(bg.offsetY, staff.groupPitch, bg.h).take(staff.groups)) for (k in 0 until staff.lines) {
                val y = g + k * bg.pitchY
                if (y <= bg.h) { out.add(staff.left); out.add(y); out.add(staff.right); out.add(y) }
            }
            return out.toFloatArray()
        }
        val ys = positions(bg.offsetY, bg.pitchY, bg.h)
        val xs = if (bg.kind == BgKind.GRID) positions(bg.offsetX, bg.pitchX, bg.w) else FloatArray(0)
        val out = FloatArray((xs.size + ys.size) * 4)
        var i = 0
        for (x in xs) { out[i++] = x; out[i++] = 0f; out[i++] = x; out[i++] = bg.h }
        for (y in ys) { out[i++] = 0f; out[i++] = y; out[i++] = bg.w; out[i++] = y }
        return out
    }

    /** Punkty jako pary x, y: tylko dla kropek (DOTS), w przecięciach tej samej siatki co kratka. */
    fun dots(bg: SdBackground): FloatArray {
        if (bg.kind != BgKind.DOTS) return FloatArray(0)
        val xs = positions(bg.offsetX, bg.pitchX, bg.w)
        val ys = positions(bg.offsetY, bg.pitchY, bg.h)
        val out = FloatArray(xs.size * ys.size * 2)
        var i = 0
        for (y in ys) for (x in xs) { out[i++] = x; out[i++] = y }
        return out
    }

    /** Marginesy jako czwórki x, y1, x, y2 (od pierwszej linii poziomej do dołu strony), w kolejności [SdBackground.margins]. */
    fun margins(bg: SdBackground): FloatArray {
        val out = FloatArray(bg.margins.size * 4)
        var i = 0
        for (m in bg.margins) { out[i++] = m.x; out[i++] = bg.offsetY; out[i++] = m.x; out[i++] = bg.h }
        return out
    }
}

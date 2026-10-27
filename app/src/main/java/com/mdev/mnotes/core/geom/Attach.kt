package com.mdev.mnotes.core.geom

import com.mdev.mnotes.core.model.BoxElement
import com.mdev.mnotes.core.model.Element
import com.mdev.mnotes.core.model.Stroke
import com.mdev.mnotes.core.shapes.ShapeBox
import kotlin.math.hypot

/**
 * „Przyczep do figury” (F22): koniec linii albo przeciągany punkt przyciąga się do innych obiektów na stronie, żeby
 * odcinki schodziły się dokładnie (bok trójkąta w wierzchołku, promień w środku okręgu, styczna na okręgu).
 * Kolejność ważności: wierzchołki i końce, środki kształtów i ramek, a dopiero potem dowolny punkt na kresce.
 */
object Attach {
    enum class Kind { VERTEX, CENTER, ON_STROKE }

    class Hit(val x: Float, val y: Float, val kind: Kind, val elementId: Int)

    /** Punkt przyczepienia w zasięgu [tol] od (x, y) albo null; [skip] = id elementów pominiętych (przeciągany). */
    fun snap(x: Float, y: Float, els: List<Element>, tol: Float, skip: Set<Int> = emptySet()): Hit? {
        var best: Hit? = null
        var bd = Float.MAX_VALUE
        var line: Hit? = null
        var ld = Float.MAX_VALUE
        // Wierzchołek i środek w zasięgu zawsze wygrywają z „punktem na kresce” (jak przyciąganie w programach CAD).
        fun offer(px: Float, py: Float, kind: Kind, id: Int) {
            val d = hypot(px - x, py - y)
            if (d > tol) return
            if (kind == Kind.ON_STROKE) { if (d < ld) { ld = d; line = Hit(px, py, kind, id) } }
            else if (d < bd) { bd = d; best = Hit(px, py, kind, id) }
        }
        for (e in els) {
            if (e.id in skip || !e.bounds.inflate(tol).contains(x, y)) continue
            when (e) {
                is Stroke -> {
                    val p = e.pts; val n = e.count
                    if (n == 0) continue
                    val frame = ShapeBox.frameOf(e)
                    if (frame != null) offer(frame.cx, frame.cy, Kind.CENTER, e.id)
                    val c = e.ctrl
                    if (c != null) {
                        for (i in 0 until c.size / 2 step 3) offer(c[2 * i], c[2 * i + 1], Kind.VERTEX, e.id)
                    } else if (e.poly) {
                        for (i in 0 until n) offer(p[3 * i], p[3 * i + 1], Kind.VERTEX, e.id)
                    } else if (!e.ellipse) {
                        offer(p[0], p[1], Kind.VERTEX, e.id); offer(p[3 * n - 3], p[3 * n - 2], Kind.VERTEX, e.id)
                    }
                    nearestOnStroke(e, x, y)?.let { offer(it[0], it[1], Kind.ON_STROKE, e.id) }
                }
                is BoxElement -> offer(e.cx, e.cy, Kind.CENTER, e.id)
                else -> {}
            }
        }
        return best ?: line
    }

    /** Najbliższy punkt osi kreski (łamanej przez jej punkty). */
    fun nearestOnStroke(s: Stroke, x: Float, y: Float): FloatArray? {
        val p = s.pts; val n = s.count
        if (n == 0) return null
        if (n == 1) return floatArrayOf(p[0], p[1])
        var best = Float.MAX_VALUE; var bx = p[0]; var by = p[1]
        for (i in 0 until n - 1) {
            val ax = p[3 * i]; val ay = p[3 * i + 1]; val cx = p[3 * i + 3]; val cy = p[3 * i + 4]
            val dx = cx - ax; val dy = cy - ay
            val l2 = dx * dx + dy * dy
            val t = if (l2 < 1e-9f) 0f else (((x - ax) * dx + (y - ay) * dy) / l2).coerceIn(0f, 1f)
            val qx = ax + t * dx; val qy = ay + t * dy
            val d = (qx - x) * (qx - x) + (qy - y) * (qy - y)
            if (d < best) { best = d; bx = qx; by = qy }
        }
        return floatArrayOf(bx, by)
    }
}

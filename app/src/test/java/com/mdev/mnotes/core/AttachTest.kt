package com.mdev.mnotes.core

import com.mdev.mnotes.core.geom.Attach
import com.mdev.mnotes.core.model.Ids
import com.mdev.mnotes.core.model.PenKind
import com.mdev.mnotes.core.model.Stroke
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** F22: przyczepianie końców do innych obiektów. */
class AttachTest {
    private fun stroke(vararg xy: Float, poly: Boolean = true, ellipse: Boolean = false): Stroke {
        val pts = FloatArray(xy.size / 2 * 3)
        for (i in 0 until xy.size / 2) { pts[3 * i] = xy[2 * i]; pts[3 * i + 1] = xy[2 * i + 1]; pts[3 * i + 2] = 1f }
        return Stroke(Ids.next(), PenKind.BALLPOINT, 0, 1f, pts, poly = poly, ellipse = ellipse)
    }

    private val tri = stroke(0f, 0f, 100f, 0f, 50f, 80f, 0f, 0f)

    @Test fun vertexWinsOverPointOnStroke() {
        val h = Attach.snap(97f, 3f, listOf(tri), 8f)!!
        assertEquals(Attach.Kind.VERTEX, h.kind)
        assertEquals(100f, h.x, 0f); assertEquals(0f, h.y, 0f)
    }

    @Test fun pointOnStrokeWhenNoVertexNear() {
        val h = Attach.snap(50f, 4f, listOf(tri), 8f)!!
        assertEquals(Attach.Kind.ON_STROKE, h.kind)
        assertEquals(50f, h.x, 1e-4f); assertEquals(0f, h.y, 1e-4f)
    }

    @Test fun circleCenterAndSkipAndFar() {
        val n = 49
        val xy = FloatArray(2 * n) { k -> val a = 2 * PI * (k / 2) / (n - 1); if (k % 2 == 0) 200f + 40f * cos(a).toFloat() else 200f + 40f * sin(a).toFloat() }
        val circle = stroke(*xy, poly = false, ellipse = true)
        val h = Attach.snap(203f, 198f, listOf(circle), 8f)!!
        assertEquals(Attach.Kind.CENTER, h.kind)
        assertEquals(200f, h.x, 0.5f)
        assertNull(Attach.snap(97f, 3f, listOf(tri), 8f, skip = setOf(tri.id)))
        assertNull(Attach.snap(300f, 300f, listOf(tri, circle), 8f))
    }

    @Test fun freehandStrokeEndsAttach() {
        val s = stroke(10f, 10f, 20f, 15f, 30f, 12f, poly = false)
        val h = Attach.snap(31f, 13f, listOf(s), 6f)!!
        assertEquals(Attach.Kind.VERTEX, h.kind)
        assertEquals(30f, h.x, 0f)
    }
}

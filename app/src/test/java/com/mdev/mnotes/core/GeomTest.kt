package com.mdev.mnotes.core

import com.mdev.mnotes.core.geom.Affine
import com.mdev.mnotes.core.geom.Bounds
import com.mdev.mnotes.core.geom.GeomMath
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeomTest {

    @Test
    fun boundsUnionAndIntersection() {
        val a = Bounds(0f, 0f, 10f, 10f)
        val b = Bounds(5f, 5f, 20f, 8f)
        assertTrue(a.intersects(b))
        assertEquals(Bounds(0f, 0f, 20f, 10f), a.union(b))
        assertEquals(Bounds(5f, 5f, 10f, 8f), a.intersection(b))
        assertNull(a.intersection(Bounds(11f, 0f, 12f, 1f)))
        assertEquals(a, a.union(Bounds.EMPTY))
        assertEquals(a, Bounds.EMPTY.union(a))
        assertTrue(Bounds.EMPTY.isEmpty)
    }

    @Test
    fun boundsOfPoints() {
        val b = Bounds.ofPoints(floatArrayOf(3f, 4f, -1f, 9f, 7f, 2f))
        assertEquals(Bounds(-1f, 2f, 7f, 9f), b)
        assertTrue(Bounds.ofPoints(FloatArray(0), 0).isEmpty)
    }

    @Test
    fun affineCompositionOrder() {
        val m = Affine.scale(2f, 2f).then(Affine.translation(10f, 0f))
        assertEquals(12f, m.mapX(1f, 0f), 1e-5f)
        assertEquals(2f, m.scaleFactor, 1e-5f)
    }

    @Test
    fun affineInvertRoundTrip() {
        val m = Affine.rotation(0.7f, 5f, -3f).then(Affine.scale(1.5f, 1.5f, 2f, 2f)).then(Affine.translation(4f, 9f))
        val inv = m.invert()
        assertNotNull(inv)
        val x = m.mapX(12f, 7f)
        val y = m.mapY(12f, 7f)
        assertEquals(12f, inv!!.mapX(x, y), 1e-3f)
        assertEquals(7f, inv.mapY(x, y), 1e-3f)
        assertNull(Affine(0f, 0f, 0f, 0f, 0f, 0f).invert())
    }

    @Test
    fun affineRotationAboutPivot() {
        val m = Affine.rotation(Math.PI.toFloat() / 2f, 10f, 10f)
        assertEquals(10f, m.mapX(20f, 10f), 1e-4f)
        assertEquals(20f, m.mapY(20f, 10f), 1e-4f)
        assertEquals(Math.PI.toFloat() / 2f, m.rotation, 1e-5f)
    }

    @Test
    fun affineMapBoundsOfRotatedSquare() {
        val bb = Affine.rotation(Math.PI.toFloat() / 4f, 0f, 0f).mapBounds(Bounds(-1f, -1f, 1f, 1f))
        assertEquals(-1.41421f, bb.l, 1e-3f)
        assertEquals(1.41421f, bb.r, 1e-3f)
    }

    @Test
    fun pointInPolygon() {
        val sq = floatArrayOf(0f, 0f, 10f, 0f, 10f, 10f, 0f, 10f)
        assertTrue(GeomMath.pointInPolygon(5f, 5f, sq, 4))
        assertFalse(GeomMath.pointInPolygon(11f, 5f, sq, 4))
        assertFalse(GeomMath.pointInPolygon(5f, -1f, sq, 4))
        val concave = floatArrayOf(0f, 0f, 10f, 0f, 10f, 10f, 5f, 3f, 0f, 10f)
        assertTrue(GeomMath.pointInPolygon(2f, 2f, concave, 5))
        assertFalse(GeomMath.pointInPolygon(5f, 8f, concave, 5))
    }

    @Test
    fun segmentIntersection() {
        assertTrue(GeomMath.segmentsIntersect(0f, 0f, 10f, 10f, 0f, 10f, 10f, 0f))
        assertFalse(GeomMath.segmentsIntersect(0f, 0f, 1f, 1f, 2f, 2f, 3f, 3f))
        assertFalse(GeomMath.segmentsIntersect(0f, 0f, 10f, 0f, 0f, 1f, 10f, 1f))
        assertTrue(GeomMath.segmentsIntersect(0f, 0f, 10f, 0f, 5f, 0f, 5f, 5f))
    }

    @Test
    fun simplifyKeepsCornersDropsCollinear() {
        val pts = floatArrayOf(0f, 0f, 1f, 0f, 2f, 0f, 3f, 0f, 3f, 1f, 3f, 2f, 3f, 3f)
        assertArrayEquals(intArrayOf(0, 3, 6), GeomMath.simplify(pts, 7, 0.01f))
    }

    @Test
    fun distanceToSegment() {
        assertEquals(4f, GeomMath.distSqPointSegment(5f, 2f, 0f, 0f, 10f, 0f), 1e-5f)
        assertEquals(25f, GeomMath.distSqPointSegment(-3f, 4f, 0f, 0f, 10f, 0f), 1e-5f)
        assertEquals(2f, GeomMath.distSqPointSegment(1f, 1f, 0f, 0f, 0f, 0f), 1e-5f)
    }
}

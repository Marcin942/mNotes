package com.mdev.mnotes.core

import com.mdev.mnotes.core.geom.Affine
import com.mdev.mnotes.core.model.Element
import com.mdev.mnotes.core.model.ElementsEdit
import com.mdev.mnotes.core.model.Ids
import com.mdev.mnotes.core.model.ImageEl
import com.mdev.mnotes.core.model.PageContent
import com.mdev.mnotes.core.model.PenKind
import com.mdev.mnotes.core.model.Stroke
import com.mdev.mnotes.core.model.TextBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

internal fun stroke(vararg xy: Float, pen: PenKind = PenKind.BALLPOINT, width: Float = 2f): Stroke {
    val pts = FloatArray(xy.size / 2 * 3)
    for (i in 0 until xy.size / 2) {
        pts[3 * i] = xy[2 * i]; pts[3 * i + 1] = xy[2 * i + 1]; pts[3 * i + 2] = 0.5f
    }
    return Stroke(Ids.next(), pen, 0xFF112233.toInt(), width, pts)
}

internal fun ids(c: PageContent): List<Int> = c.elements.map { it.id }

class PageContentTest {

    private fun sample(n: Int): Pair<PageContent, List<Element>> {
        val list = (0 until n).map { stroke(it * 10f, 0f, it * 10f + 5f, 5f) }
        return PageContent.of(list) to list
    }

    @Test
    fun addAndRevert() {
        val (c, _) = sample(3)
        val extra = listOf(stroke(1f, 1f, 2f, 2f), stroke(3f, 3f, 4f, 4f))
        val e = ElementsEdit.add(c, extra)
        val after = c.apply(e)
        assertEquals(5, after.size)
        assertSame(extra[1], after.elements[4])
        assertEquals(ids(c), ids(after.revert(e)))
    }

    @Test
    fun removeKeepsOrderOnRevert() {
        val (c, l) = sample(5)
        val e = ElementsEdit.remove(c, setOf(l[1].id, l[3].id))
        val after = c.apply(e)
        assertEquals(listOf(l[0].id, l[2].id, l[4].id), ids(after))
        assertEquals(ids(c), ids(after.revert(e)))
    }

    @Test
    fun replaceKeepsPosition() {
        val (c, l) = sample(4)
        val moved = (l[2] as Stroke).transformed(Affine.translation(100f, 0f))
        val e = ElementsEdit.replace(c, mapOf(l[2].id to moved))
        val after = c.apply(e)
        assertSame(moved, after.elements[2])
        assertEquals(ids(c), ids(after))
        assertSame(l[2], after.revert(e).elements[2])
    }

    @Test
    fun bringToFrontAndSendToBack() {
        val (c, l) = sample(5)
        val front = ElementsEdit.bringToFront(c, setOf(l[0].id, l[2].id))
        val a = c.apply(front)
        assertEquals(listOf(l[1].id, l[3].id, l[4].id, l[0].id, l[2].id), ids(a))
        assertEquals(ids(c), ids(a.revert(front)))

        val back = ElementsEdit.sendToBack(c, setOf(l[3].id, l[4].id))
        val b = c.apply(back)
        assertEquals(listOf(l[3].id, l[4].id, l[0].id, l[1].id, l[2].id), ids(b))
        assertEquals(ids(c), ids(b.revert(back)))
    }

    @Test
    fun emptyEditIsNoop() {
        val (c, _) = sample(2)
        assertSame(c, c.apply(ElementsEdit.EMPTY))
        assertSame(c, c.apply(ElementsEdit.bringToFront(c, emptySet())))
    }

    @Test
    fun dirtyBoundsCoverRemovedAndAdded() {
        val (c, l) = sample(2)
        val moved = (l[0] as Stroke).transformed(Affine.translation(500f, 0f))
        val e = ElementsEdit.replace(c, mapOf(l[0].id to moved))
        assertTrue(e.dirtyBounds.contains(l[0].bounds))
        assertTrue(e.dirtyBounds.contains(moved.bounds))
    }

    @Test
    fun strokeTransformScalesWidth() {
        val s = stroke(0f, 0f, 10f, 0f, width = 2f)
        val t = s.transformed(Affine.scale(3f, 3f))
        assertEquals(6f, t.width, 1e-4f)
        assertEquals(30f, t.pts[3], 1e-4f)
        assertEquals(s.id, t.id)
    }

    @Test
    fun boxTransformMovesAndRotates() {
        val img = ImageEl(Ids.next(), "a.webp", 100f, 50f, 40f, 20f)
        val moved = img.transformed(Affine.translation(10f, 5f))
        assertEquals(110f, moved.cx, 1e-4f)
        assertEquals(55f, moved.cy, 1e-4f)
        val turned = img.transformed(Affine.rotation(Math.PI.toFloat() / 2f, 100f, 50f))
        assertEquals(Math.PI.toFloat() / 2f, turned.rot, 1e-4f)
        assertEquals(40f, turned.w, 1e-3f)
        assertTrue(turned.containsPoint(100f, 50f + 19f))
        assertTrue(!turned.containsPoint(100f + 19f, 50f))
    }

    @Test
    fun textScalingScalesFont() {
        val t = TextBox(Ids.next(), 50f, 50f, 100f, 30f, 0f, "abc", 12f, 0xFF000000.toInt())
        val s = t.transformed(Affine.scale(2f, 2f, 50f, 50f))
        assertEquals(24f, s.size, 1e-4f)
        assertEquals(200f, s.w, 1e-3f)
    }
}

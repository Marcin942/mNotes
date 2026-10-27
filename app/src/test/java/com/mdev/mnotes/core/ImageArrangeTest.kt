package com.mdev.mnotes.core

import com.mdev.mnotes.core.edit.ImageArrange
import com.mdev.mnotes.core.geom.Bounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** F23: porządkowanie zdjęć w równych wierszach. */
class ImageArrangeTest {
    private val area = Bounds(40f, 100f, 560f, 700f)

    private fun check(aspects: List<Float>) {
        val s = ImageArrange.arrange(aspects, area)
        assertEquals(aspects.size, s.size)
        for ((i, slot) in s.withIndex()) {
            assertEquals("proporcje $i", aspects[i], slot.w / slot.h, 1e-3f)
            assertTrue("w obszarze $i", slot.cx - slot.w / 2 >= area.l - 0.01f && slot.cx + slot.w / 2 <= area.r + 0.01f)
            assertTrue(slot.cy - slot.h / 2 >= area.t - 0.01f && slot.cy + slot.h / 2 <= area.b + 0.01f)
        }
        // Bez nakładania się.
        for (i in s.indices) for (j in i + 1 until s.size) {
            val a = s[i]; val b = s[j]
            val overlap = kotlin.math.abs(a.cx - b.cx) < (a.w + b.w) / 2 - 0.01f && kotlin.math.abs(a.cy - b.cy) < (a.h + b.h) / 2 - 0.01f
            assertTrue("nakładają się $i i $j", !overlap)
        }
    }

    @Test fun variousSetsFitWithoutOverlap() {
        check(listOf(1.5f))
        check(listOf(1.33f, 0.75f))
        check(listOf(1.5f, 1.5f, 0.66f, 1f, 1.77f))
        check(List(12) { if (it % 3 == 0) 0.7f else 1.4f })
    }

    @Test fun orderIsKeptInReadingOrder() {
        val s = ImageArrange.arrange(listOf(1.5f, 1.5f, 1.5f, 1.5f), area)
        for (i in 1 until s.size) assertTrue(s[i].cy > s[i - 1].cy + 1f || (kotlin.math.abs(s[i].cy - s[i - 1].cy) < 1f && s[i].cx > s[i - 1].cx))
    }

    @Test fun wideImagesStackInRows() {
        // Cztery panoramy w wysokim obszarze: więcej niż jeden wiersz daje większe obrazy.
        val s = ImageArrange.arrange(List(4) { 3f }, Bounds(0f, 0f, 300f, 600f))
        assertTrue(s.map { it.cy }.distinct().size > 1)
    }
}

package com.mdev.mnotes.core

import com.mdev.mnotes.core.model.ImageFx
import com.mdev.mnotes.core.model.ImageTone
import com.mdev.mnotes.core.model.WhiteBalance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** F13: jasność, temperatura i odcień sprzężone z R/G/B; F15: autobalans bieli. */
class ImageToneTest {

    @Test fun neutralIsUnitGains() {
        val t = ImageTone.of(ImageFx.NONE)
        assertEquals(1f, t.brightness, 1e-6f); assertEquals(0f, t.temperature, 1e-6f); assertEquals(0f, t.tint, 1e-6f)
    }

    @Test fun toneAndRgbRoundTrip() {
        for ((r, g, b) in listOf(Triple(1.2f, 0.9f, 0.7f), Triple(0.5f, 1.5f, 1f), Triple(1f, 1f, 1.3f))) {
            val back = ImageTone.of(r, g, b).gains()
            assertEquals(r, back[0], 1e-5f); assertEquals(g, back[1], 1e-5f); assertEquals(b, back[2], 1e-5f)
        }
    }

    @Test fun eachSliderMovesOnlyItsOwnComponent() {
        val fx = ImageFx(r = 1.1f, g = 0.95f, b = 0.9f)
        val warmer = ImageTone.withTemperature(fx, 0.3f)
        val t0 = ImageTone.of(fx); val t1 = ImageTone.of(warmer)
        assertEquals(t0.brightness, t1.brightness, 1e-5f)
        assertEquals(t0.tint, t1.tint, 1e-5f)
        assertEquals(0.3f, t1.temperature, 1e-5f)
        assertTrue(warmer.r > fx.r && warmer.b < fx.b)
        val brighter = ImageTone.withBrightness(fx, 1.4f)
        assertEquals(t0.temperature, ImageTone.of(brighter).temperature, 1e-5f)
        assertTrue(brighter.r > fx.r && brighter.g > fx.g && brighter.b > fx.b)
    }

    @Test fun gainsAreClamped() {
        val g = ImageTone(1.9f, 0.8f, 0f).gains()
        assertEquals(ImageFx.GAIN_MAX, g[0], 0f)
        assertTrue(g.all { it in 0f..ImageFx.GAIN_MAX })
    }

    private fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    @Test fun whitePatchMakesYellowishPaperWhite() {
        // Kartka przy żarówce (żółtawa) z ciemnym pismem: tło ma wyjść neutralne.
        val px = IntArray(1000) { if (it % 10 == 0) rgb(40, 35, 25) else rgb(235, 215, 160) }
        val g = WhiteBalance.whitePatch(px)
        val r = 235 * g[0]; val gg = 215 * g[1]; val b = 160 * g[2]
        assertEquals(r, gg, 3f); assertEquals(gg, b, 3f)
        assertTrue(g[2] > g[0])
    }

    @Test fun grayWorldNeutralizesCast() {
        val px = IntArray(500) { rgb(150 + it % 50, 120 + it % 50, 90 + it % 50) }
        val g = WhiteBalance.grayWorld(px)
        assertTrue(g[0] < 1f && g[2] > 1f)
        val flat = WhiteBalance.grayWorld(IntArray(10) { rgb(100, 100, 100) })
        assertEquals(1f, flat[0], 1e-5f); assertEquals(1f, flat[2], 1e-5f)
    }
}

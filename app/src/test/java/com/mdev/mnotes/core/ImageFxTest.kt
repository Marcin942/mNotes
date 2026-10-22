package com.mdev.mnotes.core

import com.mdev.mnotes.core.codec.PageCodec
import com.mdev.mnotes.core.model.Ids
import com.mdev.mnotes.core.model.ImageEl
import com.mdev.mnotes.core.model.ImageFx
import com.mdev.mnotes.core.model.PageContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageFxTest {

    /** Stosuje macierz 4×5 do piksela (R, G, B w 0..255) tak jak `ColorMatrixColorFilter`, z obcięciem do 0..255. */
    private fun apply(m: FloatArray, r: Int, g: Int, b: Int): IntArray = IntArray(3) { row ->
        (m[row * 5] * r + m[row * 5 + 1] * g + m[row * 5 + 2] * b + m[row * 5 + 4]).coerceIn(0f, 255f).let { Math.round(it) }
    }

    @Test
    fun noEffectsIsIdentity() {
        assertTrue(ImageFx.NONE.isNone)
        assertEquals(listOf(10, 120, 250), apply(ImageFx.NONE.matrix(), 10, 120, 250).toList())
    }

    @Test
    fun invertFlipsChannels() {
        assertEquals(listOf(245, 135, 5), apply(ImageFx(invert = true).matrix(), 10, 120, 250).toList())
    }

    @Test
    fun grayscaleGivesEqualChannels() {
        val out = apply(ImageFx(mode = ImageFx.MODE_GRAY).matrix(), 200, 40, 90)
        assertTrue(out[0] == out[1] && out[1] == out[2])
    }

    @Test
    fun blackAndWhiteSplitsAtThreshold() {
        val fx = ImageFx(mode = ImageFx.MODE_BW, threshold = 0.5f)
        assertEquals(listOf(255, 255, 255), apply(fx.matrix(), 230, 230, 230).toList())
        assertEquals(listOf(0, 0, 0), apply(fx.matrix(), 40, 40, 40).toList())
        assertEquals(listOf(0, 0, 0), apply(fx.copy(threshold = 0.95f).matrix(), 230, 230, 230).toList())
    }

    @Test
    fun channelGainsScaleIndependently() {
        val out = apply(ImageFx(r = 0f, g = 2f, b = 1f).matrix(), 100, 100, 100)
        assertEquals(listOf(0, 200, 100), out.toList())
    }

    @Test
    fun invertComesAfterGains() {
        // R wyzerowane, potem odwrócone: czerwień kończy jako 255
        assertEquals(listOf(255, 155, 155), apply(ImageFx(invert = true, r = 0f).matrix(), 100, 100, 100).toList())
    }

    @Test
    fun codecRoundTripKeepsEffects() {
        val fx = ImageFx(invert = true, mode = ImageFx.MODE_BW, threshold = 0.3f, r = 0.5f, g = 1.5f, b = 2f)
        val plain = ImageEl(Ids.next(), "a.webp", 100f, 100f, 50f, 40f)
        val styled = ImageEl(Ids.next(), "b.webp", 200f, 100f, 50f, 40f, fx = fx)
        val back = PageCodec.decode(PageCodec.encode(PageContent.of(listOf(plain, styled)))).elements.map { it as ImageEl }
        assertTrue(back[0].fx.isNone)
        assertEquals(fx.invert, back[1].fx.invert)
        assertEquals(fx.mode, back[1].fx.mode)
        assertEquals(fx.threshold, back[1].fx.threshold, 0.005f)
        assertEquals(fx.r, back[1].fx.r, 0.005f)
        assertEquals(fx.g, back[1].fx.g, 0.005f)
        assertEquals(fx.b, back[1].fx.b, 0.005f)
    }

    @Test
    fun copyKeepsEffects() {
        val fx = ImageFx(invert = true)
        assertEquals(fx, ImageEl(Ids.next(), "a.webp", 0f, 0f, 10f, 10f, fx = fx).copy(cx = 5f).fx)
    }
}

package com.mdev.mnotes.core.model

/**
 * Jasność, temperatura i odcień obrazu sprzężone z wzmocnieniami R/G/B (F13). W pliku zostają tylko wzmocnienia
 * ([ImageFx.r], [g], [b]), a te trzy suwaki to inny układ tych samych trzech liczb — liniowy i dokładnie odwracalny,
 * więc ruch jednym suwakiem przesuwa R/G/B, a ruch R/G/B przesuwa jasność, temperaturę i odcień:
 *  - jasność = średnia wzmocnień,
 *  - temperatura = (R − B) / 2 (ciepło: więcej czerwieni, mniej błękitu),
 *  - odcień = G − (R + B) / 2 (zieleń ↔ magenta).
 */
data class ImageTone(val brightness: Float, val temperature: Float, val tint: Float) {

    /** Wzmocnienia R, G, B (obcięte do 0..[ImageFx.GAIN_MAX]). */
    fun gains(): FloatArray {
        val r = brightness + temperature - tint / 3f
        val g = brightness + 2f * tint / 3f
        val b = brightness - temperature - tint / 3f
        return floatArrayOf(r.coerceIn(0f, ImageFx.GAIN_MAX), g.coerceIn(0f, ImageFx.GAIN_MAX), b.coerceIn(0f, ImageFx.GAIN_MAX))
    }

    companion object {
        fun of(fx: ImageFx): ImageTone = of(fx.r, fx.g, fx.b)
        fun of(r: Float, g: Float, b: Float) = ImageTone((r + g + b) / 3f, (r - b) / 2f, g - (r + b) / 2f)

        /** [fx] z nową wartością jednej składowej; dwie pozostałe zostają (o ile pozwala zakres wzmocnień). */
        fun withBrightness(fx: ImageFx, v: Float) = apply(fx, of(fx).copy(brightness = v))
        fun withTemperature(fx: ImageFx, v: Float) = apply(fx, of(fx).copy(temperature = v))
        fun withTint(fx: ImageFx, v: Float) = apply(fx, of(fx).copy(tint = v))

        private fun apply(fx: ImageFx, t: ImageTone): ImageFx { val g = t.gains(); return fx.copy(r = g[0], g = g[1], b = g[2]) }
    }
}

/**
 * Autobalans bieli tła zdjęcia (F15): zdjęcie kartki albo tablicy przy żarówce wychodzi żółte, przy świetlówce sine.
 * Dwie proste metody na pikselach ARGB (zmniejszony obraz):
 *  - [whitePatch]: najjaśniejsze piksele (papier, tło) mają być bielą — dobre dla notatek i kartek,
 *  - [grayWorld]: średni kolor ma być szary — dobre dla zdjęć bez wyraźnego białego tła.
 * Wynik to wzmocnienia R, G, B dla [ImageFx] (0,5–2).
 */
object WhiteBalance {
    private const val MIN = 0.5f

    fun whitePatch(px: IntArray, top: Float = 0.02f, target: Float = 245f): FloatArray {
        if (px.isEmpty()) return floatArrayOf(1f, 1f, 1f)
        // Histogram jasności, żeby znaleźć próg najjaśniejszych [top] pikseli bez sortowania.
        val hist = IntArray(766)
        for (c in px) hist[((c shr 16) and 255) + ((c shr 8) and 255) + (c and 255)]++
        val want = (px.size * top).toInt().coerceAtLeast(1)
        var acc = 0; var thr = 765
        while (thr > 0 && acc + hist[thr] < want) { acc += hist[thr]; thr-- }
        var sr = 0L; var sg = 0L; var sb = 0L; var n = 0
        for (c in px) {
            val r = (c shr 16) and 255; val g = (c shr 8) and 255; val b = c and 255
            if (r + g + b >= thr) { sr += r; sg += g; sb += b; n++ }
        }
        if (n == 0) return floatArrayOf(1f, 1f, 1f)
        return floatArrayOf(gain(target, sr.toFloat() / n), gain(target, sg.toFloat() / n), gain(target, sb.toFloat() / n))
    }

    fun grayWorld(px: IntArray): FloatArray {
        if (px.isEmpty()) return floatArrayOf(1f, 1f, 1f)
        var sr = 0L; var sg = 0L; var sb = 0L
        for (c in px) { sr += (c shr 16) and 255; sg += (c shr 8) and 255; sb += c and 255 }
        val r = sr.toFloat() / px.size; val g = sg.toFloat() / px.size; val b = sb.toFloat() / px.size
        val m = (r + g + b) / 3f
        return floatArrayOf(gain(m, r), gain(m, g), gain(m, b))
    }

    private fun gain(target: Float, have: Float): Float = if (have < 1f) 1f else (target / have).coerceIn(MIN, ImageFx.GAIN_MAX)
}

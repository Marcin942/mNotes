// Algorytmy kredy na podstawie programu kreda — autor i licencja do potwierdzenia przed publikacją (app/src/kreda/NOTICE.md).

package com.mdev.mnotes.core.image

import kotlin.math.max

/**
 * Jak zdekodować zdjęcie [w]×[h], żeby wyszło od razu z dłuższym bokiem ≤ `longest`: próbkowanie potęgą dwójki
 * ([sample], `inSampleSize`) i dokończenie skalowania w dekoderze (`inDensity` → `inTargetDensity`). Wcześniej 12 Mpx
 * szło w pełnym rozmiarze do [Raster] i dopiero [Raster.fit] je zmniejszało: trzy pełne klatki naraz zamiast jednej.
 */
class DecodePlan(val sample: Int, val density: Int = 0, val targetDensity: Int = 0) {
    /** Czy dekoder ma jeszcze skalować po próbkowaniu (gęstości 0 = bez skalowania). */
    val scales: Boolean get() = density > 0 && targetDensity in 1 until density

    companion object {
        fun of(w: Int, h: Int, longest: Int): DecodePlan {
            require(longest > 0) { "longest > 0" }
            val side = max(w, h)
            if (side <= 0) return DecodePlan(1)
            var sample = 1
            while (side / (sample * 2) >= longest) sample *= 2
            val sampled = (side + sample - 1) / sample
            return if (sampled > longest) DecodePlan(sample, sampled, longest) else DecodePlan(sample)
        }
    }
}

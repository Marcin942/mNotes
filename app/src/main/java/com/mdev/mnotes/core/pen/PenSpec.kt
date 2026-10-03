package com.mdev.mnotes.core.pen

import com.mdev.mnotes.core.model.PenKind
import kotlin.math.pow

/**
 * Strojenie pióra. Szerokości w pt (0,5 mm ≈ 1,42 pt). Współczynnik grubości punktu:
 * `minFactor + (1 - minFactor) * nacisk^gamma`, osłabiany prędkością (`speedK`), a na końcach zbiegany (`taper*`,
 * długość zbiegania jako wielokrotność grubości).
 */
class PenSpec(
    val kind: PenKind,
    val minWidth: Float,
    val maxWidth: Float,
    val defaultWidth: Float,
    val minFactor: Float,
    val gamma: Float,
    val speedK: Float,
    val taperStart: Float,
    val taperEnd: Float,
    /** Domyślne wygładzanie 0..1 (0 = brak). */
    val smoothing: Float,
    /** Krycie przy rysowaniu (zakreślacz 0,5; ołówek 0,92). */
    val alpha: Float,
) {
    /** Pióro o stałej grubości — rysowane jako gruba linia, bez wielokąta. */
    val constantWidth: Boolean get() = minFactor >= 0.999f && speedK == 0f && taperStart == 0f && taperEnd == 0f

    fun factor(pressure: Float, speedPtPerMs: Float): Float {
        val p = pressure.coerceIn(0f, 1f)
        val base = minFactor + (1f - minFactor) * p.pow(gamma)
        val f = if (speedK > 0f) base / (1f + speedK * speedPtPerMs) else base
        return f.coerceIn(0.04f, 1f)
    }

    companion object {
        private val table: Map<PenKind, PenSpec> = mapOf(
            PenKind.BALLPOINT to PenSpec(PenKind.BALLPOINT, 0.5f, 6f, 1.42f, 1f, 1f, 0f, 0f, 0f, 0.35f, 1f),
            PenKind.FOUNTAIN to PenSpec(PenKind.FOUNTAIN, 0.8f, 10f, 2.83f, 0.30f, 0.85f, 0.30f, 0.6f, 2.2f, 0.45f, 1f),
            PenKind.BRUSH to PenSpec(PenKind.BRUSH, 2f, 30f, 8.5f, 0.12f, 1.25f, 0.20f, 0.5f, 3.0f, 0.55f, 1f),
            PenKind.HIGHLIGHTER to PenSpec(PenKind.HIGHLIGHTER, 6f, 40f, 14.17f, 1f, 1f, 0f, 0f, 0f, 0.5f, 0.5f),
            PenKind.PENCIL to PenSpec(PenKind.PENCIL, 0.6f, 8f, 1.98f, 0.45f, 1f, 0.06f, 0.4f, 1.2f, 0.25f, 0.92f),
        )

        fun of(kind: PenKind): PenSpec = table.getValue(kind)
    }
}

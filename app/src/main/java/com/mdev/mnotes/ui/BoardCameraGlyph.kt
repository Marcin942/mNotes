package com.mdev.mnotes.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import kotlin.math.cos
import kotlin.math.sin

/**
 * Ikona przepływu „Zdjęcie tablicy” (2b-A14 / 2b-B3): tablica z falką kredy i aparat w prawym dolnym rogu. Te same
 * role i grubości co glify piór i gumki (2b-A1): rama tablicy jako [GlyphRole.BODY], kreda jako [GlyphRole.MARK],
 * obiektyw jako [GlyphRole.DETAIL]; jednobarwna, jak gumka i lasso.
 * Odróżnia się od „Wstaw obraz” (ramka z górami) tablicą z pismem i aparatem. Siatka 24 × 24, czysta geometria.
 */
object BoardCameraGlyph {
    private fun xy(role: GlyphRole, vararg p: Float, closed: Boolean = true) = GlyphPart(role, p, closed)

    private fun ring(role: GlyphRole, cx: Float, cy: Float, r: Float, n: Int = 14) =
        GlyphPart(role, FloatArray(2 * n) { k -> val a = 2 * Math.PI * (k / 2) / n; if (k % 2 == 0) cx + r * cos(a).toFloat() else cy + r * sin(a).toFloat() })

    /** Falka kredy na tablicy: jedna linia jak pismo, nie prostokąt. */
    private fun chalk(): GlyphPart {
        val n = 14
        return GlyphPart(GlyphRole.MARK, FloatArray(2 * (n + 1)) { k ->
            val t = (k / 2) / n.toFloat()
            if (k % 2 == 0) 4.6f + 9.6f * t else 8.6f - 1.6f * sin(t * 2 * Math.PI).toFloat()
        }, closed = false)
    }

    val parts: List<GlyphPart> = listOf(
        // tablica z półką (dolna listwa), lekko szersza niż wysoka
        xy(GlyphRole.BODY, 2.2f, 3.2f, 19.6f, 3.2f, 19.6f, 14.2f, 2.2f, 14.2f),
        xy(GlyphRole.DETAIL, 1.4f, 15.6f, 11.6f, 15.6f, closed = false),
        chalk(),
        xy(GlyphRole.MARK, 4.8f, 11.6f, 9.4f, 11.6f, closed = false),
        // aparat: korpus z garbem wizjera i obiektyw
        xy(GlyphRole.BODY, 12.4f, 14.8f, 15.2f, 14.8f, 16.2f, 13.2f, 19.4f, 13.2f, 20.4f, 14.8f, 22.6f, 14.8f, 22.6f, 22.2f, 12.4f, 22.2f),
        ring(GlyphRole.DETAIL, 17.5f, 18.4f, 2.3f),
    )

    /** Najmniejszy prostokąt obejmujący punkty: minX, minY, maxX, maxY (do testu siatki). */
    fun bounds(): FloatArray {
        var x0 = Float.MAX_VALUE; var y0 = Float.MAX_VALUE; var x1 = -Float.MAX_VALUE; var y1 = -Float.MAX_VALUE
        for (p in parts) {
            var i = 0
            while (i < p.pts.size) { x0 = minOf(x0, p.pts[i]); x1 = maxOf(x1, p.pts[i]); y0 = minOf(y0, p.pts[i + 1]); y1 = maxOf(y1, p.pts[i + 1]); i += 2 }
        }
        return floatArrayOf(x0, y0, x1, y1)
    }
}

/** Ikona „Zdjęcie tablicy” w kolorze [color] (jednobarwna jak gumka i lasso). */
@Composable
fun BoardCameraIcon(color: Color, modifier: Modifier = Modifier) = GlyphCanvas(BoardCameraGlyph.parts, color, color, modifier)

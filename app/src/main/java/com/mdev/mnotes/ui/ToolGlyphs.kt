package com.mdev.mnotes.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/*
 * Ikony narzędzi rysowane w kodzie (F7): lasso z przerywanej linii, jak na pasku zaznaczenia, i trzy tryby gumki.
 * Rysunek liczy się w siatce 24 × 24, jak ikony Material, i skaluje do rozmiaru przycisku.
 */

private fun DrawScope.u(v: Float) = v * size.minDimension / 24f

private fun DrawScope.dashed(color: Color, path: Path, w: Float = 1.9f) = drawPath(
    path, color,
    style = Stroke(u(w), cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(u(3.2f), u(2.6f)))),
)

/** Pętla lassa (swobodna albo prostokątna) z przerywanej linii. */
private fun DrawScope.loop(color: Color, rect: Boolean, w: Float = 1.9f) {
    val p = Path()
    if (rect) {
        p.addRoundRect(androidx.compose.ui.geometry.RoundRect(u(3f), u(4.5f), u(21f), u(17.5f), u(2f), u(2f)))
    } else {
        p.moveTo(u(6f), u(17.5f))
        p.cubicTo(u(1.5f), u(14f), u(2.5f), u(4f), u(12f), u(4f))
        p.cubicTo(u(21.5f), u(4f), u(22.5f), u(14f), u(14f), u(16f))
        p.cubicTo(u(10f), u(17f), u(7.5f), u(16.5f), u(6f), u(17.5f))
    }
    dashed(color, p, w)
    if (!rect) drawLine(color, Offset(u(6f), u(17.5f)), Offset(u(7.5f), u(21f)), u(w), StrokeCap.Round)
}

@Composable
fun LassoGlyph(rect: Boolean, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) { loop(color, rect) }
}

/** Mała gumka (ukośny klocek) w prostokącie [x, y, w, h] siatki 24. */
private fun DrawScope.eraserBlock(color: Color, cx: Float, cy: Float, s: Float) {
    val p = Path()
    // Klocek pochylony o 45°: tył pełny, czubek obrysowany.
    p.moveTo(u(cx - 0.9f * s), u(cy + 0.1f * s)); p.lineTo(u(cx + 0.1f * s), u(cy - 0.9f * s))
    p.lineTo(u(cx + 0.9f * s), u(cy - 0.1f * s)); p.lineTo(u(cx - 0.1f * s), u(cy + 0.9f * s)); p.close()
    drawPath(p, color, style = Stroke(u(1.8f), join = StrokeJoin.Round))
    val back = Path()
    back.moveTo(u(cx + 0.1f * s), u(cy - 0.9f * s)); back.lineTo(u(cx + 0.9f * s), u(cy - 0.1f * s))
    back.lineTo(u(cx + 0.45f * s), u(cy + 0.35f * s)); back.lineTo(u(cx - 0.35f * s), u(cy - 0.45f * s)); back.close()
    drawPath(back, color)
}

/** Tryb gumki: 0 obiektowa (klocek nad całą kreską), 1 pikselowa (kreska przerwana pod klockiem), 2 lasso gumki. */
@Composable
fun EraserModeGlyph(mode: Int, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        when (mode) {
            0 -> {
                eraserBlock(color, 13f, 9f, 7.5f)
                drawLine(color, Offset(u(3f), u(20f)), Offset(u(21f), u(20f)), u(1.6f), StrokeCap.Round)
            }
            1 -> {
                eraserBlock(color, 12f, 9f, 6.5f)
                drawLine(color, Offset(u(2.5f), u(20f)), Offset(u(8f), u(20f)), u(1.8f), StrokeCap.Round)
                drawLine(color, Offset(u(16f), u(20f)), Offset(u(21.5f), u(20f)), u(1.8f), StrokeCap.Round)
                for (i in 0..2) drawRect(color, Offset(u(9.5f + 2.2f * i), u(19.2f + (i % 2) * 1.2f)), Size(u(1.2f), u(1.2f)))
            }
            else -> {
                loop(color, rect = false, w = 1.6f)
                eraserBlock(color, 12f, 10.5f, 4f)
            }
        }
    }
}

/** Rozmiar gumki: okrąg tym większy, im większa gumka (pierwiastek, żeby 2 i 40 pt zmieściły się w przycisku). */
@Composable
fun EraserSizeGlyph(pt: Float, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val r = u((1.6f + 1.45f * kotlin.math.sqrt(pt.coerceIn(1f, 60f))).coerceAtMost(11f))
        drawCircle(color, r, center, style = Stroke(u(1.7f)))
    }
}

package com.mdev.mnotes.ui

import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCut
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
import androidx.compose.ui.graphics.drawscope.translate

/*
 * Ikony narzędzi rysowane w kodzie (F7): lasso, cztery tryby gumki, krzywa, kątomierz. Rysunek liczy się w siatce
 * 24 × 24, jak ikony Material, i skaluje do rozmiaru przycisku. Gumka i lasso korzystają z części glifów piór
 * ([ToolGlyphParts], 2b-A1), więc mają tę samą grubość i styl co pióra na pasku.
 */

private fun DrawScope.u(v: Float) = v * size.minDimension / 24f

/** Wycinek (3c-B4): nożyczki w przerywanej ramce — prostokątnej albo owalnej (kształt lassa). */
@Composable
fun SnipGlyph(lasso: Boolean, color: Color, modifier: Modifier = Modifier) {
    val cut = androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.Rounded.ContentCut)
    Canvas(modifier) {
        val st = Stroke(u(1.6f), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(u(2.6f), u(2.2f))))
        if (lasso) drawOval(color, Offset(u(1.5f), u(2.5f)), Size(u(21f), u(19f)), style = st)
        else drawRect(color, Offset(u(2f), u(2f)), Size(u(20f), u(20f)), style = st)
        translate(u(5.5f), u(5.5f)) { with(cut) { draw(Size(u(13f), u(13f)), colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(color)) } }
    }
}

@Composable
fun LassoGlyph(rect: Boolean, color: Color, modifier: Modifier = Modifier) = GlyphCanvas(ToolGlyphParts.lasso(rect), color, color, modifier)

/** Tryb gumki: 0 obiektowa, 1 pikselowa, 2 lasso gumki, 3 wycinanie — ta sama gumka, inny znak w rogu. */
@Composable
fun EraserModeGlyph(mode: Int, color: Color, modifier: Modifier = Modifier) = GlyphCanvas(ToolGlyphParts.eraser(mode), color, color, modifier)

/** Krzywa Béziera (F17): łuk w kształcie „S” z dwiema kotwicami i ramionami uchwytów. */
@Composable
fun BezierGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val p = Path()
        p.moveTo(u(4f), u(18f))
        p.cubicTo(u(4f), u(6f), u(20f), u(18f), u(20f), u(6f))
        drawPath(p, color, style = Stroke(u(2f), cap = StrokeCap.Round))
        drawLine(color, Offset(u(4f), u(18f)), Offset(u(4f), u(9f)), u(1.1f), StrokeCap.Round)
        drawLine(color, Offset(u(20f), u(6f)), Offset(u(20f), u(15f)), u(1.1f), StrokeCap.Round)
        drawCircle(color, u(1.9f), Offset(u(4f), u(18f)))
        drawCircle(color, u(1.9f), Offset(u(20f), u(6f)))
        drawCircle(color, u(1.4f), Offset(u(4f), u(9f)), style = Stroke(u(1.1f)))
        drawCircle(color, u(1.4f), Offset(u(20f), u(15f)), style = Stroke(u(1.1f)))
    }
}

/** Kątomierz (A2-13): półkole na linii bazowej, trzy kreski podziałki i promień od środka — jak szkolny przyrząd. */
@Composable
fun ProtractorGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val c = Offset(u(12f), u(17f))
        drawArc(color, 180f, 180f, false, Offset(u(3f), u(8f)), androidx.compose.ui.geometry.Size(u(18f), u(18f)), style = Stroke(u(1.8f), cap = StrokeCap.Round))
        drawLine(color, Offset(u(2.5f), u(17f)), Offset(u(21.5f), u(17f)), u(1.8f), StrokeCap.Round)
        for (deg in intArrayOf(30, 90, 150)) {
            val a = Math.toRadians(deg.toDouble())
            val o = Offset(c.x + u(9f) * kotlin.math.cos(a).toFloat(), c.y - u(9f) * kotlin.math.sin(a).toFloat())
            val i = Offset(c.x + u(6.6f) * kotlin.math.cos(a).toFloat(), c.y - u(6.6f) * kotlin.math.sin(a).toFloat())
            drawLine(color, i, o, u(1.2f), StrokeCap.Round)
        }
        val a = Math.toRadians(55.0)
        drawLine(color, c, Offset(c.x + u(7f) * kotlin.math.cos(a).toFloat(), c.y - u(7f) * kotlin.math.sin(a).toFloat()), u(1.2f), StrokeCap.Round)
        drawCircle(color, u(1.3f), c)
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

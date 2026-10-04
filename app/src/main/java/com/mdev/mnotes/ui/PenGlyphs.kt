package com.mdev.mnotes.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import com.mdev.mnotes.core.model.PenKind

/** Rola części glifu: decyduje o tym, czym i jak jest rysowana. */
enum class GlyphRole {
    /** Wypełnienie kolorem atramentu (końcówka, grafit, włosie), z delikatnym konturem. */
    INK,

    /** Korpus: lekkie wypełnienie i kontur w kolorze narzędzia. */
    BODY,

    /** Cienka kreska detalu (pierścień, ferrule, ścięcie) w kolorze narzędzia. */
    DETAIL,

    /** Wycięcie (szczelina stalówki) — wymazuje to, co pod spodem. */
    CUT,
}

/** Wielokąt w układzie 24×24 (pary x,y); [closed] = kontur zamknięty. */
class GlyphPart(val role: GlyphRole, val pts: FloatArray, val closed: Boolean = true)

/**
 * Geometria dwukolorowych glifów piór: korpus w kolorze narzędzia, końcówka w kolorze aktualnego atramentu.
 * Czysta (bez Compose), więc da się ją sprawdzić testem i wyrenderować w narzędziu podglądu.
 *
 * Wszystkie narzędzia leżą po przekątnej: czubek w (2,5; 21,5), oś w górę i w prawo.
 * Opisujemy je we współrzędnych (u,v): u — wzdłuż osi od czubka, v — w poprzek.
 */
object PenGlyphs {
    private const val R = 0.70710678f
    private const val TIP_X = 2.5f
    private const val TIP_Y = 21.5f

    private fun x(u: Float, v: Float) = TIP_X + R * (u + v)
    private fun y(u: Float, v: Float) = TIP_Y + R * (v - u)

    /** Wielokąt z listy (u,v). */
    private fun poly(role: GlyphRole, vararg uv: Float, closed: Boolean = true): GlyphPart {
        val out = FloatArray(uv.size)
        var i = 0
        while (i < uv.size) { out[i] = x(uv[i], uv[i + 1]); out[i + 1] = y(uv[i], uv[i + 1]); i += 2 }
        return GlyphPart(role, out, closed)
    }

    /** Wielokąt z krzywych: kolejne punkty (u,v), a przy `curve=true` środek łuku kwadratowego przed punktem. */
    private class Outline {
        val pts = ArrayList<Float>()
        fun to(u: Float, v: Float) = apply { pts.add(x(u, v)); pts.add(y(u, v)) }
        fun quad(cu: Float, cv: Float, u: Float, v: Float, steps: Int = 7) = apply {
            val x0 = pts[pts.size - 2]; val y0 = pts[pts.size - 1]
            val cx = x(cu, cv); val cy = y(cu, cv); val x1 = x(u, v); val y1 = y(u, v)
            for (s in 1..steps) {
                val t = s / steps.toFloat(); val a = (1 - t) * (1 - t); val b = 2 * (1 - t) * t; val c = t * t
                pts.add(a * x0 + b * cx + c * x1); pts.add(a * y0 + b * cy + c * y1)
            }
        }
        fun build(role: GlyphRole, closed: Boolean = true) = GlyphPart(role, pts.toFloatArray(), closed)
    }

    private val cache = HashMap<PenKind, List<GlyphPart>>()

    fun parts(kind: PenKind): List<GlyphPart> = cache.getOrPut(kind) {
        when (kind) {
            PenKind.BALLPOINT -> listOf(
                poly(GlyphRole.BODY, 6f, -1.7f, 7.4f, -2.1f, 23f, -2.1f, 23f, 2.1f, 7.4f, 2.1f, 6f, 1.7f),
                poly(GlyphRole.BODY, 23f, -1.2f, 26.4f, -1.2f, 26.4f, 1.2f, 23f, 1.2f),
                poly(GlyphRole.INK, 0f, 0f, 6f, -1.7f, 6f, 1.7f),
                poly(GlyphRole.DETAIL, 10f, -2.1f, 10f, 2.1f, closed = false),
            )

            PenKind.PENCIL -> listOf(
                poly(GlyphRole.BODY, 7f, -2.3f, 22f, -2.3f, 22f, 2.3f, 7f, 2.3f),
                poly(GlyphRole.BODY, 22f, -2.3f, 26.6f, -2.3f, 26.6f, 2.3f, 22f, 2.3f),
                poly(GlyphRole.BODY, 0f, 0f, 7f, -2.3f, 7f, 2.3f),
                poly(GlyphRole.INK, 0f, 0f, 3.6f, -1.2f, 3.6f, 1.2f),
                poly(GlyphRole.DETAIL, 7f, 0f, 22f, 0f, closed = false),
                poly(GlyphRole.DETAIL, 24.2f, -2.3f, 24.2f, 2.3f, closed = false),
            )

            PenKind.FOUNTAIN -> listOf(
                // Stalówka: barki i szczelina; wycięcie robi „oko” oddechowe.
                Outline().to(0f, 0f).quad(4.5f, -0.5f, 9.2f, -2.4f).to(9.2f, 2.4f).quad(4.5f, 0.5f, 0f, 0f).build(GlyphRole.INK),
                poly(GlyphRole.CUT, 0.9f, 0f, 6.0f, 0f, closed = false),
                poly(GlyphRole.CUT, 6.0f, -0.5f, 6.7f, 0f, 6.0f, 0.5f, 5.3f, 0f),
                poly(GlyphRole.BODY, 9.2f, -2.4f, 11.6f, -2.5f, 26f, -2.5f, 26f, 2.5f, 11.6f, 2.5f, 9.2f, 2.4f),
                poly(GlyphRole.DETAIL, 11.6f, -2.5f, 11.6f, 2.5f, closed = false),
                poly(GlyphRole.DETAIL, 13.6f, -2.5f, 13.6f, 2.5f, closed = false),
            )

            PenKind.BRUSH -> listOf(
                Outline().to(0f, 0f).quad(1.8f, -2.4f, 6.8f, -2.2f).to(6.8f, 2.2f).quad(1.8f, 2.4f, 0f, 0f).build(GlyphRole.INK),
                poly(GlyphRole.BODY, 6.8f, -2.2f, 11f, -2.0f, 11f, 2.0f, 6.8f, 2.2f),
                poly(GlyphRole.BODY, 11f, -1.7f, 26.6f, -0.85f, 26.6f, 0.85f, 11f, 1.7f),
                poly(GlyphRole.DETAIL, 8.9f, -2.1f, 8.9f, 2.1f, closed = false),
            )

            PenKind.HIGHLIGHTER -> listOf(
                // Ścięta, skośna końcówka (dłuto) i szeroka obudowa z zatyczką.
                poly(GlyphRole.INK, 0f, -1.9f, 1.6f, 1.9f, 5.2f, 3.0f, 5.2f, -3.0f),
                poly(GlyphRole.BODY, 5.2f, -3.0f, 22f, -3.2f, 22f, 3.2f, 5.2f, 3.0f),
                poly(GlyphRole.BODY, 22f, -3.2f, 25.2f, -2.6f, 25.2f, 2.6f, 22f, 3.2f),
                poly(GlyphRole.DETAIL, 10f, -3.05f, 10f, 3.05f, closed = false),
            )
        }
    }

    /** Czy wszystkie punkty mieszczą się w polu 24×24 (z zapasem na kontur). */
    fun bounds(kind: PenKind): FloatArray {
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        for (p in parts(kind)) {
            var i = 0
            while (i < p.pts.size) {
                minX = minOf(minX, p.pts[i]); maxX = maxOf(maxX, p.pts[i])
                minY = minOf(minY, p.pts[i + 1]); maxY = maxOf(maxY, p.pts[i + 1]); i += 2
            }
        }
        return floatArrayOf(minX, minY, maxX, maxY)
    }
}

/**
 * Glif narzędzia pióra: korpus w [tool] (kolor ikony), końcówka w [ink] (kolor aktualnego atramentu).
 * Kolor atramentu bez przezroczystości — zakreślacz ma w presecie półprzezroczysty, a w ikonie ma być czytelny.
 */
@Composable
fun PenGlyph(kind: PenKind, ink: Color, tool: Color, modifier: Modifier = Modifier) {
    val parts = PenGlyphs.parts(kind)
    val inkOpaque = ink.copy(alpha = 1f)
    Canvas(modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
        val k = size.minDimension / 24f
        fun path(p: GlyphPart): Path {
            val out = Path()
            var i = 0
            while (i < p.pts.size) {
                val px = p.pts[i] * k; val py = p.pts[i + 1] * k
                if (i == 0) out.moveTo(px, py) else out.lineTo(px, py)
                i += 2
            }
            if (p.closed) out.close()
            return out
        }
        val line = 1.35f * k
        for (p in parts) {
            val pa = path(p)
            when (p.role) {
                GlyphRole.BODY -> {
                    drawPath(pa, tool.copy(alpha = tool.alpha * 0.16f), style = Fill)
                    drawPath(pa, tool, style = Stroke(line, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                GlyphRole.INK -> {
                    drawPath(pa, inkOpaque, style = Fill)
                    drawPath(pa, inkOpaque, style = Stroke(0.7f * k, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    // Cienki kontur narzędzia: biały atrament na jasnym tle nadal widać.
                    drawPath(pa, tool.copy(alpha = tool.alpha * 0.55f), style = Stroke(0.8f * k, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                GlyphRole.DETAIL -> drawPath(pa, tool.copy(alpha = tool.alpha * 0.8f), style = Stroke(1.05f * k, cap = StrokeCap.Round, join = StrokeJoin.Round))
                GlyphRole.CUT -> {
                    if (p.closed) drawPath(pa, Color.Black, style = Fill, blendMode = BlendMode.Clear)
                    else drawPath(pa, Color.Black, style = Stroke(0.85f * k, cap = StrokeCap.Round, join = StrokeJoin.Round), blendMode = BlendMode.Clear)
                }
            }
        }
    }
}

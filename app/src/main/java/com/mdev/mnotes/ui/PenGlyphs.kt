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

    /** Znak narzędzia (kreska, nożyczki) pełnym kolorem i grubością konturu korpusu (2b-A1). */
    MARK,

    /** Linia przerywana (pętla lassa) tą samą grubością co [MARK]. */
    DASH,

    /** Małe pełne wypełnienie kolorem narzędzia (okruchy gumki pikselowej). */
    DOT,
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
 * Ikony gumki i lassa w tym samym układzie co pióra (2b-A1: dawne ikony nie pasowały do glifów piór): gumka leży na tej
 * samej przekątnej, z czubkiem w lewym dolnym rogu, tą samą grubością konturu i lekkim wypełnieniem korpusu. Cztery tryby
 * gumki różnią się jednym znakiem w prawym dolnym rogu — cała kreska (obiektowa), okruchy (pikselowa), przerywana pętla
 * (lasso gumki), nożyczki (wycinanie) — a reszta rysunku jest wspólna.
 */
object ToolGlyphParts {
    private const val R = 0.70710678f
    private fun x(u: Float, v: Float) = 2.5f + R * (u + v)
    private fun y(u: Float, v: Float) = 21.5f + R * (v - u)
    private fun uv(role: GlyphRole, vararg p: Float, closed: Boolean = true) =
        GlyphPart(role, FloatArray(p.size) { i -> if (i % 2 == 0) x(p[i], p[i + 1]) else y(p[i - 1], p[i]) }, closed)
    private fun xy(role: GlyphRole, vararg p: Float, closed: Boolean = true) = GlyphPart(role, p, closed)

    /** Okrąg albo elipsa w siatce 24 jako wielokąt. */
    private fun ring(role: GlyphRole, cx: Float, cy: Float, rx: Float, ry: Float = rx, n: Int = 18) =
        GlyphPart(role, FloatArray(2 * n) { k -> val a = 2 * Math.PI * (k / 2) / n; if (k % 2 == 0) cx + rx * kotlin.math.cos(a).toFloat() else cy + ry * kotlin.math.sin(a).toFloat() })

    /** Wspólna gumka: gumowa końcówka i oprawka, rozdzielone opaską; długość jak zakreślacz, żeby stała w rzędzie z piórami. */
    val eraserBase: List<GlyphPart> = listOf(
        uv(GlyphRole.BODY, 0.8f, -2.8f, 7.2f, -3.1f, 7.2f, 3.1f, 0.8f, 2.8f),
        uv(GlyphRole.BODY, 7.2f, -3.1f, 19.5f, -3.1f, 19.5f, 3.1f, 7.2f, 3.1f),
        uv(GlyphRole.DETAIL, 9.6f, -3.1f, 9.6f, 3.1f, closed = false),
    )

    /** Fala (cała kreska) w prawym dolnym rogu. */
    private fun wave(): GlyphPart {
        val n = 16
        return GlyphPart(GlyphRole.MARK, FloatArray(2 * (n + 1)) { k -> val t = (k / 2) / n.toFloat(); if (k % 2 == 0) 13.6f + 8f * t else 18.8f - 1.9f * kotlin.math.sin(t * 2 * Math.PI).toFloat() }, closed = false)
    }

    /** Znak trybu gumki: 0 obiektowa (cała kreska), 1 pikselowa (okruchy), 2 lasso gumki (przerywana pętla), 3 wycinanie (nożyczki). */
    fun eraserMark(mode: Int): List<GlyphPart> = when (mode) {
        0 -> listOf(wave())
        1 -> listOf(
            xy(GlyphRole.DOT, 13.8f, 18.8f, 15.9f, 18.8f, 15.9f, 20.9f, 13.8f, 20.9f),
            xy(GlyphRole.DOT, 17.1f, 15.6f, 19.2f, 15.6f, 19.2f, 17.7f, 17.1f, 17.7f),
            xy(GlyphRole.DOT, 19.4f, 19.6f, 21.5f, 19.6f, 21.5f, 21.7f, 19.4f, 21.7f),
        )
        2 -> listOf(ring(GlyphRole.DASH, 17.7f, 18.6f, 4.0f, 3.2f, n = 24))
        else -> listOf(
            ring(GlyphRole.MARK, 15.5f, 20.6f, 1.5f, n = 12), ring(GlyphRole.MARK, 20.1f, 20.6f, 1.5f, n = 12),
            xy(GlyphRole.MARK, 16.4f, 19.4f, 20.2f, 14.6f, closed = false),
            xy(GlyphRole.MARK, 19.2f, 19.4f, 15.4f, 14.6f, closed = false),
        )
    }

    fun eraser(mode: Int): List<GlyphPart> = eraserBase + eraserMark(mode)

    /**
     * Lasso: przerywana pętla z sznurem zwiniętym w supeł (kropka) — z prostym ogonkiem wyglądało jak dymek komiksu;
     * prostokąt: przerywana ramka z pełnym uchwytem w rogu, jak zaznaczenie w programach graficznych.
     */
    fun lasso(rect: Boolean): List<GlyphPart> = if (rect) listOf(
        xy(GlyphRole.DASH, 3.5f, 4.5f, 19f, 4.5f, 19f, 17f, 3.5f, 17f),
        xy(GlyphRole.DOT, 17.4f, 15.4f, 21.2f, 15.4f, 21.2f, 19.2f, 17.4f, 19.2f),
    ) else listOf(
        ring(GlyphRole.DASH, 12.4f, 10.2f, 8.4f, 6.4f, n = 28),
        xy(GlyphRole.MARK, 7.0f, 15.1f, 6.0f, 17.4f, 7.6f, 19.0f, 6.6f, 20.6f, closed = false),
        ring(GlyphRole.DOT, 6.2f, 21.2f, 1.3f, n = 10),
    )
}

/**
 * Glify konstrukcji narzędzia Geometria (2b-A8) w tym samym stylu co narzędzia: wejścia ciągłą kreską, wynik i linie
 * pomocnicze przerywane, punkty jako kropki.
 */
object GeoGlyphParts {
    private fun xy(role: GlyphRole, vararg p: Float, closed: Boolean = false) = GlyphPart(role, p, closed)
    private fun ring(role: GlyphRole, cx: Float, cy: Float, r: Float, n: Int = 24) =
        GlyphPart(role, FloatArray(2 * n) { k -> val a = 2 * Math.PI * (k / 2) / n; if (k % 2 == 0) cx + r * kotlin.math.cos(a).toFloat() else cy + r * kotlin.math.sin(a).toFloat() })
    private fun dot(cx: Float, cy: Float, r: Float = 1.5f) = ring(GlyphRole.DOT, cx, cy, r, 10)
    private fun arc(cx: Float, cy: Float, r: Float, a0: Float, a1: Float, role: GlyphRole = GlyphRole.MARK): GlyphPart {
        val n = 10
        return GlyphPart(role, FloatArray(2 * (n + 1)) { k -> val a = Math.toRadians((a0 + (a1 - a0) * (k / 2) / n).toDouble()); if (k % 2 == 0) cx + r * kotlin.math.cos(a).toFloat() else cy + r * kotlin.math.sin(a).toFloat() }, false)
    }
    private val tri = xy(GlyphRole.MARK, 3.5f, 19.5f, 20.5f, 19.5f, 8f, 5f, closed = true)

    fun parts(k: com.mdev.mnotes.core.geom.GeoTool.Kind): List<GlyphPart> = when (k) {
        com.mdev.mnotes.core.geom.GeoTool.Kind.COMPASS -> listOf(ring(GlyphRole.MARK, 12f, 12f, 8f), xy(GlyphRole.DASH, 12f, 12f, 17.7f, 6.3f), dot(12f, 12f), dot(17.7f, 6.3f))
        com.mdev.mnotes.core.geom.GeoTool.Kind.CIRCLE3 -> listOf(ring(GlyphRole.DASH, 12f, 12f, 8f), dot(4f, 12f), dot(17.7f, 6.3f), dot(15f, 19.4f))
        com.mdev.mnotes.core.geom.GeoTool.Kind.CENTER -> listOf(ring(GlyphRole.MARK, 12f, 12f, 8.5f), dot(12f, 12f, 2f))
        com.mdev.mnotes.core.geom.GeoTool.Kind.BISECTOR -> listOf(xy(GlyphRole.MARK, 3.5f, 15f, 20.5f, 15f), dot(3.5f, 15f), dot(20.5f, 15f), xy(GlyphRole.DASH, 12f, 3f, 12f, 21.5f), dot(12f, 15f, 1.3f))
        com.mdev.mnotes.core.geom.GeoTool.Kind.PERP -> listOf(xy(GlyphRole.MARK, 3f, 18f, 21f, 18f), dot(12f, 5f), xy(GlyphRole.DASH, 12f, 5f, 12f, 18f), xy(GlyphRole.DETAIL, 12f, 15f, 15f, 15f, 15f, 18f))
        com.mdev.mnotes.core.geom.GeoTool.Kind.PARALLEL -> listOf(xy(GlyphRole.MARK, 3f, 17.5f, 21f, 17.5f), xy(GlyphRole.DASH, 3f, 7.5f, 21f, 7.5f), dot(12f, 7.5f))
        com.mdev.mnotes.core.geom.GeoTool.Kind.ANGLE -> listOf(xy(GlyphRole.MARK, 20.5f, 19f, 4f, 19f, 15f, 5f), arc(4f, 19f, 8f, -52f, 0f))
        com.mdev.mnotes.core.geom.GeoTool.Kind.ANGLE_BISECTOR -> listOf(xy(GlyphRole.MARK, 20.5f, 19f, 4f, 19f, 15f, 5f), xy(GlyphRole.DASH, 4f, 19f, 20.5f, 10.4f))
        com.mdev.mnotes.core.geom.GeoTool.Kind.XLL -> listOf(xy(GlyphRole.MARK, 4f, 19f, 20f, 5f), xy(GlyphRole.MARK, 4f, 6f, 20f, 18f), dot(12.4f, 11.8f, 2f))
        com.mdev.mnotes.core.geom.GeoTool.Kind.XLC -> listOf(ring(GlyphRole.MARK, 12f, 12f, 7.5f), xy(GlyphRole.MARK, 2.5f, 15f, 21.5f, 15f), dot(5.1f, 15f), dot(18.9f, 15f))
        com.mdev.mnotes.core.geom.GeoTool.Kind.XCC -> listOf(ring(GlyphRole.MARK, 9f, 12f, 6.5f), ring(GlyphRole.MARK, 15f, 12f, 6.5f), xy(GlyphRole.DASH, 12f, 2.5f, 12f, 21.5f), dot(12f, 6.8f), dot(12f, 17.2f))
        com.mdev.mnotes.core.geom.GeoTool.Kind.CIRCUM -> listOf(tri, ring(GlyphRole.DASH, 12f, 13.6f, 9.2f))
        com.mdev.mnotes.core.geom.GeoTool.Kind.INCIRCLE -> listOf(tri, ring(GlyphRole.DASH, 9.4f, 15.3f, 3.9f))
        com.mdev.mnotes.core.geom.GeoTool.Kind.ORTHO -> listOf(tri, xy(GlyphRole.DASH, 8f, 5f, 8f, 19.5f), xy(GlyphRole.DASH, 20.5f, 19.5f, 5.2f, 9.6f), dot(8f, 11.3f))
        com.mdev.mnotes.core.geom.GeoTool.Kind.CENTROID -> listOf(tri, xy(GlyphRole.DASH, 8f, 5f, 12f, 19.5f), xy(GlyphRole.DASH, 3.5f, 19.5f, 14.25f, 12.25f), dot(10.7f, 14.7f))
        com.mdev.mnotes.core.geom.GeoTool.Kind.REFLECT -> listOf(xy(GlyphRole.DASH, 12f, 2.5f, 12f, 21.5f), xy(GlyphRole.MARK, 3f, 17f, 9f, 17f, 9f, 7f, closed = true), xy(GlyphRole.DETAIL, 21f, 17f, 15f, 17f, 15f, 7f, closed = true))
        com.mdev.mnotes.core.geom.GeoTool.Kind.ROTATE -> listOf(
            dot(12f, 13f, 1.8f), xy(GlyphRole.MARK, 8f, 16.5f, 16f, 16.5f, 16f, 21.5f, 8f, 21.5f, closed = true),
            arc(12f, 13f, 8f, 190f, 350f), xy(GlyphRole.MARK, 20.9f, 8.8f, 19.9f, 11.6f, 17.9f, 9.3f),
        )
    }
}

/**
 * Rysuje części glifu: [tool] = kolor ikony (korpus, znaki), [ink] = kolor atramentu (tylko [GlyphRole.INK]).
 * Wspólne dla piór, gumki i lassa, więc grubości i wypełnienia są wszędzie te same.
 */
@Composable
fun GlyphCanvas(parts: List<GlyphPart>, ink: Color, tool: Color, modifier: Modifier = Modifier) {
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
                GlyphRole.MARK -> drawPath(pa, tool, style = Stroke(line, cap = StrokeCap.Round, join = StrokeJoin.Round))
                GlyphRole.DASH -> drawPath(
                    pa, tool,
                    style = Stroke(line, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(2.6f * k, 2.2f * k))),
                )
                GlyphRole.DOT -> drawPath(pa, tool, style = Fill)
            }
        }
    }
}

/**
 * Glif narzędzia pióra: korpus w [tool] (kolor ikony), końcówka w [ink] (kolor aktualnego atramentu).
 * Kolor atramentu bez przezroczystości — zakreślacz ma w presecie półprzezroczysty, a w ikonie ma być czytelny.
 */
@Composable
fun PenGlyph(kind: PenKind, ink: Color, tool: Color, modifier: Modifier = Modifier) = GlyphCanvas(PenGlyphs.parts(kind), ink, tool, modifier)
